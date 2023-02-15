(ns blaze.fhir.spec.impl-test
  (:require
    [blaze.fhir.spec.impl :as impl]
    [blaze.fhir.spec.impl-spec]
    [blaze.fhir.spec.impl.specs :as specs]
    [blaze.fhir.spec.impl.util-spec]
    [blaze.fhir.spec.impl.xml :as xml]
    [blaze.fhir.spec.impl.xml-spec]
    [blaze.fhir.spec.type :as type]
    [blaze.fhir.structure-definition-repo :as u]
    [blaze.test-util :as tu :refer [with-system]]
    [clojure.alpha.spec :as s2]
    [clojure.data.xml.name :as xml-name]
    [clojure.data.xml.node :as xml-node]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [clojure.walk :as walk]
    [juxt.iota :refer [given]])
  (:import
    [java.util.regex Pattern]))


(xml-name/alias-uri 'f "http://hl7.org/fhir")


(st/instrument)


(test/use-fixtures :each tu/fixture)


(defn- regexes->str
  "Replaces all regular expression patterns in `form` with their string
  representation.

  This is necessary because patterns don't implement equals."
  [form]
  (walk/postwalk #(if (instance? Pattern %) (str %) %) form))


(defn- primitive-type [structure-definition-repo name]
  (some #(when (= name (:name %)) %)
        (u/primitive-types structure-definition-repo)))


(def system
  {:blaze.fhir/structure-definition-repo {}})


(deftest primitive-type->spec-defs-test
  (with-system [{:blaze.fhir/keys [structure-definition-repo]} system]
    (testing "boolean"
      (is (= (-> (primitive-type structure-definition-repo "boolean")
                 impl/primitive-type->spec-defs
                 regexes->str)
             [{:key :fhir/boolean
               :spec-form `type/boolean?}
              {:key :fhir.json/boolean
               :spec-form `(specs/json-pred-primitive boolean? type/boolean)}
              {:key :fhir.xml/boolean
               :spec-form
               `(s2/and
                  xml/element?
                  (fn [~'e] (xml/value-matches? "true|false" ~'e))
                  (s2/conformer xml/remove-character-content xml/set-extension-tag)
                  (s2/schema {:content (s2/coll-of :fhir.xml/Extension)})
                  (s2/conformer type/xml->Boolean type/to-xml))}
              {:key :fhir.cbor/boolean
               :spec-form `(specs/cbor-primitive type/boolean)}])))

    (testing "integer"
      (is (= (-> (primitive-type structure-definition-repo "integer")
                 impl/primitive-type->spec-defs
                 regexes->str)
             [{:key :fhir/integer
               :spec-form `type/integer?}
              {:key :fhir.json/integer
               :spec-form `(specs/json-pred-primitive int? type/integer)}
              {:key :fhir.xml/integer
               :spec-form
               `(s2/and
                  xml/element?
                  (fn [~'e] (xml/value-matches? "-?([0]|([1-9][0-9]*))" ~'e))
                  (s2/conformer xml/remove-character-content xml/set-extension-tag)
                  (s2/schema {:content (s2/coll-of :fhir.xml/Extension)})
                  (s2/conformer type/xml->Integer type/to-xml))}
              {:key :fhir.cbor/integer
               :spec-form `(specs/cbor-primitive type/integer)}])))

    (testing "string"
      (is (= (-> (primitive-type structure-definition-repo "string")
                 impl/primitive-type->spec-defs
                 regexes->str)
             [{:key :fhir/string
               :spec-form `type/string?}
              {:key :fhir.json/string
               :spec-form `(specs/json-regex-primitive "[ \\r\\n\\t\\S]+" type/string)}
              {:key :fhir.xml/string
               :spec-form
               `(s2/and
                  xml/element?
                  (fn [~'e] (xml/value-matches? "[ \\r\\n\\t\\S]+" ~'e))
                  (s2/conformer xml/remove-character-content xml/set-extension-tag)
                  (s2/schema {:content (s2/coll-of :fhir.xml/Extension)})
                  (s2/conformer type/xml->String type/to-xml))}
              {:key :fhir.cbor/string
               :spec-form `(specs/cbor-primitive type/string)}])))

    (testing "decimal"
      (is (= (-> (primitive-type structure-definition-repo "decimal")
                 impl/primitive-type->spec-defs
                 regexes->str)
             [{:key :fhir/decimal
               :spec-form `type/decimal?}
              {:key :fhir.json/decimal
               :spec-form `(specs/json-pred-primitive impl/decimal-or-int? type/decimal)}
              {:key :fhir.xml/decimal
               :spec-form
               `(s2/and
                  xml/element?
                  (fn [~'e] (xml/value-matches? "-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?" ~'e))
                  (s2/conformer xml/remove-character-content xml/set-extension-tag)
                  (s2/schema {:content (s2/coll-of :fhir.xml/Extension)})
                  (s2/conformer type/xml->Decimal type/to-xml))}
              {:key :fhir.cbor/decimal
               :spec-form `(specs/cbor-primitive type/decimal)}])))

    (testing "uri"
      (is (= (-> (impl/primitive-type->spec-defs (primitive-type structure-definition-repo "uri"))
                 regexes->str)
             [{:key :fhir/uri
               :spec-form `type/uri?}
              {:key :fhir.json/uri
               :spec-form `(specs/json-regex-primitive "\\S*" type/uri)}
              {:key :fhir.xml/uri
               :spec-form
               `(s2/and
                  xml/element?
                  (fn [~'e] (xml/value-matches? "\\S*" ~'e))
                  (s2/conformer xml/remove-character-content xml/set-extension-tag)
                  (s2/schema {:content (s2/coll-of :fhir.xml/Extension)})
                  (s2/conformer type/xml->Uri type/to-xml))}
              {:key :fhir.cbor/uri
               :spec-form `(specs/cbor-primitive type/uri)}])))

    (testing "canonical"
      (is (= (-> (impl/primitive-type->spec-defs (primitive-type structure-definition-repo "canonical"))
                 regexes->str)
             [{:key :fhir/canonical
               :spec-form `type/canonical?}
              {:key :fhir.json/canonical
               :spec-form `(specs/json-regex-primitive "\\S*" type/canonical)}
              {:key :fhir.xml/canonical
               :spec-form
               `(s2/and
                  xml/element?
                  (fn [~'e] (xml/value-matches? "\\S*" ~'e))
                  (s2/conformer xml/remove-character-content xml/set-extension-tag)
                  (s2/schema {:content (s2/coll-of :fhir.xml/Extension)})
                  (s2/conformer type/xml->Canonical type/to-xml))}
              {:key :fhir.cbor/canonical
               :spec-form `(specs/cbor-primitive type/canonical)}])))

    (testing "base64Binary"
      (is (= (-> (impl/primitive-type->spec-defs (primitive-type structure-definition-repo "base64Binary"))
                 regexes->str)
             [{:key :fhir/base64Binary
               :spec-form `type/base64Binary?}
              {:key :fhir.json/base64Binary
               :spec-form `(specs/json-regex-primitive "([0-9a-zA-Z\\\\+/=]{4})+" type/base64Binary)}
              {:key :fhir.xml/base64Binary
               :spec-form
               `(s2/and
                  xml/element?
                  (fn [~'e] (xml/value-matches? "([0-9a-zA-Z\\\\+/=]{4})+" ~'e))
                  (s2/conformer xml/remove-character-content xml/set-extension-tag)
                  (s2/schema {:content (s2/coll-of :fhir.xml/Extension)})
                  (s2/conformer type/xml->Base64Binary type/to-xml))}
              {:key :fhir.cbor/base64Binary
               :spec-form `(specs/cbor-primitive type/base64Binary)}])))

    (testing "code"
      (is (= (-> (impl/primitive-type->spec-defs (primitive-type structure-definition-repo "code"))
                 regexes->str)
             [{:key :fhir/code
               :spec-form `type/code?}
              {:key :fhir.json/code
               :spec-form `(specs/json-regex-primitive "[^\\s]+(\\s[^\\s]+)*" type/code)}
              {:key :fhir.xml/code
               :spec-form
               `(s2/and
                  xml/element?
                  (fn [~'e] (xml/value-matches? "[^\\s]+(\\s[^\\s]+)*" ~'e))
                  (s2/conformer xml/remove-character-content xml/set-extension-tag)
                  (s2/schema {:content (s2/coll-of :fhir.xml/Extension)})
                  (s2/conformer type/xml->Code type/to-xml))}
              {:key :fhir.cbor/code
               :spec-form `(specs/cbor-primitive type/code)}])))

    (testing "unsignedInt"
      (is (= (-> (impl/primitive-type->spec-defs (primitive-type structure-definition-repo "unsignedInt"))
                 regexes->str)
             [{:key :fhir/unsignedInt
               :spec-form `type/unsignedInt?}
              {:key :fhir.json/unsignedInt
               :spec-form `(specs/json-pred-primitive int? type/unsignedInt)}
              {:key :fhir.xml/unsignedInt
               :spec-form
               `(s2/and
                  xml/element?
                  (fn [~'e] (xml/value-matches? "[0]|([1-9][0-9]*)" ~'e))
                  (s2/conformer xml/remove-character-content xml/set-extension-tag)
                  (s2/schema {:content (s2/coll-of :fhir.xml/Extension)})
                  (s2/conformer type/xml->UnsignedInt type/to-xml))}
              {:key :fhir.cbor/unsignedInt
               :spec-form `(specs/cbor-primitive type/unsignedInt)}])))

    (testing "positiveInt"
      (is (= (-> (impl/primitive-type->spec-defs (primitive-type structure-definition-repo "positiveInt"))
                 regexes->str)
             [{:key :fhir/positiveInt
               :spec-form `type/positiveInt?}
              {:key :fhir.json/positiveInt
               :spec-form `(specs/json-pred-primitive int? type/positiveInt)}
              {:key :fhir.xml/positiveInt
               :spec-form
               `(s2/and
                  xml/element?
                  (fn [~'e] (xml/value-matches? "[1-9][0-9]*" ~'e))
                  (s2/conformer xml/remove-character-content xml/set-extension-tag)
                  (s2/schema {:content (s2/coll-of :fhir.xml/Extension)})
                  (s2/conformer type/xml->PositiveInt type/to-xml))}
              {:key :fhir.cbor/positiveInt
               :spec-form `(specs/cbor-primitive type/positiveInt)}])))

    (testing "uuid"
      (is (= (-> (impl/primitive-type->spec-defs (primitive-type structure-definition-repo "uuid"))
                 regexes->str)
             [{:key :fhir/uuid
               :spec-form `type/uuid?}
              {:key :fhir.json/uuid
               :spec-form `(specs/json-regex-primitive "urn:uuid:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}" type/uuid)}
              {:key :fhir.xml/uuid
               :spec-form
               `(s2/and
                  xml/element?
                  (fn [~'e] (xml/value-matches? "urn:uuid:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}" ~'e))
                  (s2/conformer xml/remove-character-content xml/set-extension-tag)
                  (s2/schema {:content (s2/coll-of :fhir.xml/Extension)})
                  (s2/conformer type/xml->Uuid type/to-xml))}
              {:key :fhir.cbor/uuid
               :spec-form `(specs/cbor-primitive type/uuid)}])))

    (testing "xhtml"
      (is (= (impl/primitive-type->spec-defs (primitive-type structure-definition-repo "xhtml"))
             [{:key :fhir/xhtml
               :spec-form `type/xhtml?}
              {:key :fhir.json/xhtml
               :spec-form
               `(s2/and
                  string?
                  (s2/conformer type/->Xhtml identity))}
              {:key :fhir.xml/xhtml
               :spec-form
               `(s2/and
                  xml/element?
                  (s2/conformer type/xml->Xhtml type/to-xml))}
              {:key :fhir.cbor/xhtml
               :spec-form `(s2/conformer type/->Xhtml identity)}])))))


(defn- complex-type [structure-definition-repo name]
  (some #(when (= name (:name %)) %) (u/complex-types structure-definition-repo)))


(defn- resource [structure-definition-repo name]
  (some #(when (= name (:name %)) %) (u/resources structure-definition-repo)))


(deftest struct-def->spec-def-test
  (with-system [{:blaze.fhir/keys [structure-definition-repo]} system]
    (testing "internal representation of choice typed data element"
      (given (group-by :key (impl/struct-def->spec-def (complex-type structure-definition-repo "UsageContext")))
        [:fhir.UsageContext/value 0 :spec-form]
        := `(s2/or :valueCodeableConcept :fhir/CodeableConcept
                   :valueQuantity :fhir/Quantity
                   :valueRange :fhir/Range
                   :valueReference :fhir/Reference)))

    (testing "internal representation of complex type"
      (testing "has a type checker"
        (given (group-by :key (impl/struct-def->spec-def (complex-type structure-definition-repo "UsageContext")))
          [:fhir/UsageContext 0 :spec-form 1]
          := `(fn [~'m] (identical? :fhir/UsageContext (type/type ~'m)))))
      (testing "has a schema form"
        (given (group-by :key (impl/struct-def->spec-def (complex-type structure-definition-repo "UsageContext")))
          [:fhir/UsageContext 0 :spec-form 2]
          := `(s2/schema
                {:id :fhir.UsageContext/id
                 :extension (s2/coll-of :fhir.UsageContext/extension)
                 :code :fhir.UsageContext/code
                 :value :fhir.UsageContext/value}))))

    (testing "JSON representation of choice typed data element"
      (given (group-by :key (impl/struct-def->spec-def (complex-type structure-definition-repo "UsageContext")))
        [:fhir.json.UsageContext/valueCodeableConcept 0 :choice-group] := :value))

    (testing "JSON representation of complex type with choice typed data element"
      (testing "has a type annotating conformer"
        (given (group-by :key (impl/struct-def->spec-def (complex-type structure-definition-repo "UsageContext")))
          [:fhir.json/UsageContext 0 :spec-form 3]
          := `(s2/conformer
                (fn [~'m] (assoc ~'m :fhir/type :fhir/UsageContext))
                (fn [~'m] (dissoc ~'m :fhir/type)))))
      (testing "has a choice group merging conformer"
        (given (group-by :key (impl/struct-def->spec-def (complex-type structure-definition-repo "UsageContext")))
          [:fhir.json/UsageContext 0 :spec-form 4]
          := `(s2/conformer
                (fn [~'m]
                  (impl/remove-choice-type ~'m [:valueCodeableConcept :valueQuantity :valueRange :valueReference] :value))
                (fn [~'m]
                  (impl/add-choice-type ~'m :value))))))

    (testing "JSON representation of Patient"
      (testing "has a type annotating conformer"
        (given (group-by :key (impl/struct-def->spec-def (resource structure-definition-repo "Patient")))
          [:fhir.json/Patient 0 :spec-form 3]
          := `(s2/conformer
                (fn [~'m] (-> (assoc ~'m :fhir/type :fhir/Patient) (dissoc :resourceType)))
                (fn [~'m] (-> (dissoc ~'m :fhir/type) (assoc :resourceType "Patient")))))))

    (testing "JSON representation of Bundle"
      (given (group-by :key (impl/struct-def->spec-def (resource structure-definition-repo "Bundle")))
        [:fhir.json/Bundle 0 :spec-form 2]
        := `(s2/schema
              {:id :fhir.json.Bundle/id
               :meta :fhir.json.Bundle/meta
               :implicitRules :fhir.json.Bundle/implicitRules
               :language :fhir.json.Bundle/language
               :identifier :fhir.json.Bundle/identifier
               :type :fhir.json.Bundle/type
               :timestamp :fhir.json.Bundle/timestamp
               :total :fhir.json.Bundle/total
               :link (s2/and (s2/conformer impl/ensure-coll identity)
                             (s2/coll-of :fhir.json.Bundle/link))
               :entry (s2/and (s2/conformer impl/ensure-coll identity)
                              (s2/coll-of :fhir.json.Bundle/entry))
               :signature :fhir.json.Bundle/signature})))

    (testing "JSON representation of Bundle.id"
      (given (group-by :key (impl/struct-def->spec-def (resource structure-definition-repo "Bundle")))
        [:fhir.json.Bundle/id 0 :spec-form regexes->str]
        := `(s2/and string? (fn [~'s] (.matches (re-matcher "[A-Za-z0-9\\-\\.]{1,64}" ~'s))))))

    (testing "XML representation of Bundle.id"
      (given (group-by :key (impl/struct-def->spec-def (resource structure-definition-repo "Bundle")))
        [:fhir.xml.Bundle/id 0 :spec-form regexes->str]
        := `(s2/and
              xml/element?
              (s2/conformer impl/conform-xml-value impl/unform-xml-value)
              (fn [~'s] (.matches (re-matcher "[A-Za-z0-9\\-\\.]{1,64}" ~'s))))))

    (testing "JSON representation of Bundle.entry"
      (given (group-by :key (impl/struct-def->spec-def (resource structure-definition-repo "Bundle")))
        [:fhir.json.Bundle/entry 0 :spec-form 2]
        := `(s2/schema
              {:id :fhir.json.Bundle.entry/id
               :extension (s2/and (s2/conformer impl/ensure-coll identity)
                                  (s2/coll-of :fhir.json.Bundle.entry/extension))
               :modifierExtension (s2/and (s2/conformer impl/ensure-coll identity)
                                          (s2/coll-of :fhir.json.Bundle.entry/modifierExtension))
               :link (s2/and (s2/conformer impl/ensure-coll identity)
                             (s2/coll-of :fhir.json.Bundle.entry/link))
               :fullUrl :fhir.json.Bundle.entry/fullUrl
               :resource :fhir.json.Bundle.entry/resource
               :search :fhir.json.Bundle.entry/search
               :request :fhir.json.Bundle.entry/request
               :response :fhir.json.Bundle.entry/response})))

    (testing "XML representation of Bundle entry"
      (given (group-by :key (impl/struct-def->spec-def (resource structure-definition-repo "Bundle")))
        [:fhir.xml.Bundle/entry 0 :spec-form]
        := `(s2/and
              (s2/conformer
                blaze.fhir.spec.impl/conform-xml
                (fn [~'m]
                  (when ~'m
                    (xml-node/element*
                      nil
                      (blaze.fhir.spec.impl/select-non-nil-keys ~'m [:id])
                      (-> []
                          (impl/conj-all ::f/extension (:extension ~'m))
                          (impl/conj-all ::f/modifierExtension (:modifierExtension ~'m))
                          (impl/conj-all ::f/link (:link ~'m))
                          (impl/conj-when (some-> ~'m :fullUrl (assoc :tag ::f/fullUrl)))
                          (impl/conj-when (:resource ~'m))
                          (impl/conj-when (some-> ~'m :search (assoc :tag ::f/search)))
                          (impl/conj-when (some-> ~'m :request (assoc :tag ::f/request)))
                          (impl/conj-when (some-> ~'m :response (assoc :tag ::f/response))))))))
              (s2/schema
                {:id :fhir.xml.Bundle.entry/id
                 :extension (s2/and
                              (s2/conformer impl/ensure-coll identity)
                              (s2/coll-of :fhir.xml.Bundle.entry/extension))
                 :modifierExtension (s2/and
                                      (s2/conformer impl/ensure-coll identity)
                                      (s2/coll-of :fhir.xml.Bundle.entry/modifierExtension))
                 :link (s2/and
                         (s2/conformer impl/ensure-coll identity)
                         (s2/coll-of :fhir.xml.Bundle.entry/link))
                 :fullUrl :fhir.xml.Bundle.entry/fullUrl
                 :resource :fhir.xml.Bundle.entry/resource
                 :search :fhir.xml.Bundle.entry/search
                 :request :fhir.xml.Bundle.entry/request
                 :response :fhir.xml.Bundle.entry/response})
              (s2/conformer
                (fn [~'m] (assoc ~'m :fhir/type :fhir.Bundle/entry))
                identity))))

    (testing "JSON representation of Bundle.entry.resource"
      (given (group-by :key (impl/struct-def->spec-def (resource structure-definition-repo "Bundle")))
        [:fhir.json.Bundle.entry/resource 0 :spec-form] := :fhir.json/Resource))

    (testing "XML representation of Bundle.entry.resource"
      (given (group-by :key (impl/struct-def->spec-def (resource structure-definition-repo "Bundle")))
        [:fhir.xml.Bundle.entry/resource 0 :spec-form] := :fhir.xml/Resource))

    (testing "XML representation of Extension"
      (given (group-by :key (impl/struct-def->spec-def (complex-type structure-definition-repo "Extension")))
        [:fhir.Extension/url 0 :spec-form regexes->str]
        := `(s2/and string? (specs/regex "\\S*" impl/intern-string))
        [:fhir.json.Extension/url 0 :spec-form regexes->str]
        := `(s2/and string? (specs/regex "\\S*" impl/intern-string))
        [:fhir.xml.Extension/url 0 :spec-form regexes->str]
        := `(s2/and string? (specs/regex "\\S*" impl/intern-string))
        [:fhir.xml.Extension/url 0 :representation] := :xmlAttr))

    (testing "XML representation of Coding"
      (given (group-by :key (impl/struct-def->spec-def (complex-type structure-definition-repo "Coding")))
        [:fhir.xml/Coding 0 :spec-form 1 2 2 2]
        := `(xml-node/element*
              nil
              (blaze.fhir.spec.impl/select-non-nil-keys ~'m [:id])
              (->
                []
                (impl/conj-all ::f/extension (:extension ~'m))
                (impl/conj-when (some-> ~'m :system (assoc :tag ::f/system)))
                (impl/conj-when (some-> ~'m :version (assoc :tag ::f/version)))
                (impl/conj-when (some-> ~'m :code (assoc :tag ::f/code)))
                (impl/conj-when (some-> ~'m :display (assoc :tag ::f/display)))
                (impl/conj-when (some-> ~'m :userSelected (assoc :tag ::f/userSelected)))))))

    (testing "XML representation of Measure unformer XML attributes"
      (given (group-by :key (impl/struct-def->spec-def (resource structure-definition-repo "Measure")))
        [:fhir.xml/Measure 0 :spec-form 1 2 2 2 2] :=
        `(assoc (blaze.fhir.spec.impl/select-non-nil-keys ~'m []) :xmlns "http://hl7.org/fhir")))

    (testing "XML representation of Measure.url"
      (given (group-by :key (impl/struct-def->spec-def (resource structure-definition-repo "Measure")))
        [:fhir.xml.Measure/url 0 :spec-form] := :fhir.xml/uri))

    (testing "XML representation of Questionnaire.item contains recursive spec to itself"
      (given (group-by :key (impl/struct-def->spec-def (resource structure-definition-repo "Questionnaire")))
        [:fhir.xml.Questionnaire/item 0 :spec-form 2 1 :item]
        := `(s2/and
              (s2/conformer impl/ensure-coll clojure.core/identity)
              (s2/coll-of :fhir.xml.Questionnaire.item/item))))

    (testing "JSON representation of Quantity.unit"
      (given (group-by :key (impl/struct-def->spec-def (complex-type structure-definition-repo "Quantity")))
        [:fhir.json.Quantity/unit 0 :spec-form regexes->str]
        := `(specs/json-regex-primitive "[ \\r\\n\\t\\S]+" type/intern-string)))

    (testing "XML representation of Quantity.unit"
      (given (group-by :key (impl/struct-def->spec-def (complex-type structure-definition-repo "Quantity")))
        [:fhir.xml.Quantity/unit 0 :spec-form regexes->str]
        := `(s2/and
              xml/element?
              (fn [~'e] (xml/value-matches? "[ \\r\\n\\t\\S]+" ~'e))
              (s2/conformer xml/remove-character-content xml/set-extension-tag)
              (s2/schema {:content (s2/coll-of :fhir.xml/Extension)})
              (s2/conformer type/xml->InternedString type/to-xml))))

    (testing "CBOR representation of Quantity.unit"
      (given (group-by :key (impl/struct-def->spec-def (complex-type structure-definition-repo "Quantity")))
        [:fhir.cbor.Quantity/unit 0 :spec-form regexes->str]
        := `(specs/cbor-primitive type/intern-string)))))


(deftest elem-def->spec-def-test
  (testing "normal type"
    (is (= (impl/elem-def->spec-def
             {:path "Observation.code"
              :min 1
              :max "1"
              :type [{:code "CodeableConcept"}]})
           [{:key :fhir.Observation/code
             :min 1
             :max "1"
             :spec-form :fhir/CodeableConcept}
            {:key :fhir.json.Observation/code
             :modifier :json
             :min 1
             :max "1"
             :spec-form :fhir.json/CodeableConcept}
            {:key :fhir.xml.Observation/code
             :modifier :xml
             :min 1
             :max "1"
             :spec-form :fhir.xml/CodeableConcept}
            {:key :fhir.cbor.Observation/code
             :modifier :cbor
             :min 1
             :max "1"
             :spec-form :fhir.cbor/CodeableConcept}])))

  (testing "choice type"
    (impl/elem-def->spec-def
      {:path "Observation.value[x]"
       :min 0
       :max "1"
       :type [{:code "Quantity"}
              {:code "CodeableConcept"}
              {:code "string"}
              {:code "boolean"}
              {:code "integer"}
              {:code "Range"}
              {:code "Ratio"}
              {:code "SampledData"}
              {:code "time"}
              {:code "dateTime"}
              {:code "Period"}]}))

  (testing "Bundle.entry.resource"
    (given (impl/elem-def->spec-def
             {:path "Bundle.entry.resource"
              :min 0
              :max "1"
              :type [{:code "Resource"}]})
      [0 :key] := :fhir.Bundle.entry/resource
      [0 :min] := 0
      [0 :max] := "1"
      [0 :spec-form] := :fhir/Resource
      [1 :key] := :fhir.json.Bundle.entry/resource
      [1 :min] := 0
      [1 :max] := "1"
      [1 :spec-form] := :fhir.json/Resource
      [2 :key] := :fhir.xml.Bundle.entry/resource
      [2 :min] := 0
      [2 :max] := "1"
      [2 :spec-form] := :fhir.xml/Resource
      [3 :key] := :fhir.cbor.Bundle.entry/resource
      [3 :min] := 0
      [3 :max] := "1"
      [3 :spec-form] := :fhir.cbor/Resource))

  (testing "Questionnaire.item.item"
    (given (impl/elem-def->spec-def
             {:path "Questionnaire.item.item"
              :min 0
              :max "*"
              :contentReference "#Questionnaire.item"})
      [0 :key] := :fhir.Questionnaire.item/item
      [0 :min] := 0
      [0 :max] := "*"
      [0 :spec-form] := :fhir.Questionnaire/item
      [1 :key] := :fhir.json.Questionnaire.item/item
      [1 :min] := 0
      [1 :max] := "*"
      [1 :spec-form] := :fhir.json.Questionnaire/item
      [2 :key] := :fhir.xml.Questionnaire.item/item
      [2 :min] := 0
      [2 :max] := "*"
      [2 :spec-form] := :fhir.xml.Questionnaire/item
      [3 :key] := :fhir.cbor.Questionnaire.item/item
      [3 :min] := 0
      [3 :max] := "*"
      [3 :spec-form] := :fhir.cbor.Questionnaire/item)))
