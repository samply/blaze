(ns blaze.fhir-client.spec
  (:require
   [blaze.fhir.parsing-context.spec]
   [blaze.fhir.writing-context.spec]
   [blaze.http-client.spec]
   [clojure.spec.alpha :as s]))

(s/def :blaze.fhir-client/options
  (s/keys :req-un [:blaze.fhir/parsing-context
                   :blaze.fhir/writing-context]
          :opt-un [:blaze/http-client]))
