(ns blaze.luid-spec
  (:require
    [blaze.luid :as luid]
    [blaze.spec]
    [clojure.spec.alpha :as s]))


(s/fdef luid/luid
  :args (s/cat :clock :blaze/clock :rng-fn :blaze/rng)
  :ret string?)


(s/fdef luid/successive-luids
  :args (s/cat :clock :blaze/clock :rng-fn :blaze/rng)
  :ret (s/every string?))
