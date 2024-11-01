(ns blaze.cql.translator-spec
  (:require
   [blaze.anomaly-spec]
   [blaze.cql.translator :as t]
   [blaze.elm.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef t/translate
  :args (s/cat :cql string?)
  :ret (s/or :library :elm/library :anomaly ::anom/anomaly))
