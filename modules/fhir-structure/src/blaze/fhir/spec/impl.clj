(ns blaze.fhir.spec.impl
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [throw-anom]]
   [blaze.fhir.spec.impl.specs :as specs]
   [blaze.fhir.spec.impl.xml :as xml]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.spec.type.string-util :as su]
   [blaze.fhir.spec.type.system :as system]
   [blaze.util :refer [str]]
   [clojure.alpha.spec :as s]
   [clojure.data.xml.name :as xml-name]
   [clojure.data.xml.node :as xml-node]
   [clojure.string :as str])
  (:import
   [java.net URLEncoder]
   [java.nio.charset StandardCharsets]))

(xml-name/alias-uri 'f "http://hl7.org/fhir")

(set! *warn-on-reflection* true)

(def ^:const fhir-namespace
  (str "xmlns." (URLEncoder/encode "http://hl7.org/fhir" StandardCharsets/UTF_8)))

(defn- find-fhir-type [{:keys [extension]}]
  (some
   #(when (= "http://hl7.org/fhir/StructureDefinition/structuredefinition-fhir-type" (:url %))
      (:valueUrl %))
   extension))

(defn- find-regex [{:keys [extension]}]
  (some
   #(when (= "http://hl7.org/fhir/StructureDefinition/regex" (:url %))
      (re-pattern (:valueString %)))
   extension))

(defn- type-regex [type]
  (or (when (= "base64Binary" (find-fhir-type type))
        #"([0-9a-zA-Z\\+/=]{4})+")
      (find-regex type)
      (when (= "id" (find-fhir-type type))
        #"[A-Za-z0-9\-\.]{1,64}")
      (when (= "url" (find-fhir-type type))
        #"[A-Za-z0-9\-\.]{1,64}")))

(def id-matcher-form
  `(fn [~'s] (.matches (re-matcher #"[A-Za-z0-9\-\.]{1,64}" ~'s))))

(def conform-xml-value
  "Takes the value out of an XML element."
  (comp :value :attrs))

(defn unform-xml-value
  "Creates an XML element with `value` as attribute."
  [value]
  (xml-node/element nil {:value value}))

(defn id-string-spec [modifier]
  (case modifier
    nil `(s/and string? ~id-matcher-form)
    :xml `(s/and xml/element? (s/conformer conform-xml-value unform-xml-value) ~id-matcher-form)))

(defn- string-spec [modifier type]
  (case (find-fhir-type type)
    "id" (id-string-spec modifier)
    (if-let [regex (type-regex type)]
      `(s/and string? (fn [~'s] (.matches (re-matcher ~regex ~'s))))
      `string?)))

(defn- system-type->spec-form [modifier {:keys [code] :as type}]
  (case code
    "http://hl7.org/fhirpath/System.String" (string-spec modifier type)
    "http://hl7.org/fhirpath/System.Time" `string?
    "http://hl7.org/fhirpath/System.Date" (string-spec modifier type)
    "http://hl7.org/fhirpath/System.DateTime" `string?
    "http://hl7.org/fhirpath/System.Integer" `int?
    "http://hl7.org/fhirpath/System.Decimal" `decimal?
    "http://hl7.org/fhirpath/System.Boolean" `boolean?
    (throw-anom (ba/unsupported (format "Unsupported system type `%s`." code)))))

(defn- split-path [path]
  (str/split path #"\."))

(defn- key-name [last-path-part {:keys [code]}]
  (if (str/ends-with? last-path-part "[x]")
    (str/replace last-path-part "[x]" (su/capital code))
    last-path-part))

(defn- path-parts->key [path-parts type]
  (keyword
   (str/join "." (cons "fhir" (butlast path-parts)))
   (key-name (last path-parts) type)))

(defn- path->key [path type]
  (path-parts->key (split-path path) type))

(defn- path-parts->key' [prefix path-parts]
  (keyword
   (str/join "." (cons prefix (butlast path-parts)))
   (last path-parts)))

(defn- choice-pair [path type]
  [(keyword (name (path->key path type)))
   (keyword "fhir" (:code type))])

(defn- choice-spec-form [path types]
  `(s/or ~@(mapcat #(choice-pair path %) types)))

(defn- choice-spec-def* [modifier path code min max]
  {:key (path-parts->key' (str "fhir." (name modifier)) (split-path (str/replace path "[x]" (su/capital code))))
   :modifier modifier
   :min min
   :max max
   :spec-form (keyword (str "fhir." (name modifier)) code)})

(defn- choice-spec-def [modifier path path-parts code min max]
  (cond-> (choice-spec-def* modifier path code min max)
    (identical? :xml modifier)
    (assoc :choice-group (keyword (last path-parts)))))

(defn- system-spec-defs [{:keys [path min max representation] [type] :type}]
  (let [rep (some-> representation first keyword)]
    [{:key (path-parts->key' "fhir" (split-path path))
      :min min
      :max max
      :spec-form (system-type->spec-form nil type)}
     (cond->
      {:key (path-parts->key' "fhir.xml" (split-path path))
       :modifier :xml
       :min min
       :max max
       :spec-form (system-type->spec-form (if rep :xmlAttr :xml) type)}
       rep
       (assoc :representation rep))]))

(defn- primitive-spec-defs [{:keys [path min max] [type] :type}]
  [{:key (path-parts->key' "fhir" (split-path path))
    :min min
    :max max
    :spec-form (keyword "fhir" (:code type))}
   {:key (path-parts->key' "fhir.xml" (split-path path))
    :modifier :xml
    :min min
    :max max
    :spec-form
    (case path
      ("Address.city"
       "Address.district"
       "Address.state"
       "Address.postalCode"
       "Address.country"
       "Age.unit"
       "Bundle.link.relation"
       "Bundle.response.status"
       "CodeableConcept.text"
       "Coding.version"
       "Coding.display"
       "Count.unit"
       "Distance.unit"
       "Duration.unit"
       "HumanName.family"
       "HumanName.prefix"
       "HumanName.suffix"
       "Quantity.unit")
      (xml/primitive-xml-form `type/string-interned `identity)
      ("Resource.implicitRules"
       "Account.implicitRules"
       "ActivityDefinition.implicitRules"
       "ActivityDefinition.url"
       "AdverseEvent.implicitRules"
       "Age.system"
       "AllergyIntolerance.implicitRules"
       "Appointment.implicitRules"
       "AppointmentResponse.implicitRules"
       "AuditEvent.implicitRules"
       "AuditEvent.agent.policy"
       "Basic.implicitRules"
       "Binary.implicitRules"
       "BiologicallyDerivedProduct.implicitRules"
       "BodyStructure.implicitRules"
       "Bundle.implicitRules"
       "CapabilityStatement.implicitRules"
       "CapabilityStatement.url"
       "CarePlan.implicitRules"
       "CarePlan.instantiatesUri"
       "CarePlan.activity.detail.instantiatesUri"
       "CareTeam.implicitRules"
       "CatalogEntry.implicitRules"
       "ChargeItem.implicitRules"
       "ChargeItem.definitionUri"
       "ChargeItemDefinition.implicitRules"
       "ChargeItemDefinition.url"
       "ChargeItemDefinition.derivedFromUri"
       "Claim.implicitRules"
       "ClaimResponse.implicitRules"
       "ClinicalImpression.implicitRules"
       "ClinicalImpression.protocol"
       "CodeSystem.implicitRules"
       "CodeSystem.url"
       "CodeSystem.property.uri"
       "Communication.implicitRules"
       "Communication.instantiatesUri"
       "CommunicationRequest.implicitRules"
       "CompartmentDefinition.implicitRules"
       "CompartmentDefinition.url"
       "Composition.implicitRules"
       "ConceptMap.implicitRules"
       "ConceptMap.url"
       "Condition.implicitRules"
       "Consent.implicitRules"
       "Consent.policy.authority"
       "Consent.policy.uri"
       "Contract.implicitRules"
       "Contract.url"
       "Contract.instantiatesUri"
       "Count.system"
       "Coverage.implicitRules"
       "CoverageEligibilityRequest.implicitRules"
       "CoverageEligibilityResponse.implicitRules"
       "CoverageEligibilityResponse.insurance.item.authorizationUrl"
       "DetectedIssue.implicitRules"
       "Device.implicitRules"
       "Device.udiCarrier.issuer"
       "Device.udiCarrier.jurisdiction"
       "Device.url"
       "DeviceDefinition.implicitRules"
       "DeviceDefinition.udiDeviceIdentifier.issuer"
       "DeviceDefinition.udiDeviceIdentifier.jurisdiction"
       "DeviceDefinition.url"
       "DeviceDefinition.onlineInformation"
       "DeviceMetric.implicitRules"
       "DeviceRequest.implicitRules"
       "DeviceRequest.instantiatesUri"
       "DeviceUseStatement.implicitRules"
       "DiagnosticReport.implicitRules"
       "Distance.system"
       "DocumentManifest.implicitRules"
       "DocumentManifest.source"
       "DocumentReference.implicitRules"
       "DomainResource.implicitRules"
       "Duration.system"
       "EffectEvidenceSynthesis.implicitRules"
       "EffectEvidenceSynthesis.url"
       "Encounter.implicitRules"
       "Endpoint.implicitRules"
       "EnrollmentRequest.implicitRules"
       "EnrollmentResponse.implicitRules"
       "EpisodeOfCare.implicitRules"
       "EventDefinition.implicitRules"
       "EventDefinition.url"
       "Evidence.implicitRules"
       "Evidence.url"
       "EvidenceVariable.implicitRules"
       "EvidenceVariable.url"
       "ExampleScenario.implicitRules"
       "ExampleScenario.url"
       "ExplanationOfBenefit.implicitRules"
       "FamilyMemberHistory.implicitRules"
       "FamilyMemberHistory.instantiatesUri"
       "Flag.implicitRules"
       "Goal.implicitRules"
       "GraphDefinition.implicitRules"
       "GraphDefinition.url"
       "Group.implicitRules"
       "GuidanceResponse.implicitRules"
       "HealthcareService.implicitRules"
       "ImagingStudy.implicitRules"
       "Immunization.implicitRules"
       "Immunization.education.reference"
       "ImmunizationEvaluation.implicitRules"
       "ImmunizationRecommendation.implicitRules"
       "ImplementationGuide.implicitRules"
       "ImplementationGuide.url"
       "InsurancePlan.implicitRules"
       "Invoice.implicitRules"
       "Library.implicitRules"
       "Library.url"
       "Linkage.implicitRules"
       "List.implicitRules"
       "Location.implicitRules"
       "Measure.implicitRules"
       "Measure.url"
       "MeasureReport.implicitRules"
       "Media.implicitRules"
       "Medication.implicitRules"
       "MedicationAdministration.implicitRules"
       "MedicationAdministration.instantiates"
       "MedicationDispense.implicitRules"
       "MedicationKnowledge.implicitRules"
       "MedicationRequest.implicitRules"
       "MedicationRequest.instantiatesUri"
       "MedicationStatement.implicitRules"
       "MedicinalProduct.implicitRules"
       "MedicinalProductAuthorization.implicitRules"
       "MedicinalProductContraindication.implicitRules"
       "MedicinalProductIndication.implicitRules"
       "MedicinalProductIngredient.implicitRules"
       "MedicinalProductInteraction.implicitRules"
       "MedicinalProductManufactured.implicitRules"
       "MedicinalProductPackaged.implicitRules"
       "MedicinalProductPharmaceutical.implicitRules"
       "MedicinalProductUndesirableEffect.implicitRules"
       "MessageDefinition.implicitRules"
       "MessageDefinition.url"
       "MessageHeader.implicitRules"
       "MolecularSequence.implicitRules"
       "MolecularSequence.repository.url"
       "NamingSystem.implicitRules"
       "NutritionOrder.implicitRules"
       "NutritionOrder.instantiatesUri"
       "NutritionOrder.instantiates"
       "Observation.implicitRules"
       "ObservationDefinition.implicitRules"
       "OperationDefinition.implicitRules"
       "OperationDefinition.url"
       "OperationOutcome.implicitRules"
       "Organization.implicitRules"
       "OrganizationAffiliation.implicitRules"
       "Parameters.implicitRules"
       "Patient.implicitRules"
       "PaymentNotice.implicitRules"
       "PaymentReconciliation.implicitRules"
       "Person.implicitRules"
       "PlanDefinition.implicitRules"
       "PlanDefinition.url"
       "Practitioner.implicitRules"
       "PractitionerRole.implicitRules"
       "Procedure.implicitRules"
       "Procedure.instantiatesUri"
       "Provenance.implicitRules"
       "Provenance.policy"
       "Questionnaire.implicitRules"
       "Questionnaire.url"
       "Questionnaire.item.definition"
       "QuestionnaireResponse.implicitRules"
       "QuestionnaireResponse.item.definition"
       "RelatedPerson.implicitRules"
       "RequestGroup.implicitRules"
       "RequestGroup.instantiatesUri"
       "ResearchDefinition.implicitRules"
       "ResearchDefinition.url"
       "ResearchElementDefinition.implicitRules"
       "ResearchElementDefinition.url"
       "ResearchStudy.implicitRules"
       "ResearchSubject.implicitRules"
       "RiskAssessment.implicitRules"
       "RiskEvidenceSynthesis.implicitRules"
       "RiskEvidenceSynthesis.url"
       "Schedule.implicitRules"
       "SearchParameter.implicitRules"
       "SearchParameter.url"
       "ServiceRequest.implicitRules"
       "ServiceRequest.instantiatesUri"
       "Slot.implicitRules"
       "Specimen.implicitRules"
       "SpecimenDefinition.implicitRules"
       "StructureDefinition.implicitRules"
       "StructureDefinition.url"
       "StructureDefinition.mapping.uri"
       "StructureDefinition.type"
       "StructureMap.implicitRules"
       "StructureMap.url"
       "Subscription.implicitRules"
       "Substance.implicitRules"
       "SubstanceNucleicAcid.implicitRules"
       "SubstancePolymer.implicitRules"
       "SubstanceProtein.implicitRules"
       "SubstanceReferenceInformation.implicitRules"
       "SubstanceSourceMaterial.implicitRules"
       "SubstanceSpecification.implicitRules"
       "SupplyDelivery.implicitRules"
       "SupplyRequest.implicitRules"
       "Task.implicitRules"
       "Task.instantiatesUri"
       "TerminologyCapabilities.implicitRules"
       "TerminologyCapabilities.url"
       "TestReport.implicitRules"
       "TestReport.participant.uri"
       "TestReport.setup.action.operation.detail"
       "TestScript.implicitRules"
       "TestScript.url"
       "TestScript.metadata.link.url"
       "TestScript.metadata.capability.link"
       "ValueSet.implicitRules"
       "ValueSet.url"
       "ValueSet.compose.include.system"
       "ValueSet.expansion.identifier"
       "ValueSet.expansion.contains.system"
       "VerificationResult.implicitRules"
       "VisionPrescription.implicitRules"
       "MetadataResource.implicitRules"
       "MetadataResource.url"
       "Coding.system"
       "Identifier.system"
       "Quantity.system"
       "Reference.type")
      (xml/primitive-xml-form #"(?U)[\p{Print}&&[^\p{Blank}]]*" `type/uri-interned `identity)
      (keyword "fhir.xml" (:code type)))}])

(defn elem-def->spec-def
  "Takes an element definition and returns a coll of spec definitions."
  [{:keys [path min max contentReference] [type & more :as types] :type :as elem-def}]
  (cond
    more
    (let [path-parts (split-path (str/replace path "[x]" ""))]
      (into
       [{:key (path-parts->key' "fhir" path-parts)
         :min min
         :max max
         :spec-form (choice-spec-form path types)}]
       (mapcat
        (fn [{:keys [code]}]
          [(choice-spec-def :xml path path-parts code min max)]))
       types))
    type
    (if (str/starts-with? (:code type) "http://hl7.org/fhirpath/System")
      (system-spec-defs elem-def)
      (primitive-spec-defs elem-def))
    :else
    [{:key (path-parts->key' "fhir" (split-path path))
      :min min
      :max max
      :spec-form (path-parts->key' "fhir" (split-path (subs contentReference 1)))}
     {:key (path-parts->key' "fhir.xml" (split-path path))
      :modifier :xml
      :min min
      :max max
      :spec-form (path-parts->key' "fhir.xml" (split-path (subs contentReference 1)))}]))

(defn- spec-key [prefix parent-path-parts path-part]
  (keyword (str/join "." (cons prefix parent-path-parts)) path-part))

(defn- fix-fhir-type-extension* [extensions]
  (mapv
   (fn [{:keys [url] :as extension}]
     (cond-> extension
       (= "http://hl7.org/fhir/StructureDefinition/structuredefinition-fhir-type" url)
       (assoc :valueUrl "id")))
   extensions))

(defn- fix-fhir-type-extension [types]
  (mapv #(update % :extension fix-fhir-type-extension*) types))

(defn- fix-fhir-25274
  "https://jira.hl7.org/browse/FHIR-25274"
  [{{base-path :path} :base :as elem-def}]
  (if (= "Resource.id" base-path)
    (update elem-def :type fix-fhir-type-extension)
    elem-def))

(defn ensure-coll [x]
  (cond (vector? x) x (some? x) [x]))

(defn- schema-spec-form [modifier child-spec-defs]
  `(s/schema
    ~(into
      {}
      (comp
       (filter :key)
       (filter #(= modifier (:modifier %)))
       (map
        (fn [{:keys [key max]}]
          [(keyword (name key))
           (if (= "1" max)
             key
             (if (= :xml modifier)
               `(s/and
                 (s/conformer ensure-coll identity)
                 (s/coll-of ~key))
               `(s/coll-of ~key)))])))
      child-spec-defs)))

(defn- record-spec-form [class-name child-spec-defs]
  `(specs/record
    ~(symbol (str "blaze.fhir.spec.type." class-name))
    ~(into
      {}
      (comp
       (filter :key)
       (filter #(nil? (:modifier %)))
       (map
        (fn [{:keys [key max]}]
          [(keyword (name key)) (if (= "1" max) key `(s/coll-of ~key))])))
      child-spec-defs)))

(defn- type-check-form [key]
  `(fn [~'m] (identical? ~key (:fhir/type ~'m))))

(defn- internal-schema-spec-def [parent-path-parts path-part elem-def child-spec-defs]
  (let [key (spec-key "fhir" parent-path-parts path-part)]
    {:key key
     :min (:min elem-def)
     :max (:max elem-def)
     :spec-form
     (case key
       (:fhir/Address
        :fhir/Age
        :fhir/Annotation
        :fhir/Attachment
        :fhir/CodeableConcept
        :fhir/Coding
        :fhir/ContactDetail
        :fhir/ContactPoint
        :fhir/Contributor
        :fhir/Count
        :fhir/DataRequirement
        :fhir/Distance
        :fhir/Dosage
        :fhir/Duration
        :fhir/Expression
        :fhir/Extension
        :fhir/HumanName
        :fhir/Identifier
        :fhir/Meta
        :fhir/Money
        :fhir/Narrative
        :fhir/ParameterDefinition
        :fhir/Period
        :fhir/Quantity
        :fhir/Range
        :fhir/Ratio
        :fhir/Reference
        :fhir/RelatedArtifact
        :fhir/SampledData
        :fhir/Signature
        :fhir/Timing
        :fhir/TriggerDefinition
        :fhir/UsageContext)
       (record-spec-form path-part child-spec-defs)
       :fhir.DataRequirement/codeFilter
       (record-spec-form "DataRequirement$CodeFilter" child-spec-defs)
       :fhir.DataRequirement/dateFilter
       (record-spec-form "DataRequirement$DateFilter" child-spec-defs)
       :fhir.DataRequirement/sort
       (record-spec-form "DataRequirement$Sort" child-spec-defs)
       :fhir.Dosage/doseAndRate
       (record-spec-form "Dosage$DoseAndRate" child-spec-defs)
       :fhir.Timing/repeat
       (record-spec-form "Timing$Repeat" child-spec-defs)
       :fhir.Bundle.entry/search
       (record-spec-form "BundleEntrySearch" child-spec-defs)
       `(s/and ~(type-check-form key) ~(schema-spec-form nil child-spec-defs)))}))

(defn remove-choice-type
  "Removes the type suffix from the first key of a choice typed data element.

  Also removes bare properties with key `key` if no typed keys were found."
  [m typed-keys key]
  (loop [[k & keys] typed-keys]
    (if k
      (if-some [v (get m k)]
        (-> (dissoc m k) (assoc key v))
        (recur keys))
      (dissoc m key))))

(defn- choice-type-key [key type]
  (keyword (str (name key) (su/capital (name type)))))

(defn add-choice-type
  "Add the type suffix to the key of a choice typed data element."
  [m key]
  (if-some [v (get m key)]
    (-> (dissoc m key) (assoc (choice-type-key key (:fhir/type v)) v))
    m))

(defn- remap-choice-conformer-form
  "Creates a conformer form which removes the type suffix of keys on conforming
  and adds it back on uniforming."
  [[internal-key child-spec-defs]]
  `(s/conformer
    (fn [~'m]
      (remove-choice-type ~'m ~(mapv (comp keyword name :key) child-spec-defs)
                          ~internal-key))
    (fn [~'m]
      (add-choice-type ~'m ~internal-key))))

(defn- remap-choice-conformer-forms [child-spec-defs]
  (into
   []
   (comp
    (remove (comp nil? first))
    (map remap-choice-conformer-form))
   (group-by :choice-group child-spec-defs)))

(defn- append-child [old element]
  (cond
    (vector? old)
    (conj old element)
    old
    [old element]
    :else
    element))

(defn conform-xml
  "First step in conforming an XML `element` into the internal form.

  Builds a map from child tags to either vector of children or single-valued
  children."
  {:arglists '([element])}
  [{:keys [attrs content]}]
  (transduce
   ;; remove mixed character content
   (filter xml/element?)
   (completing
    (fn [ret {:keys [tag] :as element}]
      (update ret (keyword (name tag)) append-child element)))
   (dissoc attrs :xmlns)
   content))

(defn select-non-nil-keys [m ks]
  (into {} (keep (fn [entry] (when (ks (key entry)) entry))) m))

(defn- xml-attrs-form [child-spec-defs]
  `(select-non-nil-keys
    ~'m
    ~(into
      #{}
      (comp
       (filter :key)
       (filter :representation)
       (filter #(= :xml (:modifier %)))
       (map
        (fn [{:keys [key]}]
          (keyword (name key)))))
      child-spec-defs)))

(defn conj-when [coll x]
  (cond-> coll (some? x) (conj x)))

(defn conj-all [to tag from]
  (transduce (map #(assoc % :tag tag)) conj to from))

(defn- xml-unformer
  [kind type child-spec-defs]
  `(fn [~'m]
     (when ~'m
       (xml-node/element*
        ~(when (= "resource" kind) (keyword fhir-namespace (name type)))
        ~(if (= "resource" kind)
           `(assoc ~(xml-attrs-form child-spec-defs) :xmlns "http://hl7.org/fhir")
           (xml-attrs-form child-spec-defs))
        ~(seq
          (into
           [`-> []]
           (comp
            (filter :key)
            (remove :representation)
            (filter #(= :xml (:modifier %)))
            (map
             (fn [{:keys [key max]}]
               (let [key (keyword (name key))
                     tag (keyword fhir-namespace (name key))]
                 (cond
                   (or (and (= :entry type) (= :resource key))
                       (and (= :Narrative type) (= :div key)))
                   `(conj-when (~key ~'m))
                   (= "1" max)
                   `(conj-when (some-> ~'m ~key (assoc :tag ~tag)))
                   :else
                   `(conj-all ~tag (~key ~'m)))))))
           child-spec-defs))))))

(defn- xml-schema-spec-form [kind key child-spec-defs]
  (conj (seq (remap-choice-conformer-forms child-spec-defs))
        `(s/conformer (fn [~'m] (assoc ~'m :fhir/type ~key)) identity)
        (schema-spec-form :xml child-spec-defs)
        `(s/conformer conform-xml
                      ~(xml-unformer kind (keyword (name key)) child-spec-defs))
        `s/and))

(defn- special-xml-schema-spec-form [kind type-name child-spec-defs]
  (let [constructor-sym (symbol "blaze.fhir.spec.type" (su/pascal->kebab type-name))]
    (if-let [constructor (resolve constructor-sym)]
      (conj (seq (conj (remap-choice-conformer-forms child-spec-defs)
                       `(s/conformer ~constructor #(into {} %))))
            (schema-spec-form :xml child-spec-defs)
            `(s/conformer conform-xml
                          ~(xml-unformer kind (keyword type-name) child-spec-defs))
            `s/and)
      (throw (Exception. (format "Can't resolve constructor `%s`." constructor-sym))))))

(defn- xml-schema-spec-def
  [kind parent-path-parts path-part elem-def child-spec-defs]
  (let [key (spec-key "fhir.xml" parent-path-parts path-part)]
    {:key key
     :min (:min elem-def)
     :max (:max elem-def)
     :modifier :xml
     :spec-form
     (case key
       (:fhir.xml/Address
        :fhir.xml/Age
        :fhir.xml/Annotation
        :fhir.xml/Attachment
        :fhir.xml/CodeableConcept
        :fhir.xml/Coding
        :fhir.xml/ContactDetail
        :fhir.xml/ContactPoint
        :fhir.xml/Contributor
        :fhir.xml/Count
        :fhir.xml/DataRequirement
        :fhir.xml/Distance
        :fhir.xml/Dosage
        :fhir.xml/Duration
        :fhir.xml/Expression
        :fhir.xml/Extension
        :fhir.xml/HumanName
        :fhir.xml/Identifier
        :fhir.xml/Meta
        :fhir.xml/Money
        :fhir.xml/Narrative
        :fhir.xml/ParameterDefinition
        :fhir.xml/Period
        :fhir.xml/Quantity
        :fhir.xml/Range
        :fhir.xml/Ratio
        :fhir.xml/Reference
        :fhir.xml/RelatedArtifact
        :fhir.xml/SampledData
        :fhir.xml/Signature
        :fhir.xml/Timing
        :fhir.xml/TriggerDefinition
        :fhir.xml/UsageContext)
       (special-xml-schema-spec-form kind (name key) child-spec-defs)
       :fhir.xml.DataRequirement/codeFilter
       (special-xml-schema-spec-form kind "DataRequirementCodeFilter" child-spec-defs)
       :fhir.xml.DataRequirement/dateFilter
       (special-xml-schema-spec-form kind "DataRequirementDateFilter" child-spec-defs)
       :fhir.xml.DataRequirement/sort
       (special-xml-schema-spec-form kind "DataRequirementSort" child-spec-defs)
       :fhir.xml.Dosage/doseAndRate
       (special-xml-schema-spec-form kind "DosageDoseAndRate" child-spec-defs)
       :fhir.xml.Timing/repeat
       (special-xml-schema-spec-form kind "TimingRepeat" child-spec-defs)
       :fhir.xml.Bundle.entry/search
       (special-xml-schema-spec-form kind "BundleEntrySearch" child-spec-defs)
       (xml-schema-spec-form kind (spec-key "fhir" parent-path-parts path-part)
                             child-spec-defs))}))

(defn- build-spec-defs [kind parent-path-parts indexed-elem-defs]
  (into
   []
   (comp
    (filter (comp string? first))
    (mapcat
     (fn [[path-part coll-or-elem-def]]
       (if (map? coll-or-elem-def)
         (elem-def->spec-def (fix-fhir-25274 coll-or-elem-def))
         (let [elem-def (ffirst coll-or-elem-def)
               child-spec-defs (build-spec-defs "backbone-element" (conj parent-path-parts path-part) coll-or-elem-def)]
           [child-spec-defs
            (internal-schema-spec-def parent-path-parts path-part elem-def child-spec-defs)
            (xml-schema-spec-def kind parent-path-parts path-part elem-def child-spec-defs)])))))
   indexed-elem-defs))

(defn index-by-path* [elem-defs]
  (into
   []
   (comp
    (partition-by first)
    (map
     (fn [xs]
       (if (< 1 (count xs))
         [(ffirst xs) (index-by-path* (map rest xs))]
         (first xs)))))
   elem-defs))

(defn- index-by-path [elem-defs]
  (index-by-path*
   (map
    (fn [{:keys [path] :as elem-def}]
      (conj (split-path path) elem-def))
    elem-defs)))

(defn struct-def->spec-def [{:keys [kind] {elem-defs :element} :snapshot}]
  (flatten (build-spec-defs kind [] (index-by-path elem-defs))))

(defn- internal-pred [name]
  (case name
    "boolean" `type/boolean?
    "integer" `type/integer?
    "string" `type/string?
    "decimal" `type/decimal?
    "uri" `type/uri?
    "url" `type/url?
    "canonical" `type/canonical?
    "base64Binary" `type/base64Binary?
    "instant" `type/instant?
    "date" `type/date?
    "dateTime" `type/dateTime?
    "time" `type/time?
    "code" `type/code?
    "oid" `type/oid?
    "id" `type/id?
    "markdown" `type/markdown?
    "unsignedInt" `type/unsignedInt?
    "positiveInt" `type/positiveInt?
    "uuid" `type/uuid?
    "xhtml" `type/xhtml?
    (throw (ex-info (format "Unknown primitive type `%s`." name) {}))))

(defn- value-type [element]
  (some #(when (str/ends-with? (:path %) "value") (first (:type %))) element))

(defn- pattern [name element]
  (case name
    "string" nil
    "uri" #"(?U)[\p{Print}&&[^\p{Blank}]]*"
    "url" #"(?U)[\p{Print}&&[^\p{Blank}]]*"
    "canonical" #"(?U)[\p{Print}&&[^\p{Blank}]]*"
    "code" nil
    "markdown" nil
    (type-regex (value-type element))))

(defn- xml-spec-form [name {:keys [element]}]
  (let [pattern (pattern name element)]
    (case name
      "boolean" (xml/primitive-xml-form pattern `type/boolean `system/parse-boolean)
      "integer" (xml/primitive-xml-form pattern `type/integer `system/parse-integer)
      "decimal" (xml/primitive-xml-form pattern `type/decimal `system/parse-decimal)
      "instant" (xml/primitive-xml-form pattern `type/instant `system/parse-date-time)
      "date" (xml/primitive-xml-form pattern `type/date `system/parse-date)
      "dateTime" (xml/primitive-xml-form pattern `type/dateTime `system/parse-date-time)
      "time" (xml/primitive-xml-form pattern `type/time `system/parse-time)
      "unsignedInt" (xml/primitive-xml-form pattern `type/unsignedInt `system/parse-integer)
      "positiveInt" (xml/primitive-xml-form pattern `type/positiveInt `system/parse-integer)
      "xhtml" `(s/and xml/element? (s/conformer type/xml->Xhtml type/xhtml-to-xml))
      (if pattern
        (xml/primitive-xml-form pattern (symbol "blaze.fhir.spec.type" name) `identity)
        (xml/primitive-xml-form (symbol "blaze.fhir.spec.type" name) `identity)))))

(defn primitive-type->spec-defs
  "Converts a primitive type structure definition into spec defs for XML and
   internal representation."
  [{:keys [name snapshot]}]
  [{:key (keyword "fhir" name) :spec-form (internal-pred name)}
   {:key (keyword "fhir.xml" name) :spec-form (xml-spec-form name snapshot)}])

(defn- resolve-spec [spec-form]
  (if (keyword? spec-form) spec-form (s/resolve-spec spec-form)))

(defn register
  "Registers `spec-defs`"
  [spec-defs]
  (run!
   (fn [{:keys [key spec-form]}]
     (s/register key (resolve-spec spec-form)))
   spec-defs))

(defmulti xml-resource (constantly :default))

(defmethod xml-resource :default [{:keys [tag] :fhir/keys [type]}]
  (->> (or tag type) name (keyword "fhir.xml")))

(defn conform-xml-resource [{:keys [content]}]
  (or (some #(when (xml/element? %) %) content) ::s/invalid))

(defn unform-xml-resource [resource]
  (xml-node/element ::f/resource {} resource))

(s/def :fhir.xml/Resource
  (s/and (s/conformer conform-xml-resource unform-xml-resource)
         (s/multi-spec xml-resource (fn [value _] value))))

(defmulti resource (constantly :default))

(defmethod resource :default [{:fhir/keys [type]}]
  type)

(s/def :fhir/Resource
  (s/multi-spec resource :fhir/type))

;; should be 15992
(comment (count (keys (s/registry))))
