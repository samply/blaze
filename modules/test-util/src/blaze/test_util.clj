(ns blaze.test-util
  (:require
    [blaze.anomaly :as ba]
    [blaze.byte-buffer :as bb]
    [blaze.executors :as ex]
    [blaze.fhir.structure-definition-repo]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer [is]]
    [clojure.test.check :as tc]
    [integrant.core :as ig]
    [juxt.iota :refer [given]])
  (:import
    [java.time Clock Instant ZoneId]
    [java.util Arrays Random]
    [java.util.concurrent Executors TimeUnit]))


(set! *warn-on-reflection* true)


(defn all-ex-data [e]
  (cond-> (ex-data e)
    (ex-data (ex-cause e))
    (assoc :cause-data (all-ex-data (ex-cause e)))))


(defmacro given-thrown [v & body]
  `(given (try ~v (is false) (catch Exception e# (all-ex-data e#)))
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


(defmethod ig/init-key :blaze.test/fixed-clock
  [_ _]
  (Clock/fixed Instant/EPOCH (ZoneId/of "UTC")))


(defmethod ig/init-key :blaze.test/system-clock
  [_ _]
  (Clock/systemUTC))


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
        (let [bb (bb/wrap byte-array)]
          (bb/set-position! bb 12)
          (bb/put-long! bb (swap! state inc)))))))


(defmethod ig/init-key :blaze.test/executor
  [_ _]
  (Executors/newFixedThreadPool 4))


(defmethod ig/halt-key! :blaze.test/executor
  [_ executor]
  (ex/shutdown! executor)
  (ex/await-termination executor 10 TimeUnit/SECONDS))


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


(ig/init {:blaze.fhir/structure-definition-repo {}})


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))
