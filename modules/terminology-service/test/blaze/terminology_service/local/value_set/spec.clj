(ns blaze.terminology-service.local.value-set.spec
  (:require
   [blaze.db.spec]
   [blaze.terminology-service.local :as-alias local]
   [blaze.terminology-service.local.spec]
   [blaze.terminology-service.local.value-set :as vs]
   [clojure.spec.alpha :as s]))

(s/def ::vs/find-context
  (s/keys :req-un [:blaze.db/db] :opt-un [::local/tx-resources]))
