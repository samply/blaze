import http from 'k6/http';
import { check, fail, group } from 'k6';
import { randomItem, uuidv4 } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

export const options = {

	thresholds: {
		http_req_failed: [{ threshold: 'rate<0.01' }], // http errors should be less than 0.1%
		http_req_duration: ['p(99)<100'] // 99% of requests should be below 100ms
	},

	setupTimeout: '300s',
	insecureSkipTLSVerify: true,

	summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)', 'p(99)', 'count'],

	scenarios: {
		update_patient: {
			executor: 'ramping-arrival-rate',
			exec: 'updatePatient',
			startRate: 1,
			timeUnit: '1s',
			preAllocatedVUs: 5,
			stages: [
				// ramp up
				{ duration: '50s', target: 100 },
				// maintain load
				{ duration: '540s', target: 100 },
				// ramp down to zero
				{ duration: '10s', target: 0 }
			]
		},

		/*delete_patient: {
			executor: 'ramping-arrival-rate',
			exec: 'deletePatient',
			startRate: 1,
			timeUnit: '1s',
			preAllocatedVUs: 1,
			stages: [
				// ramp up
				{ duration: '50s', target: 10 },
				// maintain load
				{ duration: '540s', target: 10 },
				// ramp down to zero
				{ duration: '10s', target: 0 }
			]
		},*/

		delete_patient_history: {
			executor: 'ramping-arrival-rate',
			exec: 'deletePatientHistory',
			startRate: 1,
			timeUnit: '1s',
			preAllocatedVUs: 1,
			stages: [
				// ramp up
				{ duration: '50s', target: 10 },
				// maintain load
				{ duration: '540s', target: 10 },
				// ramp down to zero
				{ duration: '10s', target: 0 }
			]
		},

		read_patient: {
			executor: 'ramping-arrival-rate',
			exec: 'readPatient',
			startRate: 1,
			timeUnit: '1s',
			preAllocatedVUs: 10,
			stages: [
				// ramp up
				{ duration: '50s', target: 1000 },
				// maintain load
				{ duration: '540s', target: 1000 },
				// ramp down to zero
				{ duration: '10s', target: 0 }
			]
		},

		patient_everything: {
			executor: 'ramping-arrival-rate',
			exec: 'patientEverything',
			startRate: 1,
			timeUnit: '1s',
			preAllocatedVUs: 10,
			stages: [
				// ramp up
				{ duration: '50s', target: 10 },
				// maintain load
				{ duration: '540s', target: 10 },
				// ramp down to zero
				{ duration: '10s', target: 0 }
			]
		},

		read_patient_history: {
			executor: 'ramping-arrival-rate',
			exec: 'readPatientHistory',
			startRate: 1,
			timeUnit: '1s',
			preAllocatedVUs: 5,
			stages: [
				// ramp up
				{ duration: '50s', target: 100 },
				// maintain load
				{ duration: '540s', target: 100 },
				// ramp down to zero
				{ duration: '10s', target: 0 }
			]
		},

		read_patients_history: {
			executor: 'ramping-arrival-rate',
			exec: 'readSomePatientsHistory',
			startRate: 1,
			timeUnit: '1s',
			preAllocatedVUs: 5,
			stages: [
				// ramp up
				{ duration: '50s', target: 10 },
				// maintain load
				{ duration: '540s', target: 10 },
				// ramp down to zero
				{ duration: '10s', target: 0 }
			]
		},

		read_patients: {
			executor: 'ramping-arrival-rate',
			exec: 'readSomePatients',
			startRate: 1,
			timeUnit: '1s',
			preAllocatedVUs: 5,
			stages: [
				// ramp up
				{ duration: '50s', target: 10 },
				// maintain load
				{ duration: '540s', target: 10 },
				// ramp down to zero
				{ duration: '10s', target: 0 }
			]
		}
	}
};

const base = 'https://blaze.srv.local/fhir';
//const base = 'http://localhost:8080/fhir';
//const base = 'http://blaze-test-host:8080/fhir';
const accessToken = 'eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJ5VmMwcnVQZjdrMDgxN2JWMWF0ZFoycWpJUUFqYnR3RUpiZklvZ3k3aElzIn0.eyJleHAiOjE3MjY3NDI2NjAsImlhdCI6MTcyNjczOTA2MCwianRpIjoiMjMxMzQwZjAtZWFjNi00ZDI5LWI5MTEtYmIzMWY3NDc1ODY5IiwiaXNzIjoiaHR0cHM6Ly9rZXljbG9hay5zcnYubG9jYWwvcmVhbG1zL2JsYXplIiwic3ViIjoiNzJhN2YzN2UtYjMzZi00OTA4LTlhZDktMzNiZTBkNGMxNjIwIiwidHlwIjoiQmVhcmVyIiwiYXpwIjoiYWNjb3VudCIsInJlc291cmNlX2FjY2VzcyI6eyJhY2NvdW50Ijp7InJvbGVzIjpbIm1hbmFnZS1hY2NvdW50IiwibWFuYWdlLWFjY291bnQtbGlua3MiLCJ2aWV3LXByb2ZpbGUiXX19LCJzY29wZSI6ImVtYWlsIHByb2ZpbGUiLCJjbGllbnRJZCI6ImFjY291bnQiLCJjbGllbnRIb3N0IjoiMTcyLjE4LjAuNSIsImVtYWlsX3ZlcmlmaWVkIjpmYWxzZSwicHJlZmVycmVkX3VzZXJuYW1lIjoic2VydmljZS1hY2NvdW50LWFjY291bnQiLCJjbGllbnRBZGRyZXNzIjoiMTcyLjE4LjAuNSJ9.VEw1c3dEyO8p-lnmxvNgCQAk6l4rGJoRrEZ8zplK6amOyAKhZXDe-Tt5bwQgbTEm0kBCiybM42jq6HWPVVNutstH9_obztuMHLPDW___xNdzxyXcWQKzehR29jxVOAkOOasBGNQCmxdcFvA6os_xs6gAyEQwEaYSInqMxubgc0s4q8V5bV90HvMHxtVar_9yALZU-Mfs4DPayJqoD71tCUQSRg9PKnkMpMW8hlAatZojAOP9Cwnnez5ovrQtyXoAChkw0fzsYXx7SvT9fb82nOIxueqSo72PpOHtW9B5bPAPvkohGbTpoPyqEGjbDmgDARj3emnoz69qnrhcGg7Vew';

const commonHeaders = {
	'Accept': 'application/fhir+json',
	'Accept-Encoding': 'gzip',
	'Authorization': `Bearer ${accessToken}`
};

const readParams = {
	headers: commonHeaders,
	tags: {
		name: 'read'
	},
	responseCallback: http.expectedStatuses(200, 410)
};

const everythingParams = {
	headers: commonHeaders,
	tags: {
		name: 'everything'
	},
};

const updateParams = {
	headers: {
		...commonHeaders,
		'Content-Type': 'application/fhir+json',
		'Prefer': 'return=representation'
	},
	tags: {
		name: 'update'
	}
};

const deleteParams = {
	headers: commonHeaders,
	tags: {
		name: 'delete'
	}
};

const deleteHistoryParams = {
	headers: commonHeaders,
	tags: {
		name: 'delete-history'
	}
};

const searchTypeParams = {
	headers: commonHeaders,
	tags: {
		name: 'search-type'
	}
};

const historyInstanceParams = {
	headers: commonHeaders,
	tags: {
		name: 'history-instance'
	}
};

const historyTypeParams = {
	headers: commonHeaders,
	tags: {
		name: 'history-type'
	}
};

const transactParams = {
	headers: {
		...commonHeaders,
		'Content-Type': 'application/fhir+json',
		'Prefer': 'return=representation'
	},
	tags: {
		name: 'transact'
	}
};

function etagToVersionId(etag) {
	return parseInt(etag.substring(3, etag.length - 1));
}

function areVersionIdsMonotonic(entry, lastVersionId) {
	return entry.reduce((acc, cur) => {
		const versionId = etagToVersionId(cur.response.etag);
		return versionId <= acc ? versionId : 0;
	}, lastVersionId) !== 0;
}

function isValidHistoryBundle(bundle, lastVersionId = Number.MAX_SAFE_INTEGER) {
	return bundle.resourceType === 'Bundle' &&
		bundle.type === 'history' &&
		bundle.entry === undefined || areVersionIdsMonotonic(bundle.entry, lastVersionId);
}

export function readPatient({ patientIds }) {
	group('Read Patient', function() {
		const id = randomItem(patientIds);

		const res = http.get(`${base}/Patient/${id}`, readParams);

		check(res, {
			'response code was 200 or 410': (res) => res.status === 200 || res.status === 410
		});

		const body = res.json();

		check(body, {
			'response body is a Patient or an OperationOutcome': (body) => body.resourceType === 'Patient' || body.resourceType === 'OperationOutcome',
		});
	});
}

export function patientEverything({ patientIds }) {
	group('Patient $everything', function() {
		const id = randomItem(patientIds);

		const res = http.get(`${base}/Patient/${id}/$everything`, everythingParams);

		check(res, {
			'response code was 200': (res) => res.status === 200
		});

		const body = res.json();

		check(body, {
			'response body is an searchset Bundle': (body) => body.resourceType === 'Bundle' && body.type === 'searchset',
		});
	});
}

function readRandomExistingPatient(patientIds) {
	const res = http.get(`${base}/Patient/${randomItem(patientIds)}`, readParams);
	return res.status === 200 ? res.json() : readRandomExistingPatient(patientIds);
}

function updatePatientProperties(patient) {
	if (patient.active === undefined || patient.active === false) {
		return { ...patient, active: true };
	}
	if (patient.deceasedBoolean === undefined || patient.deceasedBoolean === false) {
		return { ...patient, deceasedBoolean: true };
	}
	return patient;
}

export function updatePatient({ patientIds }) {
	group('Update Patient', function() {
		const patient = updatePatientProperties(readRandomExistingPatient(patientIds));

		const payload = JSON.stringify(patient);

		const res = http.put(`${base}/Patient/${patient.id}`, payload, updateParams);

		check(res, {
			'response code was 200': (res) => res.status === 200
		});

		const body = res.json();

		check(body, {
			'response body is a Patient': (body) => body.resourceType === 'Patient',
			'Patient id did not change': (body) => body.id === patient.id,
			'patient is active': (body) => body.active === true
		});
	});
}

export function deletePatient({ patientIds }) {
	group('Delete Patient', function() {
		const id = randomItem(patientIds);

		const res = http.del(`${base}/Patient/${id}`, null, deleteParams);

		check(res, {
			'response code was 204': (res) => res.status === 204
		});
	});
}

export function deletePatientHistory({ patientIds }) {
	group('Delete Patient History', function() {
		const id = randomItem(patientIds);

		const res = http.del(`${base}/Patient/${id}/_history`, null, deleteHistoryParams);

		check(res, {
			'response code was 204': (res) => res.status === 204
		});
	});
}

export function readPatientHistory({ patientIds }) {
	group('Read Patient History', function() {
		const id = randomItem(patientIds);

		const res = http.get(`${base}/Patient/${id}/_history`, historyInstanceParams);

		check(res, {
			'response code was 200': (res) => res.status === 200,
			'response is compressed': (res) => res.headers['Content-Encoding'] === 'gzip',
		});

		const body = res.json();

		check(body, {
			'response body is a history Bundle': (body) => isValidHistoryBundle(body)
		});
	});
}

function patient(mrn) {
	return {
		resourceType: 'Patient',
		identifier: [
			{
				type: {
					coding: [
						{
							system: 'http://terminology.hl7.org/CodeSystem/v2-0203',
							code: 'MR',
							display: 'Medical Record Number'
						}
					],
					text: 'Medical Record Number'
				},
				system: 'http://hospital.smarthealthit.org',
				value: mrn
			}
		],
		gender: randomItem(['male', 'female']),
		birthDate: '2004-10-21'
	};
}

function isValidTransactionResponseBundle(body) {
	return body.resourceType === 'Bundle' && body.type === 'transaction-response';
}

function createPatients(count) {
	const payload = JSON.stringify({
		resourceType: 'Bundle',
		type: 'transaction',
		entry: Array(count).fill('').map((x) => ({
			resource: patient(uuidv4()),
			request: {
				method: 'POST',
				url: 'Patient'
			}
		}))
	});

	const res = http.post(base, payload, transactParams);

	check(res, {
		'transact response code was 200': (res) => res.status === 200
	});

	const body = res.json();

	if (!check(body, {
		'transact response body is a transaction response Bundle': (body) => isValidTransactionResponseBundle(body)
	})) {
		fail('transact response body is not a transaction response Bundle');
	}

	return body.entry.map((e) => e.resource.id);
}

function readPatientIds(url) {
	const res = http.get(url, searchTypeParams);

	if (res.status !== 200) fail('non 200 response code while reading all patients');

	const body = res.json();

	const nextLink = body.link.find((link) => link.relation === 'next');

	return {
		patientIds: body.entry.map((e) => e.resource.id),
		url: nextLink !== undefined ? nextLink.url : undefined,
	};
}

export function setup() {
	//const patientIds = Array(100).fill(10000).flatMap((count) => createPatients(count));

	let url = `${base}/Patient?_elements=id&_count=1000`;
	let patientIds = [];
	while (url !== undefined) {
		const data = readPatientIds(url);
		patientIds = patientIds.concat(data.patientIds);
		url = data.url;
	}

	console.log('number of patients:', patientIds.length);
	return { patientIds: patientIds };
}

function readPatientsHistory(url, lastVersionId) {
	const res = http.get(url, historyTypeParams);

	check(res, {
		'response code was 200': (res) => res.status === 200,
		'response is compressed': (res) => res.headers['Content-Encoding'] === 'gzip',
	});

	const body = res.json();

	if (!check(body, {
		'response body is a valid history Bundle': (bundle) => isValidHistoryBundle(bundle, lastVersionId)
	})) {
		console.log(body);
	}

	const nextLink = body.link.find((link) => link.relation === 'next');

	return nextLink !== undefined ? nextLink.url : undefined;
}

// Reads the first 1000 Patient history entries
//
// Uses paging with a size of 100
// Checks that the versionIds of the history entries are monotonically falling
export function readSomePatientsHistory() {
	group('Read Patient Type History', function() {
		let url = `${base}/Patient/_history?_count=100`;
		let pageCount = 0;
		while (pageCount < 10 && url !== undefined) {
			url = readPatientsHistory(url, Number.MAX_SAFE_INTEGER);
			pageCount++;
		}
	});
}

function isValidSearchsetBundle(bundle, type) {
	return bundle.resourceType === 'Bundle' &&
		bundle.type === 'searchset' &&
		bundle.entry === undefined ||
		bundle.entry.every((e) => e.resource.resourceType === type);
}

function readPatients(url) {
	const res = http.get(url, searchTypeParams);

	check(res, {
		'response code was 200': (res) => res.status === 200,
		'response is compressed': (res) => res.headers['Content-Encoding'] === 'gzip',
	});

	const body = res.json();

	if (!check(body, {
		'response body is a valid searchset Bundle of Patient resources':
			(bundle) => isValidSearchsetBundle(bundle, 'Patient')
	})) {
		console.log(body);
	}

	const nextLink = body.link.find((link) => link.relation === 'next');

	return nextLink !== undefined ? nextLink.url : undefined;
}

// Reads the first 1000 Patient entries
//
// Uses paging with a size of 100
// Checks that the resource types are all Patient
export function readSomePatients() {
	group('Read Patient List', function() {
		let url = `${base}/Patient?_count=100`;
		let pageCount = 0;
		while (pageCount < 10 && url !== undefined) {
			url = readPatients(url);
			pageCount++;
		}
	});
}
