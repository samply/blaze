(ns blaze.job.disk-perf.histogram-spec
  (:require
   [blaze.job.disk-perf.histogram :as histogram]
   [clojure.spec.alpha :as s]))

(defn histogram? [x]
  (instance? (Class/forName "[J") x))

(s/fdef histogram/create
  :args (s/cat)
  :ret histogram?)

(s/fdef histogram/record!
  :args (s/cat :histogram histogram? :nanos int?))

(s/fdef histogram/merge-into!
  :args (s/cat :target histogram? :source histogram?))

(s/fdef histogram/total
  :args (s/cat :histogram histogram?)
  :ret nat-int?)

(s/fdef histogram/quantile
  :args (s/cat :histogram histogram? :q (s/double-in :min 0.0 :max 1.0))
  :ret nat-int?)

(s/fdef histogram/maximum
  :args (s/cat :histogram histogram?)
  :ret nat-int?)
