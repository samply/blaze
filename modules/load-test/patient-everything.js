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

const base = 'https://blaze.srv.local/fhir';
//const base = 'http://localhost:8080/fhir';
//const base = 'http://blaze-test-host:8080/fhir';
const accessToken = 'eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJ5VmMwcnVQZjdrMDgxN2JWMWF0ZFoycWpJUUFqYnR3RUpiZklvZ3k3aElzIn0.eyJleHAiOjE3Mjc2MjQ2MjUsImlhdCI6MTcyNzYyMTAyNSwianRpIjoiMzdlMTc1MjgtODc1ZC00Njc2LTljZjAtODYxMjE4NWQ0NTUxIiwiaXNzIjoiaHR0cHM6Ly9rZXljbG9hay5zcnYubG9jYWwvcmVhbG1zL2JsYXplIiwic3ViIjoiNzJhN2YzN2UtYjMzZi00OTA4LTlhZDktMzNiZTBkNGMxNjIwIiwidHlwIjoiQmVhcmVyIiwiYXpwIjoiYWNjb3VudCIsInJlc291cmNlX2FjY2VzcyI6eyJhY2NvdW50Ijp7InJvbGVzIjpbIm1hbmFnZS1hY2NvdW50IiwibWFuYWdlLWFjY291bnQtbGlua3MiLCJ2aWV3LXByb2ZpbGUiXX19LCJzY29wZSI6ImVtYWlsIHByb2ZpbGUiLCJjbGllbnRJZCI6ImFjY291bnQiLCJjbGllbnRIb3N0IjoiMTcyLjE4LjAuMyIsImVtYWlsX3ZlcmlmaWVkIjpmYWxzZSwicHJlZmVycmVkX3VzZXJuYW1lIjoic2VydmljZS1hY2NvdW50LWFjY291bnQiLCJjbGllbnRBZGRyZXNzIjoiMTcyLjE4LjAuMyJ9.KGRpmMd_dmGilvZskup-o7bW-Cwn5gPgnKshUN6EhtyH47WQyofIgKYekx3LiLYgyvYO8QW0LTwdmWAkW55WLQxq84G-AiwyfYiItaaoJuIWOBrGZu59ATNualVxhoCHUEJy9W3Ndkajp9ZXDyonvOY2AxtSxlJG6RdBeIQ2b4_zjRAdKqziG3e6g9ufZIlil0uO3ZRZVR-zd19mRXXmG_zCceP_fHFbRvyth4w8lm0M_L5HBQRatvkUHFCaXL2HGO6VLUTEPywLKpXdX403cL7mu7CebGUXwBa9XybacOScBVJzwjPcwQq-3RRnrIaPltQ-zBboP8hLyGhyOLYygg';

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
