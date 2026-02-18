(ns blaze.db.impl.search-param.near
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.db.impl.index.index-handle :as ih]
   [blaze.db.impl.index.resource-as-of :as rao]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param.near.geo :as spng]
   [blaze.db.impl.search-param.special :as special]
   [blaze.db.impl.search-param.util :as u]
   [blaze.db.search-param :as-alias sp]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.spec.type.system :as system]
   [blaze.util :refer [conj-vec]]
   [clojure.string :as str]
   [cognitect.anomalies :as anom]))

(set! *warn-on-reflection* true)

(defn- position->coords [{:keys [longitude latitude]}]
  (let [lat-val (:value latitude)
        lon-val (:value longitude)]
    (when (and lat-val lon-val)
      {:latitude lat-val
       :longitude lon-val})))

(defn- near? [position {:keys [distance] :as compiled-value}]
  (when-let [coordinates (position->coords position)]
    (let [actual-dist (spng/haversine-distance coordinates compiled-value)]
      (<= actual-dist distance))))

(defn- matches? [batch-db resource-handle compiled-values]
  (let [{:keys [position]} @(d/pull batch-db resource-handle)]
    (some #(near? position %) compiled-values)))

(defn- missing-param-msg [arg code]
  (format "Missing argument `%s` in search parameter `%s`." arg code))

(defn- parsing-err-msg [arg code]
  (format "Error parsing argument `%s` in search parameter `%s`." arg code))

(defn- update-msg [msg]
  (fn [anomaly]
    (update anomaly ::anom/message #(str msg " " %))))

(defn- parse-decimal-arg [coord name code]
  (if (str/blank? coord)
    (ba/incorrect (missing-param-msg name code))
    (-> (system/parse-decimal coord)
        (ba/exceptionally (update-msg (parsing-err-msg name code))))))

(defn- unsupported-unit-msg [unit code]
  (format "Unsupported unit `%s` in search parameter `%s`. Supported are 'km', 'm'." unit code))

(defn- parse-distance [dist-str unit code]
  (cond
    (str/blank? dist-str)
    1000M

    (or (nil? unit) (= unit "km"))
    (when-ok [dist (parse-decimal-arg dist-str "distance" code)]
      (* dist 1000))

    (= unit "m")
    (parse-decimal-arg dist-str "distance" code)

    :else
    (ba/incorrect (unsupported-unit-msg unit code))))

(def ^:private ^:const min-latitude -90.0)

(def ^:private ^:const max-latitude 90.0)

(defn invalid-latitude-msg [arg code]
  (format
   "Invalid argument `%s` for latitude in search parameter `%s`, must be between %s and %s."
   arg code min-latitude max-latitude))

(defn- parse-latitude [val code]
  (when-ok [latitude (parse-decimal-arg val "latitude" code)]
    (if (<= min-latitude latitude max-latitude)
      latitude
      (ba/incorrect (invalid-latitude-msg val code)))))

(def ^:private ^:const min-longitude -180.0M)

(def ^:private ^:const max-longitude 180.0M)

(defn invalid-longitude-msg [val code]
  (format
   "Invalid argument `%s` for longitude in search parameter `%s`, must be between %s and %s."
   val code min-longitude max-longitude))

(defn- parse-longitude [val code]
  (when-ok [longitude (parse-decimal-arg val "longitude" code)]
    (if (<= min-longitude longitude max-longitude)
      longitude
      (ba/incorrect (invalid-longitude-msg val code)))))

(defn- min-distance [{:keys [position]} compiled-values]
  (when-let [coords (position->coords position)]
    (->> compiled-values
         (map #(spng/haversine-distance coords %))
         (apply min))))

(defn- distance-extension [distance]
  (type/extension
   {:url "http://hl7.org/fhir/StructureDefinition/location-distance"
    :value (type/distance {:value (type/decimal distance)
                           :unit #fhir/string "m"
                           :system #fhir/uri "http://unitsofmeasure.org"
                           :code #fhir/code "m"})}))

(defn- add-match-extension [meta distance]
  (update meta ::sp/match-extension conj-vec (distance-extension distance)))

(defn- postprocess-matches-xf [batch-db compiled-values]
  (map
   (fn [resource-handle]
     (let [distance (min-distance @(d/pull batch-db resource-handle) compiled-values)]
       (vary-meta resource-handle add-match-extension distance)))))

(defrecord SearchParamNear [name type code]
  p/SearchParam
  (-validate-modifier [_ modifier]
    (some->> modifier (u/unknown-modifier-anom code)))

  (-compile-value [_ _ value]
    (let [[lat long dist unit] (str/split value #"\|" 4)]
      (when-ok [parsed-lat (parse-latitude lat code)
                parsed-long (parse-longitude long code)
                parsed-dist (parse-distance dist unit code)]
        {:latitude parsed-lat
         :longitude parsed-long
         :distance parsed-dist})))

  (-estimated-scan-size [_ batch-db tid _ _]
    (rao/estimated-scan-size (:kv-store batch-db) tid))

  (-supports-ordered-index-handles [_ _ _ _ _]
    true)

  (-ordered-index-handles [search-param batch-db tid modifier compiled-values]
    (if (= 1 (count compiled-values))
      (p/-index-handles search-param batch-db tid modifier (first compiled-values))
      (let [index-handles #(p/-index-handles search-param batch-db tid modifier %)]
        (u/union-index-handles (map index-handles compiled-values)))))

  (-ordered-index-handles [search-param batch-db tid modifier compiled-values start-id]
    (if (= 1 (count compiled-values))
      (p/-index-handles search-param batch-db tid modifier (first compiled-values) start-id)
      (let [index-handles #(p/-index-handles search-param batch-db tid modifier % start-id)]
        (u/union-index-handles (map index-handles compiled-values)))))

  (-index-handles [search-param batch-db tid modifier compiled-value]
    (coll/eduction
     (comp (p/-matcher search-param batch-db modifier [compiled-value])
           (map ih/from-resource-handle))
     (p/-type-list batch-db tid)))

  (-index-handles [search-param batch-db tid modifier compiled-value start-id]
    (coll/eduction
     (comp (p/-matcher search-param batch-db modifier [compiled-value])
           (map ih/from-resource-handle))
     (p/-type-list batch-db tid start-id)))

  (-supports-ordered-compartment-index-handles [_ _]
    false)

  (-ordered-compartment-index-handles [_ _ _ _ _]
    (ba/unsupported))

  (-ordered-compartment-index-handles [_ _ _ _ _ _]
    (ba/unsupported))

  (-matcher [_ batch-db _ compiled-values]
    (filter #(matches? batch-db % compiled-values)))

  (-single-version-id-matcher [search-param batch-db tid modifier compiled-values]
    (u/single-version-id-matcher search-param batch-db tid modifier compiled-values))

  (-postprocess-matches [_ batch-db _ compiled-values]
    (postprocess-matches-xf batch-db compiled-values))

  (-index-values [_ _ _]
    []))

(defmethod special/special-search-param "near"
  [_ _]
  (->SearchParamNear "near" "special" "near"))
