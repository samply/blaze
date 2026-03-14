(ns blaze.terminology-service.local.code-system.loinc-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.spec]
   [blaze.path.spec]
   [blaze.terminology-service.local.code-system.loinc :as loinc]
   [blaze.terminology-service.local.code-system.loinc.spec]
   [clojure.spec.alpha :as s]))

(s/fdef loinc/ensure-code-systems
  :args (s/cat :context (s/keys :req-un [:blaze.db/node :blaze/clock :blaze/rng-fn])
               :loinc-context :loinc/context)
  :ret ac/completable-future?)
