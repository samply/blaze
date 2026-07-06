(ns blaze.interaction.search-system
  "FHIR search interaction.

  https://www.hl7.org/fhir/http.html#search"
  (:require
   [blaze.anomaly :refer [when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.db.spec]
   [blaze.handler.util :as handler-util]
   [blaze.interaction.search.nav :as nav]
   [blaze.interaction.search.page :as page]
   [blaze.interaction.search.params :as params]
   [blaze.interaction.search.util :as search-util]
   [blaze.interaction.search.util.spec]
   [blaze.module :as m :refer [reg-collector]]
   [blaze.page-store.spec]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [prometheus.alpha :as prom]
   [reitit.core :as reitit]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(prom/defhistogram search-duration-seconds
  "Latencies in seconds of the individual phases of building a single system
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
   :subsystem "search_system"}
  (take 16 (iterate #(* 2 %) 0.0001))
  "phase")

(defn- system-list [db page-type page-id]
  (if (and page-type page-id)
    (d/system-list db page-type page-id)
    (d/system-list db)))

(defn- compile-system-query [db clauses handling]
  (if (identical? :blaze.preference.handling/strict handling)
    (d/compile-system-query db clauses)
    (d/compile-system-query-lenient db clauses)))

(defn- execute-query [db query page-type page-id]
  (if (and page-type page-id)
    (d/execute-query db query page-type page-id)
    (d/execute-query db query)))

(defn- compile-query
  [{:blaze.preference/keys [handling] {:keys [clauses]} :params} db]
  (if (empty? clauses)
    (ac/completed-future nil)
    (let [timer (prom/timer search-duration-seconds "compile-query")]
      (do-sync [query (compile-system-query db clauses handling)]
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
    {:keys [include-defs page-size page-type page-id] :as params} :params
    :as context}]
  (do-sync [query (compile-query context db)]
    (let [pull (d/pull-fn db (page/match-pull-opts params))]
      (with-open [_ (prom/timer search-duration-seconds "scan")
                  batch-db (d/new-batch-db db)]
        (when-ok [handles (if query
                            (execute-query batch-db query page-type page-id)
                            (system-list batch-db page-type page-id))
                  page (page/build-page search-duration-seconds batch-db pull
                                        include-defs page-size handles)]
          (assoc page :query query))))))

(defn- entries [context match-future include-future]
  (-> (into [] (page/match-xf context) (ac/join match-future))
      (into (page/include-xf context) (ac/join include-future))))

(defn- page-data
  "Returns a CompletableFuture that will complete with a map of:

  :entries - the bundle entries of the page
  :next-handle - the resource handle of the first resource of the next page
  :total - the total number of matching resources, or nil if not reported
  :clauses - the actually used clauses (only present if there is a query)

  or will complete exceptionally with an anomaly in case of errors."
  {:arglists '([context])}
  [{:blaze/keys [db] :keys [params] :as context}]
  (-> (build-page context)
      (ac/then-compose
       (fn [{:keys [matches match-futures includes next-match query pull-timer]}]
         (let [total-future (page/total-future db query #(d/system-total db)
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
                   {:entries (entries context match-future include-future)
                    :next-handle next-match
                    :total (ac/join total-future)}
                    query
                    (assoc :clauses (d/query-clauses query)))))))))))

(defn- page-start
  "Returns the type qualified start-id of the current page in the form
  `Type/id` or an empty string for the first page, which has no start."
  [{:keys [page-type page-id]}]
  (if page-id (str page-type "/" page-id) ""))

(defn- next-link-offset [params next-handle]
  (page/next-link-offset params (page-start params)
                         {"__page-type" (name (:fhir/type next-handle))
                          "__page-id" (:id next-handle)}))

(defn- decode-page-start [page-start]
  (let [[type id] (str/split page-start #"/" 2)]
    {"__page-type" type "__page-id" id}))

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

(defn- no-query-summary-response [{:blaze/keys [db] :as context}]
  (ac/completed-future (page/summary-response context (d/system-total db) [])))

(defn- search-summary
  [{:blaze/keys [db] {:keys [clauses]} :params
    :blaze.preference/keys [handling] :as context}]
  (if (empty? clauses)
    (no-query-summary-response context)
    (-> (compile-system-query db clauses handling)
        (ac/then-compose
         (fn [query]
           (let [clauses (d/query-clauses query)]
             (if (empty? clauses)
               (no-query-summary-response context)
               (do-sync [total (d/count-query db query)]
                 (page/summary-response context total clauses)))))))))

(defn- search [{:keys [params] :as context}]
  (if (:summary? params)
    (search-summary context)
    (search-normal context)))

(defn- page-match [{::reitit/keys [router]}]
  (fn [page-id]
    (reitit/match-by-name router :page {:page-id page-id})))

(defn- self-link-url-fn [{:blaze/keys [base-url] ::reitit/keys [match]} params]
  (fn [clauses]
    (nav/url base-url match params clauses)))

(defn- search-context
  [{:keys [page-store page-id-cipher] :as context}
   {:keys [headers params]
    :blaze/keys [base-url db]
    ::reitit/keys [router match]
    :as request}]
  (let [handling (handler-util/preference headers "handling")]
    (do-sync [params (params/decode page-store handling params)]
      (cond->
       (assoc context
              :blaze/base-url base-url
              :blaze/db db
              ::reitit/router router
              ::reitit/match match
              :params params
              :self-link-url-fn (self-link-url-fn request params)
              :gen-token-fn (page/gen-token-fn context request)
              :first-link-url-fn (page/first-link-url-fn request page-id-cipher
                                                         page-match params)
              :page-link-url-fn (page/page-link-url-fn request page-id-cipher
                                                       page-match params))
        handling
        (assoc :blaze.preference/handling handling)))))

(defmethod m/pre-init-spec :blaze.interaction/search-system [_]
  (s/keys :req [::search-util/link]
          :req-un [:blaze/clock :blaze/rng-fn :blaze/page-store
                   :blaze/page-id-cipher]))

(defmethod ig/init-key :blaze.interaction/search-system [_ context]
  (log/info "Init FHIR search-system interaction handler")
  (fn [request]
    (-> (search-context context request)
        (ac/then-compose search))))

(reg-collector ::search-duration-seconds
  search-duration-seconds)
