# Operation \$purge on Patient <Badge type="info" text="Feature: OPERATION_PATIENT_PURGE"/> <Badge type="warning" text="Since 0.30.1"/>

> [!CAUTION]
> The operation \$purge on patient is currently **alpha** and has to be enabled explicitly by setting the env var `ENABLE_OPERATION_PATIENT_PURGE` to true. Please don't use it in production. Please be aware that the database might not be able to migrate to a newer version of Blaze if the operation was used.

> [!CAUTION]
> The operation \$purge on patient is trial use in the unreleased next version of FHIR (R6). So it is subject to change. Please use it with care.

The patient \$purge operation is used to request the removal of all current and historical versions for all resources in a patient compartment. The return is an OperationOutcome with results and/or details about execution. The `code` of the first issue of the OperationOutcome is either `success` on successful purging of all resources or an error.

```
POST [base]/Patient/[id]/$purge
```

All resources selected by the [purge GraphDefinition](#selected-resources) are marked as purged immediately. All reads which happen after the \$purge operation completes, will not see any of the purged resources. Only active [paging sessions](../../api.md#paging-sessions) and [asynchronous requests](../../api.md#asynchronous-requests) started before the patient \$purge operation will be able to access the purged versions for a limited amount of time.  

The official documentation can be found [here][1].

## Selected Resources <Badge type="warning" text="Since 1.14.0"/>

The following table lists the search parameters of each resource type of the [Patient compartment](https://hl7.org/fhir/R4/compartmentdefinition-patient.html), which Blaze used before 1.14.0, and the search parameter of the purge GraphDefinition `https://blaze-server.org/fhir/GraphDefinition/PatientPurge`. A resource is purged if it references the patient through the search parameter of the purge GraphDefinition. Resources of the types without one (—) aren't purged, except the patient itself.

| Resource Type               | Patient Compartment                                   | Purge GraphDefinition |
|-----------------------------|-------------------------------------------------------|-----------------------|
| Account                     | `subject`                                             | —                     |
| AdverseEvent                | `subject`                                             | `subject`             |
| AllergyIntolerance          | `patient`, `recorder`, `asserter`                     | `patient`             |
| Appointment                 | `actor`                                               | —                     |
| AppointmentResponse         | `actor`                                               | `patient`             |
| AuditEvent                  | `patient`                                             | —                     |
| Basic                       | `patient`, `author`                                   | `patient`             |
| BodyStructure               | `patient`                                             | `patient`             |
| CarePlan                    | `patient`, `performer`                                | `patient`             |
| CareTeam                    | `patient`, `participant`                              | `subject`             |
| ChargeItem                  | `subject`                                             | `subject`             |
| Claim                       | `patient`, `payee`                                    | `patient`             |
| ClaimResponse               | `patient`                                             | `patient`             |
| ClinicalImpression          | `subject`                                             | `subject`             |
| Communication               | `subject`, `sender`, `recipient`                      | `subject`             |
| CommunicationRequest        | `subject`, `sender`, `recipient`, `requester`         | `subject`             |
| Composition                 | `subject`, `author`, `attester`                       | `subject`             |
| Condition                   | `patient`, `asserter`                                 | `patient`             |
| Consent                     | `patient`                                             | `patient`             |
| Coverage                    | `policy-holder`, `subscriber`, `beneficiary`, `payor` | `beneficiary`         |
| CoverageEligibilityRequest  | `patient`                                             | `patient`             |
| CoverageEligibilityResponse | `patient`                                             | `patient`             |
| DetectedIssue               | `patient`                                             | `patient`             |
| DeviceRequest               | `subject`, `performer`                                | `subject`             |
| DeviceUseStatement          | `subject`                                             | `subject`             |
| DiagnosticReport            | `subject`                                             | `subject`             |
| DocumentManifest            | `subject`, `author`, `recipient`                      | `subject`             |
| DocumentReference           | `subject`, `author`                                   | `subject`             |
| Encounter                   | `patient`                                             | `patient`             |
| EnrollmentRequest           | `subject`                                             | `subject`             |
| EpisodeOfCare               | `patient`                                             | `patient`             |
| ExplanationOfBenefit        | `patient`, `payee`                                    | `patient`             |
| FamilyMemberHistory         | `patient`                                             | `patient`             |
| Flag                        | `patient`                                             | `patient`             |
| Goal                        | `patient`                                             | `patient`             |
| Group                       | `member`                                              | —                     |
| ImagingStudy                | `patient`                                             | `patient`             |
| Immunization                | `patient`                                             | `patient`             |
| ImmunizationEvaluation      | `patient`                                             | `patient`             |
| ImmunizationRecommendation  | `patient`                                             | `patient`             |
| Invoice                     | `subject`, `patient`, `recipient`                     | `subject`             |
| List                        | `subject`, `source`                                   | `subject`             |
| MeasureReport               | `patient`                                             | `patient`             |
| Media                       | `subject`                                             | `subject`             |
| MedicationAdministration    | `patient`, `performer`, `subject`                     | `patient`             |
| MedicationDispense          | `subject`, `patient`, `receiver`                      | `patient`             |
| MedicationRequest           | `subject`                                             | `subject`             |
| MedicationStatement         | `subject`                                             | `subject`             |
| MolecularSequence           | `patient`                                             | `patient`             |
| NutritionOrder              | `patient`                                             | `patient`             |
| Observation                 | `subject`, `performer`                                | `subject`             |
| Patient                     | `link`                                                | —                     |
| Person                      | `patient`                                             | —                     |
| Procedure                   | `patient`, `performer`                                | `patient`             |
| Provenance                  | `patient`                                             | `patient`             |
| QuestionnaireResponse       | `subject`, `author`                                   | `subject`             |
| RelatedPerson               | `patient`                                             | `patient`             |
| RequestGroup                | `subject`, `participant`                              | `subject`             |
| ResearchSubject             | `individual`                                          | `individual`          |
| RiskAssessment              | `subject`                                             | `subject`             |
| Schedule                    | `actor`                                               | —                     |
| ServiceRequest              | `subject`, `performer`                                | `subject`             |
| Specimen                    | `subject`                                             | `subject`             |
| SupplyDelivery              | `patient`                                             | `patient`             |
| SupplyRequest               | `subject`                                             | `subject`             |
| VisionPrescription          | `patient`                                             | `patient`             |

If [referential integrity](../../deployment/environment-variables.md#enforce-referential-integrity) is enforced, which is the default, the operation fails with an OperationOutcome with code `conflict` as long as resources that aren't purged still reference the patient or one of the purged resources, like a Group with the patient as member. The diagnostics list the referencing resources. After removing the references, the operation can be retried.

The resources a purge would erase can be previewed with the \$graph operation:

```
GET [base]/Patient/[id]/$graph?graph=https://blaze-server.org/fhir/GraphDefinition/PatientPurge
```

## Internal Resources <Badge type="warning" text="Since 1.14.0"/>

Resources Blaze owns itself can carry the security label `internal` with the system `https://blaze-server.org/fhir/CodeSystem/AccessControl` in `meta.security`. Such resources can't be purged. If the patient compartment contains one, the operation fails with an OperationOutcome with code `conflict` and purges no resource at all.

[1]: <https://build.fhir.org/patient-operation-purge.html>
