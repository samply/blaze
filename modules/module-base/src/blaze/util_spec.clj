(ns blaze.util-spec
  (:require
    [blaze.util :as u]
    [clojure.spec.alpha :as s]))


(s/fdef u/duration-s
  :args (s/cat :start int?)
  :ret double?)


(s/fdef u/to-seq
  :args (s/cat :x any?)
  :ret (s/nilable sequential?))
