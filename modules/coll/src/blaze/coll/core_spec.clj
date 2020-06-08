(ns blaze.coll.core-spec
  (:require
    [blaze.coll.core :as coll]
    [clojure.spec.alpha :as s])
  (:import
    [clojure.lang IReduceInit]))


(s/fdef coll/first
  :args (s/cat :coll (s/nilable #(instance? IReduceInit %)))
  :ret any?)
