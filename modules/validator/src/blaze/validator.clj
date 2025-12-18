(ns blaze.validator
  "FHIR Resource profile validation middleware."

  (:require
   [blaze.async.flow :as flow]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.db.spec]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.writing-context.spec]
   [blaze.module :as m]
   [clojure.core.protocols :as p]
   [clojure.datafy :as datafy]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [taoensso.timbre :as log])

  (:import
   [ca.uhn.fhir.context FhirContext]
   [ca.uhn.fhir.context.support DefaultProfileValidationSupport]
   [ca.uhn.fhir.validation FhirValidator]
   [java.io ByteArrayInputStream]
   [java.util.concurrent Flow$Subscriber]
   [org.hl7.fhir.common.hapi.validation.support
    BaseValidationSupport
    CommonCodeSystemsTerminologyService
    InMemoryTerminologyServerValidationSupport
    PrePopulatedValidationSupport
    ValidationSupportChain]
   [org.hl7.fhir.common.hapi.validation.validator FhirInstanceValidator]
   [org.hl7.fhir.instance.model.api IBaseResource]
   [org.hl7.fhir.r4.model
    CodeableConcept
    OperationOutcome
    OperationOutcome$OperationOutcomeIssueComponent
    StringType]))

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
      (assoc :diagnostics (.getDiagnostics issue))
      (.hasExpression issue)
      (assoc :expression (mapv datafy/datafy (.getExpression issue)))))

  CodeableConcept
  (datafy [concept]
    (cond-> {:fhir/type :fhir.CodeableConcept}
      (.hasText concept)
      (assoc :text (.getText concept))))

  StringType
  (datafy [string]
    (.toString string)))

(defn- error-issues [outcome]
  (update outcome :issue (partial filterv (comp #{#fhir/code"error"} :severity))))

(defn- drop-empty-operation-outcome [operation-outcome]
  (if (empty? (:issue operation-outcome))
    nil
    operation-outcome))

(defn- transform-resource
  "Transforms `resource` from the internal FHIR representation to a HAPI resource."
  [context writing-context resource]
  (let [parser (.newJsonParser ^FhirContext context)
        source (fhir-spec/write-json-as-bytes writing-context resource)]
    (.parseResource parser (ByteArrayInputStream. source))))

(defn validate [{:keys [validator fhir-context writing-context]} resource]
  (->> ^IBaseResource (transform-resource fhir-context writing-context resource)
       (.validateWithResult ^FhirValidator validator)
       (.toOperationOutcome)
       (datafy/datafy)
       (error-issues)
       (drop-empty-operation-outcome)))

(defn- db-profile-validation-support [context writing-context node]
  (proxy [BaseValidationSupport] [context]
    (fetchAllStructureDefinitions []
      (map #(transform-resource context writing-context %)
           @(d/pull-many node (d/type-list (d/db node) "StructureDefinition"))))
    (fetchStructureDefinition [url]
      (when-let [handle (coll/first (d/type-query (d/db node) "StructureDefinition" [["url" url]]))]
        (transform-resource context writing-context @(d/pull node handle))))))

(defn- load-profile [context name]
  (log/debug "Load profile" name)
  (let [parser (.newJsonParser ^FhirContext context)
        classloader (.getContextClassLoader (Thread/currentThread))]
    (with-open [source (.getResourceAsStream classloader name)]
      (.parseResource parser source))))

(defn- admin-profile-validation-support [context]
  (let [s (PrePopulatedValidationSupport. context)]
    (run!
     #(.addResource s (load-profile context %))
     ["blaze/db/CodeSystem-ColumnFamily.json"
      "blaze/db/CodeSystem-Database.json"
      "blaze/db/ValueSet-ColumnFamily.json"
      "blaze/db/ValueSet-Database.json"
      "blaze/job_scheduler/StructureDefinition-Job.json"
      "blaze/job_scheduler/CodeSystem-JobType.json"
      "blaze/job_scheduler/CodeSystem-JobOutput.json"
      "blaze/job/async_interaction/StructureDefinition-AsyncInteractionJob.json"
      "blaze/job/async_interaction/StructureDefinition-AsyncInteractionRequestBundle.json"
      "blaze/job/async_interaction/StructureDefinition-AsyncInteractionResponseBundle.json"
      "blaze/job/async_interaction/CodeSystem-AsyncInteractionJobOutput.json"
      "blaze/job/async_interaction/CodeSystem-AsyncInteractionJobParameter.json"
      "blaze/job/compact/CodeSystem-CompactJobOutput.json"
      "blaze/job/compact/CodeSystem-CompactJobParameter.json"
      "blaze/job/compact/StructureDefinition-CompactJob.json"
      "blaze/job/re_index/StructureDefinition-ReIndexJob.json"
      "blaze/job/re_index/CodeSystem-ReIndexJobOutput.json"
      "blaze/job/re_index/CodeSystem-ReIndexJobParameter.json"])
    s))

(deftype StructureDefinitionSubscriber [validation-support-chain ^:volatile-mutable subscription]
  Flow$Subscriber
  (onSubscribe [_ s]
    (set! subscription s)
    (flow/request! subscription 1))
  (onNext [_ structure-definition-handles]
    (log/trace "Got" (count structure-definition-handles) "changed StructureDefinition(s)")
    (.invalidateCaches ^ValidationSupportChain validation-support-chain)
    (flow/request! subscription 1))
  (onError [_ e]
    (log/fatal "Validator cache invalidation failed. Please restart Blaze. Cause:" (ex-message e))
    (flow/cancel! subscription))
  (onComplete [_]))

(defn- create-validator [node writing-context]
  (let [^FhirContext fhir-context (FhirContext/forR4)
        _ (.newJsonParser fhir-context)
        validator (.newValidator fhir-context)
        chain (doto (ValidationSupportChain.)
                (.addValidationSupport (DefaultProfileValidationSupport. fhir-context))
                (.addValidationSupport (InMemoryTerminologyServerValidationSupport. fhir-context))
                (.addValidationSupport (CommonCodeSystemsTerminologyService. fhir-context))
                (.addValidationSupport (db-profile-validation-support fhir-context writing-context node))
                (.addValidationSupport (admin-profile-validation-support fhir-context)))
        instanceValidator (FhirInstanceValidator. chain)]
    (.registerValidatorModule validator instanceValidator)

    {:validator validator
     :fhir-context fhir-context
     :writing-context writing-context
     :validation-support-chain chain}))

(defmethod m/pre-init-spec :blaze/validator [_]
  (s/keys :req-un [:blaze.db/node :blaze.fhir/writing-context]))

(defmethod ig/init-key :blaze/validator
  [_ {:keys [node writing-context]}]
  (log/info "Init Validator")
  (let [{:keys [validation-support-chain] :as validator} (create-validator node writing-context)
        publisher (d/changed-resources-publisher node "StructureDefinition")
        subscriber (->StructureDefinitionSubscriber validation-support-chain nil)]
    (flow/subscribe! publisher subscriber)
    validator))
