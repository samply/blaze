(ns blaze.interaction.search-type
  "FHIR search interaction.

  https://www.hl7.org/fhir/http.html#search"
  (:require
   [blaze.anomaly :refer [if-ok when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.db.spec]
   [blaze.fhir.spec.type :as type]
   [blaze.handler.util :as handler-util]
   [blaze.interaction.search.include :as include]
   [blaze.interaction.search.nav :as nav]
   [blaze.interaction.search.params :as params]
   [blaze.interaction.search.query-plan :as query-plan]
   [blaze.interaction.search.util :as search-util]
   [blaze.interaction.search.util.spec]
   [blaze.job.async-interaction.request :as req]
   [blaze.module :as m :refer [reg-collector]]
   [blaze.page-id-cipher.spec]
   [blaze.page-store :as page-store]
   [blaze.page-store.spec]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [prometheus.alpha :as prom]
   [reitit.core :as reitit]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(prom/defhistogram search-duration-seconds
  "Latencies in seconds of the individual phases of building a single type
  search page.

  The `phase` label distinguishes the phases:

   * `compile-query` - compiles the query
   * `scan` - the single-threaded scan that materializes the page of resource
     handles, already triggering the match pulls
   * `pull-matches` - fetches the resource contents of the matches; overlaps the
     `scan` phase because the pulls are triggered during the scan
   * `pull-includes` - fetches the resource contents of the includes

  Comparing `scan` with `pull-matches` shows how much the match pulls overlap the
  scan."
  {:namespace "fhir_interaction"
   :subsystem "search_type"}
  (take 16 (iterate #(* 2 %) 0.0001))
  "phase")

(defn- type-list [db type page-id]
  (if page-id
    (d/type-list db type page-id)
    (d/type-list db type)))

(defn- compile-type-query [db type clauses handling]
  (if (identical? :blaze.preference.handling/strict handling)
    (d/compile-type-query db type clauses)
    (d/compile-type-query-lenient db type clauses)))

(defn- execute-query [db query page-id]
  (if page-id
    (d/execute-query db query page-id)
    (d/execute-query db query)))

(defn- compile-query
  [{:keys [type] :blaze.preference/keys [handling]
    {:keys [clauses]} :params}
   db]
  (if (empty? clauses)
    (ac/completed-future nil)
    (let [timer (prom/timer search-duration-seconds "compile-query")]
      (do-sync [query (compile-type-query db type clauses handling)]
        (prom/observe-duration! timer)
        query))))

(defn- build-page**
  "Materializes one page worth of resource handles while already pulling the
  resource content of the matches.

  Pulls are triggered during the scan via `pull` (a function of a resource
  handle returning a CompletableFuture), so the resource I/O overlaps with the
  single-threaded scan. The next-match handle, which only seeds the next page's
  paging link, is not pulled."
  [pull page-size handles]
  ;; start the pull timer before reducing the handles, because the pulls are
  ;; triggered during the reduction and run concurrently with the rest of the
  ;; scan; it is observed once all match futures complete
  (let [pull-timer (prom/timer search-duration-seconds "pull-matches")
        {:keys [matches match-futures next-match]}
        (reduce
         (fn [{:keys [matches] :as acc} handle]
           (if (< (count matches) page-size)
             (assoc acc
                    :matches (conj! matches handle)
                    :match-futures (conj! (:match-futures acc) (pull handle)))
             ;; the first handle past the page is the next-match: it only seeds
             ;; the next page's paging link, so it is not pulled and ends the
             ;; reduction
             (reduced (assoc acc :next-match handle))))
         {:matches (transient []) :match-futures (transient [])}
         handles)]
    (cond-> {:matches (persistent! matches)
             :match-futures (persistent! match-futures)
             :pull-timer pull-timer}
      next-match (assoc :next-match next-match))))

(defn- build-page* [batch-db pull include-defs page-size handles]
  (let [{:keys [matches] :as res} (build-page** pull page-size handles)]
    (if (:direct include-defs)
      (if-ok [includes (include/add-includes batch-db include-defs matches)]
        (assoc res :includes includes)
        #(if (and (-> % :fhir/issue #{"too-costly"})
                  (> page-size 1))
           (build-page* batch-db pull include-defs (quot page-size 2) handles)
           %))
      res)))

(defn- query-plan-outcome [{:blaze/keys [db]} query]
  (let [plan (d/explain-query db query)]
    {:fhir/type :fhir/OperationOutcome
     :issue
     [{:fhir/type :fhir.OperationOutcome/issue
       :severity #fhir/code "information"
       :code #fhir/code "informational"
       :diagnostics (type/string (query-plan/render plan))}]}))

(defn- query-plan-entry [context query]
  (search-util/outcome-entry context (query-plan-outcome context query)))

(defn- match-xf [context]
  (map (partial search-util/match-entry context)))

(defn- include-xf [context]
  (map (partial search-util/include-entry context)))

(defn- entries
  [{{:keys [explain?]} :params :as context} query match-future include-future]
  (log/trace "build entries")
  (-> (cond-> [] (and explain? query) (conj (query-plan-entry context query)))
      (into (match-xf context) (ac/join match-future))
      (into (include-xf context) (ac/join include-future))))

(defn- total-future
  "Calculates the total number of resources returned.

  If we are on the first page (page-id is nil) and we don't have a next match,
  we can use number of matches.

  If we have no query (returning all resources), we can use `d/type-total`.

  If we are forced to calculate the total (total is accurate) we issue an
  `d/count-query`.

  Otherwise, we don't report it."
  [db type query {:keys [page-id total]} matches next-match]
  (cond
    ;; evaluate this criteria first, because we can potentially safe the
    ;; d/type-total call
    (and (nil? page-id) (nil? next-match))
    (ac/completed-future (count matches))

    (nil? query)
    (ac/completed-future (d/type-total db type))

    (= "accurate" total)
    (d/count-query db query)

    :else
    (ac/completed-future nil)))

(defn- wrap-cache-handling [opts {:keys [page-id]}]
  (cond-> opts page-id (assoc :skip-cache-insertion? true)))

(defn- match-pull-opts [{:keys [summary elements] :as params}]
  (-> (cond
        (seq elements) {:elements elements}
        (= "true" summary) {:variant :summary}
        :else {})
      (wrap-cache-handling params)))

(defn- include-pull-opts [params]
  (wrap-cache-handling {} params))

(defn- build-page
  "Compiles the query and scans one page worth of resource handles, already
  pulling the match resources concurrently during the scan (see `build-page**`).

  Returns a CompletableFuture that will complete with the page map containing
  :matches, :match-futures, :next-match, optional :includes, :pull-timer and the
  used :query, or will complete exceptionally with an anomaly in case of errors."
  [{:blaze/keys [db]
    {:keys [include-defs page-size page-id] :as params} :params :as context}]
  (do-sync [query (compile-query context db)]
    (let [pull (d/pull-fn db (match-pull-opts params))]
      (with-open [_ (prom/timer search-duration-seconds "scan")
                  batch-db (d/new-batch-db db)]
        (when-ok [handles (if query
                            (execute-query batch-db query page-id)
                            (type-list batch-db (:type context) page-id))
                  page (build-page* batch-db pull include-defs page-size handles)]
          (assoc page :query query))))))

(defn- pull-matches
  "Awaits the `match-futures` already pulled during the scan and returns a
  CompletableFuture of all match resources in order.

  Observes `pull-timer` (started in `build-page**` before the scan reduction)
  once all pulls complete, so the `pull-matches` metric captures the full pull
  duration that overlaps the scan.

  Completes exceptionally with an anomaly if any of the `match-futures` does."
  [pull-timer match-futures]
  (do-sync [_ (ac/all-of match-futures)]
    (prom/observe-duration! pull-timer)
    (mapv ac/join match-futures)))

(defn- pull-includes [db includes opts]
  (let [pull-timer (prom/timer search-duration-seconds "pull-includes")]
    (do-sync [resources (d/pull-many db includes opts)]
      (prom/observe-duration! pull-timer)
      resources)))

(defn- page-data
  "Returns a CompletableFuture that will complete with a map of:

  :entries - the bundle entries of the page
  :next-handle - the resource handle of the first resource of the next page
  :total - the total number of matching resources, or nil if not reported
  :clauses - the actually used clauses (only present if there is a query)

  or will complete exceptionally with an anomaly in case of errors."
  {:arglists '([context])}
  [{:blaze/keys [db] :keys [type params] :as context}]
  (-> (build-page context)
      (ac/then-compose
       (fn [{:keys [matches match-futures includes next-match query pull-timer]}]
         (let [total-future (total-future db type query params matches next-match)
               match-future (pull-matches pull-timer match-futures)
               include-future (if (seq includes)
                                (pull-includes db includes (include-pull-opts params))
                                (ac/completed-future nil))]
           (-> (ac/all-of [total-future match-future include-future])
               (ac/exceptionally
                #(assoc %
                        ::anom/category ::anom/fault
                        :fhir/issue "incomplete"))
               (ac/then-apply
                (fn [_]
                  (cond->
                   {:entries (entries context query match-future include-future)
                    :next-handle next-match
                    :total (ac/join total-future)}
                    query
                    (assoc :clauses (d/query-clauses query)))))))))))

(defn- self-link
  [{::search-util/keys [link] :keys [self-link-url-fn]} clauses]
  (link "self" (self-link-url-fn clauses)))

(defn- first-link
  [{::search-util/keys [link] :keys [first-link-url-fn]} token clauses]
  (link "first" (first-link-url-fn token clauses)))

(def ^:private ^:const max-page-id-stack-size
  "Upper bound on the number of ancestor page start-id's carried in the
  `__page-id-stack` of a paging link. Keeps the encrypted page id (and thus the
  paging URL) small enough; beyond it, the previous link is no longer offered
  and the always present first link is used instead."
  10)

(defn- push-page-id [page-id-stack page-id]
  (let [stack (conj page-id-stack page-id)
        overflow (- (count stack) max-page-id-stack-size)]
    (cond-> stack (pos? overflow) (subvec overflow))))

(defn- next-link-offset [{:keys [page-id page-id-stack]} next-handle]
  {"__page-id" (:id next-handle)
   ;; an empty string represents the first page, which has no start-id
   "__page-id-stack" (push-page-id page-id-stack (or page-id ""))})

(defn- next-link
  [{::search-util/keys [link] :keys [params page-link-url-fn]} token clauses
   next-handle]
  (->> (page-link-url-fn token clauses (next-link-offset params next-handle))
       (link "next")))

(defn- prev-link-offset [{:keys [page-id-stack]}]
  (let [prev-page-id (peek page-id-stack)
        prev-page-id-stack (pop page-id-stack)]
    (cond-> {}
      ;; an empty string represents the first page, which has no start-id
      (not= "" prev-page-id) (assoc "__page-id" prev-page-id)
      (seq prev-page-id-stack) (assoc "__page-id-stack" prev-page-id-stack))))

(defn- prev-link
  [{::search-util/keys [link] :keys [params page-link-url-fn]} token clauses]
  (->> (page-link-url-fn token clauses (prev-link-offset params))
       (link "previous")))

(defn- zero-bundle
  "Generate a special bundle if the search results in zero matches to avoid
  generating a token for the first link, we don't need in this case."
  [context clauses]
  {:fhir/type :fhir/Bundle
   :id (m/luid context)
   :type #fhir/code "searchset"
   :total #fhir/unsignedInt 0
   :link [(self-link context clauses)]})

(defn- normal-bundle
  [{{{route-name :name} :data} ::reitit/match :as context} token
   {:keys [entries total clauses]}]
  (cond->
   {:fhir/type :fhir/Bundle
    :id (m/luid context)
    :type #fhir/code "searchset"
    :entry entries
    :link [(first-link context token clauses)]}
    (not= "page" (name route-name))
    (update :link conj (self-link context clauses))
    total
    (assoc :total (type/unsignedInt total))))

(defn- gen-token! [{{:keys [token]} :params :keys [gen-token-fn]} clauses]
  (if token
    (ac/completed-future token)
    (gen-token-fn clauses)))

(defn- page-bundle
  [{{:keys [page-id-stack]} :params :as context} token clauses
   {:keys [next-handle] :as page-data}]
  (cond-> (normal-bundle context token page-data)
    (seq page-id-stack)
    (update :link conj (prev-link context token clauses))
    next-handle
    (update :link conj (next-link context token clauses next-handle))))

(defn- search-normal [context]
  (-> (page-data context)
      (ac/then-compose
       (fn [{:keys [entries clauses] :as page-data}]
         (if (seq entries)
           (do-sync [token (gen-token! context clauses)]
             (page-bundle context token clauses page-data))
           (ac/completed-future (zero-bundle context clauses)))))
      (ac/then-apply ring/response)))

(defn- summary-response [context total clauses]
  (ring/response
   {:fhir/type :fhir/Bundle
    :id (m/luid context)
    :type #fhir/code "searchset"
    :total (type/unsignedInt total)
    :link [(self-link context clauses)]}))

(defn no-query-summary-response [{:keys [type] :blaze/keys [db] :as context}]
  (ac/completed-future (summary-response context (d/type-total db type) [])))

(defn- search-summary
  [{:keys [type] :blaze/keys [db] {:keys [clauses]} :params
    :blaze.preference/keys [handling] :as context}]
  (if (empty? clauses)
    (no-query-summary-response context)
    (-> (compile-type-query db type clauses handling)
        (ac/then-compose
         (fn [query]
           (let [clauses (d/query-clauses query)]
             (if (empty? clauses)
               (no-query-summary-response context)
               (if (:blaze.preference/respond-async context)
                 (req/handle-async context (:request context))
                 (do-sync [total (d/count-query db query)]
                   (summary-response context total clauses))))))))))

(defn- search [{:keys [params] :as context}]
  (if (:summary? params)
    (search-summary context)
    (search-normal context)))

(defn- match
  [{{{:fhir.resource/keys [type]} :data} ::reitit/match
    ::reitit/keys [router]}
   name]
  (reitit/match-by-name router (keyword type name)))

(defn- page-match
  [{{{:fhir.resource/keys [type]} :data} ::reitit/match
    ::reitit/keys [router]}]
  (fn [page-id]
    (reitit/match-by-name router (keyword type "page") {:page-id page-id})))

(defn- self-link-url-fn [{:blaze/keys [base-url] :as request} params]
  (fn [clauses]
    (nav/url base-url (match request "type") params clauses)))

(defn- gen-token-fn
  [{:keys [page-store]} {{{route-name :name} :data} ::reitit/match}]
  (if (= "search" (name route-name))
    (fn [clauses]
      (if (empty? clauses)
        (ac/completed-future nil)
        (page-store/put! page-store clauses)))
    (fn [_clauses]
      (ac/completed-future nil))))

(defn- first-link-url-fn
  "Returns a function of `token` and `clauses` that returns the URL of the first
  link."
  [{:blaze/keys [base-url db] :as request} page-id-cipher params]
  (fn [token clauses]
    (nav/token-url base-url page-id-cipher (page-match request) params token clauses
                   (d/t db) nil)))

(defn- page-link-url-fn
  "Returns a function of `token`, `clauses` and `offset` that returns the URL
  of a paging link (next or previous)."
  [{:blaze/keys [base-url db] :as request} page-id-cipher params]
  (fn [token clauses offset]
    (nav/token-url base-url page-id-cipher (page-match request) params token
                   clauses (d/t db) offset)))

(defn- search-context
  [{:keys [page-store page-id-cipher] :as context}
   {{{:fhir.resource/keys [type]} :data} ::reitit/match
    :keys [headers params]
    :blaze/keys [base-url db]
    ::reitit/keys [router match]
    :as request}]
  (let [handling (handler-util/preference headers "handling")
        respond-async (handler-util/preference headers "respond-async")]
    (do-sync [params (params/decode page-store handling params)]
      (cond->
       (assoc context
              :blaze/base-url base-url
              :blaze/db db
              :request request
              ::reitit/router router
              ::reitit/match match
              :type type
              :params params
              :self-link-url-fn (self-link-url-fn request params)
              :gen-token-fn (gen-token-fn context request)
              :first-link-url-fn (first-link-url-fn request page-id-cipher params)
              :page-link-url-fn (page-link-url-fn request page-id-cipher params))
        handling
        (assoc :blaze.preference/handling handling)
        respond-async
        (assoc :blaze.preference/respond-async true)))))

(defmethod m/pre-init-spec :blaze.interaction/search-type [_]
  (s/keys :req [::search-util/link]
          :req-un [:blaze/clock :blaze/rng-fn :blaze/page-store
                   :blaze/page-id-cipher]
          :opt-un [:blaze/context-path]))

(defmethod ig/init-key :blaze.interaction/search-type [_ context]
  (log/info "Init FHIR search-type interaction handler")
  (fn [request]
    (-> (search-context context request)
        (ac/then-compose search))))

(reg-collector ::search-duration-seconds
  search-duration-seconds)
