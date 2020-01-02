(ns blaze.handler.fhir.util-spec
  (:require
    [blaze.handler.fhir.util :as util]
    [clojure.spec.alpha :as s]
    [reitit.core :as reitit]))


(s/fdef util/versioned-instance-url
  :args (s/cat :router reitit/router? :type string? :id string? :vid string?)
  :ret string?)
