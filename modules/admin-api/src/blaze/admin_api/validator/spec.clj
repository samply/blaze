(ns blaze.admin-api.validator.spec
  (:require
   [clojure.spec.alpha :as s])
  (:import
   [org.hl7.fhir.validation.instance InstanceValidator]))

(s/def :blaze.admin-api/validator
  #(instance? InstanceValidator %))
