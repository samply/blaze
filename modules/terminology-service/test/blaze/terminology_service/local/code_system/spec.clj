(ns blaze.terminology-service.local.code-system.spec
  (:require
   [blaze.db.spec]
   [blaze.terminology-service.expand-value-set.request :as-alias request]
   [blaze.terminology-service.local.code-system :as-alias cs]
   [blaze.terminology-service.local.code-system.sct.spec]
   [clojure.spec.alpha :as s]))

(s/def ::cs/find-context
  (s/keys :req-un [:blaze.db/db] :opt [:sct/context]))

(s/def ::cs/expand-request
  (s/keys :opt-un [::request/active-only]))
