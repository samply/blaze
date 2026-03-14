(ns blaze.terminology-service.local.validate-code-spec
  (:require
   [blaze.terminology-service.local.validate-code :as vc]
   [blaze.terminology-service.local.validate-code.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef vc/check-display
  :args (s/cat :context map? :params ::vc/params :concept map?)
  :ret (s/or :concept map? :anomaly ::anom/anomaly))
