(ns blaze.job.async-interaction.util-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.tx-log.spec]
   [blaze.fhir.spec.spec]
   [blaze.job.async-interaction.util :as u]
   [clojure.spec.alpha :as s]))

(s/fdef u/request-bundle-input
  :args (s/cat :reference string?)
  :ret :fhir.Task/input)

(s/fdef u/processing-duration
  :args (s/cat :start int?)
  :ret :fhir/Quantity)

(s/fdef u/pull-request-bundle
  :args (s/cat :node :blaze.db/node :job :fhir/Task)
  :ret ac/completable-future?)
