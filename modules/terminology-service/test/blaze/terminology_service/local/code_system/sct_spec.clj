(ns blaze.terminology-service.local.code-system.sct-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.spec]
   [blaze.path.spec]
   [blaze.terminology-service.local.code-system.sct :as sct]
   [blaze.terminology-service.local.code-system.sct.spec]
   [clojure.spec.alpha :as s]))

(s/fdef sct/ensure-code-systems
  :args (s/cat :context (s/keys :req-un [:blaze.db/node :blaze/clock :blaze/rng-fn])
               :sct-context :sct/context)
  :ret ac/completable-future?)
