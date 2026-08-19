import http from 'k6/http';
import exec from 'k6/execution';
import { Counter, Trend } from 'k6/metrics';
import { fail } from 'k6';

const base = __ENV.BASE;
const duration = __ENV.DURATION || 60;
const system = __ENV.SYSTEM;
const warmup = __ENV.WARMUP || 1000;

// The concurrency levels of the sweep, one scenario each, run one after the
// other.
const sweep = [1, 2, 4, 8, 16, 32, 64, 128];

// A single level can be selected via the VUS env var, so that the server can be
// profiled at one operating point. The sweep would blur it, because the batch
// sizes of the transaction log and the resource store grow with the number of
// concurrent clients, so each level has its own ratio of per-batch to per-entry
// work.
const singleLevel = __ENV.VUS ? Number(__ENV.VUS) : null;

if (singleLevel !== null && !(singleLevel > 0)) {
	fail(`VUS has to be a positive number but was ${__ENV.VUS}`);
}

// Only a sweep writes the CSV, because a single level can't fill it. So the
// system is only needed for a sweep and a single level run can't overwrite the
// committed data of a sweep.
if (singleLevel === null && !system) {
	fail('SYSTEM env var is required, e.g. SYSTEM=LEA47');
}

const levels = singleLevel === null ? sweep : [singleLevel];

function createScenario(vus, offset) {
	return {
		executor: 'constant-vus',
		vus: vus,
		startTime: `${duration * offset + offset}s`,
		duration: `${duration}s`
	};
}

function createScenarios() {
	const scenarios = {};
	levels.forEach((vus, offset) => {
		scenarios[`c${vus}`] = createScenario(vus, offset);
	});
	return scenarios;
}

export const options = {

	setupTimeout: '300s',
	insecureSkipTLSVerify: true,
	discardResponseBodies: true,

	summaryTrendStats: ['med', 'p(95)', 'p(99)'],

	scenarios: createScenarios()
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

const countParams = {
	headers: {
		'Accept': 'application/fhir+json'
	},
	responseType: 'text',
	tags: {
		name: 'count'
	}
};

function createMetric(vus) {
  return {
    requests: new Counter(`requests_c${vus}`),
    responseTime: new Trend(`response_time_c${vus}`, true)
  };
}

const metric = {};

levels.forEach((vus) => {
	metric[`c${vus}`] = createMetric(vus);
});

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

function postBundle() {
	return http.post(base, createBundle(), params);
}

function patientCount() {
	const resp = http.get(`${base}/Patient?_summary=count`, countParams);

	if (resp.status !== 200) fail(`non 200 response code ${resp.status} while counting patients`);

	return resp.json().total;
}

// Executes a number of transactions before the measured scenarios start so that
// the JIT compiler, the resource caches and the index column families are warm.
// Otherwise the c1 scenario, which runs first, would measure a cold system and
// so wouldn't be comparable to the later, higher-concurrency scenarios.
export function setup() {
	// The transaction test creates resources itself, so its throughput depends on
	// how much data the server already holds. Require an essentially empty server
	// so runs stay comparable across systems and repetitions.
	const count = patientCount();

	if (count !== 0) fail(`the server has to be empty but contains ${count} patients`);

	console.log(`warmup with ${warmup} transactions...`);

	for (let i = 0; i < warmup; i++) {
		const resp = postBundle();

		if (resp.status !== 200) fail(`non 200 response code ${resp.status} during warmup`);
	}

	console.log('warmup finished');
}

export default function() {
	exec.vu.tags['vus_active'] = exec.instance.vusActive;

	const resp = postBundle();

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
  const lines = levels.map((vus) => resultLine(vus, data)).join('');

  // A single level run reports on stdout instead, so that it leaves the
  // committed CSV of the sweep alone.
  return singleLevel === null
    ? { [`data/transaction-${system}.csv`]: lines }
    : { stdout: lines };
}
