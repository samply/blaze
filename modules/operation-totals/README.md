# Module - Operation - Totals

**This operation is currently private. Please do not use it.**

This operation retrieves the total counts of resources available by resource type.

The operation is defined on system level:

```
URL: [base]/$totals
```

The output is a [Parameters][1] resource were the name of each parameter is the resource type like `Patient` and the value is the total count of resources of that type as [unsignedInt][2].

[1]: <http://hl7.org/fhir/parameters.html>
[2]: <https://hl7.org/fhir/datatypes.html#unsignedInt>
