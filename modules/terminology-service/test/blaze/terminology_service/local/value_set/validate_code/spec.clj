(ns blaze.terminology-service.local.value-set.validate-code.spec
  (:require
   [blaze.terminology-service.local.value-set.validate-code :as-alias l-vs-validate-code]
   [blaze.terminology-service.spec]
   [blaze.terminology-service.value-set-validate-code :as-alias vs-validate-code]
   [clojure.spec.alpha :as s]))

(s/def ::l-vs-validate-code/context
  (s/keys :req-un [::vs-validate-code/request]))
