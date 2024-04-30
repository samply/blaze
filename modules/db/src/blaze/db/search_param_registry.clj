(ns blaze.db.search-param-registry
  (:refer-clojure :exclude [get])
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.coll.core :as coll]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param :as search-param]
   [blaze.db.impl.search-param.core :as sc]
   [blaze.db.kv.spec]
   [blaze.db.search-param-registry.spec]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.module :as m]
   [blaze.util :refer [conj-vec]]
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [jsonista.core :as j]
   [taoensso.timbre :as log]))

(defn get
  "Returns the search parameter with `code` and `type` or nil if not found."
  [search-param-registry code type]
  (p/-get search-param-registry code type))

(defn get-by-url
  "Returns the search parameter with `url` or nil if not found."
  [search-param-registry url]
  (p/-get-by-url search-param-registry url))

(defn all-types
  "Returns a set of all types with search parameters."
  [search-param-registry]
  (p/-all-types search-param-registry))

(defn list-by-type
  "Returns a seq of search params of `type` in no particular order."
  [search-param-registry type]
  (p/-list-by-type search-param-registry type))

(defn list-by-target-type
  "Returns a seq of search params of type reference which point to
  `target-type`."
  [search-param-registry target-type]
  (p/-list-by-target search-param-registry target-type))

(defn linked-compartments
  "Returns a list of compartments linked to `resource`.

  For example an Observation may be linked to the compartment `[\"Patient\" \"0\"]`
  because its subject points to this patient. Compartments are represented
  through a tuple of `code` and `id`."
  [search-param-registry resource]
  (p/-linked-compartments search-param-registry resource))

(defn compartment-resources
  "Returns a seq of `[type codes]` tuples of resources in compartment of
  `compartment-type` or a list of codes if the optional `type` is given.

  Example:
  * `[\"Observation\" [\"subject\" \"performer\"]]` and others for \"Patient\"
  * `[\"subject\"]` and others for \"Patient\" and \"Observation\""
  ([search-param-registry compartment-type]
   (p/-compartment-resources search-param-registry compartment-type))
  ([search-param-registry compartment-type type]
   (p/-compartment-resources search-param-registry compartment-type type)))

(defn tb
  "Returns the type byte of `type`. The type byte is a one-byte integer
  identifier of each resource type starting with 1."
  [search-param-registry type]
  (p/-tb search-param-registry type))

(deftype MemSearchParamRegistry [url-index index target-index compartment-index
                                 compartment-resource-index
                                 compartment-resource-index-by-type
                                 type-byte-index]
  p/SearchParamRegistry
  (-get [_ code type]
    (or (get-in index [type code])
        (get-in index ["Resource" code])))

  (-get-by-url [_ url]
    (url-index url))

  (-all-types [_]
    (disj (set (keys index)) "Resource"))

  (-list-by-type [_ type]
    (-> (into [] (map val) (index "Resource"))
        (into (map val) (index type))))

  (-list-by-target [_ target]
    (target-index target))

  (-linked-compartments [_ resource]
    (transduce
     (comp
      (map
       (fn [{:keys [def-code search-param]}]
         (when-ok [compartment-ids (search-param/compartment-ids search-param resource)]
           (coll/eduction
            (map (fn [id] [def-code id]))
            compartment-ids))))
      (halt-when ba/anomaly?)
      cat)
     conj
     #{}
     (compartment-index (name (fhir-spec/fhir-type resource)))))

  (-compartment-resources [_ compartment-type]
    (compartment-resource-index compartment-type []))

  (-compartment-resources [_ compartment-type type]
    (get-in compartment-resource-index-by-type [compartment-type type] []))

  (-tb [_ type]
    (type-byte-index type)))

(def ^:private object-mapper
  (j/object-mapper
   {:decode-key-fn true}))

(defn- read-json-resource [x]
  (with-open [rdr (io/reader x)]
    (j/read-value rdr object-mapper)))

(defn- read-classpath-json-resource
  "Reads the JSON encoded resource with `name` from classpath."
  [name]
  (log/trace (format "Read resource `%s` from class path." name))
  (read-json-resource (io/resource name)))

(defn- read-file-json-resource
  "Reads the JSON encoded resource with `name` from filesystem."
  [name]
  (log/trace (format "Read resource `%s` from filesystem." name))
  (read-json-resource name))

(defn read-standard-entries []
  (into
   []
   (remove
    (comp
     #{"http://hl7.org/fhir/SearchParameter/Bundle-message"
       "http://hl7.org/fhir/SearchParameter/Bundle-composition"}
     :fullUrl))
   (:entry (read-classpath-json-resource "blaze/db/search-parameters.json"))))

(defn- read-bundle-entries [extra-bundle-file]
  (cond-> (read-standard-entries)
    true
    (into (:entry (read-classpath-json-resource "blaze/db/Bundle-JobSearchParameterBundle.json")))
    extra-bundle-file
    (into (:entry (read-file-json-resource extra-bundle-file)))))

(defn- index-search-param [context index {:keys [url] :as sp}]
  (if-ok [search-param (sc/search-param context index sp)]
    (assoc index url search-param)
    #(if (ba/unsupported? %)
       index
       (reduced %))))

(defn- index-compartment-def
  {:arglists '([search-param-index compartment-def])}
  [search-param-index {def-code :code resource-defs :resource}]
  (into
   {}
   (map
    (fn [{res-type :code param-codes :param}]
      [res-type
       (into
        []
        (comp
         (keep #(get-in search-param-index [res-type %]))
         (map
          (fn [search-param]
            {:search-param search-param
             :def-code def-code})))
        param-codes)]))
   resource-defs))

(defn- index-compartment-resources [{def-code :code resource-defs :resource}]
  {def-code
   (into
    []
    (keep
     (fn [{res-type :code param-codes :param}]
       (when param-codes
         [res-type param-codes])))
    resource-defs)})

(defn- index-compartment-resources-by-type [{def-code :code resource-defs :resource}]
  {def-code
   (reduce
    (fn [res {res-type :code param-codes :param}]
      (cond-> res param-codes (assoc res-type param-codes)))
    {}
    resource-defs)})

(def ^:private list-search-param
  {:type "special"
   :name "_list"})

(def ^:private has-search-param
  {:type "special"
   :name "_has"})

(defn- add-special
  "Add special search params to `index`.

  See: https://www.hl7.org/fhir/search.html#special"
  [context index]
  (-> (assoc-in index ["Resource" "_list"] (sc/search-param context index list-search-param))
      (assoc-in ["Resource" "_has"] (sc/search-param context index has-search-param))))

(defn- build-url-index* [context index filter entries]
  (transduce
   (comp (map :resource)
         filter)
   (completing (partial index-search-param context))
   index
   entries))

(def ^:private remove-composite
  (remove (comp #{"composite"} :type)))

(def ^:private filter-composite
  (filter (comp #{"composite"} :type)))

(defn- build-url-index
  "Builds an index from url to search-param.

  Ensures that non-composite search params are build first so that composite
  search params will find it's components in the already partial build index."
  [context entries]
  (when-ok [non-composite (build-url-index* context {} remove-composite entries)]
    (build-url-index* context non-composite filter-composite entries)))

(defn- build-index
  "Builds an index from [type code] to search param."
  [url-index entries]
  (transduce
   (map :resource)
   (completing
    (fn [index {:keys [url base code]}]
      (if-let [search-param (clojure.core/get url-index url)]
        (reduce
         (fn [index type]
           (assoc-in index [type code] search-param))
         index
         base)
        index)))
   {}
   entries))

(defn- build-target-index [url-index entries]
  (transduce
   (comp
    (map :resource)
    (filter (comp #{"reference"} :type)))
   (completing
    (fn [index {:keys [url target]}]
      (if-let [search-param (clojure.core/get url-index url)]
        (reduce
         (fn [index target]
           (update index target conj-vec search-param))
         index
         target)
        index)))
   {}
   entries))

(def ^:private type-byte-index
  {"ImmunizationEvaluation" 63,
   "Appointment" 5
   "StructureMap" 129
   "CareTeam" 15
   "Linkage" 69
   "Communication" 23
   "MedicationDispense" 77
   "ImagingStudy" 61
   "ChargeItem" 17
   "AdverseEvent" 3
   "Media" 74
   "SubstancePolymer" 133
   "QuestionnaireResponse" 113
   "Coverage" 31
   "Procedure" 110
   "AuditEvent" 7
   "PaymentReconciliation" 105
   "MedicinalProductManufactured" 87
   "CompartmentDefinition" 25
   "Organization" 100
   "ExplanationOfBenefit" 53
   "Composition" 26
   "CoverageEligibilityResponse" 33
   "DocumentReference" 42
   "EventDefinition" 49
   "SubstanceProtein" 134
   "TerminologyCapabilities" 141
   "Encounter" 44
   "ImplementationGuide" 65
   "EvidenceVariable" 51
   "ObservationDefinition" 97
   "DiagnosticReport" 40
   "ExampleScenario" 52
   "ResearchDefinition" 116
   "Parameters" 102
   "SearchParameter" 123
   "MedicinalProductInteraction" 86
   "CodeSystem" 22
   "MessageDefinition" 91
   "NutritionOrder" 95
   "VerificationResult" 145
   "MedicationAdministration" 76
   "CatalogEntry" 16
   "Flag" 55
   "DeviceUseStatement" 39
   "Contract" 30
   "Invoice" 67
   "PaymentNotice" 104
   "Location" 71
   "Claim" 19
   "Specimen" 126
   "MedicationStatement" 80
   "EnrollmentResponse" 47
   "Evidence" 50
   "Bundle" 12
   "ResearchElementDefinition" 117
   "BodyStructure" 11
   "MedicinalProduct" 81
   "ResearchStudy" 118
   "AppointmentResponse" 6
   "MedicinalProductIndication" 84
   "Measure" 72
   "Person" 106
   "InsurancePlan" 66
   "Patient" 103
   "EffectEvidenceSynthesis" 43
   "ResearchSubject" 119
   "Medication" 75
   "ConceptMap" 27
   "CoverageEligibilityRequest" 32
   "SubstanceSourceMaterial" 136
   "VisionPrescription" 146
   "MolecularSequence" 93
   "MedicinalProductUndesirableEffect" 90
   "OperationOutcome" 99
   "MessageHeader" 92
   "AllergyIntolerance" 4
   "SubstanceReferenceInformation" 135
   "SupplyDelivery" 138
   "EpisodeOfCare" 48
   "PractitionerRole" 109
   "Library" 68
   "Practitioner" 108
   "MedicationRequest" 79
   "ImmunizationRecommendation" 64
   "Immunization" 62
   "GraphDefinition" 57
   "Account" 1
   "MedicinalProductIngredient" 85
   "MeasureReport" 73
   "DeviceMetric" 37
   "Goal" 56
   "MedicationKnowledge" 78
   "ClaimResponse" 20
   "DeviceDefinition" 36
   "Slot" 125
   "ValueSet" 144
   "MedicinalProductAuthorization" 82
   "StructureDefinition" 128
   "MedicinalProductContraindication" 83
   "DeviceRequest" 38
   "List" 70
   "Questionnaire" 112
   "Endpoint" 45
   "NamingSystem" 94
   "MedicinalProductPackaged" 88
   "Basic" 8
   "Binary" 9
   "PlanDefinition" 107
   "Subscription" 130
   "RelatedPerson" 114
   "SubstanceSpecification" 137
   "SubstanceNucleicAcid" 132
   "GuidanceResponse" 59
   "ClinicalImpression" 21
   "OrganizationAffiliation" 101
   "Condition" 28
   "CapabilityStatement" 13
   "HealthcareService" 60
   "SpecimenDefinition" 127
   "RiskAssessment" 120
   "OperationDefinition" 98
   "ActivityDefinition" 2
   "Schedule" 122
   "BiologicallyDerivedProduct" 10
   "Group" 58
   "MedicinalProductPharmaceutical" 89
   "FamilyMemberHistory" 54
   "ServiceRequest" 124
   "DetectedIssue" 34
   "Device" 35
   "RequestGroup" 115
   "TestScript" 143
   "RiskEvidenceSynthesis" 121
   "SupplyRequest" 139
   "Task" 140
   "CommunicationRequest" 24
   "EnrollmentRequest" 46
   "ChargeItemDefinition" 18
   "Substance" 131
   "Provenance" 111
   "Consent" 29
   "CarePlan" 14
   "TestReport" 142
   "Observation" 96
   "DocumentManifest" 41})

(defmethod m/pre-init-spec :blaze.db/search-param-registry [_]
  (s/keys :req-un [:blaze.db/kv-store :blaze.fhir/structure-definition-repo]
          :opt-un [::extra-bundle-file]))

(defmethod ig/init-key :blaze.db/search-param-registry
  [_ {:keys [kv-store extra-bundle-file]}]
  (log/info
   (cond-> "Init in-memory fixed R4 search parameter registry"
     extra-bundle-file
     (str " including extra search parameters from file: " extra-bundle-file)))
  (let [entries (read-bundle-entries extra-bundle-file)
        patient-compartment (read-classpath-json-resource "blaze/db/compartment/patient.json")
        context {:kv-store kv-store :type-byte-index type-byte-index}]
    (when-ok [url-index (build-url-index context entries)
              index (build-index url-index entries)]
      (->MemSearchParamRegistry
       url-index
       (add-special index)
      (->MemSearchParamRegistry
       url-index (add-special context index)
       (build-target-index url-index entries)
       (index-compartment-def index patient-compartment)
       (index-compartment-resources patient-compartment)
       (index-compartment-resources-by-type patient-compartment)
       type-byte-index))))
