(ns blaze.job-scheduler.spec
  (:require
   [blaze.db.spec]
   [blaze.job-scheduler :as-alias js]
   [blaze.job-scheduler.protocols :as p]
   [blaze.spec]
   [clojure.spec.alpha :as s])
  (:import
   [clojure.lang IAtom]))

(s/def :blaze.job/handler
  #(satisfies? p/JobHandler %))

(s/def ::js/handlers
  (s/map-of keyword? :blaze.job/handler))

(s/def ::js/context
  (s/keys :req-un [:blaze.db/node :blaze/clock :blaze/rng-fn]))

(s/def ::js/running-jobs
  #(instance? IAtom %))

(s/def :blaze/job-scheduler
  (s/keys :req-un [::js/context ::js/running-jobs]))
