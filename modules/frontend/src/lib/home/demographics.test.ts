import { describe, it, expect } from 'vitest';
import type { MeasureReport, MeasureReportGroupStratifierStratum, Measure, Library } from 'fhir/r4';
import {
  ageClassLabel,
  demographicsCql,
  demographicsMeasureUrl,
  demographicsParameters,
  parseDemographics
} from './demographics.js';

function stratum(
  ageClass: string,
  gender: string,
  count: number
): MeasureReportGroupStratifierStratum {
  return {
    component: [
      { code: { text: 'age-class' }, value: { text: ageClass } },
      { code: { text: 'gender' }, value: { text: gender } }
    ],
    population: [{ count }]
  };
}

function report(strata: MeasureReportGroupStratifierStratum[], total: number): MeasureReport {
  return {
    resourceType: 'MeasureReport',
    status: 'complete',
    type: 'summary',
    measure: demographicsMeasureUrl,
    period: { start: '2026', end: '2026' },
    extension: [
      {
        url: 'https://blaze-server.org/fhir/StructureDefinition/eval-duration',
        valueQuantity: { value: 0.123, unit: 's' }
      }
    ],
    group: [{ population: [{ count: total }], stratifier: [{ stratum: strata }] }]
  };
}

describe('demographicsParameters test', () => {
  const parameters = demographicsParameters(2026);

  it('uses the given year as measurement period', () => {
    expect(parameters.parameter?.find((p) => p.name === 'periodStart')?.valueDate).toBe('2026');
    expect(parameters.parameter?.find((p) => p.name === 'periodEnd')?.valueDate).toBe('2026');
  });

  it('contains the inline Measure with a contained Library holding the CQL', () => {
    const measure = parameters.parameter?.find((p) => p.name === 'measure')?.resource as Measure;

    expect(measure.resourceType).toBe('Measure');
    expect(measure.url).toBe(demographicsMeasureUrl);
    expect(measure.library).toEqual(['#library']);

    const library = measure.contained?.[0] as Library;
    expect(library.resourceType).toBe('Library');
    expect(library.id).toBe('library');
    expect(library.content?.[0].contentType).toBe('text/cql');
    expect(atob(library.content?.[0].data ?? '')).toBe(demographicsCql);
  });

  it('stratifies by age class and gender', () => {
    const measure = parameters.parameter?.find((p) => p.name === 'measure')?.resource as Measure;
    const components = measure.group?.[0].stratifier?.[0].component;

    expect(components?.map((c) => [c.code?.text, c.criteria.expression])).toEqual([
      ['age-class', 'AgeClass'],
      ['gender', 'Gender']
    ]);
  });
});

describe('ageClassLabel test', () => {
  it('labels ten year classes', () => {
    expect(ageClassLabel(0)).toBe('0–9');
    expect(ageClassLabel(40)).toBe('40–49');
  });
  it('labels the last class open ended', () => {
    expect(ageClassLabel(90)).toBe('90+');
  });
});

describe('parseDemographics test', () => {
  it('returns all age classes zero filled in ascending order', () => {
    const demographics = parseDemographics(report([], 0));

    expect(demographics.ageClasses.map((c) => c.label)).toEqual([
      '0–9',
      '10–19',
      '20–29',
      '30–39',
      '40–49',
      '50–59',
      '60–69',
      '70–79',
      '80–89',
      '90+'
    ]);
    expect(demographics.ageClasses.every((c) => c.male === 0 && c.female === 0)).toBe(true);
    expect(demographics.total).toBe(0);
    expect(demographics.notShown).toBe(0);
  });

  it('counts male and female patients per age class', () => {
    const demographics = parseDemographics(
      report([stratum('10', 'male', 3), stratum('10', 'female', 4), stratum('90', 'female', 1)], 8)
    );

    expect(demographics.ageClasses[1]).toEqual({
      ageClass: 10,
      label: '10–19',
      male: 3,
      female: 4
    });
    expect(demographics.ageClasses[9]).toEqual({ ageClass: 90, label: '90+', male: 0, female: 1 });
    expect(demographics.total).toBe(8);
  });

  it('counts patients with other or missing gender or unknown age as not shown', () => {
    const demographics = parseDemographics(
      report(
        [
          stratum('10', 'male', 3),
          stratum('10', 'other', 2),
          stratum('20', 'unknown', 1),
          stratum('20', 'null', 4),
          stratum('null', 'female', 5)
        ],
        15
      )
    );

    expect(demographics.ageClasses[1].male).toBe(3);
    expect(demographics.ageClasses[2]).toEqual({
      ageClass: 20,
      label: '20–29',
      male: 0,
      female: 0
    });
    expect(demographics.notShown).toBe(12);
    expect(demographics.genderTotals).toEqual({
      male: 3,
      female: 5,
      other: 2,
      unknown: 1,
      missing: 4
    });
    expect(demographics.unknownAge).toBe(5);
  });

  it('treats negative age classes as unknown age', () => {
    const demographics = parseDemographics(report([stratum('-10', 'male', 2)], 2));

    expect(demographics.unknownAge).toBe(2);
    expect(demographics.notShown).toBe(2);
  });

  it('returns the evaluation duration', () => {
    expect(parseDemographics(report([], 0)).evalDurationSeconds).toBe(0.123);
  });

  it('returns no evaluation duration if absent', () => {
    const withoutExtension = { ...report([], 0), extension: undefined };
    expect(parseDemographics(withoutExtension).evalDurationSeconds).toBeUndefined();
  });

  it('handles a report without groups', () => {
    const demographics = parseDemographics({ ...report([], 0), group: undefined });

    expect(demographics.total).toBe(0);
    expect(demographics.ageClasses).toHaveLength(10);
  });
});
