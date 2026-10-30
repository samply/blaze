(ns blaze.handler.async-job-spec
  (:require
   [blaze.fhir.util.spec]
   [blaze.handler.async-job :as async-job]
   [blaze.spec]
   [clojure.spec.alpha :as s]))

(s/fdef async-job/handler
  :args (s/cat :context (s/keys :req-un [:blaze/clock] :opt-un [:blaze/context-path])
               :param-specs :blaze.fhir.util/param-specs
               :job-fn fn?)
  :ret fn?)
