(ns blaze.terminology-service.local.value-set.validate-code.spec
  (:require
   [blaze.db.spec]
   [blaze.fhir.spec]
   [blaze.terminology-service.local.validate-code :as-alias vc]
   [blaze.terminology-service.local.validate-code.spec]
   [blaze.terminology-service.local.value-set.validate-code :as-alias vs-validate-code]
   [clojure.spec.alpha :as s]))

(s/def ::vs-validate-code/context
  (s/keys :req-un [:blaze.db/db]))

(s/def ::active-only
  boolean?)

(s/def ::vs-validate-code/params
  (s/keys :req-un [::vc/clause] :opt-un [::active-only]))
