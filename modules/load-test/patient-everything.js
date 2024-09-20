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
		{ duration: '30s', target: 16 },
		{ duration: '300s', target: 16 },
		{ duration: '10s', target: 0 }
	]
};

const base = 'https://blaze.srv.local/fhir';
//const base = 'http://localhost:8080/fhir';
//const base = 'http://blaze-test-host:8080/fhir';
const accessToken = 'eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJ5VmMwcnVQZjdrMDgxN2JWMWF0ZFoycWpJUUFqYnR3RUpiZklvZ3k3aElzIn0.eyJleHAiOjE3MjY4MTQ5MTMsImlhdCI6MTcyNjgxMTMxMywianRpIjoiOGE3NDMyMWYtZDgzNy00NmY2LTlmNDQtODM4OTM2MWU1Y2EzIiwiaXNzIjoiaHR0cHM6Ly9rZXljbG9hay5zcnYubG9jYWwvcmVhbG1zL2JsYXplIiwic3ViIjoiNzJhN2YzN2UtYjMzZi00OTA4LTlhZDktMzNiZTBkNGMxNjIwIiwidHlwIjoiQmVhcmVyIiwiYXpwIjoiYWNjb3VudCIsInJlc291cmNlX2FjY2VzcyI6eyJhY2NvdW50Ijp7InJvbGVzIjpbIm1hbmFnZS1hY2NvdW50IiwibWFuYWdlLWFjY291bnQtbGlua3MiLCJ2aWV3LXByb2ZpbGUiXX19LCJzY29wZSI6ImVtYWlsIHByb2ZpbGUiLCJjbGllbnRJZCI6ImFjY291bnQiLCJjbGllbnRIb3N0IjoiMTcyLjE4LjAuNCIsImVtYWlsX3ZlcmlmaWVkIjpmYWxzZSwicHJlZmVycmVkX3VzZXJuYW1lIjoic2VydmljZS1hY2NvdW50LWFjY291bnQiLCJjbGllbnRBZGRyZXNzIjoiMTcyLjE4LjAuNCJ9.OahQzB5-dBZww3qAdR1_HV3wsuvAU0HnbQAvZl1Nt61E3TfBCrfDVBZxFEoXFBNL-9NJUwDd5IonbF7GyumgyNs9B7oplKaJ71X6q6sE5lLMz2jFWCax0-CPovCQlHtubkO8p9OR1CiGPIldGUhz_K1uQsDGSVfo1QAY0Z_QuFohxz-mqqxy2Dk9I-WcIS_ypjLsBBO2TqaajSt6nn8mt-ZNUpmDEM-TME7ac2Mum42AOtkIVE_sToZCBOQCQu9TTFrgiYWWVYyBxJIyv3NHrGRImdluorQV-9fAdM1RJLjWV5lPqqLiivljr000IkQsk88a6nbmj84rWWTjKBU33A';

const commonHeaders = {
	'Accept': 'application/fhir+json',
	'Accept-Encoding': 'gzip',
	'Authorization': `Bearer ${accessToken}`
};

const everythingParams = {
	headers: commonHeaders,
	tags: {
		name: 'everything'
	},
};

const searchTypeParams = {
	headers: commonHeaders,
	tags: {
		name: 'search-type'
	}
};

export default function({ patientIds }) {
	const id = randomItem(patientIds);

	const res = http.get(`${base}/Patient/${id}/$everything?_count=1000`, everythingParams);

	check(res, {
		'response code was 200': (res) => res.status === 200
	});

	const body = res.json();

	check(body, {
		'response body is an searchset Bundle': (body) => body.resourceType === 'Bundle' && body.type === 'searchset',
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
