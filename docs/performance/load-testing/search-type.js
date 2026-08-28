import http from 'k6/http';
import exec from 'k6/execution';
import { Counter, Trend } from 'k6/metrics';
import { fail } from 'k6';

const base = __ENV.BASE;
const duration = __ENV.DURATION || 60;
const system = __ENV.SYSTEM;
const warmup = __ENV.WARMUP || 100;

// The patients the searches are built from: PATIENTS of them, sampled from the
// first POOL patients of the type index. Reading a prefix of the index instead
// of the whole dataset keeps the setup short, and sampling inside that prefix
// keeps the working set from being one contiguous range of it.
const pool = Number(__ENV.POOL || 100000);
const patients = Number(__ENV.PATIENTS || 10000);

// The concurrency levels of the sweep, one scenario each, run one after the
// other.
const sweep = [1, 2, 4, 8, 16, 32, 64, 128];

// A single level can be selected via the VUS env var, so that the server can be
// profiled at one operating point.
const singleLevel = __ENV.VUS ? Number(__ENV.VUS) : null;

if (singleLevel !== null && !(singleLevel > 0)) {
	fail(`VUS has to be a positive number but was ${__ENV.VUS}`);
}

// Only a sweep writes the CSV, because a single level can't fill it. So the
// system is only needed for a sweep and a single level run can't overwrite the
// committed data of a sweep.
if (singleLevel === null && !system) {
	fail('SYSTEM env var is required, e.g. SYSTEM=A5N46');
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

	setupTimeout: '900s',
	insecureSkipTLSVerify: true,
	discardResponseBodies: true,

	summaryTrendStats: ['med', 'p(95)', 'p(99)'],

	scenarios: createScenarios()
};

const params = {
	headers: {
		'Accept': 'application/fhir+json'
	},
	tags: {
		name: 'search-type'
	}
};

// Setup reads the bodies it fetches, so it can't run with the discarded
// response bodies of the measured requests.
const setupParams = {
	headers: {
		'Accept': 'application/fhir+json'
	},
	responseType: 'text',
	tags: {
		name: 'setup'
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

// All Observations of one Patient as a single page, without `_elements`, so the
// full resources are serialized. With the 1M dataset that is about 600
// Observations per request, which fits in one page.
function search(patientIds) {
	const id = patientIds[Math.floor(Math.random() * patientIds.length)];

	return http.get(`${base}/Observation?patient=${id}&_count=1000`, params);
}

function readPatientIds(url) {
	const resp = http.get(url, setupParams);

	if (resp.status !== 200) fail(`non 200 response code ${resp.status} while reading patients from ${url}`);

	const body = resp.json();

	if (body.entry === undefined) fail('the server has to contain patients but contains none');

	const nextLink = body.link.find((link) => link.relation === 'next');

	return {
		patientIds: body.entry.map((e) => e.resource.id),
		url: nextLink !== undefined ? nextLink.url : undefined
	};
}

// Reads the first POOL patient ids from the front of the type index.
function readPatientIdPool() {
	let url = `${base}/Patient?_elements=id&_count=1000`;
	let result = [];

	while (url !== undefined && result.length < pool) {
		const data = readPatientIds(url);
		result = result.concat(data.patientIds);
		url = data.url;
	}

	return result;
}

// Draws n elements without replacement, via a partial Fisher-Yates shuffle.
function sample(values, n) {
	const result = values.slice();

	for (let i = 0; i < Math.min(n, result.length); i++) {
		const j = i + Math.floor(Math.random() * (result.length - i));
		const tmp = result[i];
		result[i] = result[j];
		result[j] = tmp;
	}

	return result.slice(0, n);
}

// Collects the patients the requests are built from and warms the server up, so
// that the first scenario doesn't measure a cold system.
export function setup() {
	const patientIdPool = readPatientIdPool();

	console.log(`read ${patientIdPool.length} patients`);

	const patientIds = sample(patientIdPool, patients);

	if (patientIds.length === 0) fail('the server has to contain patients but contains none');

	console.log(`sampled ${patientIds.length} patients`);

	console.log(`warmup with ${warmup} requests...`);

	for (let i = 0; i < warmup; i++) {
		const resp = search(patientIds);

		if (resp.status !== 200) fail(`non 200 response code ${resp.status} during warmup`);
	}

	console.log('warmup finished');

	return { patientIds: patientIds };
}

export default function({ patientIds }) {
	exec.vu.tags['vus_active'] = exec.instance.vusActive;

	const resp = search(patientIds);

	if (resp.status !== 200) fail(`non 200 response code ${resp.status}`);

	const m = metric[exec.scenario.name];
	m.requests.add(1);
	// The whole response, not only the time to first byte, because the server
	// flushes its first buffer partway through generating the bundle.
	m.responseTime.add(resp.timings.duration);
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
	// k6 leaves a metric out of the summary entirely as long as it has no
	// samples, so a run that failed in setup or was aborted would crash the
	// lines below with a TypeError. That error is printed before the one that
	// actually ended the run, so it would hide it. Bail out instead, leaving
	// the committed CSV alone.
	const empty = levels.some((vus) => data.metrics[`response_time_c${vus}`] === undefined);

	if (empty) {
		return { stdout: 'no CSV written because some concurrency levels have no samples, see the error below\n' };
	}

	const lines = levels.map((vus) => resultLine(vus, data)).join('');

	// A single level run reports on stdout instead, so that it leaves the
	// committed CSV of the sweep alone.
	return singleLevel === null
		? { [`data/search-type-${system}.csv`]: lines }
		: { stdout: lines };
}
