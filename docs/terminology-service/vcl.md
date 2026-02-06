# ValueSet Compose Language (VCL) <Badge type="warning" text="Since 1.5.0"/>

> [!CAUTION]
> The VCL standard is curently in development. The implementation in currently **beta**.
 
The VCL documentation can be found [here](https://build.fhir.org/ig/FHIR/ig-guidance/vcl.html).

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
