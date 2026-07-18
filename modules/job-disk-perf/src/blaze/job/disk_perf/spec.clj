(ns blaze.job.disk-perf.spec
  (:require
   [blaze.db.spec]
   [blaze.executors :as ex]
   [blaze.job.disk-perf :as-alias job-disk-perf]
   [blaze.spec]
   [clojure.spec.alpha :as s]))

(s/def ::job-disk-perf/dirs
  (s/map-of string? string?))

(s/def ::job-disk-perf/admin-node
  :blaze.db/node)

(s/def ::job-disk-perf/executor
  ex/executor?)

(s/def ::job-disk-perf/block-size
  pos-int?)

(s/def ::job-disk-perf/database
  string?)

(s/def ::job-disk-perf/file-size
  (s/and decimal? #(<= 0.0009765625M % 64M)))

(s/def ::job-disk-perf/phase-duration
  (s/and decimal? #(< 0M %) #(<= % 300M)))

(s/def ::job-disk-perf/max-concurrency
  (s/int-in 1 1025))

(s/def ::job-disk-perf/params
  (s/keys :opt-un [::job-disk-perf/database ::job-disk-perf/file-size
                   ::job-disk-perf/phase-duration ::job-disk-perf/max-concurrency]))
