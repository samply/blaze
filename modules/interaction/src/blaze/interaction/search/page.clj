(ns blaze.interaction.search.page
  "Common functions for building search result pages and their paging links,
  shared between the search-type and the search-system interaction."
  (:require
   [blaze.anomaly :refer [if-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.interaction.search.include :as include]
   [blaze.interaction.search.nav :as nav]
   [blaze.interaction.search.util :as search-util]
   [blaze.module :as m]
   [blaze.page-store :as page-store]
   [prometheus.alpha :as prom]
   [reitit.core :as reitit]
   [ring.util.response :as ring]))

(defn- wrap-cache-handling [opts {:keys [page-id]}]
  (cond-> opts page-id (assoc :skip-cache-insertion? true)))

(defn match-pull-opts
  "Returns the options for pulling the resource contents of the matches
  according to `params`."
  [{:keys [summary elements] :as params}]
  (-> (cond
        (seq elements) {:elements elements}
        (= "true" summary) {:variant :summary}
        :else {})
      (wrap-cache-handling params)))

(defn include-pull-opts
  "Returns the options for pulling the resource contents of the includes
  according to `params`."
  [params]
  (wrap-cache-handling {} params))

(defn- build-page*
  "Materializes one page worth of resource handles while already pulling the
  resource content of the matches.

  Pulls are triggered during the scan via `pull` (a function of a resource
  handle returning a CompletableFuture), so the resource I/O overlaps with the
  single-threaded scan. The next-match handle, which only seeds the next page's
  paging link, is not pulled."
  [duration-histogram pull page-size handles]
  ;; start the pull timer before reducing the handles, because the pulls are
  ;; triggered during the reduction and run concurrently with the rest of the
  ;; scan; it is observed once all match futures complete
  (let [pull-timer (prom/timer duration-histogram "pull-matches")
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

(defn build-page
  "Materializes one page worth of resource handles while already pulling the
  resource content of the matches (see `build-page*`), and resolves the
  includes of the matches according to `include-defs`.

  If the inclusions turn out to be too costly, retries with a halved page size.

  Returns a map of :matches, :match-futures, :next-match (optional), :includes
  (optional) and :pull-timer, a timer on `duration-histogram` with the phase
  `pull-matches` that has to be observed via `pull-matches` once all pulls
  complete, or an anomaly in case of errors."
  [duration-histogram batch-db pull include-defs page-size handles]
  (let [{:keys [matches] :as res} (build-page* duration-histogram pull
                                               page-size handles)]
    (if (:direct include-defs)
      (if-ok [includes (include/add-includes batch-db include-defs matches)]
        (assoc res :includes includes)
        #(if (and (-> % :fhir/issue #{"too-costly"})
                  (> page-size 1))
           (build-page duration-histogram batch-db pull include-defs
                       (quot page-size 2) handles)
           %))
      res)))

(defn pull-includes
  "Returns a CompletableFuture of the resource contents of the `includes`,
  observing the pull duration on `duration-histogram` with the phase
  `pull-includes`."
  [duration-histogram db includes opts]
  (let [pull-timer (prom/timer duration-histogram "pull-includes")]
    (do-sync [resources (d/pull-many db includes opts)]
      (prom/observe-duration! pull-timer)
      resources)))

(defn pull-matches
  "Awaits the `match-futures` already pulled during the scan and returns a
  CompletableFuture of all match resources in order.

  Observes `pull-timer` (started in `build-page` before the scan reduction)
  once all pulls complete, so the `pull-matches` metric captures the full pull
  duration that overlaps the scan.

  Completes exceptionally with an anomaly if any of the `match-futures` does."
  [pull-timer match-futures]
  (do-sync [_ (ac/all-of match-futures)]
    (prom/observe-duration! pull-timer)
    (mapv ac/join match-futures)))

(defn gen-token-fn
  "Returns a function of `clauses` that will return a CompletableFuture of a
  token encoding the clauses or of nil if no token should be generated.

  Tokens are only generated on POST search requests (routes with the name
  `search`), because there the clauses come from the form body and are not
  visible in the URL."
  [{:keys [page-store]} {{{route-name :name} :data} ::reitit/match}]
  (if (= "search" (some-> route-name name))
    (fn [clauses]
      (if (empty? clauses)
        (ac/completed-future nil)
        (page-store/put! page-store clauses)))
    (fn [_clauses]
      (ac/completed-future nil))))

(defn gen-token!
  "Returns a CompletableFuture of the token of the current request or of a
  possibly generated token for `clauses` (see `gen-token-fn`)."
  [{{:keys [token]} :params :keys [gen-token-fn]} clauses]
  (if token
    (ac/completed-future token)
    (gen-token-fn clauses)))

(defn self-link [{::search-util/keys [link] :keys [self-link-url-fn]} clauses]
  (link "self" (self-link-url-fn clauses)))

(defn first-link
  [{::search-util/keys [link] :keys [first-link-url-fn]} token clauses]
  (link "first" (first-link-url-fn token clauses)))

(defn next-link
  [{::search-util/keys [link] :keys [page-link-url-fn]} token clauses offset]
  (link "next" (page-link-url-fn token clauses offset)))

(defn prev-link
  [{::search-util/keys [link] :keys [page-link-url-fn]} token clauses offset]
  (link "previous" (page-link-url-fn token clauses offset)))

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

(defn next-link-offset
  "Returns `offset` amended with the `__page-id-stack` of the next page, formed
  by pushing `page-start`, the start of the current page, onto the stack. An
  empty string represents the first page, which has no start."
  [{:keys [page-id-stack]} page-start offset]
  (assoc offset "__page-id-stack" (push-page-id page-id-stack page-start)))

(defn prev-link-offset
  "Returns the offset of the previous page, popping its start from the
  `__page-id-stack` and decoding it into query params via `decode-page-start`."
  [{:keys [page-id-stack]} decode-page-start]
  (let [prev-page-start (peek page-id-stack)
        prev-page-id-stack (pop page-id-stack)]
    (cond-> {}
      ;; an empty string represents the first page, which has no start
      (not= "" prev-page-start) (merge (decode-page-start prev-page-start))
      (seq prev-page-id-stack) (assoc "__page-id-stack" prev-page-id-stack))))

(defn zero-bundle
  "Generate a special bundle if the search results in zero matches to avoid
  generating a token for the first link, we don't need in this case."
  [context clauses]
  {:fhir/type :fhir/Bundle
   :id (m/luid context)
   :type #fhir/code "searchset"
   :total #fhir/unsignedInt 0
   :link [(self-link context clauses)]})

(defn normal-bundle
  [{{{route-name :name} :data} ::reitit/match :as context} token
   {:keys [entries total clauses]}]
  (cond->
   {:fhir/type :fhir/Bundle
    :id (m/luid context)
    :type #fhir/code "searchset"
    :entry entries
    :link [(first-link context token clauses)]}
    (not= "page" (some-> route-name name))
    (update :link conj (self-link context clauses))
    total
    (assoc :total (type/unsignedInt total))))

(defn summary-response [context total clauses]
  (ring/response
   {:fhir/type :fhir/Bundle
    :id (m/luid context)
    :type #fhir/code "searchset"
    :total (type/unsignedInt total)
    :link [(self-link context clauses)]}))

(defn match-xf [context]
  (map (partial search-util/match-entry context)))

(defn include-xf [context]
  (map (partial search-util/include-entry context)))

(defn total-future
  "Calculates the total number of resources returned.

  If we are on the first page (page-id is nil) and we don't have a next match,
  we can use number of matches.

  If we have no query (returning all resources), we can use `total-fn`.

  If we are forced to calculate the total (total is accurate) we issue an
  `d/count-query`.

  Otherwise, we don't report it."
  [db query total-fn {:keys [page-id total]} matches next-match]
  (cond
    ;; evaluate this criteria first, because we can potentially save the
    ;; total-fn call
    (and (nil? page-id) (nil? next-match))
    (ac/completed-future (count matches))

    (nil? query)
    (ac/completed-future (total-fn))

    (= "accurate" total)
    (d/count-query db query)

    :else
    (ac/completed-future nil)))

(defn first-link-url-fn
  "Returns a function of `token` and `clauses` that returns the URL of the first
  link."
  [{:blaze/keys [base-url db] :as request} page-id-cipher page-match params]
  (fn [token clauses]
    (nav/token-url base-url page-id-cipher (page-match request) params token
                   clauses (d/t db) nil)))

(defn page-link-url-fn
  "Returns a function of `token`, `clauses` and `offset` that returns the URL
  of a paging link (next or previous)."
  [{:blaze/keys [base-url db] :as request} page-id-cipher page-match params]
  (fn [token clauses offset]
    (nav/token-url base-url page-id-cipher (page-match request) params token
                   clauses (d/t db) offset)))
