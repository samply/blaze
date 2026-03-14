(ns blaze.terminology-service.local-spec
  (:require
   [blaze.db.spec]
   [blaze.terminology-service.local :as local]
   [blaze.terminology-service.spec]
   [clojure.spec.alpha :as s]))

(s/fdef local/post-init!
  :args (s/cat :terminology-service :blaze/terminology-service
               :node :blaze.db/node))
