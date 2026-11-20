Instance: compact
InstanceOf: OperationDefinition
Usage: #definition
* url = "https://blaze-server.org/fhir/OperationDefinition/compact"
* name = "Compact"
* title = "Compact a Database Column Family"
* status = #active
* kind = #operation
* description = "Compacts a column family of one of the RocksDB databases of Blaze. RocksDB compacts automatically, but in certain situations a manual compaction can be useful. The response is always async according to the Asynchronous Interaction Request Pattern. The progress is also available from the underlying CompactJob Task resource."
* affectsState = true
* code = #compact
* system = true
* type = false
* instance = false
* parameter[+]
  * name = #database
  * use = #in
  * min = 1
  * max = "1"
  * documentation = "The name of the database holding the column family."
  * type = #code
  * binding
    * strength = #required
    * valueSet = "https://blaze-server.org/fhir/ValueSet/Database"
* parameter[+]
  * name = #column-family
  * use = #in
  * min = 1
  * max = "1"
  * documentation = "The name of the column family to compact. Which column families exist depends on the database."
  * type = #code
  * binding
    * strength = #required
    * valueSet = "https://blaze-server.org/fhir/ValueSet/ColumnFamily"
