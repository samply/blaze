(ns blaze.interaction.util
  (:require
    [blaze.anomaly :as ba]
    [blaze.db.api :as d]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.spec.type :as type]
    [blaze.luid :as luid]
    [blaze.util :as u]
    [clojure.string :as str]))


(defn etag->t [etag]
  (let [[_ t] (re-find #"W/\"(\d+)\"" etag)]
    (some-> t parse-long)))


(defn- remove-query-param? [[k]]
  (and (str/starts-with? k "_")
       (not (#{"_id" "_list" "_profile" "_lastUpdated"} k))
       (not (str/starts-with? k "_has"))))


(defn- query-param->clauses
  "Takes a query param with possible multiple values and returns possible
  multiple clauses one for each query param."
  [[k v]]
  (map
    #(into [k] (map str/trim) (str/split % #","))
    (u/to-seq v)))


(def ^:private query-params->clauses-xf
  (comp
    (remove remove-query-param?)
    (mapcat query-param->clauses)))


(defn- sort-clauses [sort]
  (let [[param & params] (str/split sort #",")
        param (str/trim param)]
    (if params
      (ba/unsupported "More than one sort parameter is unsupported.")
      [(if (str/starts-with? param "-")
         [:sort (subs param 1) :desc]
         [:sort param :asc])])))


(defn clauses [{sort "_sort" :as query-params}]
  (into (if (str/blank? sort) [] (sort-clauses sort))
        query-params->clauses-xf query-params))


(defn search-clauses [query-params]
  (into [] query-params->clauses-xf query-params))


(defn luid [{:keys [clock rng-fn]}]
  (luid/luid clock (rng-fn)))


(defn successive-luids [{:keys [clock rng-fn]}]
  (luid/successive-luids clock (rng-fn)))


(defn- prep-if-none-match [if-none-match]
  (if (= "*" if-none-match)
    :any
    (etag->t if-none-match)))


(defn- parse-if-match [if-match]
  (keep etag->t (str/split if-match #",")))


(defn- precondition-failed-msg [{:fhir/keys [type] :keys [id]} if-match]
  (if (str/blank? if-match)
    (format "Empty precondition failed on `%s/%s`." (name type) id)
    (format "Precondition `%s` failed on `%s/%s`." if-match (name type) id)))


(defn- update-tx-op-no-preconditions
  [db {:fhir/keys [type] :keys [id] :as resource}]
  (if-let [resource-handle (d/resource-handle db (name type) id)]
    (let [new-hash (hash/generate resource)]
      (if (= (:hash resource-handle) new-hash)
        [:keep (name type) id new-hash]
        [:put resource]))
    [:put resource]))


(defn- update-tx-op-if-match
  [db {:fhir/keys [type] :keys [id] :as resource} if-match ts]
  (if-let [resource-handle (d/resource-handle db (name type) id)]
    (let [new-hash (hash/generate resource)]
      (if (= (:hash resource-handle) new-hash)
        (let [t (:t resource-handle)]
          (cond
            (some #{t} ts)
            [:keep (name type) id new-hash (filterv (partial <= t) ts)]
            (every? (partial > t) ts)
            (ba/conflict (precondition-failed-msg resource if-match)
                         :http/status 412)
            :else
            [:put resource (into [:if-match] (filter (partial < t) ts))]))
        [:put resource (into [:if-match] ts)]))
    [:put resource (into [:if-match] ts)]))


(defn update-tx-op
  "Returns either a put or a keep tx-op with `resource` and possible
  preconditions from `if-match` and `if-none-match` or an anomaly."
  [db resource if-match if-none-match]
  (let [parsed-if-match (some-> if-match parse-if-match)
        if-none-match (some-> if-none-match prep-if-none-match)]
    (cond
      (and (some? parsed-if-match) (empty? parsed-if-match))
      (ba/conflict (precondition-failed-msg resource if-match)
                   :http/status 412)
      parsed-if-match (update-tx-op-if-match db resource if-match parsed-if-match)
      if-none-match [:put resource [:if-none-match if-none-match]]
      :else (update-tx-op-no-preconditions db resource))))


(defn subsetted?
  "Checks whether `coding` is a SUBSETTED coding."
  {:arglists '([coding])}
  [{:keys [system code]}]
  (and (= #fhir/uri"http://terminology.hl7.org/CodeSystem/v3-ObservationValue" system)
       (= #fhir/code"SUBSETTED" code)))


(defn strip-meta
  "Strips :versionId :lastUpdated from :meta of `resource`."
  {:arglists '([resource])}
  [{:keys [meta] :as resource}]
  (let [meta (into {} (keep (fn [[k v]] (when (and v (not (#{:versionId :lastUpdated} k))) [k v]))) meta)]
    (if (empty? meta)
      (dissoc resource :meta)
      (assoc resource :meta (type/map->Meta meta)))))


(defn keep?
  "Determines whether `tx-op` is a keep operator."
  {:arglists '([tx-op])}
  [[op]]
  (identical? :keep op))
