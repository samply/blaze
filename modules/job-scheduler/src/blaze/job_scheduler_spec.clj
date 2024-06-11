(ns blaze.job-scheduler-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.fhir.spec.spec]
   [blaze.job-scheduler :as js]
   [blaze.job-scheduler.spec]
   [blaze.job.util-spec]
   [clojure.spec.alpha :as s]))

(s/fdef js/create-job
  :args (s/cat :scheduler :blaze/job-scheduler :job :blaze/resource
               :other-resources (s/* :blaze/resource))
  :ret ac/completable-future?)

(s/fdef js/cancel-job
  :args (s/cat :scheduler :blaze/job-scheduler :id :blaze.resource/id)
  :ret ac/completable-future?)

(s/fdef js/pause-job
  :args (s/cat :scheduler :blaze/job-scheduler :id :blaze.resource/id)
  :ret ac/completable-future?)

(s/fdef js/resume-job
  :args (s/cat :scheduler :blaze/job-scheduler :id :blaze.resource/id)
  :ret ac/completable-future?)
