(ns blaze.job.async-interaction.request-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.job.async-interaction.request :as req]
   [blaze.job.async-interaction.request.spec]
   [clojure.spec.alpha :as s]))

(s/fdef req/handle-async
  :args (s/cat :context ::req/context :request map?)
  :ret ac/completable-future?)
