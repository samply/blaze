(ns blaze.handler.util-spec
  (:require
   [blaze.anomaly-spec]
   [blaze.fhir.spec]
   [blaze.handler.util :as handler-util]
   [blaze.spec]
   [clojure.spec.alpha :as s]))

(s/fdef handler-util/preference
  :args (s/cat :headers (s/nilable map?) :name string?)
  :ret (s/nilable keyword?))

(s/fdef handler-util/error-response
  :args (s/cat :error some?)
  :ret map?)

(s/fdef handler-util/instant
  :args (s/cat :tx :blaze.db/tx)
  :ret :fhir/instant)

(s/fdef handler-util/async-status-location
  :args (s/cat :response map?
               :context (s/keys :opt-un [:blaze/context-path])
               :request (s/keys :req [:blaze/base-url])
               :job :fhir/Task)
  :ret map?)
