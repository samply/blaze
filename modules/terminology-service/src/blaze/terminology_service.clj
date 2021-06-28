(ns blaze.terminology-service)


(defprotocol TerminologyService
  (-expand-value-set [_ params]))


(defn terminology-service? [x]
  (satisfies? TerminologyService x))


(defn expand-value-set
  "Possible params are:
   * url
   * valueSetVersion
   * filter

  See also: https://www.hl7.org/fhir/valueset-operation-expand.html"
  [terminology-service params]
  (-expand-value-set terminology-service params))
