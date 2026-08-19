import http from 'k6/http';
import exec from 'k6/execution';
import { Counter, Trend } from 'k6/metrics';
import { fail } from 'k6';

const base = __ENV.BASE;
const duration = Number(__ENV.DURATION || 900);
const patients = Number(__ENV.PATIENTS || 100000);
const bundleSize = Number(__ENV.BUNDLE_SIZE || 500);

// Arrival rate. The rate itself is a random variable following a Pareto
// distribution, resampled every STEP seconds. See createStages below.
const medianRate = Number(__ENV.MEDIAN_RATE || 100);
const alpha = Number(__ENV.ALPHA || 1.5);
const step = Number(__ENV.STEP || 10);
const maxRate = Number(__ENV.MAX_RATE || 2000);
const preAllocatedVUs = Number(__ENV.PRE_ALLOCATED_VUS || 200);
const maxVUs = Number(__ENV.MAX_VUS || 2000);

// Seconds of unrecorded load run before the measured scenario starts.
const warmupDuration = Number(__ENV.WARMUP === undefined ? 60 : __ENV.WARMUP);

// Seeds. RATE_SEED fixes the sequence of sampled arrival rates so repeated runs
// see the same load profile. DATA_SEED fixes the attributes of the generated
// patients so a virtual user can reconstruct the data of patient `i` without
// carrying 100000 resources through setup data.
const rateSeed = Number(__ENV.RATE_SEED || 42);
const dataSeed = Number(__ENV.DATA_SEED || 1);

const pageSize = 50;

const mrnSystem = 'http://blaze.example.com/fhir/sid/mrn';
const maritalStatusSystem = 'http://terminology.hl7.org/CodeSystem/v3-MaritalStatus';

if (!base) {
	fail('BASE env var is required, e.g. BASE=http://localhost:8080/fhir');
}

// ---------------------------------------------------------------------------
// Random number generation
// ---------------------------------------------------------------------------

// Mulberry32, a small seedable PRNG. Used wherever the same sequence has to be
// reproduced independently in different places: the arrival-rate profile (which
// is computed in the init context of every VU but must be identical in all of
// them) and the per-patient attributes (which setup writes and the VUs later
// search for).
function mulberry32(seed) {
	let a = seed | 0;
	return function() {
		a = a + 0x6D2B79F5 | 0;
		let t = Math.imul(a ^ a >>> 15, 1 | a);
		t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t;
		return ((t ^ t >>> 14) >>> 0) / 4294967296;
	};
}

// Spreads consecutive seeds over the whole 32-bit range so that mulberry32
// streams of patient i and patient i + 1 are uncorrelated.
function hashSeed(i) {
	let h = Math.imul(i ^ i >>> 16, 0x45D9F3B);
	h = Math.imul(h ^ h >>> 16, 0x45D9F3B);
	return h ^ h >>> 16;
}

function randomInt(rand, min, max) {
	return Math.floor(rand() * (max - min + 1)) + min;
}

function pick(rand, array) {
	return array[Math.floor(rand() * array.length)];
}

function pad(n, width) {
	let s = `${n}`;
	while (s.length < width) s = `0${s}`;
	return s;
}

// A random date between 1930-01-01 and 2015-12-28. The day is capped at 28 so
// every generated date is valid regardless of month.
function randomBirthDate(rand) {
	return `${randomInt(rand, 1930, 2015)}-${pad(randomInt(rand, 1, 12), 2)}-${pad(randomInt(rand, 1, 28), 2)}`;
}

// ---------------------------------------------------------------------------
// Arrival rate
// ---------------------------------------------------------------------------

// Inverse-transform sampling of a Pareto type I distribution.
//
// Its CDF is F(x) = 1 - (xm/x)^alpha for x >= xm, so the median is
// xm * 2^(1/alpha) and the scale needed for a given median is
// xm = median * 2^(-1/alpha). Drawing U uniformly from (0, 1] and returning
// xm / U^(1/alpha) then yields Pareto distributed rates with exactly the
// requested median.
//
// alpha controls how heavy the tail is. With the default of 1.5 and a median of
// 100/s the mean is alpha*xm/(alpha-1) = 189/s, about 1.6 % of the samples lie
// above 1000/s and about 0.6 % above 2000/s, where MAX_RATE cuts them off.
const scale = medianRate * Math.pow(2, -1 / alpha);

function paretoRate(rand) {
	return scale / Math.pow(1 - rand(), 1 / alpha);
}

// Builds the stage list for the ramping-arrival-rate executor. A fresh rate is
// drawn for every step and held constant for `step` seconds. The zero-length
// stage in front of each hold makes the executor jump to the new rate instead
// of ramping towards it linearly, so the result is a step function rather than
// a smooth interpolation between the samples.
//
// A Pareto distribution is unbounded but k6 has to pre-allocate VUs, so the
// samples are capped at `maxRate`. The number of capped steps is reported by
// setup so a run whose tail was cut off can be recognized as such.
function createStages() {
	const rand = mulberry32(rateSeed);
	const stages = [];
	const rates = [];
	let capped = 0;

	for (let i = 0; i < Math.ceil(duration / step); i++) {
		const sample = paretoRate(rand);
		const rate = Math.min(Math.round(sample), maxRate);

		if (sample > maxRate) capped++;

		rates.push(rate);
		stages.push({ duration: '0s', target: rate });
		stages.push({ duration: `${step}s`, target: rate });
	}

	return { stages, rates, capped };
}

const arrival = createStages();

// Samples are tagged with the arrival rate in effect when they were taken.
// Without that, a percentile over the whole run pools a 30-fold range of offered
// load into one number and says nothing about either end of it.
// The edges are multiples of the median rate, so the buckets keep straddling the
// interesting range when MEDIAN_RATE is changed — a system that only saturates
// far above 100/s has to be driven at a higher median, and absolute edges would
// then collapse every sample into the top bucket. `med` is the per journey budget
// of that bucket in milliseconds.
//
// At the default median of 100/s the edges come out at 100, 250, 350, 500 and
// 1000, so the keys are unchanged and runs stay comparable.
const rateBucketSpec = [
	{ multiple: 1, med: 60 },
	{ multiple: 2.5, med: 80 },
	{ multiple: 3.5, med: 100 },
	{ multiple: 5, med: 100 },
	{ multiple: 10, med: 120 }
];

function createRateBuckets() {
	const letters = 'abcdefghij';

	const buckets = rateBucketSpec.map(({ multiple, med }, i) => {
		const limit = Math.round(medianRate * multiple);
		return { limit: limit, key: `${letters[i]}_le${limit}`, med: med };
	});

	const last = buckets[buckets.length - 1];

	buckets.push({ limit: Infinity, key: `${letters[buckets.length]}_gt${last.limit}`, med: 500 });

	return buckets;
}

const rateBuckets = createRateBuckets();

// Samples are also tagged with the third of the run they were taken in. Every
// update appends an entry to the search param index for every indexed value of
// the patient, and those entries survive until the old version is purged, so a
// scanning search gets slower the longer the test runs. That makes accumulated
// history a second independent variable next to the arrival rate.
//
// Splitting by elapsed time separates the two: because the rates are sampled
// i.i.d. per step, every phase sees the same rate distribution, so comparing a
// metric across phases isolates the effect of the growing history.
const phaseCount = 3;

function elapsed() {
	return (Date.now() - exec.scenario.startTime) / 1000;
}

function clampedIndex(value, length) {
	return Math.min(length - 1, Math.max(0, Math.floor(value)));
}

// The rate the executor is currently targeting, found by mapping the time
// elapsed in the scenario back onto the step that produced it. Every VU computes
// the same `arrival.rates` because the sampling PRNG is seeded.
function currentRateBucket() {
	const rate = arrival.rates[clampedIndex(elapsed() / step, arrival.rates.length)];

	for (const bucket of rateBuckets) {
		if (rate <= bucket.limit) return bucket.key;
	}
}

function currentPhase() {
	return `p${clampedIndex(elapsed() / (duration / phaseCount), phaseCount) + 1}`;
}

function currentTags() {
	return { rate: currentRateBucket(), phase: currentPhase() };
}

// ---------------------------------------------------------------------------
// Options
// ---------------------------------------------------------------------------

// Search variants a user of a patient list UI would trigger. The weights sum up
// to 100. `p95` is the response time budget in milliseconds; it is turned into a
// threshold below, which also makes k6 report every variant separately in the
// end-of-test summary.
// `med` is the response time budget in milliseconds. The budgets are deliberately
// on the median and not on a high percentile: this test drives the server far
// into saturation during the tail steps of the arrival rate, so a p95 over the
// whole run measures how bursty the load was rather than how fast the server is.
// The summary still prints med, p95 and p99 for every sub-metric a threshold
// names, so the tail stays visible — it just doesn't decide pass or fail.
const searchVariants = [
	{ key: 'identifier', weight: 25, med: 3 },
	{ key: 'family-given', weight: 25, med: 20 },
	{ key: 'family-birthdate', weight: 15, med: 20 },
	{ key: 'name', weight: 10, med: 5 },
	{ key: 'family-gender', weight: 10, med: 60 },
	{ key: 'city-family', weight: 5, med: 30 },
	{ key: 'birthdate-range', weight: 5, med: 40 },
	{ key: 'recently-updated', weight: 5, med: 5 }
];

// Thresholds are what makes k6 report a sub-metric separately in the end-of-test
// summary — a plain tag alone is not enough. So besides enforcing the budgets,
// these define the three cuts through the data worth looking at: cost per search
// variant, journey latency per arrival rate, and the cheapest possible query per
// arrival rate as a saturation canary.
function subMetricThresholds(thresholds) {
	for (const { key, med } of searchVariants) {
		thresholds[`search_duration{variant:${key}}`] = [`med<${med}`];
	}

	for (const { key, med } of rateBuckets) {
		thresholds[`journey_duration{rate:${key}}`] = [`med<${med}`];
		thresholds[`search_duration{variant:identifier,rate:${key}}`] = ['med<50'];
	}

	// The history axis. `family-given` is the probe — a scanning search with
	// enough samples per phase to be stable. `identifier` is the control: it
	// seeks to a narrow prefix, so it should stay flat while the probe rises.
	for (let i = 1; i <= phaseCount; i++) {
		thresholds[`journey_duration{phase:p${i}}`] = ['med<80'];
		thresholds[`search_duration{variant:family-given,phase:p${i}}`] = ['med<25'];
		thresholds[`search_duration{variant:identifier,phase:p${i}}`] = ['med<3'];
	}

	return thresholds;
}

// Runs the same journeys at the median rate before the measured scenario starts,
// so the JVM has compiled the hot paths and the resource caches and index column
// families are warm by the time the first recorded sample is taken. Its journeys
// are executed by `warmup`, which discards every metric.
//
// Set WARMUP=0 to measure a cold server instead.
function createScenarios() {
	const scenarios = {
		ui: {
			executor: 'ramping-arrival-rate',
			startTime: `${warmupDuration}s`,
			startRate: arrival.rates[0],
			timeUnit: '1s',
			preAllocatedVUs: preAllocatedVUs,
			maxVUs: maxVUs,
			stages: arrival.stages
		}
	};

	if (warmupDuration > 0) {
		// `preAllocatedVUs` is per scenario, so reusing the measured scenario's pool
		// here would make k6 initialize it twice. The warmup only ever runs at the
		// median rate, so it needs a small fraction of it.
		const warmupVUs = Math.max(20, Math.ceil(medianRate / 2));

		scenarios.warmup = {
			executor: 'constant-arrival-rate',
			exec: 'warmup',
			rate: medianRate,
			timeUnit: '1s',
			duration: `${warmupDuration}s`,
			preAllocatedVUs: warmupVUs,
			maxVUs: warmupVUs * 10
		};
	}

	return scenarios;
}

export const options = {

	setupTimeout: '3600s',
	insecureSkipTLSVerify: true,
	discardResponseBodies: true,

	summaryTrendStats: ['med', 'p(95)', 'p(99)'],

	scenarios: createScenarios(),

	thresholds: subMetricThresholds({
		// Scoped to the measured scenario, because unlike the custom metrics the
		// built-in HTTP metrics do see the warmup requests.
		'http_req_failed{scenario:ui}': ['rate<0.01'],
		'read_duration': ['med<2'],
		'update_duration': ['med<60']
	})
};

// ---------------------------------------------------------------------------
// Metrics
// ---------------------------------------------------------------------------

// The metrics of the measured scenario. `*Rejections` count requests answered
// with 503; they are split by operation because only the update path submits a
// transaction, so a rejected search or read would point at a different subsystem
// than the admission control of the database node.
const measured = {
	searches: new Counter('searches'),
	emptySearches: new Counter('empty_searches'),
	reads: new Counter('reads'),
	updates: new Counter('updates'),
	updateConflicts: new Counter('update_conflicts'),
	searchRejections: new Counter('search_rejections'),
	readRejections: new Counter('read_rejections'),
	updateRejections: new Counter('update_rejections'),
	searchDuration: new Trend('search_duration', true),
	readDuration: new Trend('read_duration', true),
	updateDuration: new Trend('update_duration', true),
	journeyDuration: new Trend('journey_duration', true)
};

// The same set, but discarding every sample. The warmup scenario runs with this
// so its journeys stay out of the statistics. k6 aggregates a metric over the
// whole test run, so tagging warmup samples would not exclude them from the
// summary — they must never be recorded in the first place.
const discardingSink = { add: function() {} };
const discarded = {};

for (const key in measured) discarded[key] = discardingSink;

// ---------------------------------------------------------------------------
// Reference data
// ---------------------------------------------------------------------------

// The most common German family names, in descending frequency. They are drawn
// with Zipf weights below, so a search for a name from the front of this list
// matches thousands of patients while one from the end matches a handful. That
// spread is the point: a patient list UI sees both.
const familyNames = [
	'Müller', 'Schmidt', 'Schneider', 'Fischer', 'Weber', 'Meyer', 'Wagner',
	'Becker', 'Schulz', 'Hoffmann', 'Schäfer', 'Koch', 'Bauer', 'Richter',
	'Klein', 'Wolf', 'Schröder', 'Neumann', 'Schwarz', 'Zimmermann', 'Braun',
	'Krüger', 'Hofmann', 'Hartmann', 'Lange', 'Schmitt', 'Werner', 'Schmitz',
	'Krause', 'Meier', 'Lehmann', 'Schmid', 'Schulze', 'Maier', 'Köhler',
	'Herrmann', 'König', 'Walter', 'Mayer', 'Huber', 'Kaiser', 'Fuchs',
	'Peters', 'Lang', 'Scholz', 'Möller', 'Weiß', 'Jung', 'Hahn', 'Schubert',
	'Vogel', 'Friedrich', 'Keller', 'Günther', 'Frank', 'Berger', 'Winkler',
	'Roth', 'Beck', 'Lorenz', 'Baumann', 'Franke', 'Albrecht', 'Schuster',
	'Simon', 'Ludwig', 'Böhm', 'Winter', 'Kraus', 'Martin', 'Schumacher',
	'Krämer', 'Vogt', 'Stein', 'Jäger', 'Otto', 'Sommer', 'Groß', 'Seidel',
	'Heinrich', 'Brandt', 'Haas', 'Schreiber', 'Graf', 'Schulte', 'Dietrich',
	'Ziegler', 'Kuhn', 'Kühn', 'Pohl', 'Engel', 'Horn', 'Busch', 'Bergmann',
	'Thomas', 'Voigt', 'Sauer', 'Arnold', 'Wolff', 'Pfeiffer', 'Barth',
	'Ernst', 'Ritter', 'Nowak', 'Kramer', 'Bock', 'Hansen', 'Wenzel', 'Ulrich',
	'Petersen', 'Löffler', 'Marx', 'Michel', 'Krieger', 'Schilling', 'Reuter',
	'Wilhelm', 'Adam', 'Wirth', 'Kern', 'Riedel', 'Brunner', 'Hein'
];

const givenNames = [
	'Maria', 'Peter', 'Anna', 'Michael', 'Ursula', 'Wolfgang', 'Monika',
	'Thomas', 'Petra', 'Klaus', 'Elisabeth', 'Manfred', 'Sabine', 'Jürgen',
	'Renate', 'Andreas', 'Helga', 'Stefan', 'Karin', 'Christian', 'Brigitte',
	'Uwe', 'Ingrid', 'Werner', 'Erika', 'Hans', 'Andrea', 'Matthias',
	'Susanne', 'Helmut', 'Gabriele', 'Frank', 'Claudia', 'Günter', 'Christa',
	'Dieter', 'Barbara', 'Bernd', 'Gisela', 'Martin', 'Christine', 'Rainer',
	'Angelika', 'Norbert', 'Birgit', 'Alexander', 'Heike', 'Markus',
	'Marianne', 'Rolf', 'Martina', 'Karl', 'Ute', 'Walter', 'Katharina',
	'Gerhard', 'Ruth', 'Ralf', 'Nicole', 'Joachim', 'Silvia', 'Heinz',
	'Hannelore', 'Josef', 'Regina', 'Sebastian', 'Kerstin', 'Torsten',
	'Ulrike', 'Holger', 'Doris', 'Jan', 'Beate', 'Daniel', 'Anja', 'Marco',
	'Stefanie', 'Oliver', 'Julia', 'Dirk', 'Jutta', 'Ulrich', 'Cornelia',
	'Harald', 'Bettina', 'Bernhard', 'Katrin', 'Rudolf', 'Manuela', 'Herbert',
	'Michaela', 'Kurt', 'Sandra', 'Erich', 'Melanie', 'Horst', 'Nadine',
	'Georg', 'Lisa', 'Alfred'
];

const cities = [
	{ city: 'Berlin', postalCode: '10115', state: 'Berlin' },
	{ city: 'Hamburg', postalCode: '20095', state: 'Hamburg' },
	{ city: 'München', postalCode: '80331', state: 'Bayern' },
	{ city: 'Köln', postalCode: '50667', state: 'Nordrhein-Westfalen' },
	{ city: 'Frankfurt am Main', postalCode: '60311', state: 'Hessen' },
	{ city: 'Stuttgart', postalCode: '70173', state: 'Baden-Württemberg' },
	{ city: 'Düsseldorf', postalCode: '40213', state: 'Nordrhein-Westfalen' },
	{ city: 'Leipzig', postalCode: '04109', state: 'Sachsen' },
	{ city: 'Dortmund', postalCode: '44135', state: 'Nordrhein-Westfalen' },
	{ city: 'Essen', postalCode: '45127', state: 'Nordrhein-Westfalen' },
	{ city: 'Bremen', postalCode: '28195', state: 'Bremen' },
	{ city: 'Dresden', postalCode: '01067', state: 'Sachsen' },
	{ city: 'Hannover', postalCode: '30159', state: 'Niedersachsen' },
	{ city: 'Nürnberg', postalCode: '90402', state: 'Bayern' },
	{ city: 'Duisburg', postalCode: '47051', state: 'Nordrhein-Westfalen' },
	{ city: 'Bochum', postalCode: '44787', state: 'Nordrhein-Westfalen' },
	{ city: 'Wuppertal', postalCode: '42103', state: 'Nordrhein-Westfalen' },
	{ city: 'Bielefeld', postalCode: '33602', state: 'Nordrhein-Westfalen' },
	{ city: 'Bonn', postalCode: '53111', state: 'Nordrhein-Westfalen' },
	{ city: 'Münster', postalCode: '48143', state: 'Nordrhein-Westfalen' },
	{ city: 'Karlsruhe', postalCode: '76133', state: 'Baden-Württemberg' },
	{ city: 'Mannheim', postalCode: '68159', state: 'Baden-Württemberg' },
	{ city: 'Augsburg', postalCode: '86150', state: 'Bayern' },
	{ city: 'Wiesbaden', postalCode: '65183', state: 'Hessen' },
	{ city: 'Braunschweig', postalCode: '38100', state: 'Niedersachsen' },
	{ city: 'Kiel', postalCode: '24103', state: 'Schleswig-Holstein' },
	{ city: 'Chemnitz', postalCode: '09111', state: 'Sachsen' },
	{ city: 'Aachen', postalCode: '52062', state: 'Nordrhein-Westfalen' },
	{ city: 'Halle', postalCode: '06108', state: 'Sachsen-Anhalt' },
	{ city: 'Magdeburg', postalCode: '39104', state: 'Sachsen-Anhalt' },
	{ city: 'Freiburg', postalCode: '79098', state: 'Baden-Württemberg' },
	{ city: 'Lübeck', postalCode: '23552', state: 'Schleswig-Holstein' },
	{ city: 'Mainz', postalCode: '55116', state: 'Rheinland-Pfalz' },
	{ city: 'Erfurt', postalCode: '99084', state: 'Thüringen' },
	{ city: 'Rostock', postalCode: '18055', state: 'Mecklenburg-Vorpommern' },
	{ city: 'Kassel', postalCode: '34117', state: 'Hessen' },
	{ city: 'Potsdam', postalCode: '14467', state: 'Brandenburg' }
];

const streets = [
	'Hauptstraße', 'Bahnhofstraße', 'Schulstraße', 'Gartenstraße',
	'Dorfstraße', 'Bergstraße', 'Lindenstraße', 'Kirchstraße', 'Birkenweg',
	'Amselweg', 'Goethestraße', 'Schillerstraße', 'Ringstraße', 'Waldstraße',
	'Mozartstraße', 'Rosenweg', 'Feldstraße', 'Wiesenweg', 'Talstraße',
	'Ahornweg'
];

const genders = ['male', 'female', 'other'];

const maritalStatusCodes = ['M', 'S', 'D', 'W', 'U'];

// Cumulative Zipf weights over an array of `n` elements. Element i gets a weight
// proportional to 1/(i+1)^s, which is how name frequencies are actually
// distributed.
function zipfWeights(n, s) {
	const weights = [];
	let sum = 0;

	for (let i = 0; i < n; i++) {
		sum += 1 / Math.pow(i + 1, s);
		weights.push(sum);
	}

	return weights.map((w) => w / sum);
}

const familyWeights = zipfWeights(familyNames.length, 0.8);
const givenWeights = zipfWeights(givenNames.length, 0.7);

function pickZipf(rand, array, weights) {
	const u = rand();
	let lo = 0;
	let hi = weights.length - 1;

	while (lo < hi) {
		const mid = (lo + hi) >> 1;
		if (weights[mid] < u) lo = mid + 1; else hi = mid;
	}

	return array[lo];
}

// ---------------------------------------------------------------------------
// Patient data
// ---------------------------------------------------------------------------

function mrn(i) {
	return `MRN-${pad(i, 7)}`;
}

// The attributes of patient `i`, derived solely from `i` and DATA_SEED. Setup
// builds the resources from this and the VUs call it again to obtain search
// values that actually match an existing patient — the way a user searches for
// someone they know is in the system, rather than for a random string.
function patientData(i) {
	const rand = mulberry32(hashSeed(dataSeed + i));
	return {
		mrn: mrn(i),
		family: pickZipf(rand, familyNames, familyWeights),
		given: pickZipf(rand, givenNames, givenWeights),
		gender: pick(rand, genders),
		birthDate: randomBirthDate(rand),
		address: pick(rand, cities),
		street: pick(rand, streets),
		houseNumber: randomInt(rand, 1, 199),
		phone: `+49 ${randomInt(rand, 30, 89)} ${randomInt(rand, 1000000, 9999999)}`
	};
}

function createPatient(i) {
	const data = patientData(i);
	return {
		resourceType: 'Patient',
		identifier: [{ system: mrnSystem, value: data.mrn }],
		active: true,
		name: [{ use: 'official', family: data.family, given: [data.given] }],
		telecom: [{ system: 'phone', value: data.phone, use: 'home' }],
		gender: data.gender,
		birthDate: data.birthDate,
		address: [{
			use: 'home',
			line: [`${data.street} ${data.houseNumber}`],
			city: data.address.city,
			postalCode: data.address.postalCode,
			state: data.address.state,
			country: 'DE'
		}]
	};
}

// ---------------------------------------------------------------------------
// Requests
// ---------------------------------------------------------------------------

// A 412 is the expected answer when another user saved the same patient first,
// not a failure. Without this it would be counted in `http_req_failed` and hide
// the genuine error rate.
//
// A 503 is likewise designed behaviour: once the database node reached
// `DB_MAX_IN_FLIGHT_TRANSACTIONS` transactions that are submitted but not yet
// indexed, it rejects further submits with a busy anomaly, which the REST layer
// maps to 503. A read or a search answers with 503 if it can't acquire the
// newest database state within `DB_SYNC_TIMEOUT`. Shedding load beyond capacity
// is the point of both, so rejections are counted on their own rather than
// inflating the error rate. `http_req_failed` is then left meaning what it
// says.
const readStatuses = http.expectedStatuses(200, 503);
const updateStatuses = http.expectedStatuses(200, 412, 503);

const bundleParams = {
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

const readParams = {
	headers: {
		'Accept': 'application/fhir+json'
	},
	responseType: 'text',
	responseCallback: readStatuses,
	tags: {
		name: 'read'
	}
};

function searchParams(variant, tags) {
	return {
		headers: {
			'Accept': 'application/fhir+json'
		},
		responseType: 'text',
		responseCallback: readStatuses,
		tags: Object.assign({ name: 'search', variant: variant }, tags)
	};
}

function updateParams(versionId) {
	return {
		headers: {
			'Accept': 'application/fhir+json',
			'Content-Type': 'application/fhir+json',
			'If-Match': `W/"${versionId}"`
		},
		responseCallback: updateStatuses,
		tags: {
			name: 'update'
		}
	};
}

function patientCount() {
	const resp = http.get(`${base}/Patient?_summary=count`, countParams);

	if (resp.status !== 200) fail(`non 200 response code ${resp.status} while counting patients`);

	return resp.json().total;
}

// ---------------------------------------------------------------------------
// Setup
// ---------------------------------------------------------------------------

// Creates `patients` patients using transaction bundles of `bundleSize` POST
// entries each.
export function setup() {
	// The whole test derives its search values from the patients created here,
	// so a pre-existing population would both distort the result set sizes and
	// leave the run non-reproducible.
	const count = patientCount();

	if (count !== 0) fail(`the server has to be empty but contains ${count} patients`);

	console.log(`create ${patients} patients in bundles of ${bundleSize}...`);

	for (let offset = 0; offset < patients; offset += bundleSize) {
		const entry = [];

		for (let i = offset; i < Math.min(offset + bundleSize, patients); i++) {
			entry.push({ resource: createPatient(i), request: { method: 'POST', url: 'Patient' } });
		}

		const body = JSON.stringify({ resourceType: 'Bundle', type: 'transaction', entry: entry });
		const resp = http.post(base, body, bundleParams);

		if (resp.status !== 200) fail(`non 200 response code ${resp.status} while creating patients`);

		if ((offset / bundleSize) % 20 === 0) {
			console.log(`  ${offset + entry.length} of ${patients} patients created`);
		}
	}

	const created = patientCount();

	if (created !== patients) fail(`expected ${patients} patients but the server contains ${created}`);

	const sorted = arrival.rates.slice().sort((a, b) => a - b);
	const mean = sorted.reduce((a, b) => a + b, 0) / sorted.length;

	console.log(`${patients} patients created`);
	console.log(`arrival rate over ${sorted.length} steps of ${step}s: `
		+ `median ${sorted[sorted.length >> 1]}/s, mean ${mean.toFixed(1)}/s, `
		+ `max ${sorted[sorted.length - 1]}/s, ${arrival.capped} steps capped at ${maxRate}/s`);

	return { patients: patients };
}

// ---------------------------------------------------------------------------
// Search
// ---------------------------------------------------------------------------

const totalSearchWeight = searchVariants.reduce((sum, { weight }) => sum + weight, 0);

function pickSearchVariant() {
	let u = Math.random() * totalSearchWeight;

	for (const variant of searchVariants) {
		u -= variant.weight;
		if (u < 0) return variant.key;
	}

	return searchVariants[searchVariants.length - 1].key;
}

function param(name, value) {
	return `${name}=${encodeURIComponent(value)}`;
}

// The query string of one search variant. Everything but the broad `name`, the
// birth date range and the recently-updated worklist is derived from an existing
// patient, because that is what a user does: they look up someone they know is
// in the system.
//
// No variant asks for `_total=accurate`. Blaze only counts matches when that is
// requested explicitly, so every search here costs what it takes to fill one
// page of `_count` hits, no matter how many patients match in total.
function searchQuery(variant, patientCount) {
	const data = patientData(randomInt(Math.random, 0, patientCount - 1));

	switch (variant) {
		case 'identifier':
			return param('identifier', `${mrnSystem}|${data.mrn}`);

		case 'family-given':
			return `${param('family', data.family)}&${param('given', data.given)}`;

		case 'family-birthdate':
			return `${param('family', data.family)}&${param('birthdate', data.birthDate)}`;

		case 'name':
			return param('name', pickZipf(Math.random, familyNames, familyWeights));

		case 'family-gender':
			return `${param('family', data.family)}&${param('gender', data.gender)}`;

		case 'city-family':
			return `${param('address-city', data.address.city)}&${param('family', data.family)}`;

		case 'birthdate-range': {
			const from = randomInt(Math.random, 1930, 2011);
			return `${param('birthdate', `ge${from}-01-01`)}&${param('birthdate', `le${from + 4}-12-31`)}`
				+ `&${param('gender', pick(Math.random, genders))}`;
		}

		// A worklist of the patients changed last.
		case 'recently-updated':
			return '_sort=-_lastUpdated';
	}

	fail(`unknown search variant ${variant}`);
}

// ---------------------------------------------------------------------------
// Update
// ---------------------------------------------------------------------------

function setTelecom(patient, system, value) {
	const telecom = (patient.telecom || []).filter((t) => t.system !== system);
	telecom.push({ system: system, value: value, use: 'home' });
	patient.telecom = telecom;
}

// The edits a user of a patient administration UI performs, with the weights of
// how often they occur. They are deliberately spread over different index types:
// string (name, address), token (marital status, active) and date (birth date).
const edits = [
	{
		key: 'phone',
		weight: 25,
		apply: (patient) => setTelecom(patient, 'phone', `+49 ${randomInt(Math.random, 30, 89)} ${randomInt(Math.random, 1000000, 9999999)}`)
	},
	{
		key: 'email',
		weight: 20,
		apply: (patient) => setTelecom(patient, 'email', `patient.${randomInt(Math.random, 0, 999999)}@example.com`)
	},
	{
		// The patient moved.
		key: 'address',
		weight: 20,
		apply: (patient) => {
			const address = pick(Math.random, cities);
			patient.address = [{
				use: 'home',
				line: [`${pick(Math.random, streets)} ${randomInt(Math.random, 1, 199)}`],
				city: address.city,
				postalCode: address.postalCode,
				state: address.state,
				country: 'DE'
			}];
		}
	},
	{
		key: 'marital-status',
		weight: 15,
		apply: (patient) => {
			patient.maritalStatus = {
				coding: [{ system: maritalStatusSystem, code: pick(Math.random, maritalStatusCodes) }]
			};
		}
	},
	{
		// A wrong birth date was corrected.
		key: 'birthdate',
		weight: 8,
		apply: (patient) => {
			patient.birthDate = randomBirthDate(Math.random);
		}
	},
	{
		// The patient took a different family name.
		key: 'family-name',
		weight: 7,
		apply: (patient) => {
			const name = (patient.name || [{ use: 'official' }])[0];
			name.family = pickZipf(Math.random, familyNames, familyWeights);
			patient.name = [name];
		}
	},
	{
		// The record was deactivated or a returning patient reactivated.
		key: 'active',
		weight: 5,
		apply: (patient) => {
			patient.active = patient.active === false;
		}
	}
];

const totalEditWeight = edits.reduce((sum, { weight }) => sum + weight, 0);

function applyEdit(patient) {
	let u = Math.random() * totalEditWeight;

	for (const edit of edits) {
		u -= edit.weight;
		if (u < 0) {
			edit.apply(patient);
			return edit.key;
		}
	}

	const last = edits[edits.length - 1];
	last.apply(patient);
	return last.key;
}

// ---------------------------------------------------------------------------
// Journey
// ---------------------------------------------------------------------------

// One arrival is one user interaction sequence: search the patient list, open
// one of the hits and save an edit.
export default function({ patients }) {
	run(patients, measured, currentTags(), journey);
}

// The warmup scenario browses without saving, and does so with a metric set that
// discards every sample. It carries no rate or phase tags either — both describe
// a position in the measured scenario, which has not started yet.
//
// Read-only on purpose. Warming the write path here would cost what it is worth:
// every update appends search param index entries that survive into the measured
// run, so a writing warmup would hand phase p1 a head start in accumulated
// history and blunt the very axis the phase tags exist to measure. It is also
// unnecessary — `setup` writes 100000 patients in bundles of `bundleSize`, which
// exercises the transaction log, the indexer and the resource store, batching
// included. What is actually cold when the measured scenario starts is the search
// path, and browsing warms exactly that.
export function warmup({ patients }) {
	run(patients, discarded, {}, browse);
}

function run(patients, m, tags, fn) {
	const start = Date.now();

	fn(patients, m, tags);

	// Only journeys that ran to their natural end are timed. `fail` throws, so a
	// broken journey never reaches this line.
	m.journeyDuration.add(Date.now() - start, tags);
}

function journey(patients, m, tags) {
	const patient = browse(patients, m, tags);

	if (patient !== undefined) save(patient, m);
}

// Searches the patient list and opens one of the hits, which is what a user does
// before editing anything. Returns the opened patient, or undefined if the
// journey ended before one was opened.
function browse(patients, m, tags) {
	const variant = pickSearchVariant();
	const query = searchQuery(variant, patients);

	// `_elements` models the columns a patient list actually shows. It keeps the
	// response small enough that the client isn't the bottleneck at high rates.
	const searchResp = http.get(
		`${base}/Patient?${query}&_count=${pageSize}&_elements=name,birthDate,gender,identifier`,
		searchParams(variant, tags)
	);

	// Rejected by backpressure. A UI would show "try again", so the journey ends
	// here without a retry.
	if (searchResp.status === 503) {
		m.searchRejections.add(1);
		return;
	}

	if (searchResp.status !== 200) {
		fail(`non 200 response code ${searchResp.status} while searching patients with ${query}`);
	}

	m.searches.add(1);
	m.searchDuration.add(searchResp.timings.waiting, Object.assign({ variant: variant }, tags));

	const entries = searchResp.json().entry;

	// No hits ends the journey, just as it does in a UI.
	if (entries === undefined || entries.length === 0) {
		m.emptySearches.add(1);
		return;
	}

	// The user opens one of the hits, which loads the full resource.
	const id = entries[Math.floor(Math.random() * entries.length)].resource.id;
	const readResp = http.get(`${base}/Patient/${id}`, readParams);

	if (readResp.status === 503) {
		m.readRejections.add(1);
		return;
	}

	if (readResp.status !== 200) {
		fail(`non 200 response code ${readResp.status} while reading Patient/${id}`);
	}

	m.reads.add(1);
	m.readDuration.add(readResp.timings.waiting);

	return readResp.json();
}

// Applies one edit to an opened patient and saves it.
function save(patient, m) {
	const id = patient.id;
	const versionId = patient.meta.versionId;

	applyEdit(patient);

	const updateResp = http.put(`${base}/Patient/${id}`, JSON.stringify(patient), updateParams(versionId));

	// 412 means another simulated user saved the same patient in the meantime.
	// A UI would ask its user to reload instead of overwriting, so the journey
	// ends here rather than retrying.
	if (updateResp.status === 412) {
		m.updateConflicts.add(1);
		return;
	}

	// The maximum number of in-flight transactions was reached. This is the
	// backpressure path, and the counter to compare this against on the server is
	// `blaze_db_node_submit_rejections_total`.
	if (updateResp.status === 503) {
		m.updateRejections.add(1);
		return;
	}

	if (updateResp.status !== 200) {
		fail(`non 200 response code ${updateResp.status} while updating Patient/${id}`);
	}

	m.updates.add(1);
	m.updateDuration.add(updateResp.timings.waiting);
}
