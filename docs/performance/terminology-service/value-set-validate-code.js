import http from 'k6/http';
import exec from 'k6/execution';
import { Counter, Trend } from 'k6/metrics';
import { fail } from 'k6';

const base = __ENV.BASE;
const testName = __ENV.TEST_NAME;
const valueSet = __ENV.VALUE_SET;
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

	summaryTrendStats: ['med', 'p(95)', 'p(99)'],

	scenarios: {
		c5: createScenario(5, 0),
		c10: createScenario(10, 1),
		c20: createScenario(20, 2),
		c30: createScenario(30, 3),
		c40: createScenario(40, 4),
		c50: createScenario(50, 5),
		c60: createScenario(60, 6),
		c70: createScenario(70, 7),
		c80: createScenario(80, 8)
	}
};

const commonHeaders = {
	'Accept': 'application/fhir+json'
};

const params = {
	headers: commonHeaders,
	tags: {
		name: 'validate-code'
	}
};

function createMetric(vus) {
	return {
		requests: new Counter(`requests_c${vus}`),
		responseTime: new Trend(`response_time_c${vus}`, true)
	};
}

const metric = {
	'c5': createMetric(5),
	'c10': createMetric(10),
	'c20': createMetric(20),
	'c30': createMetric(30),
	'c40': createMetric(40),
	'c50': createMetric(50),
	'c60': createMetric(60),
	'c70': createMetric(70),
	'c80': createMetric(80)
};

export default function({ codings }) {
	const { system, code } = codings[Math.floor(Math.random() * codings.length)];

	const resp = http.get(`${base}/ValueSet/\$validate-code?url=${valueSet}&system=${system}&code=${code}`, params);

	if (resp.status !== 200) fail(`non 200 response code ${resp.status}`);

	const body = resp.json();
	const result = body.parameter.find(p => p.name === 'result').valueBoolean;
	if (!result) fail(`result of code ${code} was false`);

	const m = metric[exec.scenario.name];
	m.requests.add(1);
	m.responseTime.add(resp.timings.waiting);
}

export function setup() {
	const resp = http.get(`${base}/ValueSet/\$expand?url=${valueSet}`, {
		headers: commonHeaders
	});

	if (resp.status !== 200) fail(`non 200 response code ${resp.status} while expanding the body-site value set`);

	const body = resp.json();
	const codings = body.expansion.contains;

	console.log(`found ${codings.length} concepts`);

	const ratio = 1000 / codings.length;
	const sampledCodings = codings.filter(c => Math.random() < ratio);

	console.log(`sampled ${sampledCodings.length} random concepts`);

	return { codings: sampledCodings };
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
		[`data/value-set-validate-code-${testName}.csv`]:
		resultLine(5, data)
		+ resultLine(10, data)
		+ resultLine(20, data)
		+ resultLine(30, data)
		+ resultLine(40, data)
		+ resultLine(50, data)
		+ resultLine(60, data)
		+ resultLine(70, data)
		+ resultLine(80, data)
	};
}
