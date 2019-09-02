(ns blaze.util
  (:require
    [clojure.string :as str]
    [cognitect.anomalies :as anom]))


(defn title-case [s]
  (str (str/upper-case (subs s 0 1)) (subs s 1)))


(defn throw-anom
  {:arglists '([anomaly])}
  [{::anom/keys [category message] :as x}]
  (if category
    (throw (ex-info (or message (name category)) x))
    x))


(defmacro anom-let [bindings & body]
  (assert (even? (count bindings)))
  `(try
     (let
       ~(into
          []
          (mapcat
            (fn [[binding expr]]
              [binding `(throw-anom ~expr)]))
          (partition 2 bindings))
       ~@body)
     (catch Exception e#
       (if (::anom/category (ex-data e#))
         (ex-data e#)
         (throw e#)))))
