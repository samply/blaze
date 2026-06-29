# Validation — Overview

Since v1.11.0, Blaze can validate the FHIR resources written to it. Every resource
of an incoming create, update or transaction/batch interaction is forwarded to an
external validator **before** it is persisted. Depending on the configured failure
mode, invalid resources are either tagged or rejected.

Blaze does not contain its own structural or profile validator — the validation is
performed by an external validator such as the [MII FHIR Validator][1]. The
external validator performs the profile validation and, in turn, needs its own
terminology server to resolve `ValueSet` bindings. That terminology server does
**not** have to be Blaze — any FHIR terminology server works. For example, a setup
with Blaze as the main data server and [Ontoserver][2] as the terminology server is
just as valid as one where a second Blaze instance provides the terminology.

For the feature toggle, the failure modes and a full deployment example, see the
[External Validator](external-validator.md) section.

> [!TIP]
> Looking to validate resources with a standalone validator yourself, using Blaze
> as the terminology server? See the
> [Terminology Service – Validation](../terminology-service/validation.md) section.

[1]: <https://github.com/medizininformatik-initiative/mii-fhir-validator>
[2]: <https://ontoserver.csiro.au/>
