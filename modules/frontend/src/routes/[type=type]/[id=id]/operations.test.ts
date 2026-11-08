import { describe, it, expect } from 'vitest';
import type {
  CapabilityStatement,
  CapabilityStatementRestResource,
  CapabilityStatementRestResourceOperation
} from 'fhir/r4';
import { instanceOperations } from './operations.js';

function operation(name: string): CapabilityStatementRestResourceOperation {
  return { name, definition: `http://hl7.org/fhir/OperationDefinition/${name}` };
}

function capabilityStatement(
  resources: { type: CapabilityStatementRestResource['type']; operations?: string[] }[]
): CapabilityStatement {
  return {
    resourceType: 'CapabilityStatement',
    status: 'active',
    date: '2026-11-07',
    kind: 'instance',
    fhirVersion: '4.0.1',
    format: ['json'],
    rest: [
      {
        mode: 'server',
        resource: resources.map(({ type, operations }) => ({
          type,
          ...(operations && { operation: operations.map(operation) })
        }))
      }
    ]
  };
}

describe('instanceOperations test', () => {
  it('returns $graph for every type supporting it', () => {
    const cs = capabilityStatement([
      { type: 'Patient', operations: ['everything', 'graph'] },
      { type: 'Encounter', operations: ['graph'] },
      { type: 'Observation', operations: ['graph'] }
    ]);
    expect(instanceOperations(cs, 'Patient')).toStrictEqual(['graph']);
    expect(instanceOperations(cs, 'Encounter')).toStrictEqual(['graph']);
    expect(instanceOperations(cs, 'Observation')).toStrictEqual(['graph']);
  });
  it('returns the terminology operations with a page in order', () => {
    const cs = capabilityStatement([
      { type: 'CodeSystem', operations: ['validate-code', 'lookup', 'graph'] },
      { type: 'ValueSet', operations: ['validate-code', 'expand', 'graph'] }
    ]);
    expect(instanceOperations(cs, 'CodeSystem')).toStrictEqual([
      'lookup',
      'validate-code',
      'graph'
    ]);
    expect(instanceOperations(cs, 'ValueSet')).toStrictEqual(['expand', 'validate-code', 'graph']);
  });
  it("doesn't return an operation with a page only for other types", () => {
    const cs = capabilityStatement([{ type: 'Measure', operations: ['lookup', 'expand'] }]);
    expect(instanceOperations(cs, 'Measure')).toStrictEqual([]);
  });
  it("doesn't return operations the CapabilityStatement doesn't list", () => {
    const cs = capabilityStatement([
      { type: 'Patient', operations: ['everything'] },
      { type: 'Encounter' }
    ]);
    expect(instanceOperations(cs, 'Patient')).toStrictEqual([]);
    expect(instanceOperations(cs, 'Encounter')).toStrictEqual([]);
    expect(instanceOperations(cs, 'Observation')).toStrictEqual([]);
  });
  it('returns no operations for a CapabilityStatement without rest', () => {
    const cs: CapabilityStatement = { ...capabilityStatement([]), rest: undefined };
    expect(instanceOperations(cs, 'Patient')).toStrictEqual([]);
  });
});
