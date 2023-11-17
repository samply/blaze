(ns blaze.async.flow-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.async.flow :as flow]
   [clojure.spec.alpha :as s]))

(s/fdef flow/publisher?
  :args (s/cat :x any?)
  :ret boolean?)

(s/fdef flow/subscriber?
  :args (s/cat :x any?)
  :ret boolean?)

(s/fdef flow/subscription?
  :args (s/cat :x any?)
  :ret boolean?)

(s/fdef flow/processor?
  :args (s/cat :x any?)
  :ret boolean?)

(s/fdef flow/subscribe!
  :args (s/cat :publisher flow/publisher? :subscriber flow/subscriber?))

(s/fdef flow/on-subscribe!
  :args (s/cat :subscriber flow/subscriber? :subscription flow/subscription?))

(s/fdef flow/request!
  :args (s/cat :subscription flow/subscription? :n pos-int?))

(s/fdef flow/cancel!
  :args (s/cat :subscriber flow/subscription?))

(s/fdef flow/collect
  :args (s/cat :publisher flow/publisher?)
  :ret ac/completable-future?)

(s/fdef flow/mapcat
  :args (s/cat :f ifn?)
  :ret flow/processor?)
