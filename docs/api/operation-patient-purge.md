# Operation Patient Purge

> [!CAUTION]
> The patient $purge operation is trial use in the unreleased next version of FHIR. So it is subject to change. Please use it with care.

The patient \$purge operation is used to request the removal of all current and historical versions for all resources in a patient compartment. The return is an OperationOutcome with results and/or details about execution. The `code` of the first issue of the OperationOutcome is either `success` on successful purging of all resources or an error.

```
POST [base]/Patient/[id]/$purge
```

All resources in the patient compartment are marked as purged immediately. All reads which happen after the \$purge operation completes, will not see any of the purged resources. Only active [paging sessions](../api.md#paging-sessions) and [asynchronous requests](../api.md#asynchronous-requests) started before the patient \$purge operation will be able to access the purged versions for a limited amount of time.  

The official documentation can be found [here][1].

[1]: <https://build.fhir.org/patient-operation-purge.html>
