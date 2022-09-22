(ns blaze.interaction.search.nav
  (:require
    [blaze.async.comp :as ac :refer [do-sync]]
    [blaze.page-store :as page-store]
    [clojure.string :as str]
    [reitit.core :as reitit]))


(defmulti clause->query-param (fn [_ret [key]] key))


(defmethod clause->query-param :sort
   [ret [_sort param direction]]
  (assoc ret "_sort" (if (= :desc direction) (str "-" param) param)))


(defmethod clause->query-param :default
  [ret [param & values]]
  (update ret param (fnil conj []) (str/join "," values)))


(defn- clauses->query-params [clauses]
  (reduce clause->query-param {} clauses))


(defn- clauses->token-query-params [page-store token clauses]
  (cond
    token
    (ac/completed-future {"__token" token})
    (empty? clauses)
    (ac/completed-future nil)
    :else
    (do-sync [token (page-store/put! page-store clauses)]
      {"__token" token})))


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


(defn- query-params [{:keys [include-defs summary page-size]} clauses]
  (cond-> (clauses->query-params clauses)
    (seq include-defs)
    (merge (include-defs->query-params include-defs))
    summary
    (assoc "_summary" summary)
    page-size
    (assoc "_count" page-size)))


(defn- token-query-params
  [page-store {:keys [include-defs page-size token]} clauses]
  (do-sync [clauses-params (clauses->token-query-params page-store token clauses)]
    (cond-> clauses-params
      (seq include-defs)
      (merge (include-defs->query-params include-defs))
      page-size
      (assoc "_count" page-size))))


(defn url [base-url match params clauses t offset]
  (let [query-params (-> (query-params params clauses)
                         (assoc "__t" t)
                         (merge offset))]
    (str base-url (reitit/match->path match query-params))))


(defn token-url
  "Returns a CompletableFuture that will complete with the URL."
  [page-store base-url match params clauses t offset]
  (do-sync [query-params (token-query-params page-store params clauses)]
    (str base-url (reitit/match->path match (-> query-params
                                                (assoc "__t" t)
                                                (merge offset))))))
