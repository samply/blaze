// A node with the resource type as node id.
RuleSet: Node(type)
* extension[+].url = "http://hl7.org/fhir/5.0/StructureDefinition/extension-GraphDefinition.node"
* extension[=].extension[+].url = "nodeId"
* extension[=].extension[=].valueId = "{type}"
* extension[=].extension[+].url = "type"
* extension[=].extension[=].valueCode = #{type}

// A link from the Patient to a node of `type` selecting the resources of
// `type` that reference the Patient through the search parameter `code`.
RuleSet: PurgeLink(type, code)
* insert Node({type})
* link[+].extension[+].url = "http://hl7.org/fhir/5.0/StructureDefinition/extension-GraphDefinition.link.sourceId"
* link[=].extension[=].valueId = "Patient"
* link[=].extension[+].url = "http://hl7.org/fhir/5.0/StructureDefinition/extension-GraphDefinition.link.targetId"
* link[=].extension[=].valueId = "{type}"
* link[=].extension[+].url = "http://hl7.org/fhir/5.0/StructureDefinition/extension-GraphDefinition.link.params"
* link[=].extension[=].valueString = "{code}={ref}"

// The GraphDefinition selecting the resources the Patient $purge operation
// erases together with the patient.
//
// It has one reverse link from the Patient per resource type, using only the
// reference search parameter for the patient the resource is about. So a
// resource that references the patient in another role, like an Observation
// with the patient as performer, isn't purged. Each link is a reviewed
// decision.
//
// Resource types of the Patient compartment without a link, because their
// reference can be several patients:
//  * Account - its subject, e.g. a family account
//  * Appointment - its participants
//  * AuditEvent - its entities and agents
//  * Group - its members
//  * Patient - its links
//  * Person - its links
//  * Provenance - its targets
//  * Schedule - its actors
//
// Blaze uses the R5 structure of GraphDefinition through cross-version
// extensions in R4. The Patient $purge operation only supports one hop reverse
// links from the Patient with params of the form `<code>={ref}`.
//
// A changed GraphDefinition needs a new `version`, so that replaying the
// transaction log resolves the revision valid at that time.
Instance: PatientPurge
InstanceOf: GraphDefinition
Usage: #definition
Title: "Patient Purge"
Description: "The resources the Patient $purge operation erases together with the patient. Contains one reverse link per resource type, using only the reference search parameter for the patient the resource is about."
* url = "https://blaze-server.org/fhir/GraphDefinition/PatientPurge"
* version = "1"
* name = "PatientPurge"
* status = #active
* description = "The resources the Patient $purge operation erases together with the patient. Contains one reverse link per resource type, using only the reference search parameter for the patient the resource is about."
* start = #Patient
* extension[+].url = "http://hl7.org/fhir/5.0/StructureDefinition/extension-GraphDefinition.start"
* extension[=].valueId = "Patient"
* insert Node(Patient)
* insert PurgeLink(AdverseEvent, subject)
* insert PurgeLink(AllergyIntolerance, patient)
* insert PurgeLink(AppointmentResponse, patient)
* insert PurgeLink(Basic, patient)
* insert PurgeLink(BodyStructure, patient)
* insert PurgeLink(CarePlan, patient)
* insert PurgeLink(CareTeam, subject)
* insert PurgeLink(ChargeItem, subject)
* insert PurgeLink(Claim, patient)
* insert PurgeLink(ClaimResponse, patient)
* insert PurgeLink(ClinicalImpression, subject)
* insert PurgeLink(Communication, subject)
* insert PurgeLink(CommunicationRequest, subject)
* insert PurgeLink(Composition, subject)
* insert PurgeLink(Condition, patient)
* insert PurgeLink(Consent, patient)
* insert PurgeLink(Coverage, beneficiary)
* insert PurgeLink(CoverageEligibilityRequest, patient)
* insert PurgeLink(CoverageEligibilityResponse, patient)
* insert PurgeLink(DetectedIssue, patient)
* insert PurgeLink(DeviceRequest, subject)
* insert PurgeLink(DeviceUseStatement, subject)
* insert PurgeLink(DiagnosticReport, subject)
* insert PurgeLink(DocumentManifest, subject)
* insert PurgeLink(DocumentReference, subject)
* insert PurgeLink(Encounter, patient)
* insert PurgeLink(EnrollmentRequest, subject)
* insert PurgeLink(EpisodeOfCare, patient)
* insert PurgeLink(ExplanationOfBenefit, patient)
* insert PurgeLink(FamilyMemberHistory, patient)
* insert PurgeLink(Flag, patient)
* insert PurgeLink(Goal, patient)
* insert PurgeLink(ImagingStudy, patient)
* insert PurgeLink(Immunization, patient)
* insert PurgeLink(ImmunizationEvaluation, patient)
* insert PurgeLink(ImmunizationRecommendation, patient)
* insert PurgeLink(Invoice, subject)
* insert PurgeLink(List, subject)
* insert PurgeLink(MeasureReport, patient)
* insert PurgeLink(Media, subject)
* insert PurgeLink(MedicationAdministration, patient)
* insert PurgeLink(MedicationDispense, patient)
* insert PurgeLink(MedicationRequest, subject)
* insert PurgeLink(MedicationStatement, subject)
* insert PurgeLink(MolecularSequence, patient)
* insert PurgeLink(NutritionOrder, patient)
* insert PurgeLink(Observation, subject)
* insert PurgeLink(Procedure, patient)
* insert PurgeLink(QuestionnaireResponse, subject)
* insert PurgeLink(RelatedPerson, patient)
* insert PurgeLink(RequestGroup, subject)
* insert PurgeLink(ResearchSubject, individual)
* insert PurgeLink(RiskAssessment, subject)
* insert PurgeLink(ServiceRequest, subject)
* insert PurgeLink(Specimen, subject)
* insert PurgeLink(SupplyDelivery, patient)
* insert PurgeLink(SupplyRequest, subject)
* insert PurgeLink(VisionPrescription, patient)
