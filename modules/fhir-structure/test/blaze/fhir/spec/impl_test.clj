(ns blaze.fhir.spec.impl-test
  (:require
   [blaze.fhir.spec.impl :as impl]
   [blaze.fhir.spec.impl-spec]
   [blaze.fhir.spec.impl.xml :as xml]
   [blaze.fhir.spec.impl.xml-spec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.spec.type.system :as system]
   [blaze.fhir.structure-definition-repo :as sdr]
   [blaze.test-util :as tu]
   [clojure.alpha.spec :as s2]
   [clojure.data.xml.name :as xml-name]
   [clojure.data.xml.node :as xml-node]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [clojure.walk :as walk]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [java.util.regex Pattern]))

(xml-name/alias-uri 'f "http://hl7.org/fhir")

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(defonce structure-definition-repo
  (ig/init-key :blaze.fhir/structure-definition-repo {}))

(defn- regexes->str
  "Replaces all regular expression patterns in `form` with their string
  representation.

  This is necessary because patterns don't implement equals."
  [form]
  (walk/postwalk #(if (instance? Pattern %) (str %) %) form))

(defn- primitive-type [name]
  (some #(when (= name (:name %)) %)
        (sdr/primitive-types structure-definition-repo)))

(deftest primitive-type->spec-defs-test
  (testing "Boolean"
    (is (= (-> (primitive-type "boolean")
               impl/primitive-type->spec-defs
               regexes->str)
           [{:key :fhir/boolean
             :spec-form `type/boolean?}
            {:key :fhir.xml/boolean
             :spec-form
             `(s2/and
               xml/element?
               (fn [~'e] (xml/value-matches? "true|false" ~'e))
               (s2/conformer xml/remove-character-content xml/set-extension-tag)
               (s2/schema {:content (s2/coll-of :fhir.xml/Extension)})
               (s2/conformer (xml/xml-constructor type/boolean system/parse-boolean) type/to-xml))}])))

  (testing "Integer"
    (is (= (-> (primitive-type "integer")
               impl/primitive-type->spec-defs
               regexes->str)
           [{:key :fhir/integer
             :spec-form `type/integer?}
            {:key :fhir.xml/integer
             :spec-form
             `(s2/and
               xml/element?
               (fn [~'e] (xml/value-matches? "-?([0]|([1-9][0-9]*))" ~'e))
               (s2/conformer xml/remove-character-content xml/set-extension-tag)
               (s2/schema {:content (s2/coll-of :fhir.xml/Extension)})
               (s2/conformer (xml/xml-constructor type/integer system/parse-integer) type/to-xml))}])))

  (testing "string"
    (is (= (-> (primitive-type "string")
               impl/primitive-type->spec-defs
               regexes->str)
           [{:key :fhir/string
             :spec-form `type/string?}
            {:key :fhir.xml/string
             :spec-form
             `(s2/and
               xml/element?
               (s2/conformer xml/remove-character-content xml/set-extension-tag)
               (s2/schema {:content (s2/coll-of :fhir.xml/Extension)})
               (s2/conformer (xml/xml-constructor type/string identity) type/to-xml))}])))

  (testing "Decimal"
    (is (= (-> (primitive-type "decimal")
               impl/primitive-type->spec-defs
               regexes->str)
           [{:key :fhir/decimal
             :spec-form `type/decimal?}
            {:key :fhir.xml/decimal
             :spec-form
             `(s2/and
               xml/element?
               (fn [~'e] (xml/value-matches? "-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?" ~'e))
               (s2/conformer xml/remove-character-content xml/set-extension-tag)
               (s2/schema {:content (s2/coll-of :fhir.xml/Extension)})
               (s2/conformer (xml/xml-constructor type/decimal system/parse-decimal) type/to-xml))}])))

  (testing "uri"
    (is (= (-> (impl/primitive-type->spec-defs (primitive-type "uri"))
               regexes->str)
           [{:key :fhir/uri
             :spec-form `type/uri?}
            {:key :fhir.xml/uri
             :spec-form
             `(s2/and
               xml/element?
               (fn [~'e] (xml/value-matches? "(?U)[\\p{Print}&&[^\\p{Blank}]]*" ~'e))
               (s2/conformer xml/remove-character-content xml/set-extension-tag)
               (s2/schema {:content (s2/coll-of :fhir.xml/Extension)})
               (s2/conformer (xml/xml-constructor type/uri identity) type/to-xml))}])))

  (testing "canonical"
    (is (= (-> (impl/primitive-type->spec-defs (primitive-type "canonical"))
               regexes->str)
           [{:key :fhir/canonical
             :spec-form `type/canonical?}
            {:key :fhir.xml/canonical
             :spec-form
             `(s2/and
               xml/element?
               (fn [~'e] (xml/value-matches? "(?U)[\\p{Print}&&[^\\p{Blank}]]*" ~'e))
               (s2/conformer xml/remove-character-content xml/set-extension-tag)
               (s2/schema {:content (s2/coll-of :fhir.xml/Extension)})
               (s2/conformer (xml/xml-constructor type/canonical identity) type/to-xml))}])))

  (testing "base64Binary"
    (is (= (-> (impl/primitive-type->spec-defs (primitive-type "base64Binary"))
               regexes->str)
           [{:key :fhir/base64Binary
             :spec-form `type/base64Binary?}
            {:key :fhir.xml/base64Binary
             :spec-form
             `(s2/and
               xml/element?
               (fn [~'e] (xml/value-matches? "([0-9a-zA-Z\\\\+/=]{4})+" ~'e))
               (s2/conformer xml/remove-character-content xml/set-extension-tag)
               (s2/schema {:content (s2/coll-of :fhir.xml/Extension)})
               (s2/conformer (xml/xml-constructor type/base64Binary identity) type/to-xml))}])))

  (testing "code"
    (is (= (-> (impl/primitive-type->spec-defs (primitive-type "code"))
               regexes->str)
           [{:key :fhir/code
             :spec-form `type/code?}
            {:key :fhir.xml/code
             :spec-form
             `(s2/and
               xml/element?
               (s2/conformer xml/remove-character-content xml/set-extension-tag)
               (s2/schema {:content (s2/coll-of :fhir.xml/Extension)})
               (s2/conformer (xml/xml-constructor type/code identity) type/to-xml))}])))

  (testing "unsignedInt"
    (is (= (-> (impl/primitive-type->spec-defs (primitive-type "unsignedInt"))
               regexes->str)
           [{:key :fhir/unsignedInt
             :spec-form `type/unsignedInt?}
            {:key :fhir.xml/unsignedInt
             :spec-form
             `(s2/and
               xml/element?
               (fn [~'e] (xml/value-matches? "[0]|([1-9][0-9]*)" ~'e))
               (s2/conformer xml/remove-character-content xml/set-extension-tag)
               (s2/schema {:content (s2/coll-of :fhir.xml/Extension)})
               (s2/conformer (xml/xml-constructor type/unsignedInt system/parse-integer) type/to-xml))}])))

  (testing "positiveInt"
    (is (= (-> (impl/primitive-type->spec-defs (primitive-type "positiveInt"))
               regexes->str)
           [{:key :fhir/positiveInt
             :spec-form `type/positiveInt?}
            {:key :fhir.xml/positiveInt
             :spec-form
             `(s2/and
               xml/element?
               (fn [~'e] (xml/value-matches? "[1-9][0-9]*" ~'e))
               (s2/conformer xml/remove-character-content xml/set-extension-tag)
               (s2/schema {:content (s2/coll-of :fhir.xml/Extension)})
               (s2/conformer (xml/xml-constructor type/positiveInt system/parse-integer) type/to-xml))}])))

  (testing "uuid"
    (is (= (-> (impl/primitive-type->spec-defs (primitive-type "uuid"))
               regexes->str)
           [{:key :fhir/uuid
             :spec-form `type/uuid?}
            {:key :fhir.xml/uuid
             :spec-form
             `(s2/and
               xml/element?
               (fn [~'e] (xml/value-matches? "urn:uuid:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}" ~'e))
               (s2/conformer xml/remove-character-content xml/set-extension-tag)
               (s2/schema {:content (s2/coll-of :fhir.xml/Extension)})
               (s2/conformer (xml/xml-constructor type/uuid identity) type/to-xml))}])))

  (testing "xhtml"
    (is (= (impl/primitive-type->spec-defs (primitive-type "xhtml"))
           [{:key :fhir/xhtml
             :spec-form `type/xhtml?}
            {:key :fhir.xml/xhtml
             :spec-form
             `(s2/and
               xml/element?
               (s2/conformer type/xml->Xhtml type/xhtml-to-xml))}]))))

(defn- complex-type [name]
  (some #(when (= name (:name %)) %) (sdr/complex-types structure-definition-repo)))

(defn- resource [structure-definition-repo name]
  (some #(when (= name (:name %)) %) (sdr/resources structure-definition-repo)))

(deftest struct-def->spec-def-test
  (testing "internal representation of choice typed data element"
    (given (group-by :key (impl/struct-def->spec-def (complex-type "UsageContext")))
      [:fhir.UsageContext/value 0 :spec-form]
      := `(s2/or :valueCodeableConcept :fhir/CodeableConcept
                 :valueQuantity :fhir/Quantity
                 :valueRange :fhir/Range
                 :valueReference :fhir/Reference)))

  (testing "internal representation of complex type"
    (given (group-by :key (impl/struct-def->spec-def (complex-type "UsageContext")))
      [:fhir/UsageContext 0 :spec-form]
      := `(blaze.fhir.spec.impl.specs/record
           blaze.fhir.spec.type.UsageContext
           {:id :fhir.UsageContext/id,
            :extension (s2/coll-of :fhir.UsageContext/extension),
            :code :fhir.UsageContext/code,
            :value :fhir.UsageContext/value})))

  (testing "XML representation of Bundle.id"
    (given (group-by :key (impl/struct-def->spec-def (resource structure-definition-repo "Bundle")))
      [:fhir.xml.Bundle/id 0 :spec-form regexes->str]
      := `(s2/and
           xml/element?
           (s2/conformer impl/conform-xml-value impl/unform-xml-value)
           (fn [~'s] (.matches (re-matcher "[A-Za-z0-9\\-\\.]{1,64}" ~'s))))))

  (testing "XML representation of Bundle entry"
    (given (group-by :key (impl/struct-def->spec-def (resource structure-definition-repo "Bundle")))
      [:fhir.xml.Bundle/entry 0 :spec-form]
      := `(s2/and
           (s2/conformer
            impl/conform-xml
            (fn [~'m]
              (when ~'m
                (xml-node/element*
                 nil
                 (impl/select-non-nil-keys ~'m #{:id})
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

  (testing "XML representation of Bundle.entry.resource"
    (given (group-by :key (impl/struct-def->spec-def (resource structure-definition-repo "Bundle")))
      [:fhir.xml.Bundle.entry/resource 0 :spec-form] := :fhir.xml/Resource))

  (testing "XML representation of Extension"
    (given (group-by :key (impl/struct-def->spec-def (complex-type "Extension")))
      [:fhir.Extension/url 0 :spec-form regexes->str] := `string?
      [:fhir.xml.Extension/url 0 :spec-form regexes->str] := `string?
      [:fhir.xml.Extension/url 0 :representation] := :xmlAttr))

  (testing "XML representation of Coding"
    (given (group-by :key (impl/struct-def->spec-def (complex-type "Coding")))
      [:fhir.xml/Coding 0 :spec-form 1 2 2 2]
      := `(xml-node/element*
           nil
           (impl/select-non-nil-keys ~'m #{:id})
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
      `(assoc (impl/select-non-nil-keys ~'m #{}) :xmlns "http://hl7.org/fhir")))

  (testing "XML representation of Measure.url"
    (given (group-by :key (impl/struct-def->spec-def (resource structure-definition-repo "Measure")))
      [:fhir.xml.Measure/url 0 :spec-form regexes->str]
      := `(s2/and
           xml/element?
           (fn [~'e] (xml/value-matches? "(?U)[\\p{Print}&&[^\\p{Blank}]]*" ~'e))
           (s2/conformer xml/remove-character-content xml/set-extension-tag)
           (s2/schema {:content (s2/coll-of :fhir.xml/Extension)})
           (s2/conformer (xml/xml-constructor type/uri-interned identity) type/to-xml))))

  (testing "XML representation of Questionnaire.item contains recursive spec to itself"
    (given (group-by :key (impl/struct-def->spec-def (resource structure-definition-repo "Questionnaire")))
      [:fhir.xml.Questionnaire/item 0 :spec-form 2 1 :item]
      := `(s2/and
           (s2/conformer impl/ensure-coll clojure.core/identity)
           (s2/coll-of :fhir.xml.Questionnaire.item/item))))

  (testing "XML representation of Quantity.unit"
    (given (group-by :key (impl/struct-def->spec-def (complex-type "Quantity")))
      [:fhir.xml.Quantity/unit 0 :spec-form regexes->str]
      := `(s2/and
           xml/element?
           (s2/conformer xml/remove-character-content xml/set-extension-tag)
           (s2/schema {:content (s2/coll-of :fhir.xml/Extension)})
           (s2/conformer (xml/xml-constructor type/string-interned identity) type/to-xml)))))

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
            {:key :fhir.xml.Observation/code
             :modifier :xml
             :min 1
             :max "1"
             :spec-form :fhir.xml/CodeableConcept}])))

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
      [1 :key] := :fhir.xml.Bundle.entry/resource
      [1 :min] := 0
      [1 :max] := "1"
      [1 :spec-form] := :fhir.xml/Resource))

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
      [1 :key] := :fhir.xml.Questionnaire.item/item
      [1 :min] := 0
      [1 :max] := "*"
      [1 :spec-form] := :fhir.xml.Questionnaire/item)))
