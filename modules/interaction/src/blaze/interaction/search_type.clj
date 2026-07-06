(ns blaze.interaction.search-type
  "FHIR search interaction.

  https://www.hl7.org/fhir/http.html#search"
  (:require
   [blaze.anomaly :refer [when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.db.spec]
   [blaze.fhir.spec.type :as type]
   [blaze.handler.util :as handler-util]
   [blaze.interaction.search.nav :as nav]
   [blaze.interaction.search.page :as page]
   [blaze.interaction.search.params :as params]
   [blaze.interaction.search.query-plan :as query-plan]
   [blaze.interaction.search.util :as search-util]
   [blaze.interaction.search.util.spec]
   [blaze.job.async-interaction.request :as req]
   [blaze.module :as m :refer [reg-collector]]
   [blaze.page-id-cipher.spec]
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
  [{:keys [type] :blaze.preference/keys [handling] {:keys [clauses]} :params} db]
  (if (empty? clauses)
    (ac/completed-future nil)
    (let [timer (prom/timer search-duration-seconds "compile-query")]
      (do-sync [query (compile-type-query db type clauses handling)]
        (prom/observe-duration! timer)
        query))))

(defn- build-page
  "Compiles the query and scans one page worth of resource handles, already
  pulling the match resources concurrently during the scan (see
  `page/build-page`).

  Returns a CompletableFuture that will complete with the page map containing
  :matches, :match-futures, :next-match, optional :includes, :pull-timer and the
  used :query, or will complete exceptionally with an anomaly in case of errors."
  [{:blaze/keys [db]
    {:keys [include-defs page-size page-id] :as params} :params :as context}]
  (do-sync [query (compile-query context db)]
    (let [pull (d/pull-fn db (page/match-pull-opts params))]
      (with-open [_ (prom/timer search-duration-seconds "scan")
                  batch-db (d/new-batch-db db)]
        (when-ok [handles (if query
                            (execute-query batch-db query page-id)
                            (type-list batch-db (:type context) page-id))
                  page (page/build-page search-duration-seconds batch-db pull
                                        include-defs page-size handles)]
          (assoc page :query query))))))

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

(defn- entries
  [{{:keys [explain?]} :params :as context} query match-future include-future]
  (-> (cond-> [] (and explain? query) (conj (query-plan-entry context query)))
      (into (page/match-xf context) (ac/join match-future))
      (into (page/include-xf context) (ac/join include-future))))

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
         (let [total-future (page/total-future db query #(d/type-total db type)
                                               params matches next-match)
               match-future (page/pull-matches pull-timer match-futures)
               include-future (if (seq includes)
                                (page/pull-includes search-duration-seconds db
                                                    includes (page/include-pull-opts params))
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

(defn- next-link-offset [{:keys [page-id] :as params} next-handle]
  ;; an empty string represents the first page, which has no start-id
  (page/next-link-offset params (or page-id "")
                         {"__page-id" (:id next-handle)}))

(defn- decode-page-start [page-start]
  {"__page-id" page-start})

(defn- prev-link-offset [params]
  (page/prev-link-offset params decode-page-start))

(defn- page-bundle
  [{{:keys [page-id-stack] :as params} :params :as context} token clauses
   {:keys [next-handle] :as page-data}]
  (cond-> (page/normal-bundle context token page-data)
    (seq page-id-stack)
    (update :link conj (page/prev-link context token clauses (prev-link-offset params)))
    next-handle
    (update :link conj (page/next-link context token clauses (next-link-offset params next-handle)))))

(defn- search-normal [context]
  (-> (page-data context)
      (ac/then-compose
       (fn [{:keys [entries clauses] :as page-data}]
         (if (seq entries)
           (do-sync [token (page/gen-token! context clauses)]
             (page-bundle context token clauses page-data))
           (ac/completed-future (page/zero-bundle context clauses)))))
      (ac/then-apply ring/response)))

(defn- no-query-summary-response [{:keys [type] :blaze/keys [db] :as context}]
  (ac/completed-future (page/summary-response context (d/type-total db type) [])))

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
                   (page/summary-response context total clauses))))))))))

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
              :gen-token-fn (page/gen-token-fn context request)
              :first-link-url-fn (page/first-link-url-fn request page-id-cipher
                                                         page-match params)
              :page-link-url-fn (page/page-link-url-fn request page-id-cipher
                                                       page-match params))
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
