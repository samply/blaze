(ns blaze.terminology-service.local.value-set.spec
  (:require
   [blaze.db.spec]
   [blaze.terminology-service.local.value-set :as vs]
   [blaze.terminology-service.local.value-set.find :as-alias vs-find]
   [blaze.terminology-service.request :as-alias request]
   [blaze.terminology-service.spec]
   [clojure.spec.alpha :as s]))

(s/def ::vs-find/request
  (s/keys :opt-un [::request/tx-resources]))

(s/def ::vs/find-context
  (s/keys :req-un [:blaze.db/db] :opt-un [::vs-find/request]))
