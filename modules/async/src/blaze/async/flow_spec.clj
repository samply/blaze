(ns blaze.async.flow-spec
  (:require
    [blaze.async.comp :as ac]
    [blaze.async.flow :as flow]
    [clojure.spec.alpha :as s]))


(s/fdef flow/publisher?
  :args (s/cat :x any?)
  :ret boolean?)


(s/fdef flow/processor?
  :args (s/cat :x any?)
  :ret boolean?)


(s/fdef flow/collect
  :args (s/cat :publisher flow/publisher?)
  :ret ac/completable-future?)


(s/fdef flow/mapcat
  :args (s/cat :f ifn?)
  :ret flow/processor?)
