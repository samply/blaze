(ns blaze.test-util
  (:require
    [blaze.anomaly :as ba]
    [clojure.test :refer [is]]
    [clojure.test.check :as tc]
    [integrant.core :as ig]
    [juxt.iota :refer [given]])
  (:import
    [java.nio ByteBuffer]
    [java.time Clock Instant ZoneId]
    [java.util Arrays Random]
    [java.util.concurrent Executors ExecutorService TimeUnit]))


(set! *warn-on-reflection* true)


(defmacro given-thrown [v & body]
  `(given (try ~v (is false) (catch Exception e# (ex-data e#)))
     ~@body))


(defmacro given-failed-future [future & body]
  `(given (try (deref ~future) (is false) (catch Exception e# (ba/anomaly e#)))
     ~@body))


(defmacro with-system [[binding-form system] & body]
  `(let [system# (ig/init ~system)]
     (try
       (let [~binding-form system#]
         ~@body)
       (finally
         (ig/halt! system#)))))


(defmethod ig/init-key :blaze.test/clock
  [_ _]
  (Clock/fixed Instant/EPOCH (ZoneId/of "UTC")))


(defmethod ig/init-key :blaze.test/fixed-rng-fn
  [_ {:keys [n] :or {n 0}}]
  #(proxy [Random] []
     (nextLong [] n)))


(defmethod ig/init-key :blaze.test/fixed-rng
  [_ _]
  (let [state (atom 0)]
    (proxy [Random] []
      (nextBytes [byte-array]
        (assert (= 20 (count byte-array)))
        (let [bb (ByteBuffer/wrap byte-array)]
          (.position bb 12)
          (.putLong bb (swap! state inc)))))))


(defmethod ig/init-key :blaze.test/executor
  [_ _]
  (Executors/newFixedThreadPool 4))


(defmethod ig/halt-key! :blaze.test/executor
  [_ ^ExecutorService executor]
  (.shutdown executor)
  (.awaitTermination executor 10 TimeUnit/SECONDS))


(defmacro satisfies-prop [num-tests prop]
  `(let [result# (tc/quick-check ~num-tests ~prop)]
     (if (instance? Throwable (:result result#))
       (throw (:result result#))
       (if (true? (:result result#))
         (is :success)
         (is (clojure.pprint/pprint result#))))))


(defn bytes=
  "Compares two byte arrays for equivalence."
  {:arglists '([a b])}
  [^bytes a ^bytes b]
  (Arrays/equals a b))
