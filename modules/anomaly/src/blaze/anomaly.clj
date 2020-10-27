(ns blaze.anomaly
  (:require
    [cognitect.anomalies :as anom])
  (:refer-clojure :exclude [ensure-reduced]))


(defn ex-anom
  "Creates an ExceptionInfo with `message` and an anomaly build from `kvs`,
  `category` and `message` as data."
  [anom]
  (ex-info (::anom/message anom "") anom))


(defn throw-anom
  "Throws an ExceptionInfo build with `ex-anom`."
  [category message & {:as kvs}]
  (throw (ex-anom (assoc kvs ::anom/category category ::anom/message message))))


(defmacro when-ok [[sym form] & body]
  `(let [~sym ~form]
     (if (::anom/category ~sym)
       ~sym
       (do ~@body))))


(defn ensure-reduced [x]
  (if (::anom/category x)
    (reduced x)
    x))
