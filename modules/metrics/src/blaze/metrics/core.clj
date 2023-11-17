(ns blaze.metrics.core
  (:require
   [clojure.core.protocols :as p]
   [clojure.datafy :as datafy])
  (:import
   [io.prometheus.client
    Collector Collector$MetricFamilySamples Collector$MetricFamilySamples$Sample
    Collector$Type CollectorRegistry CounterMetricFamily
    GaugeMetricFamily]
   [java.util List]))

(set! *warn-on-reflection* true)

(defmacro collector [& body]
  `(blaze.metrics.collector. (fn [] ~@body)))

(defn collect
  "Returns all the metrics of `collector`."
  [collector]
  (mapv datafy/datafy (.collect ^Collector collector)))

(defn counter-metric
  "Creates a counter metric from `samples`.

  The other arguments are:
  * name: the name with must only contain word chars
  * the help text
  * a collection of label names
  * a collection of samples"
  [name help label-names samples]
  (let [m (CounterMetricFamily. ^String name ^String help ^List label-names)]
    (run!
     (fn [{:keys [label-values value]}] (.addMetric m label-values value))
     samples)
    m))

(defn gauge-metric
  "Creates a gauge metric from `samples`.

  The other arguments are:
  * name: the name with must only contain word chars
  * the help text
  * a collection of label names
  * a collection of samples"
  [name help label-names samples]
  (let [m (GaugeMetricFamily. ^String name ^String help ^List label-names)]
    (run!
     (fn [{:keys [label-values value]}] (.addMetric m label-values value))
     samples)
    m))

(extend-protocol p/Datafiable
  Collector$Type
  (datafy [type]
    (condp identical? type
      Collector$Type/COUNTER :counter
      Collector$Type/GAUGE :gauge
      type))

  Collector$MetricFamilySamples
  (datafy [metric]
    {:name (.-name metric)
     :type (datafy/datafy (.-type metric))
     :samples (mapv datafy/datafy (.-samples metric))})

  Collector$MetricFamilySamples$Sample
  (datafy [sample]
    {:name (.-name sample)
     :label-names (vec (.-labelNames sample))
     :label-values (vec (.-labelValues sample))
     :value (.-value sample)}))

(defn register!
  "Registers `collector` to `registry`."
  [registry collector]
  (.register ^CollectorRegistry registry collector))
