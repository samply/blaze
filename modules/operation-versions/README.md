# Module - Operation - Versions

This operation returns the list of supported FHIR versions and the default version.

The operation is defined on system level:

```
URL: [base]/$versions
```

The output is a [Parameters][1] resource with one `version` parameter for each supported FHIR version (using `major.minor` form like `4.0`) and one `default` parameter with the default FHIR version of the server.

See: <https://hl7.org/fhir/R4/capabilitystatement-operation-versions.html>

[1]: <http://hl7.org/fhir/parameters.html>
