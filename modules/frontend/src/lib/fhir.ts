import type {
  Bundle,
  BundleLink,
  CodeableConcept,
  Coding,
  Parameters,
  ParametersParameter
} from 'fhir/r5';

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

export function bundleLink(bundle: Bundle, relation: string): BundleLink | undefined {
  return bundle.link?.filter((l) => l.relation == relation)[0];
}

export function coding(concept: CodeableConcept, system: string): Coding | undefined {
  return concept.coding?.filter((c) => c.system == system)[0];
}

export function parameter(concept: Parameters, name: string): ParametersParameter | undefined {
  return concept.parameter?.filter((c) => c.name == name)[0];
}

export type HttpMethod = 'GET' | 'HEAD' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';

export type SearchMode = 'match' | 'include' | 'outcome';
