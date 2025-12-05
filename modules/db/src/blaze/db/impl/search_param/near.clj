(ns blaze.db.impl.search-param.near
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.async.comp :as ac]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.db.impl.index.index-handle :as ih]
   [blaze.db.impl.index.single-version-id :as svi]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param.special :as special]
   [blaze.db.impl.search-param.util :as u]
   [blaze.fhir.spec.type.system :as system]
   [clojure.string :as str]))

(set! *warn-on-reflection* true)

(defn- deg->rad
  "Convert degrees to radians"
  [degrees]
  (* degrees (/ Math/PI 180.0)))

(def ^:private ^:const earth-radius 6371000)

(defn- haversine-distance
  "Calculate the distance between two geographic coordinates using the Haversine formula.
   Returns distance in meters.

   loc-1 and loc-2 should be maps with :latitude and :longitude keys"
  [{lat-1 :latitude lon-1 :longitude} {lat-2 :latitude lon-2 :longitude}]
  (let [a (+ (Math/pow (Math/sin (/ (- (deg->rad lat-2) (deg->rad lat-1)) 2)) 2)
             (* (Math/cos (deg->rad lat-1))
                (Math/cos (deg->rad lat-2))
                (Math/pow (Math/sin (/ (- (deg->rad lon-2) (deg->rad lon-1)) 2)) 2)))
        c (* 2 (Math/atan2 (Math/sqrt a) (Math/sqrt (- 1 a))))]
    (* earth-radius c)))

(defn- near?
  "Check if two locations are within a specified distance of each other.

   Parameters:
   - loc-1: map with :latitude and :longitude keys
   - loc-2: map with :latitude and :longitude keys
   - dist: distance threshold in meters (number)

   Returns: true if locations are within dist meters of each other"
  [loc-1 loc-2 distance]
  (let [actual-dist (haversine-distance loc-1 loc-2)]
    (<= actual-dist distance)))

(defn- matches? [batch-db resource-handle {:keys [distance] :as query}]
  (-> (d/pull batch-db resource-handle)
      (ac/then-apply
       (fn [{:keys [position] :as resource}]
         (when (near? position query distance)
           resource)))))

(defn- unsupported-unit-msg [unit code]
  (format "Unsupported unit `%s` in search parameter `%s`. Supported are 'km', 'm'" unit code))

; TODO should we implement all of https://fhir.hl7.org/fhir/valueset-distance-units.html
(defn- distance-in-meters [dist unit code]
  (cond
    (nil? dist)
    1000M

    (or (nil? unit) (= unit "km"))
    (* (system/parse-decimal dist) 1000)

    (= unit "m")
    (system/parse-decimal dist)

    :else
    (ba/incorrect (unsupported-unit-msg unit code))))

(defrecord SearchParamNear [index name type code]
  p/SearchParam
  (-validate-modifier [_ modifier]
    (some->> modifier (u/unknown-modifier-anom code)))

  (-compile-value [_ _ value]
    (let [[lat long dist unit] (str/split value #"\|" 4)]
      (when-ok [meters (distance-in-meters dist unit code)]
        {:latitude (system/parse-decimal lat)
         :longitude (system/parse-decimal long)
         :distance meters})))

  (-estimated-scan-size [_ _ _ _ _]
    (ba/unsupported))

  (-supports-ordered-index-handles [_ _ _ _ _]
    false)

  (-ordered-index-handles [_ _ _ _ _]
    (ba/unsupported))

  (-ordered-index-handles [_ _ _ _ _ _]
    (ba/unsupported))

  (-index-handles [_ batch-db tid _ _]
    (coll/eduction
     (map ih/from-resource-handle)
     (d/type-list batch-db tid)))

  (-index-handles [_ batch-db tid _ _ start-id]
    (coll/eduction
     (map ih/from-resource-handle)
     (d/type-list batch-db tid start-id)))

  (-supports-ordered-compartment-index-handles [_ _]
    false)

  (-ordered-compartment-index-handles [_ _ _ _ _]
    (ba/unsupported))

  (-ordered-compartment-index-handles [_ _ _ _ _ _]
    (ba/unsupported))

  (-matcher [_ batch-db _ compiled-values]
    (filter #(matches? batch-db % compiled-values)))

  (-single-version-id-matcher [search-param batch-db tid modifier compiled-values]
    (comp (map ih/from-single-version-id)
          (u/resource-handle-xf batch-db tid)
          (p/-matcher search-param batch-db modifier compiled-values)
          (map svi/from-resource-handle)))

  (-second-pass-filter [_ _ _])

  (-index-values [_ _ _]
    []))

(defmethod special/special-search-param "near"
  [index _]
  (->SearchParamNear index "near" "special" "near"))
