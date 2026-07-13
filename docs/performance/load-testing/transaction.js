import http from 'k6/http';
import exec from 'k6/execution';
import { Counter, Trend } from 'k6/metrics';
import { fail } from 'k6';

const base = __ENV.BASE;
const duration = __ENV.DURATION || 60;

function createScenario(vus, offset) {
	return {
		executor: 'constant-vus',
		vus: vus,
		startTime: `${duration * offset + offset}s`,
		duration: `${duration}s`
	};
}

export const options = {

	setupTimeout: '300s',
	insecureSkipTLSVerify: true,
	discardResponseBodies: true,

	summaryTrendStats: ['med', 'p(95)', 'p(99)'],

	scenarios: {
		c1: createScenario(1, 0),
		c2: createScenario(2, 1),
		c4: createScenario(4, 2),
		c8: createScenario(8, 3),
		c16: createScenario(16, 4),
		c32: createScenario(32, 5)
	}
};

const params = {
	headers: {
		'Accept': 'application/fhir+json',
		'Content-Type': 'application/fhir+json'
	},
	tags: {
		name: 'transaction'
	}
};

function createMetric(vus) {
  return {
    requests: new Counter(`requests_c${vus}`),
    responseTime: new Trend(`response_time_c${vus}`, true)
  };
}

const metric = {
	'c1': createMetric(1),
	'c2': createMetric(2),
	'c4': createMetric(4),
	'c8': createMetric(8),
	'c16': createMetric(16),
	'c32': createMetric(32)
};

// A minimal UUID v4 generator so each transaction creates fresh resources with
// unique bundle-internal URLs. No external module needed.
function uuidv4() {
	return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
		const r = Math.random() * 16 | 0;
		const v = c === 'x' ? r : (r & 0x3 | 0x8);
		return v.toString(16);
	});
}

function randomInt(min, max) {
	return Math.floor(Math.random() * (max - min + 1)) + min;
}

function pad2(n) {
	return n < 10 ? `0${n}` : `${n}`;
}

// A random date between 1920-01-01 and 2020-12-28. The day is capped at 28 so
// every generated date is valid regardless of month.
function randomBirthDate() {
	return `${randomInt(1920, 2020)}-${pad2(randomInt(1, 12))}-${pad2(randomInt(1, 28))}`;
}

// A small transaction bundle: one Patient and one Observation that references
// the Patient via a bundle-internal URN. This is the external analog of the
// internal `transact-test` benchmark and exercises reference resolution.
//
// The Patient's birthDate and the Observation's systolic blood pressure are
// randomized per transaction so the date and quantity search-param indices see
// realistic value spread rather than a single repeated entry.
function createBundle() {
	const patientUrn = `urn:uuid:${uuidv4()}`;
	return JSON.stringify({
		resourceType: 'Bundle',
		type: 'transaction',
		entry: [
			{
				fullUrl: patientUrn,
				resource: {
					resourceType: 'Patient',
					gender: 'male',
					birthDate: randomBirthDate()
				},
				request: { method: 'POST', url: 'Patient' }
			},
			{
				fullUrl: `urn:uuid:${uuidv4()}`,
				resource: {
					resourceType: 'Observation',
					status: 'final',
					subject: { reference: patientUrn },
					code: {
						coding: [{ system: 'http://loinc.org', code: '8480-6' }]
					},
					valueQuantity: {
						value: randomInt(90, 180),
						unit: 'mmHg',
						system: 'http://unitsofmeasure.org',
						code: 'mm[Hg]'
					}
				},
				request: { method: 'POST', url: 'Observation' }
			}
		]
	});
}

export default function() {
	exec.vu.tags['vus_active'] = exec.instance.vusActive;

	const resp = http.post(base, createBundle(), params);

	if (resp.status !== 200) fail(`non 200 response code ${resp.status}`);

	const m = metric[exec.scenario.name];
	m.requests.add(1);
	m.responseTime.add(resp.timings.waiting);
}

function resultLine(vus, data) {
  const requests = data.metrics[`requests_c${vus}`];
  const responseTime = data.metrics[`response_time_c${vus}`];
  const med = responseTime.values.med;
  const p95 = responseTime.values['p(95)'];
  const p99 = responseTime.values['p(99)'];
  return `${vus},${requests.values.count / duration},${med},${p95},${p99}\n`;
}

export function handleSummary(data) {
  return {
    [`data/transaction.csv`]:
      resultLine(1, data)
      + resultLine(2, data)
      + resultLine(4, data)
      + resultLine(8, data)
      + resultLine(16, data)
      + resultLine(32, data)
  };
}
