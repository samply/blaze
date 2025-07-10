export interface ColumnFamilyDescriptions {
  [key: string]: string;
}

export interface Descriptions {
  [key: string]: ColumnFamilyDescriptions;
}

export const descriptions: Descriptions = {
  index: {
    'search-param-value-index': 'Used to find resources based on search parameter values',
    'resource-value-index':
      'Used to decide whether a resource contains a search parameter based value',
    'compartment-search-param-value-index':
      'Used to find resources of a particular compartment based on search parameter values',
    'compartment-resource-type-index':
      'Used to find all resources that belong to a certain compartment',
    'active-search-params': 'Unused',
    'tx-success-index': 'Contains successful transactions with the point in time they happened',
    'tx-error-index': 'Will keep track of all failed transactions',
    't-by-instant-index': 'Used to determine the t of a real point in time',
    'resource-as-of-index': 'Contains the resource-level history',
    'type-as-of-index': 'Contains the type-level history',
    'system-as-of-index': 'Contains the system wide history',
    'patient-last-change-index':
      'Contains all changes to resources in the compartment of a particular patient',
    'type-stats-index':
      'Keeps track of the total number of resources, and the number of changes to resources of a particular type',
    'system-stats-index':
      'Keeps track of the total number of resources, and the number of changes to all resources',
    'cql-bloom-filter': 'Contains Bloom filters for the CQL cache',
    default: "Contains versioning information's"
  },
  transaction: {
    default: 'Contains all transactions'
  },
  resource: {
    default: 'Contains all resources by content hash'
  }
};
