import type {
  Library,
  Measure,
  MeasureReport,
  MeasureReportGroupStratifierStratum,
  Parameters
} from 'fhir/r4';

/**
 * The CQL of the patient demographics measure.
 *
 * It puts every patient into the initial population and stratifies it by ten
 * year age classes, with the last class open ended, and by gender.
 */
export const demographicsCql = `library "blaze-frontend-patient-demographics"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

context Patient

define InInitialPopulation:
  true

define Age:
  AgeInYears()

define AgeClass:
  if Age >= 90 then 90 else (Age div 10) * 10

define Gender:
  Patient.gender
`;

export const demographicsMeasureUrl = 'https://blaze-server.org/fhir/Measure/patient-demographics';

const evalDurationUrl = 'https://blaze-server.org/fhir/StructureDefinition/eval-duration';

const lastAgeClass = 90;

function cqlIdentifier(expression: string) {
  return { language: 'text/cql-identifier', expression };
}

function demographicsLibrary(): Library {
  return {
    resourceType: 'Library',
    id: 'library',
    status: 'active',
    type: {
      coding: [
        { system: 'http://terminology.hl7.org/CodeSystem/library-type', code: 'logic-library' }
      ]
    },
    content: [{ contentType: 'text/cql', data: btoa(demographicsCql) }]
  };
}

function demographicsMeasure(): Measure {
  return {
    resourceType: 'Measure',
    url: demographicsMeasureUrl,
    status: 'active',
    subjectCodeableConcept: {
      coding: [{ system: 'http://hl7.org/fhir/resource-types', code: 'Patient' }]
    },
    contained: [demographicsLibrary()],
    library: ['#library'],
    scoring: {
      coding: [{ system: 'http://terminology.hl7.org/CodeSystem/measure-scoring', code: 'cohort' }]
    },
    group: [
      {
        population: [
          {
            code: {
              coding: [
                {
                  system: 'http://terminology.hl7.org/CodeSystem/measure-population',
                  code: 'initial-population'
                }
              ]
            },
            criteria: cqlIdentifier('InInitialPopulation')
          }
        ],
        stratifier: [
          {
            component: [
              { code: { text: 'age-class' }, criteria: cqlIdentifier('AgeClass') },
              { code: { text: 'gender' }, criteria: cqlIdentifier('Gender') }
            ]
          }
        ]
      }
    ]
  };
}

/**
 * Returns the parameters of an `$evaluate-measure` request evaluating the
 * patient demographics measure inline, so that nothing has to be stored.
 *
 * The measurement period isn't used by the CQL. The age is calculated at the
 * time of evaluation.
 */
export function demographicsParameters(year: number): Parameters {
  return {
    resourceType: 'Parameters',
    parameter: [
      { name: 'periodStart', valueDate: String(year) },
      { name: 'periodEnd', valueDate: String(year) },
      { name: 'measure', resource: demographicsMeasure() }
    ]
  };
}

export function ageClassLabel(ageClass: number): string {
  return ageClass >= lastAgeClass ? `${ageClass}+` : `${ageClass}–${ageClass + 9}`;
}

export interface AgeClassCounts {
  /** the lower bound of the age class in years */
  ageClass: number;
  label: string;
  male: number;
  female: number;
}

export interface Demographics {
  /** the number of all patients */
  total: number;
  /** all age classes in ascending order */
  ageClasses: AgeClassCounts[];
  /** the number of patients per gender, with `missing` for patients without gender */
  genderTotals: Record<string, number>;
  /** the number of patients without a known age */
  unknownAge: number;
  /** the number of patients not in `ageClasses`, because of their gender or unknown age */
  notShown: number;
  evalDurationSeconds?: number;
}

function componentValue(
  stratum: MeasureReportGroupStratifierStratum,
  code: string
): string | undefined {
  return stratum.component?.find((c) => c.code.text === code)?.value.text;
}

/** The text of a stratum value of an expression evaluating to null. */
const nullValue = 'null';

/** Returns the age class of `value` or undefined if it's not a known age class. */
function parseAgeClass(value: string | undefined): number | undefined {
  const ageClass = Number(value);
  return value === undefined || value === nullValue || !Number.isInteger(ageClass) || ageClass < 0
    ? undefined
    : Math.min(ageClass, lastAgeClass);
}

function emptyAgeClasses(): AgeClassCounts[] {
  return Array.from({ length: lastAgeClass / 10 + 1 }, (_, i) => ({
    ageClass: i * 10,
    label: ageClassLabel(i * 10),
    male: 0,
    female: 0
  }));
}

function evalDurationSeconds(report: MeasureReport): number | undefined {
  return report.extension?.find((e) => e.url === evalDurationUrl)?.valueQuantity?.value;
}

/**
 * Parses the MeasureReport of the patient demographics measure.
 */
export function parseDemographics(report: MeasureReport): Demographics {
  const group = report.group?.[0];
  const ageClasses = emptyAgeClasses();
  const genderTotals: Record<string, number> = {};
  let unknownAge = 0;
  let shown = 0;

  for (const stratum of group?.stratifier?.[0]?.stratum ?? []) {
    const count = stratum.population?.[0]?.count ?? 0;
    const genderValue = componentValue(stratum, 'gender');
    const gender = genderValue === undefined || genderValue === nullValue ? 'missing' : genderValue;
    const ageClass = parseAgeClass(componentValue(stratum, 'age-class'));

    genderTotals[gender] = (genderTotals[gender] ?? 0) + count;

    if (ageClass === undefined) {
      unknownAge += count;
    } else if (gender === 'male' || gender === 'female') {
      ageClasses[ageClass / 10][gender] += count;
      shown += count;
    }
  }

  const total = group?.population?.[0]?.count ?? 0;

  return {
    total,
    ageClasses,
    genderTotals,
    unknownAge,
    notShown: total - shown,
    evalDurationSeconds: evalDurationSeconds(report)
  };
}
