// Primitive Types

export type integer = number;
export type decimal = number;
export type uri = string;
export type url = string;
export type canonical = string;
export type base64Binary = string;
export type instant = string;
export type date = string;
export type dateTime = string;
export type time = string;
export type code = string;
export type oid = string;
export type id = string;
export type markdown = string;
export type unsingedInt = number;
export type positiveInt = number;
export type uuid = string;

// Base Types

export interface Element {
	id?: string;
	extension?: Extension[];
	// eslint-disable-next-line
	[key: string]: any;
}

export interface BackboneElement extends Element {
	modifierExtension?: Extension[];
}

export interface Extension extends Element {
	url: uri;
}

// General-Purpose Data Types

export interface Attachment extends Element {
	contentType?: code;
	language?: code;
	data?: base64Binary;
	url?: url;
	size?: unsingedInt;
	hash?: base64Binary;
	title?: string;
	creation?: dateTime;
}

export interface Coding extends Element {
	system?: uri;
	version?: string;
	code?: string;
	display?: string;
	userSelected?: boolean;
}

export interface CodeableConcept extends Element {
	coding?: Coding[];
	text?: string;
}

export interface Quantity extends Element {
	value?: decimal;
	comparator?: code;
	unit?: string;
	system?: uri;
	code?: string;
}

export interface SimpleQuantity extends Quantity {
	comparator: undefined;
}

export interface Meta extends Element {
	versionId?: id;
	lastUpdated?: instant;
	source?: uri;
	profile?: canonical[];
	security?: Coding[];
	tag?: Coding[];
}

export interface Money extends Element {
	value?: decimal;
	currency?: code;
}

export interface Range extends Element {
	low?: SimpleQuantity;
	high?: SimpleQuantity;
}

export interface Ratio extends Element {
	numerator?: Quantity;
	denominator?: Quantity;
}

export interface RatioRange extends Element {
	lowNumerator?: SimpleQuantity;
	highNumerator?: SimpleQuantity;
	denominator?: SimpleQuantity;
}

export interface Period extends Element {
	start?: dateTime;
	end?: dateTime;
}

export interface Timing extends Element {
	event?: dateTime[];
	repeat?: Element & {
		periodUnit?: code;
		frequency?: positiveInt;
		period?: decimal;
	};
}

export interface Identifier extends Element {
	use?: code;
	type?: CodeableConcept;
	system?: uri;
	value?: string;
	period?: Period;
	//assigner?: Reference;
}

export interface HumanName extends Element {
	use?: code;
	text?: string;
	family?: string;
	given?: string[];
	prefix?: string[];
	suffix?: string[];
	period?: Period;
}

export interface Address extends Element {
	use?: code;
	type?: code;
	text?: string;
	line?: string[];
	city?: string;
	district?: string;
	state?: string;
	postalCode?: string;
	country?: string;
	period?: Period;
}

export interface ContactPoint extends Element {
	system?: code;
	value?: string;
	use?: code;
	rank?: positiveInt;
	period?: Period;
}

// Special Purpose Data Types

export interface Reference extends Element {
	reference?: string;
	type?: uri;
	identifier?: Identifier;
	display?: string;
}

export interface Dosage extends BackboneElement {
	sequence?: integer;
	text?: string;
	timing?: Timing;
	asNeededBoolean?: boolean;
	doseAndRate?: {
		type?: CodeableConcept;
		doseRange?: Range;
		doseQuantity?: SimpleQuantity;
	}[];
}

// Resource Types

export interface Resource {
	resourceType: string;
	id?: string;
	meta?: Meta;
	// eslint-disable-next-line
	[key: string]: any;
}

export enum SearchParamType {
	number = 'number',
	date = 'date',
	string = 'string',
	token = 'token',
	reference = 'reference',
	composite = 'composite',
	quantity = 'quantity',
	uri = 'uri',
	special = 'special'
}

export interface Parameter extends BackboneElement {
	name: string;
	valueUnsignedInt?: number;
}

export interface Parameters extends Resource {
	parameter: Parameter[];
}

export interface CapabilityStatement extends Resource {
	software?: {
		name: string;
		version?: string;
		releaseDate?: string;
	};
	rest: CapabilityStatementRest[];
}

export interface CapabilityStatementRest extends BackboneElement {
	mode: string;
	resource: CapabilityStatementRestResource[];
	searchParam: CapabilityStatementSearchParam[];
}

export interface CapabilityStatementSearchParam extends BackboneElement {
	name: string;
	type: SearchParamType;
}

export enum RestfulInteraction {
	read = 'read',
	vread = 'vread',
	update = 'update',
	patch = 'patch',
	delete = 'delete',
	historyInstance = 'history-instance',
	historyType = 'history-type',
	create = 'create',
	searchType = 'search-type'
}

export enum VersioningPolicy {
	noVersion = 'no-version',
	versioned = 'versioned',
	versionedUpdate = 'versioned-update'
}

export enum ConditionalDeleteStatus {
	notSupported = 'not-supported',
	single = 'single',
	multiple = 'multiple'
}

export interface CapabilityStatementRestResource extends BackboneElement {
	type: code;
	profile?: string;
	interaction?: {
		code: RestfulInteraction;
	}[];
	versioning?: VersioningPolicy;
	readHistory?: boolean;
	updateCreate?: boolean;
	conditionalCreate?: boolean;
	conditionalRead?: boolean;
	conditionalUpdate?: boolean;
	conditionalPatch?: boolean;
	conditionalDelete?: ConditionalDeleteStatus;
	searchInclude?: string[];
	searchRevInclude?: string[];
	searchParam?: CapabilityStatementSearchParam[];
}

export enum StructureDefinitionKind {
	primitiveType = 'primitive-type',
	complexType = 'complex-type',
	resource = 'resource',
	logical = 'logical'
}

export interface StructureDefinition extends Resource {
	kind: StructureDefinitionKind;
	snapshot: {
		element: ElementDefinition[];
	};
}

export interface ElementDefinition extends Element {
	id: string;
	path: string;
	short: string;
	max?: string;
	type?: ElementDefinitionType[];
	isSummary: boolean;
}

export interface ElementDefinitionType extends Element {
	code: uri;
	targetProfile?: canonical[];
}

export interface Bundle<T> extends Resource {
	type: string;
	total?: number;
	link?: BundleLink[];
	entry?: BundleEntry<T>[];
}

export interface SearchSetBundle<T> extends Bundle<T> {
	entry?: SearchSetBundleEntry<T>[];
}

export interface HistoryBundle<T> extends Bundle<T> {
	entry?: HistoryBundleEntry<T>[];
}

export interface BundleLink {
	relation: string;
	url: string;
}

export function bundleLink<T>(bundle: Bundle<T>, relation: string): BundleLink | undefined {
	return bundle.link?.filter((l) => l.relation == relation)[0];
}

export enum SearchEntryMode {
	match = 'match',
	include = 'include',
	outcome = 'outcome'
}

export enum HttpVerb {
	GET = 'GET',
	HEAD = 'HEAD',
	POST = 'POST',
	PUT = 'PUT',
	DELETE = 'DELETE',
	PATCH = 'PATCH'
}

export interface BundleEntry<T> {
	fullUrl?: uri;
	resource?: T;
	search?: {
		mode?: SearchEntryMode;
	};
	request?: BundleEntryRequest;
	response?: BundleEntryReponse;
}

interface BundleEntryRequest {
	method: HttpVerb;
	url: uri;
}

interface BundleEntryReponse {
	status: string;
	location?: uri;
	etag?: string;
	lastModified?: instant;
	outcome?: Resource;
}

export interface SearchSetBundleEntry<T> extends BundleEntry<T> {
	fullUrl: uri;
	resource: T;
	search: { mode: SearchEntryMode };
}

export interface HistoryBundleEntry<T> extends BundleEntry<T> {
	fullUrl: uri;
	resource?: T;
	request: BundleEntryRequest;
	response: BundleEntryReponse & { etag: string };
}

export interface OperationOutcome extends Resource {
	issue: OperationOutcomeIssue[];
}

export interface OperationOutcomeIssue extends BackboneElement {
	severity: code;
	code: code;
	details?: CodeableConcept;
	diagnostics?: string;
	expression?: string[];
}
