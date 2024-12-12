(ns blaze.terminology-service.local.code-system.spec
  (:require
   [blaze.db.spec]
   [blaze.terminology-service.local.code-system :as-alias cs]
   [blaze.terminology-service.local.code-system.sct.spec]
   [clojure.spec.alpha :as s]))

(s/def ::cs/find-context
  (s/keys :req [:sct/context] :req-un [:blaze.db/db]))
