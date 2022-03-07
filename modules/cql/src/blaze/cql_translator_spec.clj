(ns blaze.cql-translator-spec
  (:require
    [blaze.anomaly-spec]
    [blaze.cql-translator :as cql-translator]
    [blaze.elm.spec]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]))


(s/fdef cql-translator/translate
  :args (s/cat :cql string? :opts (s/* some?))
  :ret (s/or :library :elm/library :anomaly ::anom/anomaly))
