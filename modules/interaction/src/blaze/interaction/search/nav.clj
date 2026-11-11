(ns blaze.interaction.search.nav
  (:refer-clojure :exclude [str])
  (:require
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.middleware.fhir.page-id :as page-id]
   [blaze.page-store :as page-store]
   [blaze.util :as u :refer [str]]
   [blaze.value-set-expander :as vse]
   [clojure.string :as str]
   [reitit.core :as reitit]))

(set! *warn-on-reflection* true)

(defmulti clause->query-param (fn [_ret [key]] key))

(defmethod clause->query-param :sort
  [ret [_sort param direction]]
  (assoc ret "_sort" (if (= :desc direction) (str "-" param) param)))

(defmethod clause->query-param :default
  [ret [param & values]]
  (update ret param u/conj-vec (str/join "," values)))

(defn- clauses->query-params [clauses]
  (reduce clause->query-param {} clauses))

(defn client-clauses
  "Returns the clauses of the search with `params` as the client wrote them,
  which are `clauses` with expanded value sets mapped back.

  The page store holds clauses with expanded value sets, which lose the
  clauses they were expanded from. So the clauses the client wrote are carried
  in the page id under :client-clauses of `params` as well."
  {:arglists '([params clauses])}
  [{expanded-clauses :clauses :keys [client-clauses]} clauses]
  (or client-clauses (vse/original-clauses expanded-clauses clauses)))

(defn- token-query-params*
  "Returns the query params of a page id with `token`, which also carry the
  client clauses (see `client-clauses`), if they differ from `clauses`."
  [params token clauses]
  (let [client-clauses (client-clauses params clauses)]
    (cond-> {"__token" token}
      (not= client-clauses clauses)
      (merge (clauses->query-params client-clauses)))))

(defn- clauses->token-query-params! [page-store {:keys [token] :as params} clauses]
  (cond
    token (ac/completed-future (token-query-params* params token clauses))
    (empty? clauses) (ac/completed-future nil)
    :else
    (do-sync [token (page-store/put! page-store clauses)]
      (token-query-params* params token clauses))))

(defn- forward-include-defs->query-param-values [include-defs]
  (into
   []
   (mapcat
    (fn [[source-type include-defs]]
      (mapv
       (fn [{:keys [code target-type]}]
         (cond-> (str source-type ":" code)
           target-type
           (str ":" target-type)))
       include-defs)))
   include-defs))

(defn- reverse-include-defs->query-param-values [include-defs]
  (into
   []
   (mapcat
    (fn [[target-type include-defs]]
      (mapv
       (fn [{:keys [source-type code]}]
         (cond-> (str source-type ":" code)
           (not= :any target-type)
           (str ":" target-type)))
       include-defs)))
   include-defs))

(defn- include-defs->query-params
  [{{fwd-dir :forward rev-dir :reverse} :direct
    {fwd-itr :forward rev-itr :reverse} :iterate}]
  (let [fwd-dir (forward-include-defs->query-param-values fwd-dir)
        fwd-itr (forward-include-defs->query-param-values fwd-itr)
        rev-dir (reverse-include-defs->query-param-values rev-dir)
        rev-itr (reverse-include-defs->query-param-values rev-itr)]
    (cond-> {}
      (seq fwd-dir)
      (assoc "_include" fwd-dir)
      (seq fwd-itr)
      (assoc "_include:iterate" fwd-itr)
      (seq rev-dir)
      (assoc "_revinclude" rev-dir)
      (seq rev-itr)
      (assoc "_revinclude:iterate" rev-itr))))

(defn- merge-params
  [clauses-params {:keys [include-defs summary total elements page-size]}]
  (cond-> clauses-params
    (seq include-defs)
    (merge (include-defs->query-params include-defs))
    summary
    (assoc "_summary" summary)
    total
    (assoc "_total" total)
    (seq elements)
    (assoc "_elements" (str/join "," (map name elements)))
    page-size
    (assoc "_count" (str page-size))))

(defn- query-params [params clauses]
  (merge-params (clauses->query-params clauses) params))

(defn url
  "Returns the URL created from `clauses` used in self links."
  [base-url match params clauses]
  (let [query-params (query-params params clauses)]
    (str base-url (reitit/match->path match query-params))))

(defn- token-query-params!
  [page-store params clauses]
  (do-sync [clauses-params (clauses->token-query-params! page-store params clauses)]
    (merge-params clauses-params params)))

(defn token-url!
  "Returns a CompletableFuture that will complete with a URL that will encode
  `clauses` in a token."
  [{:keys [page-store page-id-cipher] :blaze/keys [base-url]} match params
   clauses t offset]
  (do-sync [query-params (token-query-params! page-store params clauses)]
    (->> (-> query-params
             (assoc "__t" (str t))
             (merge offset))
         (page-id/encrypt page-id-cipher)
         (match)
         (reitit/match->path)
         (str base-url))))

(defn- clauses->token-query-params [params token clauses]
  (if token
    (token-query-params* params token clauses)
    (clauses->query-params clauses)))

(defn- token-query-params [params token clauses]
  (merge-params (clauses->token-query-params params token clauses) params))

(defn token-url
  [base-url page-id-cipher match params token clauses t offset]
  (let [query-params (token-query-params params token clauses)]
    (->> (-> query-params
             (assoc "__t" (str t))
             (merge offset))
         (page-id/encrypt page-id-cipher)
         (match)
         (reitit/match->path)
         (str base-url))))
