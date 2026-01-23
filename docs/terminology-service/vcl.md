# ValueSet Compose Language (VCL) <Badge type="warning" text="unreleased"/>

> [!CAUTION]
> The VCL standard is curently in development. The implementation in currently **beta**.

The VCL documentation can be found [here](https://build.fhir.org/ig/FHIR/ig-guidance/vcl.html).

## Operations

The following operations support VCL:

* [ValueSet \$expand](../api/operation/value-set-expand.md)
* [ValueSet \$validate-code](../api/operation/value-set-validate-code.md)

## Usage

ValueSets can be created on-the-fly by using the ValueSet Compose Language (VCL) at any place where a ValueSet URL can be specified.

A VCL implicit value set URL has two parts:

* The base URL which is `http://fhir.org/VCL`
* A query portion that specifies the VCL expression itself: `?v1=<expression>` where `<expression>` is the percent-encoded VCL expression.

**Example:**

To use the VCL expression `(http://loinc.org)COMPONENT=LP14449-0`, the full URL would be:

`http://fhir.org/VCL?v1=(http://loinc.org)COMPONENT=LP14449-0` (properly encoded in actual usage).

### Examples

| Expression                                          | URL                                                                        |
|-----------------------------------------------------|----------------------------------------------------------------------------|
| `(http://loinc.org)(parent^{LP46821-2,LP259418-4})` | `http://fhir.org/VCL?v1=(http://loinc.org)(parent^{LP46821-2,LP259418-4})` |
| `(http://snomed.info/sct)concept<<119297000`        | `http://fhir.org/VCL?v1=(http://snomed.info/sct)concept<<119297000`        |

## Tested VCL Expressions

```
(http://fhir.de/CodeSystem/bfarm/icd-10-gm)concept<<E10
(http://fhir.de/CodeSystem/bfarm/icd-10-gm)concept<<E10-E14

(http://fhir.de/CodeSystem/bfarm/ops)concept<<3-20
(http://fhir.de/CodeSystem/bfarm/ops)concept<<8-19
(http://fhir.de/CodeSystem/bfarm/ops)concept<<5

(http://fhir.de/CodeSystem/bfarm/atc)concept<<A10
(http://fhir.de/CodeSystem/bfarm/atc)concept<<L03AA02

(http://snomed.info/sct)concept<<119297000

(http://loinc.org)COMPONENT=LP14449-0
(http://loinc.org)PROPERTY/"Susc|CCnc"
```
