(ns blaze.metrics.spec
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str])
  (:import
    [io.prometheus.client Collector CollectorRegistry]))


(s/def :blaze.metrics/collector
  #(instance? Collector %))


(s/def :blaze.metrics/collectors
  (s/coll-of :blaze.metrics/collector))


(defn registry? [x]
  (instance? CollectorRegistry x))


(s/def :blaze.metrics/registry
  registry?)


(s/def :blaze.metrics/metric
  (s/keys :req-un [:blaze.metrics.metric/name :blaze.metrics.metric/samples]))


(s/def :blaze.metrics.metric/name
  (s/and string? #(re-matches #"\w+" %)))


(s/def :blaze.metrics.metric/label-name
  (s/and string? #(re-matches #"\w+" %)))


(s/def :blaze.metrics.counter/name
  (s/and :blaze.metrics.metric/name #(str/ends-with? % "_total")))


(s/def :blaze.metrics.metric/samples
  (s/coll-of :blaze.metrics/sample))


(s/def :blaze.metrics/sample
  (s/keys :req-un [:blaze.metrics.sample/label-values :blaze.metrics.sample/value]))


(s/def :blaze.metrics.sample/label-values
  (s/coll-of string?))


(s/def :blaze.metrics.sample/value
  number?)
