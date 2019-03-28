(ns life-fhir-store.util.cache
  (:require
    [clojure.core.cache :as cache :refer [CacheProtocol]]
    [clojure.spec.alpha :as s]))


(defn cache? [x]
  (satisfies? CacheProtocol x))

(s/fdef update-cache
  :args (s/cat :src-cache cache? :dst-cache cache? :key some? :result any?)
  :ret cache?)

(defn update-cache
  "Updates `dst-cache` with hit/miss information according to the state of
  `src-cache`."
  [src-cache dst-cache key result]
  (if (cache/has? src-cache key)
    (cache/hit dst-cache key)
    (cache/miss dst-cache key result)))
