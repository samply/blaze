(ns blaze.terminology-service.extern
  (:require
    [blaze.executors :refer [executor?]]
    [blaze.fhir-client :as client]
    [blaze.terminology-service :as ts :refer [term-service?]]
    [clojure.core.cache :as cache]
    [clojure.spec.alpha :as s]
    [manifold.deferred :as md]
    [prometheus.alpha :as prom :refer [defhistogram]]
    [taoensso.timbre :as log])
  (:import
    [java.io Closeable]))


(def ^:private cache (atom (cache/lru-cache-factory {} :threshold 1024)))


(defhistogram request-duration-seconds
  "Terminology Service request latencies in seconds."
  {:namespace "terminology_service"}
  (take 14 (iterate #(* 2 %) 0.001)))


(defn- expand-value-set [base params]
  (let [^Closeable timer (prom/timer request-duration-seconds)]
    (log/debug "Expand ValueSet with params" (pr-str params))
    (-> (client/fetch (str base "/ValueSet/$expand") {:query-params params})
        (md/finally' #(.close timer)))))


(defn- hit [cache k]
  (if (cache/has? cache k)
    (cache/hit cache k)
    cache))


(defrecord TermService [base executor]
  ts/TermService
  (-expand-value-set [_ params]
    (if-let [res (get (swap! cache hit params) params)]
      res
      (-> (expand-value-set base params)
          (md/chain'
            (fn [value-set]
              (swap! cache cache/miss params value-set)
              value-set))))))


(s/fdef term-service
  :args (s/cat :base string? :executor executor?)
  :ret term-service?)

(defn term-service [base executor]
  (->TermService base executor))
