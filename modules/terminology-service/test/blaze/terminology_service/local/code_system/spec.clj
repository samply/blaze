(ns blaze.terminology-service.local.code-system.spec
  (:require
   [blaze.db.spec]
   [blaze.terminology-service.local.code-system :as-alias cs]
   [blaze.terminology-service.local.code-system.find :as-alias cs-find]
   [blaze.terminology-service.local.code-system.sct.spec]
   [blaze.terminology-service.request :as-alias request]
   [clojure.spec.alpha :as s]))

(s/def ::cs-find/request
  (s/keys :opt-un [::request/tx-resources]))

(s/def ::cs/find-context
  (s/keys :req-un [:blaze.db/db] :opt-un [::cs-find/request] :opt [:sct/context]))

(s/def ::cs/expand-request
  (s/keys :opt-un [::request/active-only]))
