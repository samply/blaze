# Conformance

Blaze implements the [RESTful FHIR API](https://www.hl7.org/fhir/http.html) and claims its capabilities in a [CapabilityStatement](https://www.hl7.org/fhir/capabilitystatement.html).

A current CapabilityStatement can be fetched from [https://blaze.life.uni-leipzig.de/fhir/metadata](https://blaze.life.uni-leipzig.de/fhir/metadata) (user/password: demo)

## CQL

The state of the implementation of CQL can be found [here](conformance/cql.md).

## Control Character Handling

Blaze supports storage and retrieval of control characters in the JSON format using escaping. In XML however, the characters `[\x00-\x08\x0B\x0C\x0E-\x1F]` are replaced by a question mark `?` before output.

## Conformance Tests

### Touchstone

Touchstone test results can be found [here](https://touchstone.aegis.net/touchstone/conformance/history?suite=FHIR4-0-1-Basic-Server&supportedOnly=true&suiteType=HL7_FHIR_SERVER&ownedBy=ALL&ps=10&published=true&pPass=0&strSVersion=1&format=ALL).

### Crucible

Crucible test results can be found here: [Blaze](https://projectcrucible.org/servers/5cdd7fde04ebd042c8000000)
