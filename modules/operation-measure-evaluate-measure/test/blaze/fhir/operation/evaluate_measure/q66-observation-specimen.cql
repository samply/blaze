library "q66-observation-specimen"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem loinc: 'http://loinc.org'
codesystem snomed: 'http://snomed.info/sct'

context Patient

define "Specimen 258503004":
  (from [Specimen: Code '258503004' from snomed] S
    where ToDate(S.collection.collected as dateTime) in Interval[@0001-01-01T, @2026-02-12T] or
      S.collection.collected overlaps Interval[@0001-01-01T, @2026-02-12T]) union
  (from [Specimen: Code '472862007' from snomed] S
    where ToDate(S.collection.collected as dateTime) in Interval[@0001-01-01T, @2026-02-12T] or
      S.collection.collected overlaps Interval[@0001-01-01T, @2026-02-12T]) union
  (from [Specimen: Code '258505006' from snomed] S
    where ToDate(S.collection.collected as dateTime) in Interval[@0001-01-01T, @2026-02-12T] or
      S.collection.collected overlaps Interval[@0001-01-01T, @2026-02-12T]) union
  (from [Specimen: Code '472885008' from snomed] S
    where ToDate(S.collection.collected as dateTime) in Interval[@0001-01-01T, @2026-02-12T] or
      S.collection.collected overlaps Interval[@0001-01-01T, @2026-02-12T])

define Criterion:
  exists (from [Observation: Code '252318005' from snomed] O
    with "Specimen 258503004" S
      such that O.specimen.reference contains 'Specimen/' + S.id
    where O.value.as(CodeableConcept) ~ Code '10828004' from snomed and
      O.code ~ Code '50697-2' from loinc and
      ToDate(O.effective as dateTime) in Interval[@0001-01-01T, @2026-02-12T])

define InInitialPopulation:
  Criterion
