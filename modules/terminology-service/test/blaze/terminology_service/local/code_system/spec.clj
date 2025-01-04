(ns blaze.terminology-service.local.code-system.spec
  (:require
   [blaze.db.spec]
   [blaze.terminology-service.local :as-alias local]
   [blaze.terminology-service.local.code-system :as-alias cs]
   [blaze.terminology-service.local.code-system.sct.spec]
   [blaze.terminology-service.local.spec]
   [blaze.terminology-service.local.validate-code :as-alias vc]
   [blaze.terminology-service.local.validate-code.spec]
   [clojure.spec.alpha :as s]))

(s/def ::cs/required-content
  #{"supplement"})

(s/def ::cs/find-context
  (s/keys :req-un [:blaze.db/db]
          :opt-un [::local/tx-resources]
          :opt [:sct/context ::cs/required-content]))

(s/def ::active-only
  boolean?)

(s/def ::include-designations
  boolean?)

(s/def ::cs/expand-params
  (s/keys :opt-un [::active-only]))

(s/def ::cs/validate-code-params
  (s/keys :req-un [::vc/clause] :opt-un [::active-only ::include-designations]))
