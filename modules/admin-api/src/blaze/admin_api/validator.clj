(ns blaze.admin-api.validator
  "Validation of job Task resources against the job profiles, based on the
  org.hl7.fhir core validator (the engine behind validator_cli.jar).

  The validator works on an R5 worker context. The base R4 type definitions
  are taken from the classpath resources provided by the fhir-structure
  module, the base R4 Task StructureDefinition is extracted from them at prep
  time, and both are converted to R5 on load, exactly like the core
  ValidationEngine does. The job profiles are loaded from the classpath
  resources provided by the job modules."
  (:require
   [blaze.fhir.spec.type :as type]
   [clojure.java.io :as io]
   [integrant.core :as ig]
   [taoensso.timbre :as log])
  (:import
   [java.io ByteArrayInputStream]
   [java.nio.charset StandardCharsets]
   [java.util ArrayList Date HashMap List Locale]
   [org.fhir.ucum UcumEssenceService]
   [org.hl7.fhir.convertors.loaders.loaderR5
    NullLoaderKnowledgeProviderR5 R4ToR5Loader]
   [org.hl7.fhir.r5.context
    SimpleWorkerContext SimpleWorkerContext$SimpleWorkerContextBuilder]
   [org.hl7.fhir.r5.elementmodel Manager$FhirFormat]
   [org.hl7.fhir.r5.model
    Bundle$BundleEntryComponent PackageInformation Parameters]
   [org.hl7.fhir.r5.utils.xver XVerExtensionManagerFactory]
   [org.hl7.fhir.utilities ByteProvider]
   [org.hl7.fhir.utilities.validation ValidationMessage]
   [org.hl7.fhir.validation ValidatorSettings]
   [org.hl7.fhir.validation.instance InstanceValidator]))

(set! *warn-on-reflection* true)

(def ^:private loader-types
  #{"StructureDefinition" "ValueSet" "CodeSystem" "NamingSystem"})

(defn- r4-loader ^R4ToR5Loader []
  (R4ToR5Loader. loader-types (NullLoaderKnowledgeProviderR5.) "4.0.1"))

(def ^:private type-definitions
  "The base R4 primitive and complex type definitions."
  "blaze/fhir/4.0.1/profiles-types.json")

(def ^:private base-definitions
  "The base R4 Task StructureDefinition and the CodeSystems/ValueSets backing
  the required bindings of its elements, extracted from the base definition
  bundles by the fhir-structure module at prep time so that the multi-MB
  bundles don't have to be parsed at startup."
  ["blaze/fhir/4.0.1/StructureDefinition-Task.json"
   "blaze/fhir/4.0.1/CodeSystem-task-status.json"
   "blaze/fhir/4.0.1/ValueSet-task-status.json"
   "blaze/fhir/4.0.1/CodeSystem-task-intent.json"
   "blaze/fhir/4.0.1/ValueSet-task-intent.json"
   "blaze/fhir/4.0.1/CodeSystem-request-intent.json"
   "blaze/fhir/4.0.1/ValueSet-request-intent.json"
   "blaze/fhir/4.0.1/CodeSystem-request-priority.json"
   "blaze/fhir/4.0.1/ValueSet-request-priority.json"
   "blaze/fhir/4.0.1/CodeSystem-identifier-use.json"
   "blaze/fhir/4.0.1/ValueSet-identifier-use.json"])

(def ^:private profiles
  "The current (IG 1.10.0) profiles, copied from the IG into each module,
  plus the frozen legacy (IG 0.1.0) profiles bundled with this module —
  so Tasks submitted with either the current or the legacy canonical
  validate against a matching profile edition. The 0.1.0 editions are
  frozen rather than derived from the current ones, so they keep matching
  legacy submissions once the IG diverges from 0.1.0."
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
   "blaze/job/re_index/CodeSystem-ReIndexJobParameter.json"
   "blaze/admin-api/v0_1_0/CodeSystem-ColumnFamily.json"
   "blaze/admin-api/v0_1_0/CodeSystem-Database.json"
   "blaze/admin-api/v0_1_0/ValueSet-ColumnFamily.json"
   "blaze/admin-api/v0_1_0/ValueSet-Database.json"
   "blaze/admin-api/v0_1_0/StructureDefinition-Job.json"
   "blaze/admin-api/v0_1_0/CodeSystem-JobType.json"
   "blaze/admin-api/v0_1_0/CodeSystem-JobOutput.json"
   "blaze/admin-api/v0_1_0/StructureDefinition-AsyncInteractionJob.json"
   "blaze/admin-api/v0_1_0/StructureDefinition-AsyncInteractionRequestBundle.json"
   "blaze/admin-api/v0_1_0/StructureDefinition-AsyncInteractionResponseBundle.json"
   "blaze/admin-api/v0_1_0/CodeSystem-AsyncInteractionJobOutput.json"
   "blaze/admin-api/v0_1_0/CodeSystem-AsyncInteractionJobParameter.json"
   "blaze/admin-api/v0_1_0/CodeSystem-CompactJobOutput.json"
   "blaze/admin-api/v0_1_0/CodeSystem-CompactJobParameter.json"
   "blaze/admin-api/v0_1_0/StructureDefinition-CompactJob.json"
   "blaze/admin-api/v0_1_0/StructureDefinition-ReIndexJob.json"
   "blaze/admin-api/v0_1_0/CodeSystem-ReIndexJobOutput.json"
   "blaze/admin-api/v0_1_0/CodeSystem-ReIndexJobParameter.json"])

(defn- resource-bytes [name]
  (with-open [in (io/input-stream (io/resource name))]
    (.readAllBytes in)))

(defn- version-info
  "A definitions map containing only the version.info file, used to establish
  the FHIR version (4.0.1) of the worker context, which has no other public
  way to set it."
  []
  (doto (HashMap.)
    (.put "version.info" (ByteProvider/forBytes (resource-bytes "blaze/fhir/4.0.1/version.info")))))

(defn- create-context ^SimpleWorkerContext []
  (let [loader (r4-loader)
        context (-> (SimpleWorkerContext$SimpleWorkerContextBuilder.)
                    (.withLocale Locale/ENGLISH)
                    (.fromDefinitions (version-info) loader (PackageInformation. "blaze" "4.0.1" (Date.))))]
    (with-open [in (io/input-stream (io/resource "ucum-essence.xml"))]
      (.setUcumService context (UcumEssenceService. in)))
    (.setCanRunWithoutTerminology context true)
    (.setNoTerminologyServer context true)
    (.setExpansionParameters context (Parameters.))
    (log/debug "Load definitions" type-definitions)
    (with-open [in (io/input-stream (io/resource type-definitions))]
      (run!
       #(.cacheResource context (.getResource ^Bundle$BundleEntryComponent %))
       (.getEntry (.loadBundle loader in true))))
    (run!
     (fn [name]
       (log/debug "Load profile" name)
       (with-open [in (io/input-stream (io/resource name))]
         (.cacheResource context (.loadResource loader in true))))
     (concat base-definitions profiles))
    context))

(defn- create-validator ^InstanceValidator []
  (let [context (create-context)]
    (doto (InstanceValidator. context nil
                              (XVerExtensionManagerFactory/createExtensionManager context)
                              nil (ValidatorSettings.))
      (.setAssumeValidRestReferences false))))

(defn- issue [^ValidationMessage message]
  (cond-> {:fhir/type :fhir.OperationOutcome/issue
           :severity (type/code (.toCode (.getLevel message)))
           :code (type/code (.toCode (.getType message)))
           :diagnostics (type/string (.getMessage message))}
    (.getLocation message)
    (assoc :expression [(type/string (.getLocation message))])))

(defn validate
  "Validates `source`, the JSON representation of a resource, against the
  profiles claimed in its meta.profile, returning an OperationOutcome."
  [^InstanceValidator validator ^String source]
  (let [messages (ArrayList.)]
    (.validate validator nil ^List messages
               (ByteArrayInputStream. (.getBytes source StandardCharsets/UTF_8))
               Manager$FhirFormat/JSON)
    {:fhir/type :fhir/OperationOutcome
     :issue (mapv issue messages)}))

(defmethod ig/init-key :blaze.admin-api/validator
  [_ _]
  (log/info "Init admin-api validator")
  (create-validator))
