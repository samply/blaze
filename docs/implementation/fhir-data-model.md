# FHIR Data Model

In this section, the representation of FHIR data is discussed. In Blaze there are 4 main areas where FHIR data is represented in different ways. Those areas are: external serialization of resources consumed / exposed by the REST API, internal (in-memory) representation, serialization for storage in the document store and serialization for storage in the indices.

## External Serialization of resources consumed / exposed by the REST API

Blaze supports both JSON and XML serialization, with JSON being the default option. Blaze uses [jsonista][1] to parse and generate JSON and [Clojure data.xml][2] to parse and generate XML.

### JSON

Parsing a JSON document like:

```json
{
 "resourceType": "Patient",
 "id": "0",
 "name": [
    {
      "text": "John Doe"
    }
  ],
 "birthDate": "2020",
 "deceasedBoolean": false
}
```

will produce the following Clojure data structure:

```clojure
{:resourceType "Patient"
 :id "0"
 :name [{:text "John Doe"}]
 :birthDate "2020"
 :deceasedBoolean false}
```

**Note:** Clojure data structures are explained [here][3]. Clojure uses generic data structures like maps and lists instead of domain specific classes like Java.

The Clojure map looks exactly the same as the JSON document. The main difference is, that all keys are converted to Clojure keywords which can be written without quotes and always start with a colon. The parsing process is fully generic like in JavaScript. There is no need to define any domain specific classes like in Java.

### XML

Parsing an XML document like:

```xml
<Patient xmlns="http://hl7.org/fhir">
  <id value="0"/>
  <name>
    <text value="John Doe"/>
  </name>
  <birthDate value="2020"/>
  <deceasedBoolean value="false"/>
</Patient>
```

will produce the following Clojure data structure:

```clojure
{:tag :Patient
 :content
 [{:tag :id :attrs {:value "0"}}
  {:tag :name
   :content
   [{:tag :text :attrs {:value "John Doe"}}]}
  {:tag :birthDate :attrs {:value "2020"}}
  {:tag :deceasedBoolean :attrs {:value "false"}}]}
```

The Clojure data structure the XML parser produces, looks completely different to the parsed JSON data structure. Hence, a common internal representation is necessary.

## Internal (in-memory) Representation

There are two main reasons why Blaze uses an internal representation for FHIR data which differs from either the JSON or the XML representation:
 * first, a common representation is necessary, and
 * second, both the JSON and XML representations have the type information only at the top-level and in case of polymorphic properties, in the property name instead of the value.

The internal representation of the example above looks like this:

```clojure
{:fhir/type :fhir/Patient
 :id "0"
 :name [#fhir/HumanName{:text #fhir/string "John Doe"}]
 :birthDate #fhir/date #system/date "2020"
 :deceased #fhir/boolean false}
```

Although, this internal representation is nearly identical to the JSON representation, there are two main differences:
 * the type information is made explicit for, not just the top-level resource `Patient` but also for complex types like `HumanName`. 
 * the name of the polymorphic property `deceased[x]` is changed from `deceasedBoolean` into just `deceased` because the boolean type is now obvious from the value alone. Like the other values, the value of the `birthDate` will be converted from a string into a fitting data type which is `java.time.Year` in this case.

The rules for the internal representation are:
 * use Clojure maps for resources and complex data types
 * each map contains an entry with the key `:fhir/type` and the value of the type as keyword with the namespace `fhir`
 * primitive data types will use appropriate plain Java types, or wrappers able to hold extensions

All types used to hold fhir data will implement the `FhirType` protocol. Clojure protocols are like Java interfaces but can be applied to existing types. More about protocols can be found [here][4].

```clojure
(defprotocol FhirType
  (-type [_])
  (-value [_]))
```

First, the `-type` method will return the FHIR type of a value and second the `-value` method will return the value of a primitive type as FHIRPath system type. The `FhirType` protocol will ensure that every Java type used for primitive types, together with the maps used for the other types, will look the same.

The following table shows the mapping from primitive FHIR types to Java types:

| FHIR Type    | FHIRPath Type   | Java Type                                                                                                             | Heap Size                                                                  |
|--------------|-----------------|-----------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------|
| boolean      | System.Boolean  | Boolean                                                                                                               | interned                                                                   |
| integer      | System.Integer  | Integer                                                                                                               | 16 bytes                                                                   |
| string       | System.String   | String                                                                                                                | 40 bytes + content in 8 bytes increments                                   |
| decimal      | System.Decimal  | BigDecimal                                                                                                            | 40 bytes for practical small decimals                                      |
| uri          | System.String   | Class with embedded String                                                                                            | 56 bytes + content in 8 bytes increments                                   |
| url          | System.String   | Class with embedded String                                                                                            | 56 bytes + content in 8 bytes increments                                   |
| canonical    | System.String   | Class with embedded String                                                                                            | 56 bytes + content in 8 bytes increments                                   |
| base64Binary | System.String   | Class with embedded String                                                                                            | 56 bytes + content in 8 bytes increments                                   |
| instant      | System.DateTime | Instant or class with embedded OffsetDateTime                                                                         | 24 bytes or 112 bytes                                                      |
| date         | System.Date     | Year, YearMonth, LocalDate                                                                                            | 16 bytes, 24 bytes, 24 bytes                                               |
| dateTime     | System.DateTime | Class with embedded Year, Class with embedded YearMonth, Class with embedded LocalDate, LocalDateTime, OffsetDateTime | 32 bytes, 40 bytes, 40 bytes, 72 bytes, 96 bytes (zone offsets are cached) |
| time         | System.Time     | LocalTime                                                                                                             | 24 bytes                                                                   |
| code         | System.String   | Class with embedded String                                                                                            | 56 bytes + content in 8 bytes increments                                   |
| oid          | System.String   | Class with embedded String                                                                                            | 56 bytes + content in 8 bytes increments                                   |
| id           | System.String   | Class with embedded String                                                                                            | 56 bytes + content in 8 bytes increments                                   |
| markdown     | System.String   | Class with embedded String                                                                                            | 56 bytes + content in 8 bytes increments                                   |
| unsignedInt  | System.Integer  | Class with embedded int                                                                                               | 16 bytes                                                                   |
| positiveInt  | System.Integer  | Class with embedded int                                                                                               | 16 bytes                                                                   |
| uuid         | System.String   | java.util.UUID                                                                                                        | 32 bytes                                                                   |

For `boolean`, `integer` `string` and `decimal`, the obvious Java types are used. `BigDecimal` is used instead of `double` because FHIR recommends a decimal of basis 10.

The types, `uri`, `url`, `canonical`, `base64Binary`, `code`, `oid`, `id` and `markdown` are based on the FHIRPath system type `System.String`, which means they have an internal value of type string, but can have extensions, like all other primitive FHIR types. For these types, a thin wrapper is used in case, no extension is given. This wrapper is necessary in order to differentiate it from plain Java strings. The wrapper itself is a Java class, extending the `FhirType` protocol to deliver the type and the internal value. The wrapper class costs 16 bytes of heap space. For this reason, instances of `uri` are 16 bytes bigger than instances of `string`.

The type `instant` is either, backed by a `java.time.Instant` if the time zone is UTC or by a wrapper class with embedded `java.time.OffsetDateTime`. While using the `java.time.Instant` saves a lot of memory, it can't represent time zones other than UTC so the wrapped `java.time.OffsetDateTime` has to be used in cases where other time zones are used.

The type `date` is represented with the help of the three `java.time` types `Year`, `YearMonth` and `LocalDate`, one for each precision the `date` type supports. Keeping track of the precision is important for FHIR `date` and `dateTime` types and the `java.time` types are a perfect fit here.

The type `dateTime` uses wrapper classes with the three former mentioned `java.time` types in order to differentiate them from the ones used for the `date` type. On top of that, `java.time.LocalDateTime` and `java.time.OffsetDateTime` are used to represent date time values with and without time zones.

Last but not least the type `time` is represented by `java.time.LocalTime` and the type `uuid` by `java.util.UUID`.

## Serialization for storage in the Document Store

In the document store, FHIR resources are serialized in the CBOR format. CBOR stands for Concise Binary Object Representation and is defined in [RFC 7049][5]. CBOR is a binary serialization format.

**TODO: continue...**

[1]: <https://github.com/metosin/jsonista>
[2]: <https://github.com/clojure/data.xml>
[3]: <https://clojure.org/reference/data_structures>
[4]: <https://clojure.org/reference/protocols>
[5]: <https://tools.ietf.org/html/rfc7049>
