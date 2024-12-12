(ns blaze.terminology-service.local.value-set.expand.spec
  (:require
   [blaze.terminology-service.expand-value-set :as-alias expand-vs]
   [blaze.terminology-service.local.value-set.expand :as-alias vs-expand]
   [blaze.terminology-service.spec]
   [clojure.spec.alpha :as s]))

(s/def ::vs-expand/context
  (s/keys :req-un [::expand-vs/request]))
