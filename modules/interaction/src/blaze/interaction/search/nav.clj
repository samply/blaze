(ns blaze.interaction.search.nav
  (:require
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.middleware.fhir.decrypt-page-id :as decrypt-page-id]
   [blaze.page-store :as page-store]
   [blaze.util :refer [conj-vec]]
   [clojure.string :as str]
   [reitit.core :as reitit]))

(set! *warn-on-reflection* true)

(defmulti clause->query-param (fn [_ret [key]] key))

(defmethod clause->query-param :sort
  [ret [_sort param direction]]
  (assoc ret "_sort" (if (= :desc direction) (str "-" param) param)))

(defmethod clause->query-param :default
  [ret [param & values]]
  (update ret param conj-vec (str/join "," values)))

(defn- clauses->query-params [clauses]
  (reduce clause->query-param {} clauses))

(defn- clauses->token-query-params! [page-store token clauses]
  (cond
    token (ac/completed-future {"__token" token})
    (empty? clauses) (ac/completed-future nil)
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

(defn- merge-params [clauses-params {:keys [include-defs summary elements page-size]}]
  (cond-> clauses-params
    (seq include-defs)
    (merge (include-defs->query-params include-defs))
    summary
    (assoc "_summary" summary)
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
  [page-store {:keys [token] :as params} clauses]
  (do-sync [clauses-params (clauses->token-query-params! page-store token clauses)]
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
         (decrypt-page-id/encrypt page-id-cipher)
         (match)
         (reitit/match->path)
         (str base-url))))

(defn- clauses->token-query-params [token clauses]
  (if token {"__token" token} (clauses->query-params clauses)))

(defn- token-query-params [params token clauses]
  (merge-params (clauses->token-query-params token clauses) params))

(defn token-url
  [base-url page-id-cipher match params token clauses t offset]
  (let [query-params (token-query-params params token clauses)]
    (->> (-> query-params
             (assoc "__t" (str t))
             (merge offset))
         (decrypt-page-id/encrypt page-id-cipher)
         (match)
         (reitit/match->path)
         (str base-url))))
