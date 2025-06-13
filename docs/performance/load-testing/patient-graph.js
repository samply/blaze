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
		c32: createScenario(32, 5),
		c64: createScenario(64, 6)
	}
};

const commonHeaders = {
	'Accept': 'application/fhir+json'
};

const graphParams = {
	headers: commonHeaders,
	tags: {
		name: 'graph'
	},
};

const searchTypeParams = {
	headers: commonHeaders,
	responseType: "text",
	tags: {
		name: 'search-type'
	}
};

function createMetric(vus) {
  return {
    requests: new Counter(`requests_c${vus}`),
    responseTime: new Trend(`response_time_c${vus}`, true)
  };
}

function readPatientIds(url) {
	const res = http.get(url, searchTypeParams);

	if (res.status !== 200) fail(`non 200 response code ${res.status} while reading all patients`);

	const body = res.json();

	const nextLink = body.link.find((link) => link.relation === 'next');

	return {
		patientIds: body.entry.map((e) => e.resource.id),
		url: nextLink !== undefined ? nextLink.url : undefined
	};
}

const metric = {
	'c1': createMetric(1),
	'c2': createMetric(2),
	'c4': createMetric(4),
	'c8': createMetric(8),
	'c16': createMetric(16),
	'c32': createMetric(32),
	'c64': createMetric(64)
};

export default function({ patientIds }) {
  exec.vu.tags['vus_active'] = exec.instance.vusActive;

  const id = patientIds[Math.floor(Math.random() * patientIds.length)];

  const resp = http.get(`${base}/Patient/${id}/$graph?graph=patient-observation-encounter`, graphParams);

  if (resp.status !== 200) fail(`non 200 response code ${resp.status}`);

  const m = metric[exec.scenario.name];
  m.requests.add(1);
  m.responseTime.add(resp.timings.waiting);
}

export function setup() {
	let url = `${base}/Patient?_elements=id&_count=10000`;
	let patientIds = [];
	while (url !== undefined) {
		const data = readPatientIds(url);
		patientIds = patientIds.concat(data.patientIds);
		url = data.url;
	}

	console.log('number of patients:', patientIds.length);

  const ratio = 10000 / patientIds.length;
  const sampledPatientIds = patientIds.filter(id => Math.random() < ratio);

  console.log(`sampled ${sampledPatientIds.length} random patient IDs`);

	return { patientIds: sampledPatientIds };
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
    [`data/patient-graph.csv`]:
      resultLine(1, data)
      + resultLine(2, data)
      + resultLine(4, data)
      + resultLine(8, data)
      + resultLine(16, data)
      + resultLine(32, data)
      + resultLine(64, data)
  };
}
