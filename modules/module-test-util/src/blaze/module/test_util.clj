(ns blaze.module.test-util
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :refer [do-sync]]
   [clojure.string :as str]
   [clojure.test :refer [is]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]))

(set! *warn-on-reflection* true)

(defmacro with-system
  "Runs `body` inside a system that is initialized from `config`, bound to
  `binding-form` and finally halted."
  [[binding-form config] & body]
  `(let [system# (ig/init ~config)]
     (try
       (let [~binding-form system#]
         ~@body)
       (finally
         (ig/halt! system#)))))

(defn assoc-thread-name
  "Associates the name of the thread on which a function will be executed after
  `future` completes normally under :thread-name in the metadata of the returned
  value."
  [future]
  (do-sync [value future]
    (vary-meta value assoc :thread-name (.getName (Thread/currentThread)))))

(defn common-pool-thread? [thread-name]
  (or (str/starts-with? thread-name "ForkJoinPool.commonPool")
      (= (.getName (Thread/currentThread)) thread-name)))

(defmacro given-failed-future [future & body]
  `(given (ba/try-anomaly (deref ~future) (is false))
     ~@body))
