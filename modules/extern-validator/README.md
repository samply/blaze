# Module Extern Validator

This module forwards incoming resources to an external FHIR validator (like the [MII FHIR Validator](https://github.com/medizininformatik-initiative/mii-fhir-validator)) for schema and profile validation prior to persistence. Depending on the configured failure mode, invalid resources are either tagged as invalid (optionally storing the validator's OperationOutcome as a contained resource) or rejected with an HTTP error.
