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
 * use Clojure maps for resources
 * use specialized Java classes for complex and primitive data types
 * resources and data types contain an entry with the key `:fhir/type` and the value of the type as keyword with the namespace `fhir`
 * primitive data types use specialized Java classes that can hold both the value and extensions

All internal FHIR types are implemented as Java classes in the `blaze.fhir.spec.type` package and implement the `Base` interface. This interface extends several Clojure interfaces like `IPersistentMap` and `IRecord`, allowing them to be used just like Clojure maps.

```java
public interface Base extends IPersistentMap, IKeywordLookup, Map<Object, Object>, IRecord, IObj, IHashEq {
    boolean isInterned();
    Stream<PersistentVector> references();
    int memSize();
}
```

Primitive types also implement the `Primitive` interface, which provides access to the underlying value as a FHIRPath system type.

```java
public interface Primitive extends ExtensionValue {
    Object value();
    String valueAsString();
}
```

The use of specialized Java classes instead of plain Clojure maps or generic Java types allows for significant memory savings through interning of common values and a more compact data representation.

The following table shows the mapping from primitive FHIR types to their internal Java classes and FHIRPath system types:

| FHIR Type    | FHIRPath Type   | Java Class                                | Heap Size (no extension)                                                   |
|--------------|-----------------|-------------------------------------------|----------------------------------------------------------------------------|
| boolean      | System.Boolean  | `blaze.fhir.spec.type.Boolean`            | interned                                                                   |
| integer      | System.Integer  | `blaze.fhir.spec.type.Integer`            | 24 bytes                                                                   |
| string       | System.String   | `blaze.fhir.spec.type.String`             | 32-48 bytes + content                                                      |
| decimal      | System.Decimal  | `blaze.fhir.spec.type.Decimal`            | 48-64 bytes                                                                |
| uri          | System.String   | `blaze.fhir.spec.type.Uri`                | 32-48 bytes + content                                                      |
| url          | System.String   | `blaze.fhir.spec.type.Url`                | 32-48 bytes + content                                                      |
| canonical    | System.String   | `blaze.fhir.spec.type.Canonical`          | 32-48 bytes + content                                                      |
| base64Binary | System.String   | `blaze.fhir.spec.type.Base64Binary`       | 32-48 bytes + content                                                      |
| instant      | System.DateTime | `blaze.fhir.spec.type.Instant`            | 24 bytes (UTC) or 112 bytes                                                |
| date         | System.Date     | `blaze.fhir.spec.type.Date`               | 32 bytes                                                                   |
| dateTime     | System.DateTime | `blaze.fhir.spec.type.DateTime`           | 32-112 bytes                                                               |
| time         | System.Time     | `blaze.fhir.spec.type.Time`               | 32-40 bytes                                                                |
| code         | System.String   | `blaze.fhir.spec.type.Code`               | 32-48 bytes + content                                                      |
| oid          | System.String   | `blaze.fhir.spec.type.Oid`                | 32-48 bytes + content                                                      |
| id           | System.String   | `blaze.fhir.spec.type.Id`                 | 32-48 bytes + content                                                      |
| markdown     | System.String   | `blaze.fhir.spec.type.Markdown`           | 32-48 bytes + content                                                      |
| unsignedInt  | System.Integer  | `blaze.fhir.spec.type.UnsignedInt`        | 16-24 bytes                                                                |
| positiveInt  | System.Integer  | `blaze.fhir.spec.type.PositiveInt`        | 16-24 bytes                                                                |
| uuid         | System.String   | `blaze.fhir.spec.type.Uuid`               | 40-48 bytes                                                                |
| xhtml        | System.String   | `blaze.fhir.spec.type.Xhtml`              | 32-48 bytes + content                                                      |

For `boolean`, `integer`, `string` and `decimal`, specialized Blaze classes are used. `BigDecimal` is used internally for `decimal` because FHIR recommends a decimal of basis 10.

The types `uri`, `url`, `canonical`, `base64Binary`, `code`, `oid`, `id`, `markdown` and `xhtml` are based on the FHIRPath system type `System.String`, which means they have an internal value of type string, but can have extensions. Using specialized classes allows differentiating them from plain `string` values and enables interning of common values.

The type `instant` is either backed by a `java.time.Instant` if the time zone is UTC or by a `java.time.OffsetDateTime` otherwise.

The type `date` is represented with the help of specialized system types `DateYear`, `DateYearMonth`, and `DateDate`, one for each precision the `date` type supports. Keeping track of the precision is important for FHIR `date` and `dateTime` types.

Similarly, the type `dateTime` uses specialized system types or `java.time.LocalDateTime` and `java.time.OffsetDateTime` to represent date time values with varying precision and time zone information.

The type `time` is represented by `java.time.LocalTime` and the type `uuid` by `java.util.UUID`, both wrapped in their respective Blaze classes to support extensions.

## Serialization for storage in the Document Store

In the document store, FHIR resources are serialized in the CBOR format. CBOR stands for Concise Binary Object Representation and is defined in [RFC 7049][4]. CBOR is a binary serialization format.

**TODO: continue...**

[1]: <https://github.com/metosin/jsonista>
[2]: <https://github.com/clojure/data.xml>
[3]: <https://clojure.org/reference/data_structures>
[4]: <https://tools.ietf.org/html/rfc7049>