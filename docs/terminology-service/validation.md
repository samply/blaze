# Validation

Blaze's terminology service is designed to work together with FHIR validators. It
can act as the **terminology server** of a validator that runs separately from
Blaze: the validator validates a resource against an implementation guide and
delegates every `ValueSet` binding check to Blaze. Blaze itself does not validate
the resource — it only answers the terminology questions the validator asks.

Validators that can use Blaze as their terminology server include:

* the standalone [FHIR Validator][1], and
* the [MII FHIR Validator][4].

> [!TIP]
> To validate resources on their way **into** Blaze instead of running a validator
> yourself, see the [Validation](../validation/index.md) section, which describes
> Blaze's ingress validation via an external validator.

## Download the FHIR Validator

The FHIR validator can be downloaded from its [GitHub Release Page][2].

## Usage

With Blaze running with an enabled terminology service under
`http://localhost:8080/fhir` and configured as the terminology server of the
standalone [FHIR Validator][1], a Condition resource can be validated against an
implementation guide such as
`de.medizininformatikinitiative.kerndatensatz.diagnose#2025.0.0`. Blaze resolves
all `ValueSet` bindings and the validator reports any terminology errors. See the
[FHIR Validator documentation][1] for how to configure its terminology server.

> [!NOTE]
> Since v6.5.21 of the validator, Blaze has to be explicitly authorised as a
> terminology server because it currently doesn't pass all test cases defined in
> the [FHIR Terminology Ecosystem IG][3]. The progress can be followed in the
> issue [#2635](https://github.com/samply/blaze/issues/2635).

[1]: <https://confluence.hl7.org/spaces/FHIR/pages/35718580/Using+the+FHIR+Validator>
[2]: <https://github.com/hapifhir/org.hl7.fhir.core/releases/latest>
[3]: <https://build.fhir.org/ig/HL7/fhir-tx-ecosystem-ig/testcases.html>
[4]: <https://github.com/medizininformatik-initiative/mii-fhir-validator>
