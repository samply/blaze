(ns blaze.fhir.spec.type.system-spec
  (:require
    [blaze.anomaly-spec]
    [blaze.fhir.spec.type.system :as system]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]))


(s/fdef system/value?
  :args (s/cat :x any?)
  :ret boolean?)


(s/fdef system/type
  :args (s/cat :x any?)
  :ret (s/nilable keyword?))


(s/fdef system/equals
  :args (s/cat :a any? :b any?)
  :ret (s/nilable boolean?))


(s/fdef system/decimal?
  :args (s/cat :x any?)
  :ret boolean?)


(s/fdef system/parse-decimal
  :args (s/cat :s string?)
  :ret (s/or :decimal system/decimal?
             :anomaly ::anom/anomaly))



;; ---- System.Date -----------------------------------------------------------

(s/fdef system/date?
  :args (s/cat :x any?)
  :ret boolean?)



;; ---- System.DateTime -------------------------------------------------------

(s/fdef system/date-time?
  :args (s/cat :x any?)
  :ret boolean?)


(s/fdef system/parse-date-time
  :args (s/cat :s string?)
  :ret (s/or :date-time system/date-time?
             :anomaly ::anom/anomaly))
