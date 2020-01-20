(ns blaze.anomaly
  (:require
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]))


(s/fdef throw-anom
  :args
  (s/cat
    :category ::anom/category
    :message ::anom/message
    :kvs (s/* (s/cat :k keyword? :v some?))))

(defn throw-anom
  "Throws an `ex-info` with `message` and an anomaly build from `kvs`,
  `category` and `message` as data."
  [category message & {:as kvs}]
  (throw
    (ex-info
      message
      (assoc kvs
        ::anom/category category
        ::anom/message message))))
