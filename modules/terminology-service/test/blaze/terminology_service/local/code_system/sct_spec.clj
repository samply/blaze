(ns blaze.terminology-service.local.code-system.sct-spec
  (:require
   [blaze.path.spec]
   [blaze.terminology-service.local.code-system.sct :as sct]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef sct/build-context
  :args (s/cat :release-path :blaze/dir)
  :ret (s/or :context :sct/context :anomaly ::anom/anomaly))
