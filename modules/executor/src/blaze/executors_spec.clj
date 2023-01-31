(ns blaze.executors-spec
  (:require
    [blaze.executors :as ex]
    [clojure.spec.alpha :as s])
  (:import
    [java.util.concurrent TimeUnit]))


(s/fdef ex/executor?
  :args (s/cat :x any?)
  :ret boolean?)


(s/fdef ex/executor-service?
  :args (s/cat :x any?)
  :ret boolean?)


(s/fdef ex/execute!
  :args (s/cat :executor ex/executor? :command ifn?))


(s/fdef ex/shutdown!
  :args (s/cat :executor-service ex/executor-service?))


(s/fdef ex/shutdown?
  :args (s/cat :executor-service ex/executor-service?)
  :ret boolean?)


(s/fdef ex/terminated?
  :args (s/cat :executor-service ex/executor-service?)
  :ret boolean?)


(s/fdef ex/await-termination
  :args (s/cat :executor-service ex/executor-service? :timeout nat-int?
               :unit #(instance? TimeUnit %))
  :ret boolean?)


(s/fdef ex/cpu-bound-pool
  :args (s/cat :name-template string?))


(s/fdef ex/io-pool
  :args (s/cat :n pos-int? :name-template string?))


(s/fdef ex/single-thread-executor
  :args (s/cat :name (s/? string?)))
