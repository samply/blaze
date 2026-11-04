# Operation \$purge on Patient <Badge type="info" text="Feature: OPERATION_PATIENT_PURGE"/> <Badge type="warning" text="Since 0.30.1"/>

> [!CAUTION]
> The operation \$purge on patient is currently **alpha** and has to be enabled explicitly by setting the env var `ENABLE_OPERATION_PATIENT_PURGE` to true. Please don't use it in production. Please be aware that the database might not be able to migrate to a newer version of Blaze if the operation was used.

> [!CAUTION]
> The operation \$purge on patient is trial use in the unreleased next version of FHIR (R6). So it is subject to change. Please use it with care.

The patient \$purge operation is used to request the removal of all current and historical versions for all resources in a patient compartment. The return is an OperationOutcome with results and/or details about execution. The `code` of the first issue of the OperationOutcome is either `success` on successful purging of all resources or an error.

```
POST [base]/Patient/[id]/$purge
```

All resources in the patient compartment are marked as purged immediately. All reads which happen after the \$purge operation completes, will not see any of the purged resources. Only active [paging sessions](../../api.md#paging-sessions) and [asynchronous requests](../../api.md#asynchronous-requests) started before the patient \$purge operation will be able to access the purged versions for a limited amount of time.  

The official documentation can be found [here][1].

## Purged Resources

The \$purge operation purges the Patient resource itself and every resource whose **current version** is in the [patient compartment][2] instance of the patient, as defined by the search parameters of the Patient CompartmentDefinition. Of each of these resources, all versions are purged, regardless of whether a particular version was in the compartment instance of the patient or not.

Resources whose current version isn't in the compartment instance of the patient aren't purged, even if earlier versions of them were. The following sections describe the two cases in which this happens and why Blaze keeps these resources.

### Reassigned Resources

A resource whose current version is in the compartment instance of another patient keeps all its versions, including the ones that reference the purged patient. That happens if the resource was moved to another patient, for example by a Patient \$merge or by correcting a resource that was assigned to the wrong patient.

The history of such a resource is part of the record of the patient it belongs to now. For example, a lab Observation is often first recorded with status `preliminary` and later updated to `final`. If that Observation is moved to another patient and the old patient is purged afterwards, purging the `preliminary` version would remove it from the history of the Observation, and the remaining patient would lose data.

* **After a merge**, both patients are the same person. The history is data of the remaining patient and has to stay available.
* **After correcting a misassignment**, the history documents the correction in the record of the other patient. Obligations to keep that record, including its changes, usually outweigh the erasure of the versions referencing the purged patient.

The history in FHIR is a transaction time history: it shows the versions in the order the server recorded them. These versions are immutable, so neither a \$merge nor a correction can rewrite them to reference the new patient. Even bitemporal databases, which can correct the valid time of a fact, keep the old reference in their transaction time history unless the whole entity is erased.

### Deleted Resources

A resource that was [deleted](../interaction/delete.md) before the \$purge operation isn't purged. Its current version is the delete marker, which isn't in any compartment instance. Because a delete keeps the history of a resource, the earlier versions remain, including their content and their reference to the purged patient. They can still be accessed by [versioned read](../interaction/vread.md) and are listed in the [instance history](../interaction/history-instance.md), the [type history](../interaction/history-type.md) and the [system history](../interaction/history-system.md). So they can be found without knowing the id of the resource.

Deleting a resource is often not the right way to handle data that turned out to be wrong. As the FHIR specification [notes][3], many resources have a status element that overlaps with the idea of deletion, and its [Entered in Error Summary][4] lists, for each resource type, how to handle data entered in error. For example, an Observation that should never have existed gets the status `entered-in-error`, and an Observation with a wrong result that was fixed gets the status `corrected`. Unlike a delete, setting the status keeps the resource in the compartment instance of the patient, so it is purged together with the patient.

### Avoiding and Removing Remaining Versions

To avoid or remove the versions that remain in these cases:

* Prefer setting the status of a resource, like `entered-in-error`, over deleting it, as described [above](#deleted-resources).
* Purge the patient **before** deleting any of its resources. Then all resources are still in the compartment instance of the patient and are purged with all their versions.
* Use the [delete history](../interaction/delete-history.md) interaction on the affected resources. It removes all versions except the current one. For a deleted resource, only the delete marker remains, which contains no data.

## Right to Erasure

The \$purge operation can be used to implement the right to erasure of Art. 17 GDPR. Please consider the following points:

* **Responsibility.** Whether an erasure is required, or whether an exception of Art. 17(3) GDPR applies, like a legal obligation to keep medical records, is the decision of the controller. Blaze only provides the means to purge the data.
* **Remaining versions.** The versions of deleted resources described [above](#deleted-resources) are personal data of the purged patient. They have to be removed separately, if required.
* **Compartment.** The GDPR defines personal data by its content, not by FHIR compartments. The patient compartment covers only the references in the search parameters of the Patient CompartmentDefinition. References or other information about the patient elsewhere, for example in extensions or in narrative text, aren't found by the \$purge operation.

[1]: <https://build.fhir.org/patient-operation-purge.html>
[2]: <https://www.hl7.org/fhir/R4/compartmentdefinition-patient.html>
[3]: <https://hl7.org/fhir/R4/http.html#delete>
[4]: <https://hl7.org/fhir/R4/lifecycle.html#error>
