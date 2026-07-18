(ns blaze.job.disk-perf.engine-spec
  (:require
   [blaze.anomaly-spec]
   [blaze.job.disk-perf.engine :as engine]
   [blaze.path :refer [path?]]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/def ::file-size
  pos-int?)

(s/def ::block-size
  pos-int?)

(s/def ::concurrency
  pos-int?)

(s/def ::concurrencies
  (s/coll-of pos-int? :kind vector? :min-count 1))

(s/def ::duration-millis
  pos-int?)

(s/def ::phase-duration-millis
  pos-int?)

(s/def ::direct?
  boolean?)

(s/fdef engine/check-free-space
  :args (s/cat :dir string? :file-size pos-int?)
  :ret (s/nilable ::anom/anomaly))

(s/fdef engine/write-phase!
  :args (s/cat :file path? :file-size pos-int? :progress! ifn? :cancelled? ifn?))

(s/fdef engine/probe-direct-io
  :args (s/cat :file path?)
  :ret boolean?)

(s/fdef engine/concurrency-levels
  :args (s/cat :max-concurrency pos-int?)
  :ret ::concurrencies)

(s/fdef engine/read-phase!
  :args (s/cat :file path?
               :params (s/keys :req-un [::block-size ::concurrency
                                        ::duration-millis ::direct?])
               :progress! ifn?
               :cancelled? ifn?))

(s/fdef engine/read-sweep!
  :args (s/cat :file path?
               :params (s/keys :req-un [::block-size ::concurrencies
                                        ::duration-millis ::direct?])
               :progress! ifn?
               :cancelled? ifn?))

(s/fdef engine/fsync-phase!
  :args (s/cat :file path?
               :params (s/keys :req-un [::duration-millis])
               :progress! ifn?
               :cancelled? ifn?))

(s/fdef engine/run!
  :args (s/cat :dir string?
               :params (s/keys :req-un [::file-size ::block-size ::concurrencies
                                        ::phase-duration-millis])
               :progress! ifn?
               :cancelled? ifn?))
