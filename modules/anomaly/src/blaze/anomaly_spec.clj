(ns blaze.anomaly-spec
  (:require
    [blaze.anomaly :as anomaly]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]))


(s/fdef anomaly/ex-anom
  :args (s/cat :anom ::anom/anomaly))


(s/fdef anomaly/throw-anom
  :args
  (s/cat
    :category ::anom/category
    :message ::anom/message
    :kvs (s/* (s/cat :k keyword? :v some?))))
