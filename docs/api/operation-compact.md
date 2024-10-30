# Operation \$compact

> [!NOTE]
> The system level \$compact operation is only activated if the [Admin API](./admin.md) is activated.

The system level \$compact operation is used to compact column families of RocksDB databases. RockDB compaction is done automatically but in certain situations manual compaction can be useful.

```
POST [base]/$compact
```

## Parameters

| Use | Name          | Cardinality | Type | Documentation                                                          |
|-----|---------------|-------------|------|------------------------------------------------------------------------|
| IN  | database      | 1..1        | code | One of three possible databases: `index`, `transaction` and `resource` |
| IN  | column-family | 1..1        | code | Name of the column family depending on the database. See below.        |

### Column Families

| Database    | Column Family                        |
|-------------|--------------------------------------|
| index       | search-param-value-index             |
| index       | resource-value-index                 |
| index       | compartment-search-param-value-index |
| index       | compartment-resource-type-index      |
| index       | active-search-params                 |
| index       | tx-success-index                     |
| index       | tx-error-index                       |
| index       | t-by-instant-index                   |
| index       | resource-as-of-index                 |
| index       | type-as-of-index                     |
| index       | system-as-of-index                   |
| index       | patient-last-change-index            |
| index       | type-stats-index                     |
| index       | system-stats-index                   |
| index       | cql-bloom-filter                     |
| index       | cql-bloom-filter-by-t                |
| transaction | default                              |
| resource    | default                              |

### Response

The response will be always async according the [Asynchronous Interaction Request Pattern][2] from FHIR R5.

## Using blazectl

The \$compact operation can be executed using [blazectl][1].

### Example

```sh
blazectl --server http://localhost:8080/fhir compact index resource-as-of-index
```

[1]: <https://github.com/samply/blazectl>
[2]: <http://hl7.org/fhir/R5/async-bundle.html>
