(ns blaze.job-scheduler.spec
  (:require
   [blaze.db.spec]
   [blaze.job-scheduler :as-alias js]
   [blaze.spec]
   [clojure.spec.alpha :as s])
  (:import
   [clojure.lang IAtom]))

(s/def ::js/main-node
  :blaze.db/node)

(s/def ::js/admin-node
  :blaze.db/node)

(s/def ::js/context
  (s/keys :req-un [::js/main-node ::js/admin-node :blaze/clock :blaze/rng-fn]))

(s/def ::js/running-jobs
  #(instance? IAtom %))

(s/def :blaze/job-scheduler
  (s/keys :req-un [::js/context ::js/running-jobs]))
