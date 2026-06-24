import type { Task } from 'fhir/r4';

import { describe, expect, it } from 'vitest';
import { base, oldBase } from './canonical.js';
import { number, output, statusReason, toJob } from './jobs.js';

// A minimal re-index job on `b` (either the current or the legacy base), as
// stored by Blaze.
function reIndexJob(b: string): Task {
  return {
    resourceType: 'Task',
    id: 'AAAAAAAAAAAAAAAA',
    meta: { lastUpdated: '2024-04-13T10:05:20.927Z' },
    identifier: [{ system: `${b}/sid/JobNumber`, value: '1' }],
    status: 'ready',
    intent: 'order',
    code: {
      coding: [
        {
          system: `${b}/CodeSystem/JobType`,
          code: 're-index',
          display: '(Re)Index a Search Parameter'
        }
      ]
    },
    authoredOn: '2024-04-13T10:05:20.927Z'
  };
}

describe.each([
  ['current', base],
  ['legacy', oldBase]
])('reads %s canonicals', (_name, b) => {
  it('number', () => {
    expect(number(reIndexJob(b))).toBe(1);
  });

  it('statusReason', () => {
    const job = reIndexJob(b);
    job.statusReason = { coding: [{ system: `${b}/CodeSystem/JobStatusReason`, code: 'started' }] };
    expect(statusReason(job)).toBe('started');
  });

  it('output', () => {
    const job = reIndexJob(b);
    job.output = [
      {
        type: { coding: [{ system: `${b}/CodeSystem/JobOutput`, code: 'error' }] },
        valueString: 'boom'
      }
    ];
    expect(output(job, `${base}/CodeSystem/JobOutput`, 'error')?.valueString).toBe('boom');
  });

  it('toJob extracts the type and error', () => {
    const job = reIndexJob(b);
    job.output = [
      {
        type: { coding: [{ system: `${b}/CodeSystem/JobOutput`, code: 'error' }] },
        valueString: 'boom'
      }
    ];
    expect(toJob(job)).toStrictEqual({
      id: 'AAAAAAAAAAAAAAAA',
      lastUpdated: '2024-04-13T10:05:20.927Z',
      number: 1,
      status: 'ready',
      statusReason: undefined,
      type: { code: 're-index', display: '(Re)Index a Search Parameter' },
      authoredOn: '2024-04-13T10:05:20.927Z',
      error: 'boom'
    });
  });
});
