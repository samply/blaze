(ns blaze.terminology-service.local.value-set-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.spec]
   [blaze.terminology-service.local.priority-spec]
   [blaze.terminology-service.local.value-set :as vs]
   [blaze.terminology-service.local.value-set.expand-spec]
   [blaze.terminology-service.local.value-set.validate-code-spec]
   [clojure.spec.alpha :as s]))

(s/fdef vs/find
  :args (s/cat :db :blaze.db/db :url string? :version (s/? string?))
  :ret ac/completable-future?)
