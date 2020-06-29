(ns blaze.executors-spec
  (:require
    [blaze.executors :as ex]
    [clojure.spec.alpha :as s]))


(s/fdef ex/manifold-cpu-bound-pool
  :args (s/cat :name-template string?))


(s/fdef ex/cpu-bound-pool
  :args (s/cat :name-template string?))


(s/fdef ex/io-pool
  :args (s/cat :n pos-int? :name-template string?))


(s/fdef ex/single-thread-executor
  :args (s/cat :name (s/? string?)))
