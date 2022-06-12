(ns blaze.db.impl.codec
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.byte-string :as bs]
    [blaze.fhir.spec.type.system])
  (:import
    [blaze.fhir.spec.type.system DateTimeYear DateTimeYearMonth
                                 DateTimeYearMonthDay]
    [com.github.benmanes.caffeine.cache CacheLoader Caffeine]
    [com.google.common.hash Hashing]
    [java.nio.charset StandardCharsets]
    [java.time LocalDate LocalDateTime OffsetDateTime Year YearMonth
               ZoneId ZoneOffset]
    [java.util Arrays]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)



;; ---- Sizes of Byte Arrays --------------------------------------------------

(def ^:const ^long c-hash-size Integer/BYTES)
(def ^:const ^long v-hash-size Long/BYTES)
(def ^:const ^long tid-size Integer/BYTES)
(def ^:const ^long t-size 5)
(def ^:const ^long did-size Long/BYTES)
(def ^:const ^long state-size Long/BYTES)
(def ^:const ^long max-id-size 64)



;; ---- Type Identifier -------------------------------------------------------

(defn- memoize-1 [f]
  (let [mem
        (-> (Caffeine/newBuilder)
            (.build
              (reify CacheLoader
                (load [_ x]
                  (f x)))))]
    (fn [x]
      (.get mem x))))


(def ^{:arglists '([type])} tid
  "Internal type identifier.

  Returns an integer."
  (memoize-1
    (fn [type]
      (-> (Hashing/murmur3_32_fixed)
          (.hashBytes (.getBytes ^String type StandardCharsets/ISO_8859_1))
          (.asInt)))))


(let [kvs [[-2146857976 "ClinicalImpression"]
           [-2144887593 "ImmunizationEvaluation"]
           [-2122495190 "Goal"]
           [-2117596305 "MedicinalProductAuthorization"]
           [-2109411663 "ConceptMap"]
           [-2064947246 "MedicationAdministration"]
           [-1955425438 "Media"]
           [-1938470528 "Encounter"]
           [-1930074818 "Schedule"]
           [-1921582492 "HealthcareService"]
           [-1907033276 "ServiceRequest"]
           [-1841175362 "Endpoint"]
           [-1823859526 "PaymentReconciliation"]
           [-1806190491 "Immunization"]
           [-1792712657 "CommunicationRequest"]
           [-1785826783 "ObservationDefinition"]
           [-1766148779 "Slot"]
           [-1698980665 "MedicinalProductPackaged"]
           [-1679546398 "Contract"]
           [-1664243521 "VisionPrescription"]
           [-1662021626 "NutritionOrder"]
           [-1619531880 "Linkage"]
           [-1616119630 "SubstancePolymer"]
           [-1607765974 "Appointment"]
           [-1582808583 "Consent"]
           [-1563860828 "BiologicallyDerivedProduct"]
           [-1535597530 "MedicinalProductPharmaceutical"]
           [-1508652135 "Patient"]
           [-1498775595 "ImmunizationRecommendation"]
           [-1486145234 "CompartmentDefinition"]
           [-1416327690 "BodyStructure"]
           [-1408495925 "CatalogEntry"]
           [-1395884574 "MedicinalProductContraindication"]
           [-1392896153 "VerificationResult"]
           [-1348145880 "DeviceUseStatement"]
           [-1317855574 "DeviceRequest"]
           [-1253457572 "MessageHeader"]
           [-1234412843 "Library"]
           [-1234037851 "StructureMap"]
           [-1211692102 "AllergyIntolerance"]
           [-1197972665 "CapabilityStatement"]
           [-1178333639 "Medication"]
           [-1096874819 "Account"]
           [-1061667662 "ResearchSubject"]
           [-1043762161 "DocumentReference"]
           [-1016280475 "SubstanceSourceMaterial"]
           [-987069264 "RelatedPerson"]
           [-981868869 "DetectedIssue"]
           [-906567409 "OperationOutcome"]
           [-857961912 "CareTeam"]
           [-836123537 "Substance"]
           [-821386881 "Location"]
           [-793756482 "FamilyMemberHistory"]
           [-775340144 "MedicationRequest"]
           [-762550806 "CarePlan"]
           [-759227186 "GuidanceResponse"]
           [-714079149 "Flag"]
           [-705018099 "Invoice"]
           [-620329769 "Specimen"]
           [-612809062 "Basic"]
           [-609334916 "ImagingStudy"]
           [-566801226 "MedicinalProductInteraction"]
           [-550263178 "Procedure"]
           [-533734493 "EventDefinition"]
           [-519892160 "Device"]
           [-465150864 "Organization"]
           [-456861287 "ExampleScenario"]
           [-449453287 "ChargeItem"]
           [-440388670 "MedicinalProduct"]
           [-424110863 "MedicationKnowledge"]
           [-409108303 "ResearchStudy"]
           [-399002016 "CodeSystem"]
           [-350610588 "EvidenceVariable"]
           [-346477310 "MolecularSequence"]
           [-290604778 "RiskEvidenceSynthesis"]
           [-288511436 "InsurancePlan"]
           [-275369827 "DeviceDefinition"]
           [-270384781 "Practitioner"]
           [-225603577 "SpecimenDefinition"]
           [-210768418 "Subscription"]
           [-192619824 "SupplyDelivery"]
           [-135173301 "EnrollmentResponse"]
           [-49254658 "Group"]
           [-15728338 "ExplanationOfBenefit"]
           [18499055 "Questionnaire"]
           [34688446 "ResearchDefinition"]
           [59162178 "QuestionnaireResponse"]
           [144907839 "CoverageEligibilityRequest"]
           [176172729 "Provenance"]
           [179601186 "List"]
           [193554741 "MedicinalProductIngredient"]
           [196435039 "Claim"]
           [418673785 "RiskAssessment"]
           [495401834 "Communication"]
           [554526871 "PaymentNotice"]
           [588083121 "ChargeItemDefinition"]
           [633737238 "MedicinalProductUndesirableEffect"]
           [699823978 "ImplementationGuide"]
           [816904812 "ClaimResponse"]
           [862114136 "ResearchElementDefinition"]
           [956349679 "DiagnosticReport"]
           [1008623651 "TerminologyCapabilities"]
           [1144476312 "CoverageEligibilityResponse"]
           [1157731871 "StructureDefinition"]
           [1160573513 "GraphDefinition"]
           [1165649445 "SupplyRequest"]
           [1197875005 "AuditEvent"]
           [1225883824 "Person"]
           [1280418335 "Coverage"]
           [1320002914 "MedicationStatement"]
           [1358819624 "RequestGroup"]
           [1366361718 "NamingSystem"]
           [1407925398 "Composition"]
           [1423604526 "EffectEvidenceSynthesis"]
           [1433732083 "EpisodeOfCare"]
           [1445362758 "Evidence"]
           [1446512298 "SubstanceNucleicAcid"]
           [1460414728 "Bundle"]
           [1495153489 "Condition"]
           [1499933762 "PlanDefinition"]
           [1501000637 "SubstanceSpecification"]
           [1503725773 "ActivityDefinition"]
           [1519703768 "AdverseEvent"]
           [1525596137 "OperationDefinition"]
           [1543762679 "Observation"]
           [1577883550 "MessageDefinition"]
           [1582079864 "PractitionerRole"]
           [1597447080 "DeviceMetric"]
           [1603452656 "SearchParameter"]
           [1622961479 "MedicationDispense"]
           [1634078999 "ValueSet"]
           [1654143066 "OrganizationAffiliation"]
           [1692854872 "MeasureReport"]
           [1732559825 "Parameters"]
           [1732874191 "Task"]
           [1745455705 "EnrollmentRequest"]
           [1768958495 "SubstanceReferenceInformation"]
           [1811233589 "Measure"]
           [1861004142 "Binary"]
           [1872042716 "AppointmentResponse"]
           [2009306008 "TestScript"]
           [2013764019 "MedicinalProductIndication"]
           [2027550310 "SubstanceProtein"]
           [2081142363 "TestReport"]
           [2100353352 "DocumentManifest"]
           [2121669939 "MedicinalProductManufactured"]]
      tid->idx (int-array (map first kvs))
      idx->type (object-array (map second kvs))]
  (defn tid->type [tid]
    (let [idx (Arrays/binarySearch tid->idx (int tid))]
      (when (nat-int? idx)
        (aget idx->type idx)))))



;; ---- Identifier Functions --------------------------------------------------

(defn id-from-byte-buffer [buf]
  (let [id-bytes (byte-array (bb/remaining buf))]
    (bb/copy-into-byte-array! buf id-bytes)
    (String. id-bytes StandardCharsets/ISO_8859_1)))


(defn did [t idx]
  (+ (bit-shift-left (unchecked-long t) 24) (unchecked-long idx)))



;; ---- Key Functions ---------------------------------------------------------

(defn descending-long
  "Converts positive longs so that they decrease from 0xFFFFFFFFFF.

  This function is used for the point in time `t` value, which is always ordered
  descending in indices."
  {:inline
   (fn [l]
     `(bit-and (bit-not (unchecked-long ~l)) 0xFFFFFFFFFF))}
  [l]
  (bit-and (bit-not (unchecked-long l)) 0xFFFFFFFFFF))


(defn c-hash [code]
  (-> (Hashing/murmur3_32_fixed)
      (.hashString code StandardCharsets/UTF_8)
      (.asInt)))


(def c-hash->code
  (into
    {}
    (map (fn [code] [(c-hash code) code]))
    ["_id"
     "_lastUpdated"
     "_profile"
     "address"
     "birthdate"
     "bodysite"
     "category"
     "class"
     "code"
     "code-value-quantity"
     "combo-code"
     "combo-code-value-quantity"
     "combo-value-quantity"
     "context-quantity"
     "date"
     "death-date"
     "deceased"
     "description"
     "identifier"
     "issued"
     "item"
     "item:identifier"
     "onset-date"
     "patient"
     "phonetic"
     "probability"
     "series"
     "status"
     "subject"
     "url"
     "value-quantity"
     "variant-start"
     "version"]))


(defn v-hash [value]
  (-> (Hashing/farmHashFingerprint64)
      (.hashString value StandardCharsets/UTF_8)
      (.asBytes)
      bs/from-byte-array))


(defn tid-did
  "Returns a byte string with `tid` followed by `did`."
  [tid did]
  (-> (bb/allocate (+ tid-size did-size))
      (bb/put-int! tid)
      (bb/put-long! did)
      bb/flip!
      bs/from-byte-buffer!))


(defn tid-byte-string
  "Returns a byte string with `tid`."
  [tid]
  (-> (bb/allocate tid-size)
      (bb/put-int! tid)
      bb/flip!
      bs/from-byte-buffer!))


(defn string
  "Returns a lexicographically sortable byte string of the `string` value."
  {:inline
   (fn [string]
     `(bs/from-utf8-string ~string))}
  [string]
  (bs/from-utf8-string string))


(defprotocol NumberBytes
  (-number [number]))


(defn number
  "Converts the number in a lexicographically sortable byte string.

  The byte string has variable length."
  [number]
  (-number number))


;; See https://github.com/danburkert/bytekey/blob/6980b9e33281d875f03f4c9a953b93a384eac085/src/encoder.rs#L258
;; And https://cornerwings.github.io/2019/10/lexical-sorting/
(extend-protocol NumberBytes
  BigDecimal
  (-number [val]
    ;; Truncate at two digits after the decimal point
    (-number (.longValue (.scaleByPowerOfTen val 2))))

  Integer
  (-number [val]
    (-number (long val)))

  Long
  (-number [val]
    (let [mask (bit-shift-right (.longValue val) 63)
          val (- (abs (.longValue val)) (bit-and 1 mask))]
      (condp > val
        (bit-shift-left 1 3)
        (-> (bb/allocate 1)
            (bb/put-byte! (bit-xor (bit-or val (bit-shift-left 0x10 3)) mask))
            bb/flip!
            bs/from-byte-buffer!)

        (bit-shift-left 1 11)
        (-> (bb/allocate 2)
            (bb/put-short! (bit-xor (bit-or val (bit-shift-left 0x11 11)) mask))
            bb/flip!
            bs/from-byte-buffer!)

        (bit-shift-left 1 19)
        (let [masked (bit-xor (bit-or val (bit-shift-left 0x12 19)) mask)]
          (-> (bb/allocate 3)
              (bb/put-byte! (bit-shift-right masked 16))
              (bb/put-short! masked)
              bb/flip!
              bs/from-byte-buffer!))

        (bit-shift-left 1 27)
        (-> (bb/allocate 4)
            (bb/put-int! (bit-xor (bit-or val (bit-shift-left 0x13 27)) mask))
            bb/flip!
            bs/from-byte-buffer!)

        (bit-shift-left 1 35)
        (let [masked (bit-xor (bit-or val (bit-shift-left 0x14 35)) mask)]
          (-> (bb/allocate 5)
              (bb/put-byte! (bit-shift-right masked 32))
              (bb/put-int! masked)
              bb/flip!
              bs/from-byte-buffer!))

        (bit-shift-left 1 43)
        (let [masked (bit-xor (bit-or val (bit-shift-left 0x15 43)) mask)]
          (-> (bb/allocate 6)
              (bb/put-short! (bit-shift-right masked 32))
              (bb/put-int! masked)
              bb/flip!
              bs/from-byte-buffer!))

        (bit-shift-left 1 51)
        (let [masked (bit-xor (bit-or val (bit-shift-left 0x16 51)) mask)]
          (-> (bb/allocate 7)
              (bb/put-byte! (bit-shift-right masked 48))
              (bb/put-short! (bit-shift-right masked 32))
              (bb/put-int! masked)
              bb/flip!
              bs/from-byte-buffer!))

        (bit-shift-left 1 59)
        (-> (bb/allocate 8)
            (bb/put-long! (bit-xor (bit-or val (bit-shift-left 0x17 59)) mask))
            bb/flip!
            bs/from-byte-buffer!)

        (-> (bb/allocate 9)
            (bb/put-byte! (bit-xor (bit-shift-left 0x18 3) mask))
            (bb/put-long! (bit-xor val mask))
            bb/flip!
            bs/from-byte-buffer!)))))


(defn- epoch-seconds ^long [^LocalDateTime date-time ^ZoneId zone-id]
  (.toEpochSecond (.atZone date-time zone-id)))


(defprotocol DateLowerBound
  (-date-lb [date-time zone-id]))


(extend-protocol DateLowerBound
  Year
  (-date-lb [year zone-id]
    (number (epoch-seconds (.atStartOfDay (.atDay year 1)) zone-id)))
  DateTimeYear
  (-date-lb [year zone-id]
    (number (epoch-seconds (.atStartOfDay (.atDay ^Year (.-year year) 1)) zone-id)))
  YearMonth
  (-date-lb [year-month zone-id]
    (number (epoch-seconds (.atStartOfDay (.atDay year-month 1)) zone-id)))
  DateTimeYearMonth
  (-date-lb [year-month zone-id]
    (number (epoch-seconds (.atStartOfDay (.atDay ^YearMonth (.-year_month year-month) 1)) zone-id)))
  LocalDate
  (-date-lb [date zone-id]
    (number (epoch-seconds (.atStartOfDay date) zone-id)))
  DateTimeYearMonthDay
  (-date-lb [date zone-id]
    (number (epoch-seconds (.atStartOfDay ^LocalDate (.date date)) zone-id)))
  LocalDateTime
  (-date-lb [date-time zone-id]
    (number (epoch-seconds date-time zone-id)))
  OffsetDateTime
  (-date-lb [date-time _]
    (number (.toEpochSecond date-time))))


(defn date-lb
  "Returns the lower bound of the implicit range the `date-time` value spans."
  [zone-id date-time]
  (-date-lb date-time zone-id))


(defprotocol DateUpperBound
  (-date-ub [date-time zone-id]))


(extend-protocol DateUpperBound
  Year
  (-date-ub [year zone-id]
    (number (dec (epoch-seconds (.atStartOfDay (.atDay (.plusYears year 1) 1)) zone-id))))
  DateTimeYear
  (-date-ub [year zone-id]
    (number (dec (epoch-seconds (.atStartOfDay (.atDay (.plusYears ^Year (.year year) 1) 1)) zone-id))))
  YearMonth
  (-date-ub [year-month zone-id]
    (number (dec (epoch-seconds (.atStartOfDay (.atDay (.plusMonths year-month 1) 1)) zone-id))))
  DateTimeYearMonth
  (-date-ub [year-month zone-id]
    (number (dec (epoch-seconds (.atStartOfDay (.atDay (.plusMonths ^YearMonth (.-year_month year-month) 1) 1)) zone-id))))
  LocalDate
  (-date-ub [date zone-id]
    (number (dec (epoch-seconds (.atStartOfDay (.plusDays date 1)) zone-id))))
  DateTimeYearMonthDay
  (-date-ub [date zone-id]
    (number (dec (epoch-seconds (.atStartOfDay (.plusDays ^LocalDate (.date date) 1)) zone-id))))
  LocalDateTime
  (-date-ub [date-time zone-id]
    (number (epoch-seconds date-time zone-id)))
  OffsetDateTime
  (-date-ub [date-time _]
    (number (.toEpochSecond date-time))))


(defn date-ub
  "Returns the upper bound of the implicit range the `date-time` value spans."
  [zone-id date-time]
  (-date-ub date-time zone-id))


(def date-min-bound
  (date-lb (ZoneOffset/ofHours 0) (Year/of 1)))


(def date-max-bound
  (date-ub (ZoneOffset/ofHours 0) (Year/of 9999)))


(defn date-lb-ub [lb ub]
  (-> (bb/allocate (+ 2 (bs/size lb) (bs/size ub)))
      (bb/put-byte-string! lb)
      (bb/put-byte! 0)
      (bb/put-byte-string! ub)
      (bb/put-byte! (bs/size lb))
      bb/flip!
      bs/from-byte-buffer!))


(defn date-lb-ub->lb [lb-ub]
  (bs/subs lb-ub 0 (bs/nth lb-ub (unchecked-dec-int (bs/size lb-ub)))))


(defn date-lb-ub->ub [lb-ub]
  (let [lb-size-idx (unchecked-dec-int (bs/size lb-ub))
        start (unchecked-inc-int (int (bs/nth lb-ub lb-size-idx)))]
    (bs/subs lb-ub start lb-size-idx)))


(defn quantity [unit value]
  (bs/concat (v-hash (or unit "")) (number value)))
