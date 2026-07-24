(ns blaze.job.disk-perf.score-spec
  (:require
   [blaze.job.disk-perf.score :as score]
   [clojure.spec.alpha :as s]))

(s/def ::concurrency
  pos-int?)

(s/def ::iops
  (s/double-in :min 0.0 :infinite? false :NaN? false))

(s/def ::read-run
  (s/keys :req-un [::concurrency ::iops]))

(s/def ::read-runs
  (s/coll-of ::read-run :min-count 1))

(s/def ::seq-write-bytes-per-second
  (s/double-in :min 0.0 :infinite? false :NaN? false))

(s/def ::fsync-rate
  (s/double-in :min 0.0 :infinite? false :NaN? false))

(s/def ::measurement
  (s/keys :req-un [::read-runs ::seq-write-bytes-per-second ::fsync-rate]))

(s/fdef score/score
  :args (s/cat :measurement ::measurement)
  :ret (s/double-in :min 0.0 :max 100.0))

(s/fdef score/rating
  :args (s/cat :score (s/double-in :min 0.0 :max 100.0))
  :ret #{"excellent" "good" "acceptable" "insufficient"})
