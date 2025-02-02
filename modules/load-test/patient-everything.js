import http from 'k6/http';
import exec from 'k6/execution';
import { fail } from 'k6';
import { randomItem } from 'https://jslib.k6.io/k6-utils/1.3.0/index.js';

export const options = {

	setupTimeout: '300s',
	insecureSkipTLSVerify: true,
	discardResponseBodies: true,

	stages: [
		{ duration: '30s', target: 16 },
		{ duration: '300s', target: 16 },
		{ duration: '10s', target: 0 }
	]
};

const base = __ENV.BASE;
const accessToken = __ENV.ACCESS_TOKEN;

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
	responseType: "text",
	tags: {
		name: 'search-type'
	}
};

export default function({ patientIds }) {
	exec.vu.tags['vus_active'] = exec.instance.vusActive;

	const id = randomItem(patientIds);

	http.get(`${base}/Patient/${id}/$everything?_count=1000`, everythingParams);
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
