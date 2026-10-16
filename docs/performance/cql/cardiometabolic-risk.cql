library "cardiometabolic-risk"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem sct: 'http://snomed.info/sct'
codesystem loinc: 'http://loinc.org'
codesystem act: 'http://terminology.hl7.org/CodeSystem/v3-ActCode'

valueset "Diabetes or Prediabetes": 'http://fhir.org/VCL?v1=(http://snomed.info/sct)(44054006;15777000)'

code "Hypertension": '59621000' from sct
code "HbA1c": '4548-4' from loinc
code "BMI": '39156-5' from loinc
code "Blood Pressure": '85354-9' from loinc
code "Systolic Blood Pressure": '8480-6' from loinc
code "Inpatient": 'IMP' from act
code "Emergency": 'EMER' from act

context Patient

define "Metabolic Diagnosis":
  [Condition: "Diabetes or Prediabetes"] D
    where AgeInYearsAt(D.onset as dateTime) between 18 and 75

define "Obesity":
  [Observation: "BMI"] O
    where O.value >= 30 'kg/m2'

define "Elevated HbA1c":
  [Observation: "HbA1c"] O
    where O.value > 6.0 '%'

define "Elevated Systolic Blood Pressure":
  [Observation: "Blood Pressure"] O
    where exists (O.component C
      where C.code ~ ToConcept("Systolic Blood Pressure")
        and C.value > 130 'mm[Hg]')

define "Hospitalization":
  [Encounter] E
    where E.class ~ "Inpatient" or E.class ~ "Emergency"

define "Index Diagnosis":
  "Metabolic Diagnosis" D
    with "Obesity" O
      such that (O.effective as dateTime) within 1 year of (D.onset as dateTime)
    with "Elevated HbA1c" A
      such that (A.effective as dateTime) 2 years or less after (D.onset as dateTime)
    with [Condition: "Hypertension"] H
      such that (H.onset as dateTime) on or before (D.onset as dateTime) + 1 year
    with "Elevated Systolic Blood Pressure" P
      such that (P.effective as dateTime) within 1 year of (D.onset as dateTime)
    with "Hospitalization" E
      such that E.period.start 5 years or less after (D.onset as dateTime)

define InInitialPopulation:
  exists "Index Diagnosis"
