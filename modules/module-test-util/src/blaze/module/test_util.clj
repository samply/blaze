(ns blaze.module.test-util
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :refer [do-sync]]
   [blaze.test-util :as tu]
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

(defmacro given-failed-system
  "Starts a system from `config`. Assumes that the startup fails. Stops the
  system in order to stop successful started components. Runs a given macro with
  `body` on the exception data."
  [config & body]
  `(let [data# (try (ig/init ~config) (is false) (catch Exception e# (tu/all-ex-data e#)))]
     (ig/halt! (:system data#))
     (given data#
       ~@body)))
