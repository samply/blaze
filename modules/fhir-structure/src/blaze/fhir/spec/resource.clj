(ns blaze.fhir.spec.resource
  "JSON Parsing.

  Use `create-type-handlers` to create a map of type-handlers that has to be
  given to `parse-json` in order to parse JSON from a source.

  A locator is a list of path segments already parsed in order to report the
  location of an error. Path segments are either strings of field names or
  indices of arrays. The lists are build in reverse were the path grows at the
  front. In case of an error, the locator list is reversed.

  A type-handler in this namespace is a function of two arities. On arity-0 the
  function returns the name of the type as string. On arity-3 it takes a map of
  all type handlers, a parser and a locator and returns either a FHIR value or
  an anomaly in case of errors.

  A property-handler in this namespace is a function taking a map of all type
  handlers, a parser, a locator and a partially constructed FHIR value and
  returns either the FHIR value with data added or an anomaly in case of errors.

  This namespace uses some advanced optimizations like mutable ArrayLists.
  Please change with care."
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.spec.type.string-util :as su]
   [blaze.fhir.spec.type.system :as system]
   [blaze.fhir.util :as fu]
   [blaze.util :as u :refer [str]]
   [clojure.string :as str]
   [cognitect.anomalies :as anom])
  (:import
   [blaze.fhir.spec.type Lists]
   [clojure.lang PersistentArrayMap RT]
   [com.fasterxml.jackson.core JsonFactory JsonParseException JsonParser JsonToken StreamReadConstraints]
   [com.fasterxml.jackson.core.exc InputCoercionException]
   [com.fasterxml.jackson.core.io JsonEOFException]
   [com.fasterxml.jackson.databind JsonNode ObjectMapper]
   [com.fasterxml.jackson.databind.node TreeTraversingParser]
   [com.fasterxml.jackson.dataformat.cbor CBORFactory]
   [java.io InputStream OutputStream Reader]
   [java.util ArrayList Arrays List]))

(set! *warn-on-reflection* true)

(defn- backbone-element-definition? [{types :type}]
  (and (= 1 (count types)) (#{"Element" "BackboneElement"} (-> types first :code))))

(defn- separate-element-definitions*
  [parent-type element-definitions]
  (loop [[{:keys [path] :as ed} & more :as all] element-definitions
         types {}
         out []]
    (cond
      (nil? ed)
      {:types (assoc types parent-type out)}

      (not (str/starts-with? path (str parent-type ".")))
      {:types (assoc types parent-type out) :more all}

      (backbone-element-definition? ed)
      (let [{:keys [more] child-types :types} (separate-element-definitions* path more)]
        (recur more (merge types child-types) (conj out ed)))

      :else
      (recur more types (conj out ed)))))

(defn separate-element-definitions
  "Separates nested backbone element definitions from `element-definitions` and
  returns a map of type name to non-nesting element definitions.

  In case `parent-type` has no nested backbone element definitions, the map will
  only contain the parent type as key."
  [parent-type element-definitions]
  (:types (separate-element-definitions* parent-type element-definitions)))

(defn- prepare-element-type [{:keys [code]} path]
  (condp = code
    "http://hl7.org/fhirpath/System.String" :system/string
    "http://hl7.org/fhirpath/System.Time" :system/time
    "http://hl7.org/fhirpath/System.Date" :system/date
    "http://hl7.org/fhirpath/System.DateTime" :system/date-time
    "http://hl7.org/fhirpath/System.Integer" :system/integer
    "http://hl7.org/fhirpath/System.Decimal" :system/decimal
    "http://hl7.org/fhirpath/System.Boolean" :system/boolean
    "Element" (keyword "element" path)
    "BackboneElement" (keyword "backboneElement" path)
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
      :primitive/string-interned
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
      :primitive/uri-interned
      (keyword
       (if (Character/isLowerCase ^char (first code))
         "primitive"
         "complex")
       code))))

(defn base-field-name
  "The field name without possible polymorphic type."
  [parent-type path polymorphic]
  (subs path (inc (count parent-type))
        (cond-> (count path) polymorphic (- 3))))

(defn- property-handler-definitions
  "Takes `element-definition` and returns possibly multiple
  property-handler definitions, one for each polymorphic type.

  An element handler definition contains:
   * field-name - the name of the JSON property
   * key - the key of the internal representation
   * type - a keyword of the FHIR element type
   * cardinality - :single or :many"
  {:arglists '([parent-type summary-only element-definition])}
  [parent-type summary-only
   {:keys [path max] content-reference :contentReference element-types :type
    summary :isSummary}]
  (when (or (not summary-only) summary)
    (if content-reference
      (let [base-field-name (base-field-name parent-type path false)]
        [{:field-name base-field-name
          :key (keyword base-field-name)
          :type (keyword "backboneElement" (subs content-reference 1))
          :cardinality (if (= "*" max) :many :single)
          :summary summary}])
      (let [polymorphic (< 1 (count element-types))]
        (map
         (fn [element-type]
           (let [element-type (prepare-element-type element-type path)
                 base-field-name (base-field-name parent-type path polymorphic)]
             {:field-name (cond-> base-field-name polymorphic (str (su/capital (name element-type))))
              :key (keyword base-field-name)
              :type element-type
              :cardinality (if (= "*" max) :many :single)
              :summary summary}))
         element-types)))))

(defmacro current-token [parser]
  `(.currentToken ~(with-meta parser {:tag `JsonParser})))

(defn- expression [locator]
  ;; the locator is reversed because it's a list were path segments are appended
  ;; in front
  (let [locator (reverse locator)]
    (loop [sb (StringBuilder. (str (first locator)))
           more (next locator)]
      (if more
        (if (string? (first more))
          (recur (-> sb (.append ".") (.append (first more))) (next more))
          (recur (-> sb (.append "[") (.append (str (first more))) (.append "]"))
                 (next more)))
        (str sb)))))

(defn- fhir-issue [msg locator]
  {:fhir.issues/code "invariant"
   :fhir.issues/diagnostics msg
   :fhir.issues/expression (expression locator)})

(defn- unexpected-end-of-input-msg [^JsonEOFException e]
  (condp identical? (.getTokenBeingDecoded e)
    JsonToken/FIELD_NAME "Unexpected end of input while parsing a field name."
    "Unexpected end of input."))

(defn- next-token! [parser locator]
  (try
    (.nextToken ^JsonParser parser)
    (catch JsonEOFException e
      (let [msg (unexpected-end-of-input-msg e)]
        (ba/incorrect msg :fhir/issues [(fhir-issue msg locator)])))
    (catch JsonParseException _
      (let [msg "JSON parsing error."]
        (ba/incorrect msg :fhir/issues [(fhir-issue msg locator)])))))

(defmacro current-name [parser]
  `(.currentName ~(with-meta parser {:tag `JsonParser})))

(defn- get-text [parser locator]
  (try
    (.getText ^JsonParser parser)
    (catch JsonEOFException _
      (let [msg "Unexpected end of input while reading a string value."]
        (ba/incorrect msg :fhir/issues [(fhir-issue msg locator)])))))

(defn- get-long [parser locator]
  (try
    (.getLongValue ^JsonParser parser)
    (catch InputCoercionException e
      (let [msg (first (str/split-lines (ex-message e)))]
        (ba/incorrect msg :fhir/issues [(fhir-issue msg locator)])))))

(defn- get-decimal [parser _locator]
  (.getDecimalValue ^JsonParser parser))

(defn- get-current-value [parser locator]
  (condp identical? (current-token parser)
    JsonToken/VALUE_NULL "value null"
    JsonToken/VALUE_TRUE "boolean value true"
    JsonToken/VALUE_FALSE "boolean value false"
    JsonToken/VALUE_STRING
    (when-ok [text (get-text parser locator)]
      (format "value `%s`" text))
    JsonToken/VALUE_NUMBER_INT
    (when-ok [number (get-long parser locator)]
      (format "integer value %d" number))
    JsonToken/VALUE_NUMBER_FLOAT
    (format "float value %s" (.getDecimalValue ^JsonParser parser))
    JsonToken/START_OBJECT "object start"
    JsonToken/START_ARRAY "array start"
    (format "token %s" (current-token parser))))

(defn- incorrect-value-anom*
  ([value locator expected-type]
   (let [msg (format "Error on %s. Expected type is `%s`." value expected-type)]
     (ba/incorrect msg :fhir/issues [(fhir-issue msg locator)])))
  ([value locator expected-type reason-msg]
   (let [msg (format "Error on %s. Expected type is `%s`. %s" value expected-type reason-msg)]
     (ba/incorrect msg :fhir/issues [(fhir-issue msg locator)]))))

(defn- incorrect-value-anom [parser locator expected-type]
  (when-ok [value (get-current-value parser locator)]
    (incorrect-value-anom* value locator expected-type)))

(defn- unknown-property-anom [locator name]
  (let [msg (format "Unknown property `%s`." name)]
    (ba/incorrect msg :fhir/issues [(fhir-issue msg locator)])))

(defmacro cond-next-token [parser locator & body]
  `(when-ok [token# (next-token! ~parser ~locator)]
     (condp identical? token#
       ~@body)))

(defn- create-system-string-handler
  "Returns a property-handler for System.String properties."
  [assoc-fn path expected-type]
  (fn system-string-handler [_ parser locator m]
    (cond-next-token parser locator
      JsonToken/VALUE_STRING
      (when-ok [value (get-text parser (cons path locator))]
        (assoc-fn m value))
      (incorrect-value-anom parser (cons path locator) expected-type))))

(defn- duplicate-property-anom [field-name locator]
  (let [msg (format "Duplicate property `%s`." field-name)]
    (ba/incorrect msg :fhir/issues [(fhir-issue msg locator)])))

(defn- get-value
  "Gets the value from special ArrayList `map` at `key` or returns optional
  `not-found`.

  Works like an PersistentArrayMap only that the ArrayList is mutable."
  ([map key]
   (get-value map key nil))
  ([^List map key not-found]
   (let [idx (.indexOf map key)]
     (if (neg? idx)
       not-found
       (.get map (unchecked-inc-int idx))))))

(defn- put-value!
  "Puts `value` into special ArrayList `map` at `key`.

  Works like an PersistentArrayMap only that the ArrayList is mutable."
  [^List map key value]
  (let [idx (.indexOf map key)]
    (if (neg? idx)
      (doto map (.add key) (.add value))
      (doto map (.set (unchecked-inc-int idx) value)))))

(defn- set-value!
  "Sets `value` at `index` in `list`."
  [^List list index value]
  (cond
    (< index (.size list)) (doto list (.set index value))
    (= index (.size list)) (doto list (.add value))))

(defn- persist-array-map
  "Creates an PersistentArrayMap from special ArrayList `map`.

  Should be only used if the map is consumed by a complex type constructor."
  [^List map]
  (PersistentArrayMap. (.toArray map)))

(defn- persist-map
  "Creates an IPersistentMap from special ArrayList `map`."
  [^List map]
  (RT/mapUniqueKeys (.toArray map)))

(defn- assoc-primitive-value
  "Associates `value` to `m` under `key`.

  In case an extended primitive value exists already, updates that primitive
  value with `value`. Otherwise uses `constructor` to create a new primitive
  value."
  [field-name key m constructor value locator]
  (if-some [primitive-value (get-value m key)]
    (if (some? (:value primitive-value))
      (duplicate-property-anom field-name locator)
      (put-value! m key (assoc primitive-value :value value)))
    (if-ok [value (constructor value)]
      (put-value! m key value)
      #(let [msg (::anom/message %)]
         (ba/incorrect msg :fhir/issues [(fhir-issue msg (cons (name key) locator))])))))

(defn- assoc-primitive-many-value
  "Like `assoc-primitive-value` but with a single value for cardinality many."
  [{:keys [field-name key]} m constructor value locator]
  (if-some [primitive-value (first (get-value m key))]
    (if (some? (:value primitive-value))
      (duplicate-property-anom field-name locator)
      (put-value! m key [(assoc primitive-value :value value)]))
    (put-value! m key [(constructor value)])))

(defn- primitive-boolean-value-handler
  "Returns a property-handler for boolean properties."
  [{:keys [field-name key]}]
  (fn [_ parser locator m]
    (cond-next-token parser locator
      JsonToken/VALUE_TRUE (assoc-primitive-value field-name key m type/boolean true locator)
      JsonToken/VALUE_FALSE (assoc-primitive-value field-name key m type/boolean false locator)
      (incorrect-value-anom parser (cons (name key) locator) "boolean"))))

(defn- primitive-value-handler
  "Returns a property-handler for the value part of primitive properties."
  {:arglists '([property-handler-definition constructor token extract-value expected-type])}
  ([{:keys [field-name key cardinality] :as def} constructor token extract-value expected-type]
   (let [path (name key)]
     (if (= :single cardinality)
       (fn primitive-property-handler-one-token-cardinality-single [_ parser locator m]
         (cond-next-token parser locator
           token
           (when-ok [value (extract-value parser (cons path locator))]
             (assoc-primitive-value field-name key m constructor value locator))
           (incorrect-value-anom parser (cons path locator) expected-type)))
       (fn primitive-property-handler-one-token-cardinality-many [_ parser locator m]
         (cond-next-token parser locator
           JsonToken/START_ARRAY
           (loop [l (ArrayList. ^List (get-value m key [])) i 0]
             (when-ok [t (next-token! parser locator)]
               (condp identical? t
                 token
                 (when-ok [value (extract-value parser (cons path locator))]
                   (if-some [primitive-value (when (< i (.size l)) (.get l i))]
                     (if (some? (:value primitive-value))
                       (duplicate-property-anom field-name locator)
                       (recur (doto l (.set i (assoc primitive-value :value value))) (inc i)))
                     (recur (set-value! l i (constructor value)) (inc i))))
                 JsonToken/END_ARRAY (put-value! m key (Lists/intern l))
                 JsonToken/VALUE_NULL
                 (recur (cond-> l (= i (.size l)) (doto (.add nil))) (inc i))
                 (incorrect-value-anom parser (cons path locator) (str expected-type "[]")))))
           token
           (when-ok [value (extract-value parser (cons path locator))]
             (assoc-primitive-many-value def m constructor value locator))
           (incorrect-value-anom parser (cons path locator) (str expected-type "[]")))))))
  ([{:keys [field-name key cardinality] :as def} constructor token-1
    extract-value-1 token-2 extract-value-2 expected-type]
   (let [path (name key)]
     (if (= :single cardinality)
       (fn primitive-property-handler-two-tokens-cardinality-single [_ parser locator m]
         (cond-next-token parser locator
           token-1
           (when-ok [value (extract-value-1 parser (cons path locator))]
             (assoc-primitive-value field-name key m constructor value locator))
           token-2
           (when-ok [value (extract-value-2 parser (cons path locator))]
             (assoc-primitive-value field-name key m constructor value locator))
           (incorrect-value-anom parser (cons path locator) expected-type)))
       (fn primitive-property-handler-two-tokens-cardinality-many [_ parser locator m]
         (cond-next-token parser locator
           JsonToken/START_ARRAY
           (loop [l (ArrayList. ^List (get-value m key [])) i 0]
             (when-ok [t (next-token! parser locator)]
               (condp identical? t
                 token-1
                 (when-ok [value (extract-value-1 parser (cons path locator))]
                   (if-some [primitive-value (when (< i (.size l)) (.get l i))]
                     (if (some? (:value primitive-value))
                       (duplicate-property-anom field-name locator)
                       (recur (doto l (.set i (assoc primitive-value :value value))) (inc i)))
                     (recur (set-value! l i (constructor value)) (inc i))))
                 token-2
                 (when-ok [value (extract-value-2 parser (cons path locator))]
                   (if-some [primitive-value (when (< i (.size l)) (.get l i))]
                     (if (some? (:value primitive-value))
                       (duplicate-property-anom field-name locator)
                       (recur (doto l (.set i (assoc primitive-value :value value))) (inc i)))
                     (recur (set-value! l i (constructor value)) (inc i))))
                 JsonToken/END_ARRAY (put-value! m key (Lists/intern l))
                 JsonToken/VALUE_NULL
                 (recur (cond-> l (= i (.size l)) (doto (.add nil))) (inc i))
                 (incorrect-value-anom parser (cons path locator) expected-type))))
           token-1
           (when-ok [value (extract-value-1 parser (cons path locator))]
             (assoc-primitive-many-value def m constructor value locator))
           token-2
           (when-ok [value (extract-value-2 parser (cons path locator))]
             (assoc-primitive-many-value def m constructor value locator))
           (incorrect-value-anom parser (cons path locator) expected-type)))))))

(defmacro recur-ok [expr-form]
  `(when-ok [r# ~expr-form]
     (recur r#)))

(def ^:private primitive-id-handler
  "A property-handler for id properties."
  (create-system-string-handler #(assoc %1 :id %2) "id" "string"))

(defn- parse-complex-list [handler type-handlers parser locator]
  (loop [list (ArrayList.)]
    (cond-next-token parser locator
      JsonToken/START_OBJECT
      (when-ok [value (handler type-handlers parser (cons (.size list) locator))]
        (recur (doto list (.add value))))
      JsonToken/END_ARRAY (Lists/intern list)
      (incorrect-value-anom parser (cons (.size list) locator) (handler)))))

(defn- unsupported-type-anom [type]
  (ba/unsupported (format "Unsupported type `%s`." type)))

(defn- parse-extended-primitive-properties
  [type-handlers parser locator data]
  (loop [data data]
    (cond-next-token parser locator
      JsonToken/FIELD_NAME
      (condp = (current-name parser)
        "id" (recur-ok (primitive-id-handler type-handlers parser locator data))
        "extension"
        (when-some [extension-handler (get type-handlers :Extension)]
          (cond-next-token parser locator
            JsonToken/START_ARRAY
            (when-ok [list (parse-complex-list extension-handler type-handlers parser (cons "extension" locator))]
              (recur (assoc data :extension list)))
            JsonToken/START_OBJECT
            (when-ok [extension (extension-handler type-handlers parser (cons 0 (cons "extension" locator)))]
              (recur (assoc data :extension (Lists/intern [extension]))))
            (incorrect-value-anom parser (cons "extension" locator) "Extension[]")))
        (unknown-property-anom locator (current-name parser)))
      JsonToken/END_OBJECT data)))

(defn- trim-trailing-nils [^List vector]
  (loop [i (.size vector)]
    (if (zero? i)
      (ArrayList.)
      (if (nil? (.get vector (dec i)))
        (recur (dec i))
        (.subList vector 0 i)))))

(defn- extended-primitive-handler
  "Returns a property-handler."
  [{:keys [key cardinality]} constructor]
  (let [path (name key)]
    (if (= :single cardinality)
      (fn [type-handlers parser locator m]
        (cond-next-token parser locator
          JsonToken/START_OBJECT
          (if-some [primitive-value (get-value m key)]
            (when-ok [primitive-value (parse-extended-primitive-properties type-handlers parser (cons path locator) primitive-value)]
              (put-value! m key primitive-value))
            (when-ok [data (parse-extended-primitive-properties type-handlers parser (cons path locator) {})]
              (put-value! m key (constructor data))))
          (incorrect-value-anom parser (cons path locator) "primitive extension map")))
      (fn [type-handlers parser locator m]
        (cond-next-token parser locator
          JsonToken/START_ARRAY
          (loop [l (ArrayList. ^List (get-value m key [])) i 0]
            (when-ok [t (next-token! parser (cons path locator))]
              (condp identical? t
                JsonToken/START_OBJECT
                (if-some [primitive-value (when (< i (.size l)) (.get l i))]
                  (when-ok [primitive-value (parse-extended-primitive-properties type-handlers parser (cons path locator) primitive-value)]
                    (recur (doto l (.set i primitive-value)) (inc i)))
                  (when-ok [data (parse-extended-primitive-properties type-handlers parser (cons path locator) {})]
                    (recur (set-value! l i (constructor data)) (inc i))))
                JsonToken/END_ARRAY (put-value! m key (Lists/intern (trim-trailing-nils l)))
                JsonToken/VALUE_NULL
                (recur (cond-> l (= i (.size l)) (doto (.add nil))) (inc i))
                (incorrect-value-anom parser (cons path locator) "primitive extension map"))))
          JsonToken/START_OBJECT
          (if-some [primitive-list (get-value m key)]
            (if (zero? (count primitive-list))
              (when-ok [data (parse-extended-primitive-properties type-handlers parser (cons path locator) {})]
                (put-value! m key [(constructor data)]))
              (when-ok [primitive-value (parse-extended-primitive-properties type-handlers parser (cons path locator) (first primitive-list))]
                (put-value! m key (assoc primitive-list 0 primitive-value))))
            (when-ok [data (parse-extended-primitive-properties type-handlers parser (cons path locator) {})]
              (put-value! m key [(constructor data)])))
          JsonToken/VALUE_NULL m
          (incorrect-value-anom parser (cons path locator) "primitive extension map"))))))

(defn- primitive-handler
  "Returns a map of two property-handlers, one for the field-name of
  `property-handler-definition` and one for _field-name for handling extended
  primitive data."
  {:arglists '([property-handler-definition constructor value-handler])}
  [{:keys [field-name] :as def} constructor value-handler]
  {field-name value-handler
   (str "_" field-name) (extended-primitive-handler def constructor)})

(defn- primitive-integer-handler
  "Returns a property-handler for integer properties."
  [def constructor]
  (->> (primitive-value-handler def constructor JsonToken/VALUE_NUMBER_INT
                                get-long "integer")
       (primitive-handler def constructor)))

(defn- primitive-decimal-handler
  "A handler that reads an integer or decimal value and creates the internal
  representation using `constructor`."
  [def]
  (->> (primitive-value-handler def type/decimal JsonToken/VALUE_NUMBER_INT
                                get-decimal JsonToken/VALUE_NUMBER_FLOAT get-decimal
                                "decimal")
       (primitive-handler def type/decimal)))

(defn- get-text-pattern [pattern]
  (fn [parser locator expected-type]
    (when-ok [text (get-text parser locator)]
      (if (.matches (re-matcher pattern text))
        text
        (incorrect-value-anom* (format "value `%s`" text) locator (format "%s, regex %s" expected-type pattern))))))

(defn- parse-text [system-parser locator expected-type text]
  (if-ok [value (system-parser text)]
    value
    #(incorrect-value-anom* (format "value `%s`" text) locator expected-type (::anom/message %))))

(defn- system-value-parser
  ([system-parser expected-type]
   (fn system-value-parser [parser locator]
     (when-ok [text (get-text parser locator)]
       (parse-text system-parser locator expected-type text))))
  ([system-parser expected-type pattern]
   (let [get-text (get-text-pattern pattern)]
     (fn pattern-system-value-parser [parser locator]
       (when-ok [text (get-text parser locator expected-type)]
         (parse-text system-parser locator expected-type text))))))

(defn- primitive-string-handler
  "A handler that reads a string value and creates the internal representation
  using `constructor` and optional `system-parser` and `pattern`.

  The system parser has to be a function from string to system value or anomaly."
  ([def constructor system-parser expected-type]
   (->> (primitive-value-handler
         def constructor JsonToken/VALUE_STRING
         (system-value-parser system-parser expected-type)
         expected-type)
        (primitive-handler def constructor)))
  ([def constructor system-parser expected-type pattern use-regex]
   (->> (primitive-value-handler
         def constructor JsonToken/VALUE_STRING
         (if use-regex
           (system-value-parser system-parser expected-type pattern)
           (system-value-parser system-parser expected-type))
         expected-type)
        (primitive-handler def constructor))))

(defn- create-complex-property-handler
  "Returns a map of a single JSON property name to a property-handler that
  delegates handling of the property value to the type-handler of the complex
  type of `property-handler-definition`."
  {:arglists '([opts property-handler-definition])}
  [{:keys [summary-only]} {:keys [field-name key type cardinality]}]
  {field-name
   (let [type-name (if (= "backboneElement" (namespace type))
                     "BackboneElement"
                     (name type))
         type-key (if summary-only (keyword "summary" (name type)) (keyword (name type)))
         path (name key)]
     (if (= :single cardinality)
       (fn complex-property-handler-cardinality-single [type-handlers parser locator m]
         (if-some [handler (type-handlers type-key)]
           (cond-next-token parser locator
             JsonToken/START_OBJECT
             (when-ok [value (handler type-handlers parser (cons path locator))]
               (put-value! m key value))
             (incorrect-value-anom parser (cons path locator) type-name))
           (unsupported-type-anom (name type))))
       (fn complex-property-handler-cardinality-many [type-handlers parser locator m]
         (if-some [handler (type-handlers type-key)]
           (cond-next-token parser locator
             JsonToken/START_ARRAY
             (when-ok [list (parse-complex-list handler type-handlers parser (cons path locator))]
               (put-value! m key list))
             JsonToken/START_OBJECT
             (when-ok [value (handler type-handlers parser (cons 0 (cons path locator)))]
               (put-value! m key [value]))
             (incorrect-value-anom parser (cons path locator) type-name))
           (unsupported-type-anom (name type))))))})

(defn- create-property-handlers*
  "Returns a map of JSON property names to handlers."
  {:arglists '([opts property-handler-definition])}
  [{:keys [use-regex] :as opts} {:keys [field-name key type] :as def}]
  (condp = type
    :system/string
    {field-name (create-system-string-handler #(put-value! %1 key %2) (name key) "string")}

    :primitive/boolean
    (primitive-handler def type/boolean (primitive-boolean-value-handler def))

    :primitive/integer
    (primitive-integer-handler def type/integer)

    :primitive/string
    (primitive-string-handler def type/string identity "string")

    :primitive/string-interned
    (primitive-string-handler def type/string-interned identity "string")

    :primitive/decimal
    (primitive-decimal-handler def)

    :primitive/uri
    (primitive-string-handler def type/uri identity "uri"
                              #"(?U)[\p{Print}&&[^\p{Blank}]]*" use-regex)

    :primitive/uri-interned
    (primitive-string-handler def type/uri-interned identity "uri"
                              #"(?U)[\p{Print}&&[^\p{Blank}]]*" use-regex)

    :primitive/url
    (primitive-string-handler def type/url identity "url"
                              #"(?U)[\p{Print}&&[^\p{Blank}]]*" use-regex)

    :primitive/canonical
    (primitive-string-handler def type/canonical identity "canonical"
                              #"(?U)[\p{Print}&&[^\p{Blank}]]*" use-regex)

    :primitive/base64Binary
    (primitive-string-handler def type/base64Binary identity "base64Binary"
                              #"([0-9a-zA-Z+/=]{4})+" use-regex)

    :primitive/instant
    (primitive-string-handler def type/instant system/parse-date-time "instant"
                              #"([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1])T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\.[0-9]+)?(Z|(\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00))" use-regex)

    :primitive/date
    (primitive-string-handler def type/date system/parse-date "date")

    :primitive/dateTime
    (primitive-string-handler def type/dateTime system/parse-date-time "date-time")

    :primitive/time
    (primitive-string-handler def type/time system/parse-time "time")

    :primitive/code
    (primitive-string-handler def type/code identity "code")

    :primitive/oid
    (primitive-string-handler def type/oid identity "oid"
                              #"urn:oid:[0-2](\.(0|[1-9][0-9]*))+" use-regex)

    :primitive/id
    (primitive-string-handler def type/id identity "id"
                              #"[A-Za-z0-9\-\.]{1,64}" use-regex)

    :primitive/markdown
    (primitive-string-handler def type/markdown identity "markdown")

    :primitive/unsignedInt
    (primitive-integer-handler def type/unsignedInt)

    :primitive/positiveInt
    (primitive-integer-handler def type/positiveInt)

    :primitive/uuid
    (primitive-string-handler def type/uuid identity "uuid"
                              #"urn:uuid:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}" use-regex)

    :primitive/xhtml
    (primitive-string-handler def type/xhtml identity "xhtml")

    (if (#{"complex" "element" "backboneElement"} (namespace type))
      (create-complex-property-handler opts def)
      (unsupported-type-anom (name type)))))

(defn- create-property-handlers
  "Returns a map of JSON property names to property handlers."
  [type {:keys [summary-only] :as opts} element-definitions]
  (transduce
   (mapcat (partial property-handler-definitions type summary-only))
   (fn
     ([m]
      (let [s (sort-by first (seq m))
            names (object-array (map first s))
            handlers (object-array (map second s))]
        (fn find-property-handler [field-name]
          (let [idx (Arrays/binarySearch names field-name)]
            (when-not (neg? idx)
              (aget handlers idx))))))
     ([handlers property-handler-definition]
      (if-ok [handler (create-property-handlers* opts property-handler-definition)]
        (into handlers handler)
        reduced)))
   {}
   element-definitions))

(defn- fhir-type-keyword [type]
  (let [parts (cons "fhir" (seq (str/split type #"\.")))]
    (keyword (str/join "." (butlast parts)) (last parts))))

(defn- complex-type-finalizer [type]
  (condp = type
    "Address" #(type/address (persist-array-map %))
    "Age" #(type/age (persist-array-map %))
    "Annotation" #(type/annotation (persist-array-map %))
    "Attachment" #(type/attachment (persist-array-map %))
    "Bundle.entry.search" #(type/bundle-entry-search (persist-array-map %))
    "CodeableConcept" #(type/codeable-concept (persist-array-map %))
    "Coding" #(type/coding (persist-array-map %))
    "ContactDetail" #(type/contact-detail (persist-array-map %))
    "ContactPoint" #(type/contact-point (persist-array-map %))
    "Contributor" #(type/contributor (persist-array-map %))
    "Count" #(type/count (persist-array-map %))
    "DataRequirement" #(type/data-requirement (persist-array-map %))
    "DataRequirement.codeFilter" #(type/data-requirement-code-filter (persist-array-map %))
    "DataRequirement.dateFilter" #(type/data-requirement-date-filter (persist-array-map %))
    "DataRequirement.sort" #(type/data-requirement-sort (persist-array-map %))
    "Distance" #(type/distance (persist-array-map %))
    "Dosage" #(type/dosage (persist-array-map %))
    "Dosage.doseAndRate" #(type/dosage-dose-and-rate (persist-array-map %))
    "Duration" #(type/duration (persist-array-map %))
    "Expression" #(type/expression (persist-array-map %))
    "Extension" #(type/extension (persist-array-map %))
    "HumanName" #(type/human-name (persist-array-map %))
    "Identifier" #(type/identifier (persist-array-map %))
    "Meta" #(type/meta (persist-array-map %))
    "Money" #(type/money (persist-array-map %))
    "Narrative" #(type/narrative (persist-array-map %))
    "ParameterDefinition" #(type/parameter-definition (persist-array-map %))
    "Period" #(type/period (persist-array-map %))
    "Quantity" #(type/quantity (persist-array-map %))
    "Range" #(type/range (persist-array-map %))
    "Ratio" #(type/ratio (persist-array-map %))
    "Reference" #(type/reference (persist-array-map %))
    "RelatedArtifact" #(type/related-artifact (persist-array-map %))
    "SampledData" #(type/sampled-data (persist-array-map %))
    "Signature" #(type/signature (persist-array-map %))
    "Timing" #(type/timing (persist-array-map %))
    "Timing.repeat" #(type/timing-repeat (persist-array-map %))
    "TriggerDefinition" #(type/trigger-definition (persist-array-map %))
    "UsageContext" #(type/usage-context (persist-array-map %))
    (let [fhir-type-kw (fhir-type-keyword type)]
      #(persist-map (put-value! % :fhir/type fhir-type-kw)))))

(def ^:private update-meta
  (fnil update #fhir/Meta{}))

(defn- incorrect-type-anom [locator type expected-type]
  (let [msg (format "Incorrect resource type `%s`. Expected type is `%s`." type expected-type)]
    (ba/incorrect msg :fhir/issues [(fhir-issue msg locator)])))

(defn- skip-value! [parser locator]
  (cond-next-token parser locator
    JsonToken/START_OBJECT
    (.skipChildren ^JsonParser parser)
    JsonToken/START_ARRAY
    (.skipChildren ^JsonParser parser)
    nil))

(defn- finalize-resource [fhir-type-kw resource]
  (persist-map (put-value! resource :fhir/type fhir-type-kw)))

(defn- append-subsetted [resource]
  (update resource :meta update-meta :tag u/conj-vec fu/subsetted))

(defn- create-type-handler
  "Creates a handler for `type` using `element-definitions`.

  The element definitions must not contain nested backbone element definitions.
  Use the `separate-element-definitions` function to separate nested backbone
  element definitions.
  
  A type-handler reads a JSON object. It expects that the `START_OBJECT` token
  is already read and will try to read a `FIELD_NAME` or `END_OBJECT` token. It
  either returns a value of `type` or an anomaly in case of errors."
  [kind type element-definitions {:keys [fail-on-unknown-property summary-only] :as opts}]
  (when-ok [property-handlers (create-property-handlers type opts element-definitions)]
    (let [capacity (int (* 2 (count element-definitions)))
          fhir-type-kw (keyword "fhir" type)
          finalize-resource (cond->> #(finalize-resource fhir-type-kw %)
                              summary-only (comp append-subsetted))]
      (condp = kind
        :resource
        (fn resource-handler
          ([] type)
          ([type-handlers parser locator]
           (loop [resource (ArrayList. capacity)]
             (cond-next-token parser locator
               JsonToken/FIELD_NAME
               (let [field-name (current-name parser)]
                 (if-some [handler (property-handlers field-name)]
                   (recur-ok (handler type-handlers parser locator resource))
                   (if (= "resourceType" field-name)
                     (cond-next-token parser locator
                       JsonToken/VALUE_STRING
                       (when-ok [s (get-text parser locator)]
                         (if (= type s)
                           (recur resource)
                           (incorrect-type-anom locator s type))))
                     (if fail-on-unknown-property
                       (unknown-property-anom locator field-name)
                       (do (skip-value! parser locator) (recur resource))))))
               JsonToken/END_OBJECT (finalize-resource resource)))))
        :complex-type
        (let [finalize (complex-type-finalizer type)]
          (fn complex-type-handler
            ([] type)
            ([type-handlers parser locator]
             (loop [value (ArrayList. capacity)]
               (cond-next-token parser locator
                 JsonToken/FIELD_NAME
                 (let [field-name (current-name parser)]
                   (if-some [handler (property-handlers field-name)]
                     (recur-ok (handler type-handlers parser locator value))
                     (if fail-on-unknown-property
                       (unknown-property-anom locator field-name)
                       (do (skip-value! parser locator) (recur value)))))
                 JsonToken/END_OBJECT (finalize value))))))))))

(defn create-type-handlers
  "Creates a map of keyword type names to type-handlers from the snapshot
  `element-definitions` of a StructureDefinition resource.
  
  Returns an anomaly in case of errors."
  {:arglists '([kind element-definitions opts])}
  [kind [{parent-type :path} & more] {:keys [include-summary-only] :as opts}]
  (reduce-kv
   (if include-summary-only
     (let [summary-opts (assoc opts :summary-only true)]
       (fn [res type element-definitions]
         (let [kind (if (= parent-type type) kind :complex-type)]
           (if-ok [handler (create-type-handler kind type element-definitions opts)
                   summary-handler (create-type-handler kind type element-definitions summary-opts)]
             (assoc res (keyword type) handler (keyword "summary" type) summary-handler)
             reduced))))
     (fn [res type element-definitions]
       (let [kind (if (= parent-type type) kind :complex-type)]
         (if-ok [handler (create-type-handler kind type element-definitions opts)]
           (assoc res (keyword type) handler)
           reduced))))
   {}
   (separate-element-definitions parent-type more)))

(defn- findResourceType [^JsonNode node]
  (when-let [node (.get node "resourceType")]
    (when (.isTextual node)
      (.asText node))))

(def resource-handler
  "A special type-handler that works for all resources. It first reads the
  `resourceType` property and delegates the handling to the corresponding
  type-handler."
  (fn
    ([] "Resource")
    ([type-handlers ^JsonParser parser locator]
     (let [^JsonNode tree (.readValueAsTree parser)]
       (if-let [type (findResourceType tree)]
         (if-let [type-handler (get type-handlers (keyword type))]
           (with-open [parser (TreeTraversingParser. tree (.getCodec parser))]
             ;; skip the first token
             (next-token! parser locator)
             (type-handler type-handlers parser (if (empty? locator) [type] locator)))
           (unsupported-type-anom type))
         (let [msg "Missing property `resourceType`."]
           (ba/incorrect msg :fhir/issues [(fhir-issue msg locator)])))))))

(defn- read-value* [type-handlers parser locator handler]
  (cond-next-token parser locator
    JsonToken/START_OBJECT
    (when-ok [type (handler type-handlers parser locator)
              token (next-token! parser locator)]
      (if token
        (ba/incorrect (format "incorrect trailing token %s" token))
        type))
    (incorrect-value-anom parser locator (handler))))

(defn- prefix-msg [msg]
  (str "Invalid JSON representation of a resource. " msg))

(defn- read-value
  "Reads a complex value from `parser` using `handler`.

  The locator is used to generate path-based expressions in errors. The initial
  locator has to be a list not a vector.

  The handler will determine the type of the value."
  [type-handlers parser locator handler]
  (-> (read-value* type-handlers parser locator handler)
      (ba/exceptionally #(update % ::anom/message prefix-msg))))

(def ^:private stream-read-constraints
  (-> (StreamReadConstraints/builder)
      (.maxStringLength Integer/MAX_VALUE)
      (.build)))

(defprotocol ParserFactory
  (-create-parser ^JsonParser [source factory]))

(extend-protocol ParserFactory
  InputStream
  (-create-parser [source factory]
    (.createParser ^JsonFactory factory source))
  Reader
  (-create-parser [source factory]
    (.createParser ^JsonFactory factory source))
  String
  (-create-parser [source factory]
    (.createParser ^JsonFactory factory source))
  byte/1
  (-create-parser [source factory]
    (.createParser ^JsonFactory factory ^bytes source)))

(def ^:private ^JsonFactory json-factory
  (doto (-> (JsonFactory/builder)
            (.streamReadConstraints stream-read-constraints)
            (.build))
    (ObjectMapper.)))

(defn parse-json
  "Parses a complex value from JSON `source`.

  For resources, the two-arity version can be used. In this case the
  `resourceType` JSON property is used to determine the `type`.
  
  For complex types, the `type` has to be given.
  
  Returns an anomaly in case of errors."
  ([type-handlers source]
   (with-open [parser (-create-parser source json-factory)]
     (read-value type-handlers parser nil resource-handler)))
  ([type-handlers type source]
   (if-some [handler (get type-handlers (keyword type))]
     (with-open [parser (-create-parser source json-factory)]
       (read-value type-handlers parser (RT/list type) handler))
     (unsupported-type-anom type))))

(defn write-json [type-handlers out value]
  (if-some [type (:fhir/type value)]
    (if-some [handler (get type-handlers type)]
      (with-open [gen (.createGenerator json-factory ^OutputStream out)]
        (handler type-handlers gen value))
      (unsupported-type-anom (name type)))
    (ba/incorrect (format "Missing type."))))

(def ^:private ^JsonFactory cbor-factory
  (doto (-> (CBORFactory/builder)
            (.streamReadConstraints stream-read-constraints)
            (.build))
    (ObjectMapper.)))

(defn parse-cbor
  "Parses a complex value of `type` from `source`.

  Returns an anomaly in case of errors."
  [type-handlers type variant source]
  (if-some [handler (get type-handlers (if (= :summary variant) (keyword "summary" type) (keyword type)))]
    (with-open [parser (.createParser ^JsonFactory cbor-factory ^bytes source)]
      (read-value type-handlers parser (RT/list type) handler))
    (unsupported-type-anom type)))

(defn write-cbor [type-handlers out value]
  (if-some [type (:fhir/type value)]
    (if-some [handler (get type-handlers type)]
      (with-open [gen (.createGenerator cbor-factory ^OutputStream out)]
        (handler type-handlers gen value))
      (unsupported-type-anom (name type)))
    (ba/incorrect (format "Missing type."))))
