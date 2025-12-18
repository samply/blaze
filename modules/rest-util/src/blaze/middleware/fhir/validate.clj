(ns blaze.middleware.fhir.validate
  "FHIR Resource profile validation middleware."

  (:require
   [blaze.async.comp :as ac]
   [blaze.validator :as validator]
   [ring.util.response :as ring]))

(defn wrap-validate [handler validator]
  (fn [{:keys [body] :as request}]
    (if (and (:fhir/type body) (not (#{:fhir/StructureDefinition :fhir/CodeSystem :fhir/ValueSet} (:fhir/type body))))
      (if-let [outcome (validator/validate validator body)]
        (ac/completed-future (ring/bad-request outcome))
        (handler request))
      (handler request))))
