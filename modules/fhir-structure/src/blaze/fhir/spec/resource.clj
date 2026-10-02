(ns blaze.fhir.spec.resource
  "Streaming reader and writer for FHIR resources and complex types.

  Reads and writes Blaze's internal FHIR data model from/to both JSON and CBOR,
  on top of Jackson's streaming `JsonParser` / `JsonGenerator` (the CBOR backend
  implements the same streaming interfaces). Parsing produces values directly
  from the token stream without an intermediate tree, so a resource is built in
  a single pass.

  Use `create-type-handlers` to build a map of type-handlers from
  StructureDefinition snapshots, then pass it to `parse-json` / `parse-cbor`
  (read). Use `write-json` / `write-cbor` with the type-handlers of the writing
  context (write).

  A locator is a list of path segments already parsed in order to report the
  location of an error. Path segments are either strings of field names or
  indices of arrays. The lists are built in reverse where the path grows at the
  front. In case of an error, the locator list is reversed.

  A type-handler in this namespace is a function of two arities. On arity-0 the
  function returns the name of the type as string. On arity-2 it takes a parser
  and a locator and returns either a FHIR value or an anomaly in case of errors.

  A property-handler in this namespace is a function taking a parser, a locator
  and a partially constructed FHIR value and returns either the FHIR value with
  data added or an anomaly in case of errors.

  Type-handlers of complex types don't look up other type-handlers while
  parsing. Instead they hold references to them, which are filled after all
  type-handlers are created (see `create-type-handlers`).

  Summary variant: when `create-type-handlers` is called with
  `:include-summary-only true`, it builds a second, summary-only handler for
  every type (keyed under the `summary` namespace, e.g. `:summary/Patient`). A
  summary handler only has property-handlers for elements marked `isSummary` in
  the StructureDefinition; every other property has no handler and is skipped in
  place by `skip-value!` (Jackson's `.skipChildren`), so non-summary subtrees are
  walked past in the token stream but never materialized into FHIR values.
  Because every backbone element gets its own summary handler too, a non-summary
  child of a summary element (e.g. `Observation.component.referenceRange`) is
  skipped as well. `parse-cbor` selects this variant via its `variant` argument;
  the resulting resource is tagged with the SUBSETTED meta tag.

  This namespace uses some advanced optimizations like mutable ArrayLists and
  mutable property maps.
  Please change with care."
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.spec.type.string-util :as su]
   [blaze.fhir.spec.type.system :as system]
   [blaze.fhir.util :as fu]
   [blaze.util :as u :refer [condp-identical str]]
   [clojure.string :as str]
   [cognitect.anomalies :as anom])
  (:import
   [blaze.fhir.spec PropertyMap TypeHandlerRef]
   [blaze.fhir.spec.type
    Address Age Annotation Attachment BundleEntrySearch CodeableConcept
    Coding ContactDetail ContactPoint Contributor Count DataRequirement
    DataRequirement$CodeFilter DataRequirement$DateFilter
    DataRequirement$Sort Distance Dosage Dosage$DoseAndRate Duration
    Expression Extension HumanName Identifier Meta Money
    Narrative ParameterDefinition Period Quantity Range Ratio
    Reference RelatedArtifact SampledData Signature Timing
    Timing$Repeat TriggerDefinition UsageContext]
   [clojure.lang PersistentVector RT]
   [com.fasterxml.jackson.core JsonFactory JsonParseException JsonParser JsonToken StreamReadConstraints]
   [com.fasterxml.jackson.core.exc InputCoercionException]
   [com.fasterxml.jackson.core.io JsonEOFException]
   [com.fasterxml.jackson.core.util JsonParserSequence]
   [com.fasterxml.jackson.databind.util TokenBuffer]
   [com.fasterxml.jackson.dataformat.cbor CBORFactory]
   [java.io InputStream OutputStream Reader]
   [java.util ArrayList HashMap List]))

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
       "Bundle.entry.response.status"
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
      ("Account.implicitRules"
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

  When `summary-only` is true, only elements marked `isSummary` produce a
  definition; non-summary elements return nil so that no property-handler is
  created for them and their value is skipped while parsing (see `skip-value!`).

  An element handler definition contains:
   * field-name - the name of the JSON property
   * key - the key of the internal representation
   * type - a keyword of the FHIR element type
   * cardinality - :single or :many
   * summary - whether the element is marked `isSummary`"
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
  (cond-> {:fhir.issues/code "invariant"
           :fhir.issues/diagnostics msg}
    (seq locator)
    (assoc :fhir.issues/expression (expression locator))))

(defn- unexpected-end-of-input-msg [^JsonEOFException e]
  (condp-identical (.getTokenBeingDecoded e)
    JsonToken/FIELD_NAME "Unexpected end of input while parsing a field name."
    "Unexpected end of input."))

(defn- parse-exception-anom [e locator]
  (let [msg (if (instance? JsonEOFException e)
              (unexpected-end-of-input-msg e)
              "JSON parsing error.")]
    (ba/incorrect msg :fhir/issues [(fhir-issue msg locator)])))

(defn- next-token! [parser locator]
  (try
    (.nextToken ^JsonParser parser)
    (catch JsonParseException e
      (parse-exception-anom e locator))))

(defmacro current-name [parser]
  `(.currentName ~(with-meta parser {:tag `JsonParser})))

(defn- unexpected-end-of-string-anom [locator]
  (let [msg "Unexpected end of input while reading a string value."]
    (ba/incorrect msg :fhir/issues [(fhir-issue msg locator)])))

(defn- get-text
  "Returns the text of the current token of `parser`.

  The three-arity version takes the locator of the parent and the `path` of the
  property separately and creates the locator of the property only on error."
  ([parser locator]
   (try
     (.getText ^JsonParser parser)
     (catch JsonEOFException _
       (unexpected-end-of-string-anom locator))))
  ([parser locator path]
   (try
     (.getText ^JsonParser parser)
     (catch JsonEOFException _
       (unexpected-end-of-string-anom (cons path locator))))))

(defn- input-coercion-anom [e locator]
  (let [msg (first (str/split-lines (ex-message e)))]
    (ba/incorrect msg :fhir/issues [(fhir-issue msg locator)])))

(defn- get-long
  "Returns the long value of the current token of `parser`.

  The three-arity version takes the locator of the parent and the `path` of the
  property separately and creates the locator of the property only on error."
  ([parser locator]
   (try
     (.getLongValue ^JsonParser parser)
     (catch InputCoercionException e
       (input-coercion-anom e locator))))
  ([parser locator path]
   (try
     (.getLongValue ^JsonParser parser)
     (catch InputCoercionException e
       (input-coercion-anom e (cons path locator))))))

(defn- get-decimal [parser]
  (.getDecimalValue ^JsonParser parser))

(defn- get-current-value [parser locator]
  (condp-identical (current-token parser)
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
     (condp-identical token#
       ~@body)))

(defn- create-system-string-handler
  "Returns a property-handler for System.String properties."
  [assoc-fn path expected-type]
  (fn system-string-handler [parser locator m]
    (cond-next-token parser locator
      JsonToken/VALUE_STRING
      (when-ok [value (get-text parser locator path)]
        (assoc-fn m value))
      (incorrect-value-anom parser (cons path locator) expected-type))))

(defn- duplicate-property-anom [field-name locator]
  (let [msg (format "Duplicate property `%s`." field-name)]
    (ba/incorrect msg :fhir/issues [(fhir-issue msg locator)])))

(defn- get-value
  "Gets the value from mutable property `map` at `slot` or returns optional
  `not-found` if the slot is empty."
  ([map ^long slot]
   (.get ^PropertyMap map (unchecked-int slot)))
  ([map ^long slot not-found]
   (.get ^PropertyMap map (unchecked-int slot) not-found)))

(defn- put-value!
  "Puts `value` into mutable property `map` at `slot`. Returns `map`."
  [map ^long slot value]
  (.put ^PropertyMap map (unchecked-int slot) value))

(defn- check-null-elements
  "Returns an anomaly if a list of primitive values marked as possibly
  containing null elements in mutable property `map` still contains a null
  element. Returns `map` otherwise. Uses `keys` indexed by slot for the location
  of errors.

  Nulls in JSON arrays of primitive values are placeholders that have to be
  matched by extended properties. Because the JSON array of values and the
  JSON array of extended properties can come in any order, remaining nulls can
  only be detected at the end of the object. Empty extended properties are
  handled like nulls. For single primitive values, they leave the marked slot
  empty."
  [^objects keys ^PropertyMap map locator]
  (if-some [e (.firstNullElement map)]
    (incorrect-value-anom* "value null"
                           (cond->> (cons (name (aget keys (.slot e))) locator)
                             (<= 0 (.index e)) (cons (.index e)))
                           (.expectedType e))
    map))

(defn- remove-null-elements
  "Removes all null elements of slots marked as possibly containing null
  elements in mutable property `map`. Returns `map`.

  Used instead of `check-null-elements` in internal mode, because resources
  stored by earlier versions can contain such null elements."
  [_keys ^PropertyMap map _locator]
  (.removeNullElements map))

(defn- add-null-placeholder!
  "Adds a null placeholder to the `list` of primitive values of `expected-type`
  at `slot` in mutable property `map` if `index` is at the end of `list`.
  Marks the list as possibly containing null elements in that case, which have
  to be checked with `check-null-elements` or removed with `remove-null-elements`
  at the end of the object. Returns `list`."
  [^PropertyMap map slot expected-type ^List list index]
  (when (= index (.size list))
    (.markNullElements map (int slot) expected-type)
    (.add list nil))
  list)

(defn- mark-null-element!
  "Marks the list of primitive values or the single primitive value of
  `expected-type` at `slot` in mutable property `map` as possibly containing
  null elements, which have to be checked with `check-null-elements` or removed
  with `remove-null-elements` at the end of the object. Returns nil as null
  placeholder."
  [^PropertyMap map slot expected-type]
  (.markNullElements map (int slot) expected-type)
  nil)

(defn- set-value!
  "Sets `value` at `index` in `list`."
  [^List list ^long index value]
  (cond
    (< index (.size list)) (doto list (.set index value))
    (= index (.size list)) (doto list (.add value))))

(defn- primitive-value-assoc
  "Returns a function taking slots `m`, a `value` and a locator that associates
  `value` to `m` at the slot of the property-handler definition.

  In case an extended primitive value exists already, updates that primitive
  value with `value`. Otherwise uses `constructor` to create a new primitive
  value."
  [{:keys [field-name key slot]} constructor]
  (let [slot (long slot)]
    (fn assoc-primitive-value [m value locator]
      (if-some [primitive-value (get-value m slot)]
        (if (some? (:value primitive-value))
          (duplicate-property-anom field-name locator)
          (put-value! m slot (assoc primitive-value :value value)))
        (if-ok [value (constructor value)]
          (put-value! m slot value)
          #(let [msg (::anom/message %)]
             (ba/incorrect msg :fhir/issues [(fhir-issue msg (cons (name key) locator))])))))))

(defn- primitive-many-value-assoc
  "Like `primitive-value-assoc` but with a single value for cardinality many.

  The value is the first element of the list. Other elements, already created
  from extended properties, are kept."
  [{:keys [field-name slot]} constructor]
  (let [slot (long slot)]
    (fn assoc-primitive-many-value [m value locator]
      (let [primitive-list (get-value m slot [])]
        (if-some [primitive-value (first primitive-list)]
          (if (some? (:value primitive-value))
            (duplicate-property-anom field-name locator)
            (put-value! m slot (assoc primitive-list 0 (assoc primitive-value :value value))))
          (put-value! m slot (assoc primitive-list 0 (constructor value))))))))

(defn- primitive-boolean-value-handler
  "Returns a property-handler for boolean properties."
  [{:keys [key] :as def}]
  (let [assoc-value (primitive-value-assoc def type/boolean)]
    (fn [parser locator m]
      (cond-next-token parser locator
        JsonToken/VALUE_TRUE (assoc-value m true locator)
        JsonToken/VALUE_FALSE (assoc-value m false locator)
        (incorrect-value-anom parser (cons (name key) locator) "boolean")))))

(defn- primitive-value-handler
  "Returns a property-handler for the value part of primitive properties.

  In the one-token version, `extract-value` takes a parser, the locator of the
  parent and the `path` of the property and returns either the value or an
  anomaly. In the two-token version, `extract-value` takes only a parser and
  can't fail."
  {:arglists
   '([property-handler-definition constructor token extract-value expected-type]
     [property-handler-definition constructor token-1 token-2 extract-value
      expected-type])}
  ([{:keys [field-name key slot cardinality] :as def} constructor token
    extract-value expected-type]
   (let [path (name key)
         slot (long slot)
         assoc-value (primitive-value-assoc def constructor)
         assoc-many-value (primitive-many-value-assoc def constructor)]
     (if (= :single cardinality)
       (fn primitive-property-handler-one-token-cardinality-single [parser locator m]
         (cond-next-token parser locator
           token
           (when-ok [value (extract-value parser locator path)]
             (assoc-value m value locator))
           (incorrect-value-anom parser (cons path locator) expected-type)))
       (fn primitive-property-handler-one-token-cardinality-many [parser locator m]
         (cond-next-token parser locator
           JsonToken/START_ARRAY
           (loop [l (ArrayList. ^List (get-value m slot [])) i 0]
             (when-ok [t (next-token! parser locator)]
               (condp-identical t
                 token
                 (when-ok [value (extract-value parser locator path)]
                   (if-some [primitive-value (when (< i (.size l)) (.get l i))]
                     (if (some? (:value primitive-value))
                       (duplicate-property-anom field-name locator)
                       (recur (doto l (.set i (assoc primitive-value :value value))) (inc i)))
                     (recur (set-value! l i (constructor value)) (inc i))))
                 JsonToken/END_ARRAY (put-value! m slot (PersistentVector/create ^List l))
                 JsonToken/VALUE_NULL
                 (recur (add-null-placeholder! m slot expected-type l i) (inc i))
                 (incorrect-value-anom parser (cons path locator) (str expected-type "[]")))))
           token
           (when-ok [value (extract-value parser locator path)]
             (assoc-many-value m value locator))
           (incorrect-value-anom parser (cons path locator) (str expected-type "[]")))))))
  ([{:keys [field-name key slot cardinality] :as def} constructor token-1
    token-2 extract-value expected-type]
   (let [path (name key)
         slot (long slot)
         assoc-value (primitive-value-assoc def constructor)
         assoc-many-value (primitive-many-value-assoc def constructor)]
     (if (= :single cardinality)
       (fn primitive-property-handler-two-tokens-cardinality-single [parser locator m]
         (cond-next-token parser locator
           token-1
           (assoc-value m (extract-value parser) locator)
           token-2
           (assoc-value m (extract-value parser) locator)
           (incorrect-value-anom parser (cons path locator) expected-type)))
       (fn primitive-property-handler-two-tokens-cardinality-many [parser locator m]
         (cond-next-token parser locator
           JsonToken/START_ARRAY
           (loop [l (ArrayList. ^List (get-value m slot [])) i 0]
             (when-ok [t (next-token! parser locator)]
               (condp-identical t
                 token-1
                 (let [value (extract-value parser)]
                   (if-some [primitive-value (when (< i (.size l)) (.get l i))]
                     (if (some? (:value primitive-value))
                       (duplicate-property-anom field-name locator)
                       (recur (doto l (.set i (assoc primitive-value :value value))) (inc i)))
                     (recur (set-value! l i (constructor value)) (inc i))))
                 token-2
                 (let [value (extract-value parser)]
                   (if-some [primitive-value (when (< i (.size l)) (.get l i))]
                     (if (some? (:value primitive-value))
                       (duplicate-property-anom field-name locator)
                       (recur (doto l (.set i (assoc primitive-value :value value))) (inc i)))
                     (recur (set-value! l i (constructor value)) (inc i))))
                 JsonToken/END_ARRAY (put-value! m slot (PersistentVector/create ^List l))
                 JsonToken/VALUE_NULL
                 (recur (add-null-placeholder! m slot expected-type l i) (inc i))
                 (incorrect-value-anom parser (cons path locator) expected-type))))
           token-1
           (assoc-many-value m (extract-value parser) locator)
           token-2
           (assoc-many-value m (extract-value parser) locator)
           (incorrect-value-anom parser (cons path locator) expected-type)))))))

(defmacro recur-ok [expr-form]
  `(when-ok [r# ~expr-form]
     (recur r#)))

(def ^:private primitive-id-handler
  "A property-handler for id properties."
  (create-system-string-handler #(assoc %1 :id %2) "id" "string"))

(defn- type-handler-ref
  "Returns the reference to the type-handler under `type-key` from the volatile
  map `refs`, creating the reference if it doesn't exist.

  The reference is linked by `link-type-handlers!` after all type-handlers are
  created. That way type-handlers can reference each other, even recursively."
  [refs type-key]
  (or (@refs type-key)
      ((vswap! refs assoc type-key (TypeHandlerRef.)) type-key)))

(defn- parse-complex-list [handler parser locator]
  (loop [list (ArrayList.)]
    (cond-next-token parser locator
      JsonToken/START_OBJECT
      (when-ok [value (handler parser (cons (.size list) locator))]
        (recur (doto list (.add value))))
      JsonToken/END_ARRAY (PersistentVector/create ^List list)
      (incorrect-value-anom parser (cons (.size list) locator) (handler)))))

(defn- unsupported-type-anom [type]
  (ba/unsupported (format "Unsupported type `%s`." type)))

(defn- parse-extended-primitive-properties
  [^TypeHandlerRef extension-handler-ref parser locator data]
  (loop [data data]
    (cond-next-token parser locator
      JsonToken/FIELD_NAME
      (condp = (current-name parser)
        "id" (recur-ok (primitive-id-handler parser locator data))
        "extension"
        (when-some [extension-handler (.get extension-handler-ref)]
          (cond-next-token parser locator
            JsonToken/START_ARRAY
            (when-ok [list (parse-complex-list extension-handler parser (cons "extension" locator))]
              (recur (cond-> data (seq list) (assoc :extension list))))
            JsonToken/START_OBJECT
            (when-ok [extension (extension-handler parser (cons 0 (cons "extension" locator)))]
              (recur (assoc data :extension [extension])))
            (incorrect-value-anom parser (cons "extension" locator) "Extension[]")))
        (unknown-property-anom locator (current-name parser)))
      JsonToken/END_OBJECT data)))

(defn- trim-trailing-nils
  "Removes the trailing nils of `list` with an index of at least `start`."
  [^List list start]
  (loop [i (.size list)]
    (if (and (< start i) (nil? (.get list (dec i))))
      (recur (dec i))
      (.subList list 0 i))))

(defn- extended-primitive-handler
  "Returns a property-handler."
  [{:keys [key slot cardinality extension-handler-ref]} constructor expected-type]
  (let [path (name key)
        slot (long slot)]
    (if (= :single cardinality)
      (fn [parser locator m]
        (cond-next-token parser locator
          JsonToken/START_OBJECT
          (if-some [primitive-value (get-value m slot)]
            (when-ok [primitive-value (parse-extended-primitive-properties extension-handler-ref parser (cons path locator) primitive-value)]
              (put-value! m slot primitive-value))
            (when-ok [data (parse-extended-primitive-properties extension-handler-ref parser (cons path locator) {})]
              (if (empty? data)
                (doto m (mark-null-element! slot expected-type))
                (put-value! m slot (constructor data)))))
          (incorrect-value-anom parser (cons path locator) "primitive extension map")))
      (fn [parser locator m]
        (cond-next-token parser locator
          JsonToken/START_ARRAY
          (let [^List values (get-value m slot [])
                ;; only nils added here are trimmed, because nils of values
                ;; aren't replaced by extended properties
                num-values (.size values)]
            (loop [l (ArrayList. values) i 0]
              (when-ok [t (next-token! parser (cons path locator))]
                (condp-identical t
                  JsonToken/START_OBJECT
                  (if-some [primitive-value (when (< i (.size l)) (.get l i))]
                    (when-ok [primitive-value (parse-extended-primitive-properties extension-handler-ref parser (cons path locator) primitive-value)]
                      (recur (doto l (.set i primitive-value)) (inc i)))
                    (when-ok [data (parse-extended-primitive-properties extension-handler-ref parser (cons path locator) {})]
                      (recur (set-value! l i (if (empty? data) (mark-null-element! m slot expected-type) (constructor data))) (inc i))))
                  JsonToken/END_ARRAY (put-value! m slot (PersistentVector/create ^List (trim-trailing-nils l num-values)))
                  JsonToken/VALUE_NULL
                  (recur (add-null-placeholder! m slot expected-type l i) (inc i))
                  (incorrect-value-anom parser (cons path locator) "primitive extension map")))))
          JsonToken/START_OBJECT
          (let [primitive-list (get-value m slot [])]
            (if-some [primitive-value (first primitive-list)]
              (when-ok [primitive-value (parse-extended-primitive-properties extension-handler-ref parser (cons path locator) primitive-value)]
                (put-value! m slot (assoc primitive-list 0 primitive-value)))
              (when-ok [data (parse-extended-primitive-properties extension-handler-ref parser (cons path locator) {})]
                (put-value! m slot (assoc primitive-list 0 (if (empty? data) (mark-null-element! m slot expected-type) (constructor data)))))))
          JsonToken/VALUE_NULL m
          (incorrect-value-anom parser (cons path locator) "primitive extension map"))))))

(defn- primitive-handler
  "Returns a map of two property-handlers, one for the field-name of
  `property-handler-definition` and one for _field-name for handling extended
  primitive data."
  {:arglists '([property-handler-definition constructor expected-type value-handler])}
  [{:keys [field-name] :as def} constructor expected-type value-handler]
  {field-name value-handler
   (str "_" field-name) (extended-primitive-handler def constructor expected-type)})

(defn- primitive-integer-handler
  "Returns a property-handler for integer properties."
  [def constructor]
  (->> (primitive-value-handler def constructor JsonToken/VALUE_NUMBER_INT
                                get-long "integer")
       (primitive-handler def constructor "integer")))

(defn- primitive-decimal-handler
  "A handler that reads an integer or decimal value and creates the internal
  representation using `constructor`."
  [def]
  (->> (primitive-value-handler def type/decimal JsonToken/VALUE_NUMBER_INT
                                JsonToken/VALUE_NUMBER_FLOAT get-decimal "decimal")
       (primitive-handler def type/decimal "decimal")))

(defn- pattern-mismatch-anom [locator expected-type pattern text]
  (incorrect-value-anom* (format "value `%s`" text) locator (format "%s, regex %s" expected-type pattern)))

(defn- pattern-check-string
  "Returns a check-string function that returns `text` if it matches `pattern`
  or an anomaly otherwise."
  [pattern]
  (fn [locator path expected-type text]
    (if (.matches (re-matcher pattern text))
      text
      (pattern-mismatch-anom (cons path locator) expected-type pattern text))))

(defn- check-uri
  "A check-string function for uri, url and canonical values."
  [locator path expected-type text]
  (if (su/visible? text)
    text
    (pattern-mismatch-anom (cons path locator) expected-type su/visible-pattern text)))

(def ^:private check-base64
  (pattern-check-string #"([0-9a-zA-Z+/=]{4})+"))

(def ^:private check-instant
  (pattern-check-string #"([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1])T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\.[0-9]+)?(Z|(\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00))"))

(def ^:private check-oid
  (pattern-check-string #"urn:oid:[0-2](\.(0|[1-9][0-9]*))+"))

(def ^:private check-id
  (pattern-check-string #"[A-Za-z0-9\-\.]{1,64}"))

(def ^:private check-uuid
  (pattern-check-string #"urn:uuid:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))

(defn- parse-text [system-parser locator path expected-type text]
  (if-ok [value (system-parser text)]
    value
    #(incorrect-value-anom* (format "value `%s`" text) (cons path locator) expected-type (::anom/message %))))

(defn- system-value-parser
  "Returns a function taking a parser, the locator of the parent and the `path`
  of the property, which reads the text of the current token and parses it
  using `system-parser`.

  The locator of the property is only created on error."
  ([system-parser expected-type]
   (fn system-value-parser [parser locator path]
     (when-ok [text (get-text parser locator path)]
       (parse-text system-parser locator path expected-type text))))
  ([system-parser expected-type check-string]
   (fn checked-system-value-parser [parser locator path]
     (when-ok [text (get-text parser locator path)
               text (check-string locator path expected-type text)]
       (parse-text system-parser locator path expected-type text)))))

(defn- primitive-string-handler
  "A handler that reads a string value and creates the internal representation
  using `constructor`, `system-parser` and optional `check-string`.

  The system parser has to be a function from string to system value or anomaly.

  The optional check-string function is called before the system parser. It
  takes the locator of the parent, the path of the property, the expected type
  and the string value and has to return either the string value or an
  anomaly."
  ([def constructor system-parser expected-type]
   (->> (primitive-value-handler
         def constructor JsonToken/VALUE_STRING
         (system-value-parser system-parser expected-type)
         expected-type)
        (primitive-handler def constructor expected-type)))
  ([def constructor system-parser expected-type check-string]
   (->> (primitive-value-handler
         def constructor JsonToken/VALUE_STRING
         (system-value-parser system-parser expected-type check-string)
         expected-type)
        (primitive-handler def constructor expected-type))))

(defn- create-complex-property-handler
  "Returns a map of a single JSON property name to a property-handler that
  delegates handling of the property value to the type-handler of the complex
  type of `property-handler-definition`."
  {:arglists '([opts property-handler-definition])}
  [{:keys [summary-only type-handler-ref]} {:keys [field-name key slot type cardinality]}]
  {field-name
   (let [type-name (if (= "backboneElement" (namespace type))
                     "BackboneElement"
                     (name type))
         type-key (if summary-only (keyword "summary" (name type)) (keyword (name type)))
         ^TypeHandlerRef handler-ref (type-handler-ref type-key)
         path (name key)
         slot (long slot)]
     (if (= :single cardinality)
       (fn complex-property-handler-cardinality-single [parser locator m]
         (if-some [handler (.get handler-ref)]
           (cond-next-token parser locator
             JsonToken/START_OBJECT
             (when-ok [value (handler parser (cons path locator))]
               (put-value! m slot value))
             (incorrect-value-anom parser (cons path locator) type-name))
           (unsupported-type-anom (name type))))
       (fn complex-property-handler-cardinality-many [parser locator m]
         (if-some [handler (.get handler-ref)]
           (cond-next-token parser locator
             JsonToken/START_ARRAY
             (when-ok [list (parse-complex-list handler parser (cons path locator))]
               (put-value! m slot list))
             JsonToken/START_OBJECT
             (when-ok [value (handler parser (cons 0 (cons path locator)))]
               (put-value! m slot [value]))
             (incorrect-value-anom parser (cons path locator) type-name))
           (unsupported-type-anom (name type))))))})

(defn- create-property-handlers*
  "Returns a map of JSON property names to handlers."
  {:arglists '([opts property-handler-definition])}
  [{:keys [mode] :as opts} {:keys [field-name key slot type] :as def}]
  (let [checking-primitive-string-handler
        (if (= :internal mode)
          (fn [def constructor system-parser expected-type _]
            (primitive-string-handler def constructor system-parser expected-type))
          primitive-string-handler)]
    (condp = type
      :system/string
      (let [slot (long slot)]
        {field-name (create-system-string-handler #(put-value! %1 slot %2) (name key) "string")})

      :primitive/boolean
      (primitive-handler def type/boolean "boolean" (primitive-boolean-value-handler def))

      :primitive/integer
      (primitive-integer-handler def type/integer)

      :primitive/string
      (primitive-string-handler def type/string identity "string")

      :primitive/string-interned
      (primitive-string-handler def type/string-interned identity "string")

      :primitive/decimal
      (primitive-decimal-handler def)

      :primitive/uri
      (checking-primitive-string-handler def type/uri identity "uri" check-uri)

      :primitive/uri-interned
      (checking-primitive-string-handler def type/uri-interned identity "uri"
                                         check-uri)

      :primitive/url
      (checking-primitive-string-handler def type/url identity "url" check-uri)

      :primitive/canonical
      (checking-primitive-string-handler def type/canonical identity "canonical"
                                         check-uri)

      :primitive/base64Binary
      (checking-primitive-string-handler def type/base64Binary identity
                                         "base64Binary" check-base64)

      :primitive/instant
      (checking-primitive-string-handler def type/instant system/parse-date-time
                                         "instant" check-instant)

      :primitive/date
      (primitive-string-handler def type/date system/parse-date "date")

      :primitive/dateTime
      (primitive-string-handler def type/dateTime system/parse-date-time
                                "date-time")

      :primitive/time
      (primitive-string-handler def type/time system/parse-time "time")

      :primitive/code
      (primitive-string-handler def type/code identity "code")

      :primitive/oid
      (checking-primitive-string-handler def type/oid identity "oid" check-oid)

      :primitive/id
      (checking-primitive-string-handler def type/id identity "id" check-id)

      :primitive/markdown
      (primitive-string-handler def type/markdown identity "markdown")

      :primitive/unsignedInt
      (primitive-integer-handler def type/unsignedInt)

      :primitive/positiveInt
      (primitive-integer-handler def type/positiveInt)

      :primitive/uuid
      (checking-primitive-string-handler def type/uuid identity "uuid" check-uuid)

      :primitive/xhtml
      (primitive-string-handler def type/xhtml identity "xhtml")

      (if (#{"complex" "element" "backboneElement"} (namespace type))
        (create-complex-property-handler opts def)
        (unsupported-type-anom (name type))))))

(defn- assoc-slots
  "Associates the index of its key in `keys` as :slot to each of the
  property-handler `definitions`."
  [keys definitions]
  (let [slots (zipmap keys (range))]
    (map #(assoc % :slot (slots (:key %))) definitions)))

(defn- create-property-handlers
  "Returns a function from JSON property names to property handlers."
  [{:keys [type-handler-ref] :as opts} definitions]
  (transduce
   ;; extended primitive properties can have extensions
   (map #(assoc % :extension-handler-ref (type-handler-ref :Extension)))
   (fn
     ([m]
      (if (ba/anomaly? m)
        m
        ;; Jackson interns field names, so interning the names here lets the
        ;; lookup hit on identity with the cached String hash
        (let [handlers (HashMap/newHashMap (count m))]
          (run! (fn [[field-name handler]] (.put handlers (.intern ^String field-name) handler)) m)
          (fn find-property-handler [field-name]
            (.get handlers field-name)))))
     ([handlers property-handler-definition]
      (if-ok [handler (create-property-handlers* opts property-handler-definition)]
        (into handlers handler)
        reduced)))
   {}
   definitions))

(defn- fhir-type-keyword [type]
  (let [parts (cons "fhir" (seq (str/split type #"\.")))]
    (keyword (str/join "." (butlast parts)) (last parts))))

(defmacro ^:private slot-constructor
  "Expands to a tuple of the keys of all fields of `class` in slot order and a
  function that constructs a value of `class` from the slots of a mutable
  property map."
  [class]
  (let [map (with-meta (gensym "map") {:tag `PropertyMap})]
    `[(. ~class ~'fields) (fn [~map] (. ~class ~'fromSlots (.slots ~map)))]))

(defn- complex-type-constructor
  "Returns a tuple of the keys of all fields of the complex `type` in slot order
  and a function that constructs a value of `type` from a mutable property map
  or nil if `type` has no constructor."
  [type]
  (condp = type
    "Address" (slot-constructor Address)
    "Age" (slot-constructor Age)
    "Annotation" (slot-constructor Annotation)
    "Attachment" (slot-constructor Attachment)
    "Bundle.entry.search" (slot-constructor BundleEntrySearch)
    "CodeableConcept" (slot-constructor CodeableConcept)
    "Coding" (slot-constructor Coding)
    "ContactDetail" (slot-constructor ContactDetail)
    "ContactPoint" (slot-constructor ContactPoint)
    "Contributor" (slot-constructor Contributor)
    "Count" (slot-constructor Count)
    "DataRequirement" (slot-constructor DataRequirement)
    "DataRequirement.codeFilter" (slot-constructor DataRequirement$CodeFilter)
    "DataRequirement.dateFilter" (slot-constructor DataRequirement$DateFilter)
    "DataRequirement.sort" (slot-constructor DataRequirement$Sort)
    "Distance" (slot-constructor Distance)
    "Dosage" (slot-constructor Dosage)
    "Dosage.doseAndRate" (slot-constructor Dosage$DoseAndRate)
    "Duration" (slot-constructor Duration)
    "Expression" (slot-constructor Expression)
    "Extension" (slot-constructor Extension)
    "HumanName" (slot-constructor HumanName)
    "Identifier" (slot-constructor Identifier)
    "Meta" (slot-constructor Meta)
    "Money" (slot-constructor Money)
    "Narrative" (slot-constructor Narrative)
    "ParameterDefinition" (slot-constructor ParameterDefinition)
    "Period" (slot-constructor Period)
    "Quantity" (slot-constructor Quantity)
    "Range" (slot-constructor Range)
    "Ratio" (slot-constructor Ratio)
    "Reference" (slot-constructor Reference)
    "RelatedArtifact" (slot-constructor RelatedArtifact)
    "SampledData" (slot-constructor SampledData)
    "Signature" (slot-constructor Signature)
    "Timing" (slot-constructor Timing)
    "Timing.repeat" (slot-constructor Timing$Repeat)
    "TriggerDefinition" (slot-constructor TriggerDefinition)
    "UsageContext" (slot-constructor UsageContext)
    nil))

(def ^:private update-meta
  (fnil update #fhir/Meta{}))

(defn- incorrect-type-anom [locator type expected-type]
  (let [msg (format "Incorrect resource type `%s`. Expected type is `%s`." type expected-type)]
    (ba/incorrect msg :fhir/issues [(fhir-issue msg locator)])))

(defn- skip-value!
  "Reads and discards the value of the current property without materializing it.

  Advances the parser past the next value token: for an object or array the
  whole subtree is skipped via Jackson's `.skipChildren`; a scalar token is
  simply consumed. Used both for unknown properties (when
  `fail-on-unknown-property` is false) and for non-summary properties when
  parsing the summary variant, where no property-handler exists for the field."
  [parser locator]
  (cond-next-token parser locator
    JsonToken/START_OBJECT
    (.skipChildren ^JsonParser parser)
    JsonToken/START_ARRAY
    (.skipChildren ^JsonParser parser)
    nil))

(defn- read-resource-type
  "Reads the value of the `resourceType` property. Expects that the field name
  is already read."
  [parser locator]
  (cond-next-token parser locator
    JsonToken/VALUE_STRING (get-text parser locator)
    (incorrect-value-anom parser (cons "resourceType" locator) "string")))

(defn- append-subsetted [resource]
  (update resource :meta update-meta :tag u/conj-vec fu/subsetted))

(defn- check-keys
  "Returns an anomaly if a key of the property-handler `definitions` isn't one
  of `keys`."
  [type keys definitions]
  (let [keys (set keys)]
    (when-some [key (some #(when-not (keys (:key %)) (:key %)) definitions)]
      (ba/fault (format "The complex type `%s` has no field `%s`." type (name key))))))

(defn- create-type-handler
  "Creates a handler for `type` using `element-definitions`.

  The element definitions must not contain nested backbone element definitions.
  Use the `separate-element-definitions` function to separate nested backbone
  element definitions.
  
  A type-handler reads a JSON object. It expects that the `START_OBJECT` token
  is already read and will try to read a `FIELD_NAME` or `END_OBJECT` token. It
  either returns a value of `type` or an anomaly in case of errors.

  With `:summary-only true` in `opts`, only property-handlers for `isSummary`
  elements are created; any other property encountered while parsing has no
  handler and is skipped via `skip-value!` without being materialized, and the
  returned value carries the SUBSETTED meta tag."
  [kind type element-definitions {:keys [fail-on-unknown-property summary-only mode] :as opts}]
  (let [handle-null-elements (if (= :internal mode) remove-null-elements check-null-elements)
        definitions (into [] (mapcat (partial property-handler-definitions type summary-only)) element-definitions)
        [constructor-keys construct] (when (= :complex-type kind) (complex-type-constructor type))
        ^objects keys (or constructor-keys (object-array (distinct (map :key definitions))))
        num-slots (alength keys)]
    (when-ok [_ (check-keys type keys definitions)
              property-handlers (create-property-handlers opts (assoc-slots keys definitions))]
      (condp = kind
        :resource
        (let [fhir-type-kw (keyword "fhir" type)
              finalize (cond->> #(.toPersistentMap ^PropertyMap % keys fhir-type-kw)
                         summary-only (comp append-subsetted))]
          (fn resource-handler
            ([] type)
            ([parser locator]
             (loop [resource (PropertyMap. num-slots)]
               (cond-next-token parser locator
                 JsonToken/FIELD_NAME
                 (let [field-name (current-name parser)]
                   (if-some [handler (property-handlers field-name)]
                     (recur-ok (handler parser locator resource))
                     (if (= "resourceType" field-name)
                       (when-ok [s (read-resource-type parser locator)]
                         (if (= type s)
                           (recur resource)
                           (incorrect-type-anom locator s type)))
                       (if fail-on-unknown-property
                         (unknown-property-anom locator field-name)
                         (do (skip-value! parser locator) (recur resource))))))
                 JsonToken/END_OBJECT
                 (when-ok [resource (handle-null-elements keys resource locator)]
                   (finalize resource)))))))
        :complex-type
        (let [finalize (or construct
                           (let [fhir-type-kw (fhir-type-keyword type)]
                             #(.toPersistentMap ^PropertyMap % keys fhir-type-kw)))]
          (fn complex-type-handler
            ([] type)
            ([parser locator]
             (loop [value (PropertyMap. num-slots)]
               (cond-next-token parser locator
                 JsonToken/FIELD_NAME
                 (let [field-name (current-name parser)]
                   (if-some [handler (property-handlers field-name)]
                     (recur-ok (handler parser locator value))
                     (if fail-on-unknown-property
                       (unknown-property-anom locator field-name)
                       (do (skip-value! parser locator) (recur value)))))
                 JsonToken/END_OBJECT
                 (when-ok [value (handle-null-elements keys value locator)]
                   (finalize value)))))))))))

(defn- create-type-handlers*
  "Creates a map of keyword type names to unlinked type-handlers from the
  snapshot `element-definitions` of a StructureDefinition resource.

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

(defn- missing-resource-type-anom [locator]
  (let [msg "Missing property `resourceType`."]
    (ba/incorrect msg :fhir/issues [(fhir-issue msg locator)])))

(defn- copy-property!
  "Copies the current property, field name and value, of `parser` into
  `buffer`.

  Floating-point numbers are copied as BigDecimal, so that they are read with
  the same value as from `parser` itself. The TokenBuffer would keep CBOR float
  values as Float, which it reads back through double."
  [^TokenBuffer buffer ^JsonParser parser locator]
  (try
    (.copyCurrentEvent buffer parser)
    (loop [depth 0]
      (let [token (.nextToken parser)]
        (if (identical? JsonToken/VALUE_NUMBER_FLOAT token)
          (.writeNumber buffer (.getDecimalValue parser))
          (.copyCurrentEvent buffer parser))
        (let [depth (if (.isStructStart token)
                      (inc depth)
                      (if (.isStructEnd token) (dec depth) depth))]
          (when (pos? depth)
            (recur depth)))))
    (catch JsonParseException e
      (parse-exception-anom e locator))))

(defn- resource-type-and-parser
  "Returns a tuple of the resource type and a parser positioned before the
  first property of the resource other than `resourceType`.

  Expects that the `START_OBJECT` token is already read. If `resourceType` is
  the first property, `parser` itself is returned. Otherwise the preceding
  properties are copied into a TokenBuffer which is replayed before the
  remaining properties of `parser`."
  [^JsonParser parser locator]
  (loop [^TokenBuffer buffer nil]
    (cond-next-token parser locator
      JsonToken/FIELD_NAME
      (if (= "resourceType" (current-name parser))
        (when-ok [type (read-resource-type parser locator)]
          [type (if buffer
                  (JsonParserSequence/createFlattened
                   false (.asParser buffer parser) parser)
                  parser)])
        (let [buffer (or buffer (TokenBuffer. parser))]
          (when-ok [_ (copy-property! buffer parser locator)]
            (recur buffer))))
      (missing-resource-type-anom locator))))

(defn- resource-handler
  "Returns a special type-handler that works for all resources. It first reads
  the `resourceType` property and delegates the handling to the corresponding
  type-handler of `resource-handlers`, a map of resource type names to
  type-handlers."
  [resource-handlers]
  (fn
    ([] "Resource")
    ([parser locator]
     (when-ok [[type parser] (resource-type-and-parser parser locator)]
       (if-let [type-handler (get resource-handlers type)]
         (type-handler parser (if (empty? locator) (RT/list type) locator))
         (unsupported-type-anom type))))))

(defn- resource-handlers
  "Returns a map of resource type names to the type-handlers of `type-handlers`
  of all resources in `structure-definitions`."
  [structure-definitions type-handlers]
  (into
   {}
   (keep
    (fn [{:keys [kind type]}]
      (when (= "resource" kind)
        [type (get type-handlers (keyword type))])))
   structure-definitions))

(defn- link-type-handlers!
  "Links all references in `refs` to the type-handlers of `type-handlers`.

  References to types without a type-handler stay unlinked."
  [refs type-handlers]
  (run!
   (fn [[type-key ref]]
     (when-some [handler (get type-handlers type-key)]
       (.link ^TypeHandlerRef ref handler)))
   refs))

(defn create-type-handlers
  "Creates a map of keyword type names to type-handlers from the snapshots of
  `structure-definitions`. Additionally contains the special type-handler
  `:Resource` which works for all resources.

  The type-handlers are linked to each other on creation. So replacing a
  type-handler in the returned map doesn't affect the other type-handlers.

  With `:include-summary-only true` in `opts`, each type additionally gets a
  summary-only handler keyed under the `summary` namespace (e.g. both `:Patient`
  and `:summary/Patient`), so callers can parse either the full or the summary
  projection of a resource (see `parse-cbor`).

  With `:mode :internal` in `opts`, the type-handlers are meant for parsing data
  stored by Blaze itself. Such data isn't checked against the regexes of
  primitive types and null elements remaining in arrays of primitive values are
  removed instead of being reported as errors. With `:mode :external`, which is
  the default, data from clients is parsed with full checks.

  Returns an anomaly in case of errors."
  [structure-definitions opts]
  (let [refs (volatile! {})
        opts (assoc opts :type-handler-ref (partial type-handler-ref refs))]
    (when-ok [type-handlers
              (reduce
               (fn [res {:keys [kind] {elements :element} :snapshot}]
                 (if-ok [handlers (create-type-handlers* (keyword kind) elements opts)]
                   (into res handlers)
                   reduced))
               {}
               structure-definitions)]
      (let [resource-handlers (resource-handlers structure-definitions type-handlers)
            resource-handler (resource-handler resource-handlers)
            type-handlers (assoc type-handlers :Resource resource-handler)]
        (link-type-handlers! @refs type-handlers)
        type-handlers))))

(defn- read-value* [parser locator handler]
  (cond-next-token parser locator
    JsonToken/START_OBJECT
    (when-ok [type (handler parser locator)
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
  [parser locator handler]
  (-> (read-value* parser locator handler)
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
  (-> (JsonFactory/builder)
      (.streamReadConstraints stream-read-constraints)
      (.build)))

(defn parse-json
  "Parses a complex value from JSON `source`.

  For resources, the two-arity version can be used. In this case the
  `resourceType` JSON property is used to determine the `type`.
  
  For complex types, the `type` has to be given.
  
  Returns an anomaly in case of errors."
  ([type-handlers source]
   (with-open [parser (-create-parser source json-factory)]
     (read-value parser nil (:Resource type-handlers))))
  ([type-handlers type source]
   (if-some [handler (get type-handlers (keyword type))]
     (with-open [parser (-create-parser source json-factory)]
       (read-value parser (RT/list type) handler))
     (unsupported-type-anom type))))

(defn write-json [type-handlers out value]
  (if-some [type (:fhir/type value)]
    (if-some [handler (get type-handlers type)]
      (with-open [gen (.createGenerator json-factory ^OutputStream out)]
        (handler type-handlers gen value))
      (unsupported-type-anom (name type)))
    (ba/incorrect "Missing type.")))

(def ^:private ^JsonFactory cbor-factory
  (-> (CBORFactory/builder)
      (.streamReadConstraints stream-read-constraints)
      (.build)))

(defn parse-cbor
  "Parses a complex value of `type` and `variant` from CBOR `source`.

  `variant` is `:summary` to parse only the summary elements (using the
  `summary`-namespaced handler built with `:include-summary-only`; non-summary
  elements are skipped in the token stream without being materialized) or any
  other value (e.g. `:complete`) to parse the full value.

  Returns an anomaly in case of errors."
  [type-handlers type variant source]
  (if-some [handler (get type-handlers (if (= :summary variant) (keyword "summary" type) (keyword type)))]
    (with-open [parser (.createParser ^JsonFactory cbor-factory ^bytes source)]
      (read-value parser (RT/list type) handler))
    (unsupported-type-anom type)))

(defn write-cbor [type-handlers out value]
  (if-some [type (:fhir/type value)]
    (if-some [handler (get type-handlers type)]
      (with-open [gen (.createGenerator cbor-factory ^OutputStream out)]
        (handler type-handlers gen value))
      (unsupported-type-anom (name type)))
    (ba/incorrect "Missing type.")))
