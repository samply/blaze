(ns blaze.module-spec
  (:require
   [blaze.luid.spec]
   [blaze.module :as m]
   [blaze.spec]
   [clojure.spec.alpha :as s]))

(s/fdef m/luid
  :args (s/cat :context (s/keys :req-un [:blaze/clock :blaze/rng-fn]))
  :ret :blaze/luid)

(s/fdef m/luid-generator
  :args (s/cat :context (s/keys :req-un [:blaze/clock :blaze/rng-fn]))
  :ret :blaze.luid/generator)
