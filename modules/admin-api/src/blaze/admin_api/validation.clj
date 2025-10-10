(ns blaze.admin-api.validation
  (:require
   [blaze.fhir.spec.type :as type]
   [clojure.core.protocols :as p]
   [clojure.datafy :as datafy])
  (:import
   [org.hl7.fhir.r4.model
    CodeableConcept OperationOutcome
    OperationOutcome$OperationOutcomeIssueComponent]))

(set! *warn-on-reflection* true)

(extend-protocol p/Datafiable
  OperationOutcome
  (datafy [outcome]
    {:fhir/type :fhir/OperationOutcome
     :issue (mapv datafy/datafy (.getIssue outcome))})

  OperationOutcome$OperationOutcomeIssueComponent
  (datafy [issue]
    (cond-> {:fhir/type :fhir.OperationOutcome/issue}
      (.hasSeverity issue)
      (assoc :severity (type/code (.toCode (.getSeverity issue))))
      (.hasCode issue)
      (assoc :code (type/code (.toCode (.getCode issue))))
      (.hasDetails issue)
      (assoc :details (datafy/datafy (.getDetails issue)))
      (.hasDiagnostics issue)
      (assoc :diagnostics (type/string (.getDiagnostics issue)))))

  CodeableConcept
  (datafy [concept]
    (cond-> #fhir/CodeableConcept{}
      (.hasText concept)
      (assoc :text (type/string (.getText concept))))))
