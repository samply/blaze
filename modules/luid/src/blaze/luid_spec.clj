(ns blaze.luid-spec
  (:require
   [blaze.luid :as luid]
   [blaze.luid.spec]
   [blaze.spec]
   [clojure.spec.alpha :as s]))

(s/fdef luid/luid
  :args (s/cat :clock :blaze/clock :rng :blaze/rng)
  :ret :blaze/luid)

(s/fdef luid/generator?
  :args (s/cat :x any?)
  :ret boolean?)

(s/fdef luid/head
  :args (s/cat :generator ::luid/generator)
  :ret :blaze/luid)

(s/fdef luid/next
  :args (s/cat :generator ::luid/generator)
  :ret ::luid/generator)

(s/fdef luid/generator
  :args (s/cat :clock :blaze/clock :rng :blaze/rng)
  :ret ::luid/generator)
