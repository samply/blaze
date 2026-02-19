(ns blaze.async.comp-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.executors :as ex]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom])
  (:import
   [java.util.concurrent TimeUnit]))

(s/fdef ac/completable-future?
  :args (s/cat :x any?)
  :ret boolean?)

(s/fdef ac/future
  :args (s/cat)
  :ret ac/completable-future?)

(s/fdef ac/completed-future
  :args (s/cat :x any?)
  :ret ac/completable-future?)

(s/fdef ac/failed-future
  :args (s/cat :e #(instance? Throwable %))
  :ret ac/completable-future?)

(s/fdef ac/all-of
  :args (s/cat :futures (s/coll-of ac/completable-future?))
  :ret ac/completable-future?)

(s/fdef ac/complete!
  :args (s/cat :future ac/completable-future? :x any?)
  :ret boolean?)

(s/fdef ac/complete-async!
  :args (s/cat :future ac/completable-future? :f ifn?)
  :ret boolean?)

(s/fdef ac/or-timeout!
  :args (s/cat :future ac/completable-future? :timeout pos-int?
               :unit #(instance? TimeUnit %))
  :ret ac/completable-future?)

(s/fdef ac/complete-on-timeout!
  :args (s/cat :future ac/completable-future? :x any? :timeout pos-int?
               :unit #(instance? TimeUnit %))
  :ret ac/completable-future?)

(s/fdef ac/complete-exceptionally!
  :args (s/cat :future ac/completable-future? :e #(instance? Throwable %))
  :ret boolean?)

(s/fdef ac/delayed-executor
  :args (s/cat :delay pos-int? :unit #(instance? TimeUnit %))
  :ret ex/executor?)

(s/fdef ac/join
  :args (s/cat :future ac/completable-future?)
  :ret any?)

(s/fdef ac/canceled?
  :args (s/cat :future ac/completable-future?)
  :ret boolean?)

(s/fdef ac/cancel!
  :args (s/cat :future ac/completable-future?)
  :ret boolean?)

(s/fdef ac/supply-async
  :args (s/cat :f ifn? :executor (s/? ex/executor?))
  :ret ac/completable-future?)

(s/fdef ac/completion-stage?
  :args (s/cat :x any?)
  :ret boolean?)

(s/fdef ac/then-apply
  :args (s/cat :stage ac/completion-stage? :f ifn?)
  :ret ac/completion-stage?)

(s/fdef ac/then-apply-async
  :args (s/cat :stage ac/completion-stage? :f ifn?
               :executor (s/? ex/executor?))
  :ret ac/completion-stage?)

(s/fdef ac/then-compose
  :args (s/cat :stage ac/completion-stage? :f ifn?)
  :ret ac/completion-stage?)

(s/fdef ac/then-compose-async
  :args (s/cat :stage ac/completion-stage? :f ifn?
               :executor (s/? ex/executor?))
  :ret ac/completion-stage?)

(s/fdef ac/handle
  :args (s/cat :stage ac/completion-stage? :f ifn?)
  :ret ac/completion-stage?)

(s/fdef ac/handle-async
  :args (s/cat :stage ac/completion-stage? :f ifn?)
  :ret ac/completion-stage?)

(s/fdef ac/exceptionally
  :args (s/cat :stage ac/completion-stage? :f ifn?)
  :ret ac/completion-stage?)

(s/fdef ac/exceptionally-compose
  :args (s/cat :stage ac/completion-stage? :f ifn?)
  :ret ac/completion-stage?)

(s/fdef ac/exceptionally-compose-async
  :args (s/cat :stage ac/completion-stage? :f ifn?)
  :ret ac/completion-stage?)

(s/fdef ac/when-complete
  :args (s/cat :stage ac/completion-stage? :f ifn?)
  :ret ac/completion-stage?)

(s/fdef ac/->completable-future
  :args (s/cat :stage ac/completion-stage?)
  :ret ac/completable-future?)

(s/fdef ac/retry
  :args (s/cat :f ifn? :action-name string? :num-retries pos-int?)
  :ret ac/completable-future?)

(s/fdef ac/retry2
  :args (s/cat :f ifn? :retry? (s/fspec :args (s/cat :e ::anom/anomaly) :ret boolean?))
  :ret ac/completable-future?)
