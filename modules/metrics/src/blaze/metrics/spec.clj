(ns blaze.metrics.spec
  (:require
    [clojure.spec.alpha :as s])
  (:import
    [io.prometheus.client Collector CollectorRegistry]))


(s/def :blaze.metrics/collector
  #(instance? Collector %))


(s/def :blaze.metrics/collectors
  (s/coll-of :blaze.metrics/collector))


(s/def :blaze.metrics/registry
  #(instance? CollectorRegistry %))


(s/def :blaze.metrics/metric
  (s/keys :req-un [:blaze.metrics.metric/name :blaze.metrics.metric/samples]))


(s/def :blaze.metrics.metric/name
  string?)


(s/def :blaze.metrics.metric/samples
  (s/coll-of :blaze.metrics/sample))


(s/def :blaze.metrics/sample
  (s/keys :req-un [:blaze.metrics.sample/label-values :blaze.metrics.sample/value]))


(s/def :blaze.metrics.sample/label-values
  (s/coll-of string?))


(s/def :blaze.metrics.sample/value
  double?)
