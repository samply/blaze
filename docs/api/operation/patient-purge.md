# Operation \$purge on Patient <Badge type="info" text="Feature: OPERATION_PATIENT_PURGE"/> <Badge type="warning" text="Since 0.30.1"/> <Badge type="tip" text="Stable 1.14.0"/>

> [!NOTE]
> The operation \$purge on Patient is an optional feature that has to be enabled by setting [`ENABLE_OPERATION_PATIENT_PURGE`](../../deployment/environment-variables.md#enable-operation-patient-purge) to `true`. Before 1.14.0, it was **alpha** and shouldn't be used in production.

The \$purge operation removes all current and historical versions of one patient and of all [selected resources](#selected-resources). The result is an OperationOutcome.

```
POST [base]/Patient/[id]/$purge
```

Blaze supports the operation only at instance level and only via `POST`.

The patient and all selected resources are marked as purged immediately. All reads which happen after the \$purge operation completes, will not see any of the purged resources. Only active [paging sessions](../../api.md#paging-sessions) and [asynchronous requests](../../api.md#asynchronous-requests) started before the patient \$purge operation will be able to access the purged versions for a limited amount of time.

The FHIR specification can be found [here][1].

## Selected Resources <Badge type="warning" text="Since 1.14.0"/>

The following table lists the search parameters of each resource type of the [Patient compartment](https://hl7.org/fhir/R4/compartmentdefinition-patient.html), which Blaze used before 1.14.0, and the search parameter of the purge GraphDefinition [`https://blaze-server.org/fhir/GraphDefinition/PatientPurge`](/fhir/GraphDefinition-PatientPurge.html){target="_self"}. A resource is purged if its current version references the patient through the search parameter of the purge GraphDefinition (see [Current and Historical Versions](#current-and-historical-versions)). Resources of the types without one (—) aren't purged, except the patient itself.

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
| Provenance                  | `patient`                                             | —                     |
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

Resources of a type that can reference several patients, like a Provenance through its targets, aren't purged, so that purging one patient never erases a resource about another one. So with referential integrity enforced, a Provenance targeting the patient makes the operation fail. The Provenances targeting the patient can be found by a search:

```
GET [base]/Provenance?patient=[id]
```

Each one that is only about the patient can be [deleted](../interaction/delete.md) before the purge. If all of them are only about the patient, a [conditional delete](../interaction/delete-type.md) deletes them at once:

```
DELETE [base]/Provenance?patient=[id]
```

By default, the conditional delete only deletes a single resource. If more than one Provenance targets the patient, it fails with `412 Precondition Failed`, unless the [environment variable](../../deployment/environment-variables.md#allow-multiple-delete) `ALLOW_MULTIPLE_DELETE` is set to `true`.

The resources a purge would erase can be previewed with the [\$graph](graph.md) operation:

```
GET [base]/Patient/[id]/$graph?graph=https://blaze-server.org/fhir/GraphDefinition/PatientPurge
```

## Current and Historical Versions <Badge type="warning" text="Since 1.14.0"/>

The selection looks at the current version of each resource only. A selected resource is purged with all of its versions, including historical versions that referenced another patient. A resource whose current version doesn't reference the patient isn't touched, even if some of its historical versions do. These are resources assigned to another patient and deleted resources.

### Resources Assigned to Another Patient

A resource whose current version references another patient keeps all of its versions, including the ones that reference the purged patient. That happens after two patients, that turned out to be the same person, were merged, or after a resource assigned to the wrong patient was corrected:

* **Merged patients:** both patients are the same person, so the history of the resource, like a `preliminary` Observation followed by its `final` version, is data of the remaining patient. So the patient merged into the remaining one can be purged after the merge without losing any data of the remaining patient, including the history of its resources.
* **Corrected assignment:** the resource was always about the other patient, so neither its current nor its historical versions are data about the purged patient. Only the versions before the correction keep the reference to the purged patient, which carries nothing but its id. The history is part of the record of the other patient and documents the correction. Retention obligations for that record usually outweigh the erasure of the references to the purged patient.

> [!TIP]
> Ids of patients should never contain directly identifying information, like the name, the date of birth or the insurance number of the patient. Then the references left over after a purge don't reveal who the patient was. Ids assigned by Blaze on [create](../interaction/create.md) contain no information about the patient. Clients choosing ids on [update](../interaction/update.md) should follow the same rule, for example by using UUIDs.

The history of a resource records the versions as they were written. A historical version can't be changed afterwards, so neither a merge nor a correction can make it reference the other patient.

### Deleted Resources

A resource deleted before the purge isn't purged. A delete doesn't remove the versions of a resource. It adds a new version, the deletion marker, which has no content. The deletion marker is the current version of the resource, and it doesn't reference any patient. The earlier versions, including their content and the reference to the purged patient, stay readable via [versioned read](../interaction/vread.md), [instance history](../interaction/history-instance.md), [type history](../interaction/history-type.md) and [system history](../interaction/history-system.md).

To prevent that, purge the patient instead of deleting its resources. A resource that still references the patient is selected and purged with all of its versions.

Deleting is often the wrong way to handle wrong data anyway. Many resources have a status element that overlaps with the idea of deletion (see [delete](https://hl7.org/fhir/R4/http.html#delete) and [Entered in Error Summary](https://hl7.org/fhir/R4/lifecycle.html#error) in the FHIR specification). A wrong Observation, for example, should get the status `entered-in-error` instead of being deleted. It still references the patient, so it's purged together with the patient.

### Removing Left-Over Versions

After a purge, versions that reference the patient can remain in the history of resources assigned to another patient and of deleted resources. The [delete history](../interaction/delete-history.md) interaction removes all versions of such a resource except the current one. For a deleted resource, the current version is the deletion marker, so no data of the patient remains. The [delete history version](../interaction/delete-history-version.md) interaction removes a single historical version.

## Erasure of Personal Data <Badge type="warning" text="Since 1.14.0"/>

The operation can serve requests for the erasure of personal data under Art. 17 of the [GDPR](https://eur-lex.europa.eu/eli/reg/2016/679/oj), but it doesn't guarantee that all data about the patient is gone. The GDPR defines personal data by its content, while the operation selects resources by their references to the patient. It doesn't find data about the patient outside the [selected resources](#selected-resources), like a reference in an extension, a name in the narrative text or a note of a resource of another patient, nor the versions described in [Current and Historical Versions](#current-and-historical-versions).

Whether an erasure is required, and whether an exception of Art. 17(3) GDPR applies, like for the history of a resource assigned to another patient, is up to the controller. Blaze only provides the means.

## Internal Resources <Badge type="warning" text="Since 1.14.0"/>

Resources Blaze owns itself can carry the security label `internal` with the system [`https://blaze-server.org/fhir/CodeSystem/AccessControl`](/fhir/CodeSystem-AccessControl.html){target="_self"} in `meta.security`. Such resources can't be purged. If the patient or one of the [selected resources](#selected-resources) carries the label, the operation fails with an OperationOutcome with code `conflict` and purges no resource at all.

## Response

On success the operation returns a `200 OK` together with an OperationOutcome whose first issue has the code `success`. That is also the case if the patient doesn't exist. The following errors can occur:

| Status | Issue Code | Reason                                                                                                                                                                      |
|--------|------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 409    | conflict   | Referential integrity is enforced and a resource that isn't purged still references the patient or one of the purged resources. See [Selected Resources](#selected-resources). |
| 409    | conflict   | The patient or one of the selected resources is internal. See [Internal Resources](#internal-resources).                                                                    |

If an error occurs, no resource is purged.

## Examples

Preview the resources a purge of the patient with the id `0` would erase:

```sh
curl -s "http://localhost:8080/fhir/Patient/0/\$graph?graph=https://blaze-server.org/fhir/GraphDefinition/PatientPurge"
```

Purge that patient:

```sh
curl -s -X POST "http://localhost:8080/fhir/Patient/0/\$purge"
```

[1]: <https://build.fhir.org/patient-operation-purge.html>
