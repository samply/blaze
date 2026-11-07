import { describe, it, expect } from 'vitest';
import type { Parameters } from 'fhir/r4';
import {
  grandTotal,
  patientTotal,
  rankedByTotal,
  resourceTypeTotals
} from './resource-type-totals.js';

const parameters: Parameters = {
  resourceType: 'Parameters',
  parameter: [
    { name: 'Condition', valueUnsignedInt: 30 },
    { name: 'Observation', valueUnsignedInt: 100 },
    { name: 'Patient', valueUnsignedInt: 10 },
    { name: 'Library' }
  ]
};

describe('resourceTypeTotals test', () => {
  it('extracts the totals', () => {
    expect(resourceTypeTotals(parameters)).toEqual([
      { name: 'Condition', total: 30 },
      { name: 'Observation', total: 100 },
      { name: 'Patient', total: 10 },
      { name: 'Library', total: 0 }
    ]);
  });
  it('returns an empty list on missing parameters', () => {
    expect(resourceTypeTotals({ resourceType: 'Parameters' })).toEqual([]);
  });
});

describe('rankedByTotal test', () => {
  it('sorts by total descending and by name on equal totals', () => {
    const totals = [
      { name: 'B', total: 1 },
      { name: 'C', total: 5 },
      { name: 'A', total: 1 }
    ];

    expect(rankedByTotal(totals).map((t) => t.name)).toEqual(['C', 'A', 'B']);
  });
  it('does not modify its argument', () => {
    const totals = [
      { name: 'B', total: 1 },
      { name: 'C', total: 5 }
    ];
    rankedByTotal(totals);
    expect(totals[0].name).toBe('B');
  });
});

describe('grandTotal test', () => {
  it('sums all totals', () => {
    expect(grandTotal(resourceTypeTotals(parameters))).toBe(140);
  });
});

describe('patientTotal test', () => {
  it('returns the Patient total', () => {
    expect(patientTotal(resourceTypeTotals(parameters))).toBe(10);
  });
  it('returns zero without Patients', () => {
    expect(patientTotal([])).toBe(0);
  });
});
