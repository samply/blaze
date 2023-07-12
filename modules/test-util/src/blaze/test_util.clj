(ns blaze.test-util
  (:require
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer [is]]
    [clojure.test.check :as tc]
    [juxt.iota :refer [given]])
  (:import
    [java.util Arrays]))


(set! *warn-on-reflection* true)


(defn all-ex-data [e]
  (cond-> (ex-data e)
    (ex-message e)
    (assoc :message (ex-message e))
    (ex-data (ex-cause e))
    (assoc :cause-data (all-ex-data (ex-cause e)))))


(defmacro given-thrown [v & body]
  `(given (try ~v (is false) (catch Exception e# (all-ex-data e#)))
     ~@body))


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


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))
