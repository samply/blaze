# CQL

## Compilation and Evaluation Pipeline

```
╭───────────────────────╮
│ CQL → ELM Translation │
╰───────────┬───────────╯
╭───────────┴───────────╮
│    ELM Compilation    │
╰───────────┬───────────╯
╭───────────┴───────────╮
│  Unfiltered Context   │ 
│      Evaluation       │
╰───────────┬───────────╯
╭───────────┴───────────╮
│ Reference Resolution  │
╰───────────┬───────────╯
╭───────────┴───────────╮
│     Optimization      │
╰───────────┬───────────╯
╭───────────┴───────────╮
│  Bloom filter attach  │
╰───────────┬───────────╯
╭───────────┴───────────╮
│   Parallel Patient    │ 
│  Context Evaluation   │
╰───────────────────────╯            
```

```
╭──────────────╮   ╭───────────────────────────────╮  ╭────────────────────────╮
│   Patient    │   │   MedicationAdministration    │  │   Medciation: ATC 1234 │ 
╰──────────────╯   ╰───────────────────────────────╯  ╰────────────────────────╯ 

╭──────────────╮   ╭───────────────────────────────╮
│   Patient    │   │   Observation: loinc 788-7    │
╰──────────────╯   ╰───────────────────────────────╯

```

### CQL → ELM Translation

The Expression Logical Model (ELM) is the abstract syntax tree (AST) form of CQL. Blaze uses the CQL → ELM translator from the [Clinical Quality Framework][1] project. After that translation, the ELM expressions are available in JSON form and are parsed into Clojure data structures. The function doing this is `blaze.cql-translator/translate`.

### ELM Compilation

The ELM expression are compiled into instances of the `blaze.elm.compiler.core/Expression` protocol. During this compilation a first optimization of static values and compilation of database queries is done. The database node is used during that compilation. The function used is `blaze.elm.compiler.library/compile-library`.

### Unfiltered Context Evaluation

## Expression Cache

* bloom filter
  * the set we like to build is the set of expressions returning true
  * if the bloom filter returns false, we can be sure that the expression is not in the set of expressions returning true, so it will certainly return false
  * the number of expressions returning true is far less then the number of expressions returning false
  * we'll fill the Bloom filter with the expressions that returned true
  * the problem is the following
    * if we don't have filled the filter with all expression, the answer we get has no value
      * we don't know whether the expression isn't in the set because we didn't put it there or because if returned false
  * but we could use two filters
    * one for all expressions returning true and one for all returning false
    * if the expression isn't in both filters, it is new and so we have the evaluate it
  * we could use a bloom filter for each expression
    * then we would see whether we have a bloom filter or not
    * we would insert Patients for which the expression returns true
    * the first query evaluation is only used for insertion
    * after that we mark the filter as ready for query
    * now we can determine whether a expression is not true for a certain Patient
    * 

* we'll use one Bloom filter per expression
* that Bloom filters will be stored in a Caffeine cache by expression hash
* each Bloom filter will be assigned the t of its creation
* the Bloom filters of all expressions of a query will be collected at the start of the query evaluation
* if a Bloom filter isn't found, its calculation will be queued and carried out asynchronously
* existing Bloom filters are immutable and will be used in query evaluation
  * the Patient ID will be used to test whether this Patient isn't in the Bloom filter
  * if the Patient ID wasn't found, the expression will return false
  * if the Patient ID is found, the expression will be evaluated normally

### Bloom Filter Calculation

* the Bloom filter will be calculated for a particular exists expression
* it will be calculated based on a database with a particular t
* that t will be assigned to the Bloom filter
* the calculation will evaluate the expression for each patent of the database
* the ID's of Patients for which the expression returns true will be put into the Bloom filter

## Or Expressions

Expressions like:

```
define InInitialPopulation:
  exists [Observation] or
  exists [Condition] or
  exists [Encounter] or
  exists [Specimen]
```

are compiled to Blaze expressions:

```edn
(or
 (or
  (or
   (exists
    (retrieve
     "Observation"))
   (exists
    (retrieve
     "Condition")))
  (exists
   (retrieve
    "Encounter")))
 (exists
  (retrieve
   "Specimen")))
```

which can be represented as the following tree

```
     ^
   ^  |
 ^  | |
O C E S 
```

```
 ^
|  ^   
| |  ^  
S E C O  
```

[1]: <https://github.com/cqframework>
