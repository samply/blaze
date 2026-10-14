package blaze.db.impl.codec;

import clojure.lang.IPersistentVector;
import clojure.lang.Keyword;
import clojure.lang.PersistentVector;
import clojure.lang.RT;
import com.google.common.hash.Hashing;

import java.nio.charset.StandardCharsets;

/**
 * Tables of all FHIR resource types, one indexed by their internal type
 * identifier ({@code tid}) and one indexed by their name.
 * <p>
 * The tid table holds the interned {@code :fhir/<type>} keywords, so that they
 * are strongly referenced and {@code identical?} comparisons keep working.
 * <p>
 * Both tables are small open-addressing tables with linear probing, so that
 * lookups are fast and don't allocate.
 */
public final class ResourceTypes {

    private static final int BITS = 9;
    private static final int SIZE = 1 << BITS;
    private static final int MASK = SIZE - 1;

    private static final int[] TIDS = new int[SIZE];
    private static final Keyword[] KEYWORDS = new Keyword[SIZE];

    private static final int[] NAME_HASHES = new int[SIZE];
    private static final String[] NAME_KEYS = new String[SIZE];
    private static final Integer[] NAME_TIDS = new Integer[SIZE];
    private static final PersistentVector NAMES = PersistentVector.create(
            "Account", "ActivityDefinition", "AdverseEvent", "AllergyIntolerance", "Appointment",
            "AppointmentResponse", "AuditEvent", "Basic", "Binary", "BiologicallyDerivedProduct",
            "BodyStructure", "Bundle", "CapabilityStatement", "CarePlan", "CareTeam",
            "CatalogEntry", "ChargeItem", "ChargeItemDefinition", "Claim", "ClaimResponse",
            "ClinicalImpression", "CodeSystem", "Communication", "CommunicationRequest",
            "CompartmentDefinition", "Composition", "ConceptMap", "Condition", "Consent",
            "Contract", "Coverage", "CoverageEligibilityRequest", "CoverageEligibilityResponse",
            "DetectedIssue", "Device", "DeviceDefinition", "DeviceMetric", "DeviceRequest",
            "DeviceUseStatement", "DiagnosticReport", "DocumentManifest", "DocumentReference",
            "EffectEvidenceSynthesis", "Encounter", "Endpoint", "EnrollmentRequest",
            "EnrollmentResponse", "EpisodeOfCare", "EventDefinition", "Evidence",
            "EvidenceVariable", "ExampleScenario", "ExplanationOfBenefit", "FamilyMemberHistory",
            "Flag", "Goal", "GraphDefinition", "Group", "GuidanceResponse", "HealthcareService",
            "ImagingStudy", "Immunization", "ImmunizationEvaluation", "ImmunizationRecommendation",
            "ImplementationGuide", "InsurancePlan", "Invoice", "Library", "Linkage", "List",
            "Location", "Measure", "MeasureReport", "Media", "Medication",
            "MedicationAdministration", "MedicationDispense", "MedicationKnowledge",
            "MedicationRequest", "MedicationStatement", "MedicinalProduct",
            "MedicinalProductAuthorization", "MedicinalProductContraindication",
            "MedicinalProductIndication", "MedicinalProductIngredient",
            "MedicinalProductInteraction", "MedicinalProductManufactured",
            "MedicinalProductPackaged", "MedicinalProductPharmaceutical",
            "MedicinalProductUndesirableEffect", "MessageDefinition", "MessageHeader",
            "MolecularSequence", "NamingSystem", "NutritionOrder", "Observation",
            "ObservationDefinition", "OperationDefinition", "OperationOutcome", "Organization",
            "OrganizationAffiliation", "Parameters", "Patient", "PaymentNotice",
            "PaymentReconciliation", "Person", "PlanDefinition", "Practitioner", "PractitionerRole",
            "Procedure", "Provenance", "Questionnaire", "QuestionnaireResponse", "RelatedPerson",
            "RequestGroup", "ResearchDefinition", "ResearchElementDefinition", "ResearchStudy",
            "ResearchSubject", "RiskAssessment", "RiskEvidenceSynthesis", "Schedule",
            "SearchParameter", "ServiceRequest", "Slot", "Specimen", "SpecimenDefinition",
            "StructureDefinition", "StructureMap", "Subscription", "Substance",
            "SubstanceNucleicAcid", "SubstancePolymer", "SubstanceProtein",
            "SubstanceReferenceInformation", "SubstanceSourceMaterial", "SubstanceSpecification",
            "SupplyDelivery", "SupplyRequest", "Task", "TerminologyCapabilities", "TestReport",
            "TestScript", "ValueSet", "VerificationResult", "VisionPrescription");

    static {
        for (Object o : NAMES) {
            String name = (String) o;
            int tid = murmur3(name);
            putTid(tid, name);
            putName(name, tid);
        }
    }

    private ResourceTypes() {
    }

    private static int murmur3(String name) {
        return Hashing.murmur3_32_fixed().hashBytes(name.getBytes(StandardCharsets.ISO_8859_1)).asInt();
    }

    /**
     * Returns the top {@code BITS} bits of {@code tid} as start index.
     * <p>
     * No further mixing is needed, because the tid is already a murmur3 hash.
     */
    private static int tidIndex(int tid) {
        return tid >>> (Integer.SIZE - BITS);
    }

    /**
     * Returns the start index of a name with {@code hash}.
     * <p>
     * Uses Fibonacci hashing (multiplication by 2^32 / golden ratio, taking the
     * top {@code BITS} bits), because {@link String#hashCode()} is a polynomial
     * hash whose bits are not well mixed.
     */
    private static int nameIndex(int hash) {
        return (hash * 0x9E3779B9) >>> (Integer.SIZE - BITS);
    }

    private static void putTid(int tid, String name) {
        int i = tidIndex(tid);
        while (KEYWORDS[i] != null) {
            i = (i + 1) & MASK;
        }
        TIDS[i] = tid;
        KEYWORDS[i] = RT.keyword("fhir", name);
    }

    private static void putName(String name, int tid) {
        int hash = name.hashCode();
        int i = nameIndex(hash);
        while (NAME_KEYS[i] != null) {
            i = (i + 1) & MASK;
        }
        NAME_HASHES[i] = hash;
        NAME_KEYS[i] = name;
        NAME_TIDS[i] = tid;
    }

    /**
     * Returns the internal type identifier of the resource type with
     * {@code name}.
     * <p>
     * The tid is the 32-bit murmur3 hash of the ISO-8859-1 encoded name. It is
     * stored in indices and so must never change.
     * <p>
     * The tids of all known resource types are looked up without allocation.
     * They are returned boxed, always as the same instance, so that Clojure
     * callers don't have to box them. The tids of other names are calculated.
     */
    public static Integer tid(String name) {
        int hash = name.hashCode();
        int i = nameIndex(hash);
        String key;
        while ((key = NAME_KEYS[i]) != null) {
            if (NAME_HASHES[i] == hash && key.equals(name)) return NAME_TIDS[i];
            i = (i + 1) & MASK;
        }
        return murmur3(name);
    }

    /**
     * Returns the keyword {@code :fhir/<type>} of the resource type with
     * {@code tid} or {@code null} if {@code tid} is unknown.
     */
    public static Keyword keyword(int tid) {
        int i = tidIndex(tid);
        Keyword keyword;
        while ((keyword = KEYWORDS[i]) != null) {
            if (TIDS[i] == tid) return keyword;
            i = (i + 1) & MASK;
        }
        return null;
    }

    /**
     * Returns the name of the resource type with {@code tid} or {@code null}
     * if {@code tid} is unknown.
     */
    public static String name(int tid) {
        Keyword keyword = keyword(tid);
        return keyword == null ? null : keyword.getName();
    }

    /**
     * Returns the names of all resource types in no particular order.
     */
    public static IPersistentVector names() {
        return NAMES;
    }
}
