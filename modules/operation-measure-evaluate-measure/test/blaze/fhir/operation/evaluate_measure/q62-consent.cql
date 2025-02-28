library "q62-consent"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem consent_policy: 'urn:oid:2.16.840.1.113883.3.1937.777.24.5.3'

context Patient

define InInitialPopulation:
  exists
    from [Consent] C
    where exists from C.provision.provision.code C where C ~ Code '2.16.840.1.113883.3.1937.777.24.5.3.8' from consent_policy
