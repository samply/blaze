import http from 'k6/http';
import { check, fail } from 'k6';
import { randomItem } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

export const options = {

	thresholds: {
		http_req_failed: [{ threshold: 'rate<0.01' }], // http errors should be less than 0.1%
		http_req_duration: ['p(99)<100'] // 99% of requests should be below 100ms
	},

	setupTimeout: '300s',
	insecureSkipTLSVerify: true,

	summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)', 'p(99)', 'count'],

	stages: [
		{ duration: '30s', target: 64 },
		{ duration: '300s', target: 64 },
		{ duration: '10s', target: 0 }
	]
};

const base = 'https://blaze.srv.local/fhir';
//const base = 'http://localhost:8080/fhir';
//const base = 'http://blaze-test-host:8080/fhir';
const accessToken = 'eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJ5VmMwcnVQZjdrMDgxN2JWMWF0ZFoycWpJUUFqYnR3RUpiZklvZ3k3aElzIn0.eyJleHAiOjE3MjY3NzM4MzIsImlhdCI6MTcyNjc3MDIzMiwianRpIjoiOWVhODk2MzYtNGMzNy00NWMyLTkwMDQtZjA0MDkyYjA2YzhiIiwiaXNzIjoiaHR0cHM6Ly9rZXljbG9hay5zcnYubG9jYWwvcmVhbG1zL2JsYXplIiwic3ViIjoiNzJhN2YzN2UtYjMzZi00OTA4LTlhZDktMzNiZTBkNGMxNjIwIiwidHlwIjoiQmVhcmVyIiwiYXpwIjoiYWNjb3VudCIsInJlc291cmNlX2FjY2VzcyI6eyJhY2NvdW50Ijp7InJvbGVzIjpbIm1hbmFnZS1hY2NvdW50IiwibWFuYWdlLWFjY291bnQtbGlua3MiLCJ2aWV3LXByb2ZpbGUiXX19LCJzY29wZSI6ImVtYWlsIHByb2ZpbGUiLCJjbGllbnRJZCI6ImFjY291bnQiLCJjbGllbnRIb3N0IjoiMTcyLjE4LjAuNCIsImVtYWlsX3ZlcmlmaWVkIjpmYWxzZSwicHJlZmVycmVkX3VzZXJuYW1lIjoic2VydmljZS1hY2NvdW50LWFjY291bnQiLCJjbGllbnRBZGRyZXNzIjoiMTcyLjE4LjAuNCJ9.gC4lWwCsW-tA_hYLyNjafb10wTmYYKKxmAzAFIeIWHjMmqD5mj2Vg8vkOK8Dy_7DfeHPtMP3_Yavqwd7b3ggdvyJwg3gdMVZniLDBwf0Jt5v_ldih6MXn19l2WfUsfucMFWCkZsAekApB7Vp2s6dKZcwvc3noA5uCBSn2sSaUt8xFoSBOsOgQRNVw2U4pT7eVzyjz5bXC_ZlTqyfQAtmU2k0pedhFZ0U4m3L-zhydSgz-wfEhaDyWjC1IgM5Q8kUj9gVoQtFS6zZ_959J6BOXdnq6ozMLlxZSXjGJ0EH1zxu0pWJMA-WU8C96VhsmxD6w8GynXB2b9SQeqOoxeXlAw';

const commonHeaders = {
	'Accept': 'application/fhir+json',
	'Accept-Encoding': 'gzip',
	'Authorization': `Bearer ${accessToken}`
};

const readParams = {
	headers: commonHeaders,
	tags: {
		name: 'read'
	}
};

const searchTypeParams = {
	headers: commonHeaders,
	tags: {
		name: 'search-type'
	}
};

export default function({ patientIds }) {
	const id = randomItem(patientIds);

	const res = http.get(`${base}/Patient/${id}`, readParams);

	check(res, {
		'response code was 200': (res) => res.status === 200
	});

	const body = res.json();

	check(body, {
		'response body is a Patient': (body) => body.resourceType === 'Patient',
		'Patient id': (body) => body.id === id
	});
}

function readPatientIds(url) {
	const res = http.get(url, searchTypeParams);

	if (res.status !== 200) fail('non 200 response code while reading all patients');

	const body = res.json();

	const nextLink = body.link.find((link) => link.relation === 'next');

	return {
		patientIds: body.entry.map((e) => e.resource.id),
		url: nextLink !== undefined ? nextLink.url : undefined
	};
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
	return { patientIds: patientIds };
}
