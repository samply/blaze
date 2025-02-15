(ns blaze.fhir.spec.type-test
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.fhir.spec.generators :as fg]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.spec.type-spec]
   [blaze.fhir.spec.type.protocols :as p]
   [blaze.fhir.spec.type.system.spec]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.data.xml :as xml]
   [clojure.data.xml.name :as xml-name]
   [clojure.data.xml.node :as xml-node]
   [clojure.data.xml.prxml :as prxml]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [jsonista.core :as j])
  (:import
   [com.fasterxml.jackson.core SerializableString]
   [com.fasterxml.jackson.core.io JsonStringEncoder]
   [com.fasterxml.jackson.databind ObjectMapper]
   [com.google.common.hash Hashing]
   [java.nio.charset StandardCharsets]
   [java.time Instant LocalTime OffsetDateTime ZoneOffset]))

(xml-name/alias-uri 'f "http://hl7.org/fhir")
(xml-name/alias-uri 'xhtml "http://www.w3.org/1999/xhtml")

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn murmur3 [x]
  (let [hasher (.newHasher (Hashing/murmur3_32_fixed))]
    (type/hash-into x hasher)
    (Integer/toHexString (.asInt (.hash hasher)))))

(def ^:private object-mapper
  (doto (ObjectMapper.)
    (.registerModule type/fhir-module)))

(defn- gen-json-string [x]
  (String. ^bytes (j/write-value-as-bytes x object-mapper) StandardCharsets/UTF_8))

(def ^:private sexp prxml/sexp-as-element)

(defn- sexp-value [value]
  (sexp [nil {:value value}]))

(def ^:private string-extension
  #fhir/Extension{:url #fhir/uri"foo" :valueString "bar"})

(defn interned? [x y]
  (and (identical? x y) (p/-interned x) (p/-interned y)))

(defn not-interned? [x y]
  (and (= x y)
       (not (identical? x y))
       (not (p/-interned x))
       (not (p/-interned y))))

(def ^:private internable-extension
  #fhir/Extension{:url "url-130945" :value #fhir/code"value-130953"})

(def ^:private not-internable-extension
  #fhir/Extension{:url "url-205325" :value #fhir/string"value-205336"})

(deftest nil-test
  (testing "all FhirType methods can be called on nil"
    (testing "type"
      (is (nil? (type/type nil))))

    (testing "interned"
      (is (interned? nil nil)))

    (testing "value"
      (is (nil? (type/value nil))))

    (testing "to-json"
      (is (= "null" (gen-json-string nil))))

    (testing "to-xml"
      (is (nil? (type/to-xml nil))))

    (testing "hash-into"
      (is (= "0" (murmur3 nil))))

    (testing "references"
      (is (nil? (type/references nil))))))

(deftest Object-test
  (testing "arbitrary instances have no fhir type"
    (is (nil? (type/type (Object.))))))

(deftest boolean-test
  (testing "boolean?"
    (are [x] (type/boolean? x)
      #fhir/boolean true
      #fhir/boolean{:id "foo"}))

  (testing "type"
    (are [x] (= :fhir/boolean (type/type x))
      #fhir/boolean true
      #fhir/boolean{:id "foo"}))

  (testing "Boolean"
    (is (= #fhir/boolean{:value true} #fhir/boolean true)))

  (testing "interned"
    (is (interned? true true))

    (testing "with extension"
      (are [x y] (not-interned? x y)
        (type/boolean {:id "id-161721"
                       :extension [internable-extension]
                       :value true})
        (type/boolean {:id "id-161721"
                       :extension [internable-extension]
                       :value true})

        (type/boolean {:extension [not-internable-extension]})
        (type/boolean {:extension [not-internable-extension]}))

      (are [x y] (interned? x y)
        (type/boolean {:extension [internable-extension] :value true})
        (type/boolean {:extension [internable-extension] :value true}))))

  (testing "value"
    (are [x] (true? (type/value x))
      #fhir/boolean true
      #fhir/boolean{:id "foo" :value true}))

  (testing "to-json"
    (are [b s] (= s (gen-json-string b))
      true "true"
      false "false"))

  (testing "to-xml"
    (are [b s] (= (sexp-value s) (type/to-xml b))
      true "true"
      false "false"))

  (testing "hash-into"
    (are [b hex] (= hex (murmur3 b))
      #fhir/boolean true "90690515"
      #fhir/boolean false "70fda443"

      #fhir/boolean{:id "0" :value true} "42cd2f28"
      #fhir/boolean{:id "0" :value false} "34625218"
      #fhir/boolean{:id "1" :value true} "35a3a122"
      #fhir/boolean{:id "1" :value false} "2a892c16"

      #fhir/boolean{:id "0"} "56db993c"
      #fhir/boolean{:id "1"} "25b15217"

      #fhir/boolean{:extension [#fhir/Extension{:url "0"}]} "a7664edc"
      #fhir/boolean{:extension [#fhir/Extension{:url "1"}]} "1293ee18"
      #fhir/boolean{:extension [#fhir/Extension{:url "0"} #fhir/Extension{:url "0"}]} "d1fda5de"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      true
      nil))

  (testing "print"
    (are [boolean s] (= (pr-str boolean) s)
      #fhir/boolean{:id "0"} "#fhir/boolean{:id \"0\"}")))

(deftest integer-test
  (testing "integer?"
    (are [x] (type/integer? x)
      #fhir/integer 1
      #fhir/integer{:id "foo"}))

  (testing "type"
    (are [x] (= :fhir/integer (type/type x))
      #fhir/integer 1
      #fhir/integer{:id "foo"}))

  (testing "Integer"
    (is (= #fhir/integer{:value 1} #fhir/integer 1)))

  (testing "interned"
    (is (not-interned? #fhir/integer 165519 #fhir/integer 165519))

    (testing "with extension"
      (are [x y] (not-interned? x y)
        (type/integer {:extension [internable-extension]
                       :value 165519})
        (type/integer {:extension [internable-extension]
                       :value 165519})

        (type/integer {:id "id-162329" :extension [internable-extension]})
        (type/integer {:id "id-162329" :extension [internable-extension]}))

      (are [x y] (interned? x y)
        (type/integer {:extension [internable-extension]})
        (type/integer {:extension [internable-extension]}))))

  (testing "value"
    (are [x] (= 1 (type/value x))
      #fhir/integer 1
      #fhir/integer{:id "foo" :value 1}))

  (testing "to-json"
    (is (= "1" (gen-json-string #fhir/integer 1))))

  (testing "to-xml"
    (is (= (sexp-value "1") (type/to-xml #fhir/integer 1))))

  (testing "hash-into"
    (are [i hex] (= hex (murmur3 i))
      #fhir/integer 0 "ab61a435"
      #fhir/integer 1 "f9ff6b7c"
      #fhir/integer{:id "foo"} "667e7a1b"
      #fhir/integer{:id "foo" :value 0} "fdd4f126"
      #fhir/integer{:extension [#fhir/Extension{:url "foo"}]} "b353ef83"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/integer 0
      nil)))

(deftest long-test
  (testing "long?"
    (are [x] (type/long? x)
      #fhir/long 1
      #fhir/long{:id "foo"}))

  (testing "type"
    (are [x] (= :fhir/long (type/type x))
      #fhir/long 1
      #fhir/long{:id "foo"}))

  (testing "Long"
    (is (= #fhir/long{:value 1} #fhir/long 1)))

  (testing "interned"
    (is (not-interned? #fhir/long 165519 #fhir/long 165519))

    (testing "with extension"
      (are [x y] (not-interned? x y)
        (type/long {:extension [internable-extension]
                    :value 165519})
        (type/long {:extension [internable-extension]
                    :value 165519})

        (type/long {:id "id-162329" :extension [internable-extension]})
        (type/long {:id "id-162329" :extension [internable-extension]}))

      (are [x y] (interned? x y)
        (type/long {:extension [internable-extension]})
        (type/long {:extension [internable-extension]}))))

  (testing "value"
    (are [x] (= 1 (type/value x))
      #fhir/long 1
      #fhir/long{:id "foo" :value 1}))

  (testing "to-json"
    (is (= "1" (gen-json-string #fhir/long 1))))

  (testing "to-xml"
    (is (= (sexp-value "1") (type/to-xml #fhir/long 1))))

  (testing "hash-into"
    (are [i hex] (= hex (murmur3 i))
      #fhir/long 0 "9bc977cc"
      #fhir/long 1 "fac0175c"
      #fhir/long{:id "foo"} "943aa9b2"
      #fhir/long{:id "foo" :value 0} "a5e71473"
      #fhir/long{:extension [#fhir/Extension{:url "foo"}]} "589558b6"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/long 0
      nil)))

(deftest string-test
  (testing "string?"
    (are [x] (type/string? x)
      #fhir/string""
      #fhir/string{:id "foo"}))

  (testing "type"
    (are [x] (= :fhir/string (type/type x))
      #fhir/string""
      #fhir/string{:id "foo"}))

  (testing "string"
    (is (= #fhir/string{:value "181312"} #fhir/string"181312")))

  (testing "interned"
    (is (not-interned? (String. "165645") (String. "165645")))

    (is (identical? (type/intern-string (String. "165645"))
                    (type/intern-string (String. "165645"))))

    (testing "with extension"
      (are [x y] (not-interned? x y)
        (type/string {:extension [internable-extension] :value #fhir/string"174230"})
        (type/string {:extension [internable-extension] :value #fhir/string"174230"}))

      (are [x y] (interned? x y)
        (type/string {:extension [internable-extension]})
        (type/string {:extension [internable-extension]}))))

  (testing "value"
    (are [x] (= "175227" (type/value x))
      #fhir/string"175227"
      #fhir/string{:value "175227"}))

  (testing "to-json"
    (is (= "\"105406\"" (gen-json-string #fhir/string"105406"))))

  (testing "to-xml"
    (is (= (sexp-value "121344") (type/to-xml #fhir/string"121344"))))

  (testing "equals"
    (is (.equals #fhir/string"foo" #fhir/string{:value "foo"})))

  (testing "hash-into"
    (are [s hex] (= hex (murmur3 s))
      #fhir/string"" "126916b"
      #fhir/string"foo" "ba7851a6"
      #fhir/string{:value "foo"} "ba7851a6"
      #fhir/string{:id "foo"} "88650112"
      #fhir/string{:id "foo" :value "foo"} "28b14e8f"
      #fhir/string{:extension [#fhir/Extension{:url "foo"}]} "b2f98d95"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      "151736"
      nil))

  (testing "toString"
    (satisfies-prop 10
      (prop/for-all [value fg/string-value]
        (= value (str (type/string value)))))))

(deftest decimal-test
  (testing "decimal?"
    (are [x] (type/decimal? x)
      #fhir/decimal 1M
      #fhir/decimal{:id "foo"}))

  (testing "type"
    (are [x] (= :fhir/decimal (type/type x))
      #fhir/decimal 1M
      #fhir/decimal{:id "foo"}))

  (testing "Decimal"
    (is (= #fhir/decimal{:value 1M} #fhir/decimal 1M)))

  (testing "interned"
    (is (not-interned? 165746M 165746M)))

  (testing "value"
    (are [x] (= 1M (type/value x))
      #fhir/decimal 1M
      #fhir/decimal{:id "foo" :value 1M}))

  (testing "to-json"
    (are [decimal json] (= json (gen-json-string decimal))
      1M "1"
      1.1M "1.1"))

  (testing "to-xml"
    (is (= (sexp-value "1.1") (type/to-xml 1.1M))))

  (testing "hash-into"
    (are [d hex] (= hex (murmur3 d))
      #fhir/decimal 0M "7e564b82"
      #fhir/decimal 1M "f2f4ddc7"
      #fhir/decimal{:value 1M} "f2f4ddc7"
      #fhir/decimal{:id "foo"} "86b1bd0c"
      #fhir/decimal{:id "foo" :value 0M} "4e9f9211"
      #fhir/decimal{:extension [#fhir/Extension{:url "foo"}]} "df35c8c9"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/decimal 0M
      nil)))

(deftest uri-test
  (testing "uri?"
    (are [x] (type/uri? x)
      #fhir/uri""
      #fhir/uri{:id "foo"}))

  (testing "type"
    (are [x] (= :fhir/uri (type/type x))
      #fhir/uri""
      #fhir/uri{:id "foo"}))

  (testing "uri"
    (is (= #fhir/uri{:value "181424"} #fhir/uri"181424")))

  (testing "interned"
    (is (interned? #fhir/uri"165823" #fhir/uri"165823"))

    (testing "with extension"
      (are [x y] (interned? x y)
        (type/uri {:extension [internable-extension]})
        (type/uri {:extension [internable-extension]})

        (type/uri {:extension [internable-extension] :value "185838"})
        (type/uri {:extension [internable-extension] :value "185838"}))

      (are [x y] (not-interned? x y)
        (type/uri {:extension [not-internable-extension]})
        (type/uri {:extension [not-internable-extension]})

        (type/uri {:extension [not-internable-extension] :value "185838"})
        (type/uri {:extension [not-internable-extension] :value "185838"}))))

  (testing "value"
    (are [x] (= "105614" (type/value x))
      #fhir/uri"105614"
      #fhir/uri{:id "foo" :value "105614"}))

  (testing "to-json"
    (is (= "\"105846\"" (gen-json-string #fhir/uri"105846"))))

  (testing "to-xml"
    (is (= (sexp-value "105846") (type/to-xml #fhir/uri"105846"))))

  (testing "equals"
    (is (.equals #fhir/uri"142334" #fhir/uri"142334"))
    (is (not (.equals #fhir/uri"142334" #fhir/uri"215930")))
    (is (not (.equals #fhir/uri"142334" "142334"))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/uri"" "51a99a01"
      #fhir/uri"foo" "dc60f982"
      #fhir/uri{:value "foo"} "dc60f982"
      #fhir/uri{:id "foo"} "7c797680"
      #fhir/uri{:id "foo" :value "foo"} "52e1c640"
      #fhir/uri{:extension [#fhir/Extension{:url "foo"}]} "435d07d9"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/uri"151758"
      nil))

  (testing "print"
    (are [uri s] (= (pr-str uri) s)
      #fhir/uri"142600" "#fhir/uri\"142600\""
      #fhir/uri{:id "0"} "#fhir/uri{:id \"0\"}"))

  (testing "toString"
    (satisfies-prop 10
      (prop/for-all [value fg/uri-value]
        (= value (str (type/uri value))))))

  (testing "SerializableString"
    (testing "getValue"
      (satisfies-prop 10
        (prop/for-all [value fg/uri-value]
          (= value (.getValue ^SerializableString (type/uri value))))))

    (testing "appendQuotedUTF8"
      (satisfies-prop 100
        (prop/for-all [value fg/uri-value]
          (let [expected-buffer (.quoteAsUTF8 (JsonStringEncoder/getInstance) value)
                buffer (byte-array (count expected-buffer))]
            (.appendQuotedUTF8 ^SerializableString (type/uri value) buffer 0)
            (= (bb/wrap expected-buffer) (bb/wrap buffer))))))

    (testing "asUnquotedUTF8"
      (satisfies-prop 100
        (prop/for-all [value fg/uri-value]
          (= (bb/wrap (.encodeAsUTF8 (JsonStringEncoder/getInstance) ^String value))
             (bb/wrap (.asUnquotedUTF8 ^SerializableString (type/uri value)))))))

    (testing "asQuotedUTF8"
      (satisfies-prop 100
        (prop/for-all [value fg/uri-value]
          (= (bb/wrap (.quoteAsUTF8 (JsonStringEncoder/getInstance) value))
             (bb/wrap (.asQuotedUTF8 ^SerializableString (type/uri value)))))))))

(deftest url-test
  (testing "url?"
    (are [x] (type/url? x)
      #fhir/url""
      #fhir/url{}))

  (testing "type"
    (are [x] (= :fhir/url (type/type x))
      #fhir/url""
      #fhir/url{}))

  (testing "interned"
    (is (not-interned? #fhir/url"165852" #fhir/url"165852"))

    (testing "with extension"
      (are [x y] (not-interned? x y)
        (type/url {:extension [internable-extension] :value "185838"})
        (type/url {:extension [internable-extension] :value "185838"}))

      (are [x y] (interned? x y)
        (type/url {:extension [internable-extension]})
        (type/url {:extension [internable-extension]}))))

  (testing "value"
    (are [x] (= "105614" (type/value x))
      #fhir/url"105614"
      #fhir/url{:id "foo" :value "105614"}))

  (testing "to-json"
    (is (= "\"105846\"" (gen-json-string #fhir/url"105846"))))

  (testing "to-xml"
    (is (= (sexp-value "105846") (type/to-xml #fhir/url"105846"))))

  (testing "equals"
    (is (let [url #fhir/url"142334"] (.equals url url)))
    (is (.equals #fhir/url"142334" #fhir/url"142334"))
    (is (not (.equals #fhir/url"142334" #fhir/url"220025")))
    (is (not (.equals #fhir/url"142334" "142334"))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/url"" "39b10d82"
      #fhir/url"foo" "7acc4e54"
      #fhir/url{:id "foo"} "78133d84"
      #fhir/url{:id "foo" :value "foo"} "43940bd2"
      #fhir/url{:extension [#fhir/Extension{:url "foo"}]} "95f50bf4"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/url"151809"
      nil))

  (testing "print"
    (are [x s] (= (pr-str x) s)
      #fhir/url"142600"
      "#fhir/url\"142600\""

      #fhir/url{:id "id-191655"}
      "#fhir/url{:id \"id-191655\"}"

      #fhir/url{:id "id-191655" :value "191802"}
      "#fhir/url{:id \"id-191655\", :value \"191802\"}"

      #fhir/url{:extension [#fhir/Extension{:url "url-191551"}]}
      "#fhir/url{:extension [#fhir/Extension{:url \"url-191551\"}]}"))

  (testing "toString"
    (satisfies-prop 10
      (prop/for-all [value fg/url-value]
        (= value (str (type/url value)))))))

(deftest canonical-test
  (testing "canonical?"
    (are [x] (type/canonical? x)
      #fhir/canonical""
      #fhir/canonical{:id "foo"}))

  (testing "type"
    (are [x] (= :fhir/canonical (type/type x))
      #fhir/canonical""
      #fhir/canonical{:id "foo"}))

  (testing "canonical"
    (is (= #fhir/canonical{:value "182040"} #fhir/canonical"182040")))

  (testing "interned"
    (is (interned? #fhir/canonical"165936" #fhir/canonical"165936"))

    (testing "with extension"
      (are [x y] (interned? x y)
        (type/canonical {:extension [internable-extension]})
        (type/canonical {:extension [internable-extension]})

        (type/canonical {:extension [internable-extension] :value "185838"})
        (type/canonical {:extension [internable-extension] :value "185838"}))

      (are [x y] (not-interned? x y)
        (type/canonical {:extension [not-internable-extension]})
        (type/canonical {:extension [not-internable-extension]})

        (type/canonical {:extension [not-internable-extension] :value "185838"})
        (type/canonical {:extension [not-internable-extension] :value "185838"}))))

  (testing "value"
    (are [x] (= "105614" (type/value x))
      #fhir/canonical"105614"
      #fhir/canonical{:id "foo" :value "105614"}))

  (testing "to-json"
    (is (= "\"105846\"" (gen-json-string #fhir/canonical"105846"))))

  (testing "to-xml"
    (is (= (sexp-value "105846") (type/to-xml #fhir/canonical"105846"))))

  (testing "equals"
    (is (.equals #fhir/canonical"142334" #fhir/canonical"142334"))
    (is (not (.equals #fhir/canonical"142334" #fhir/canonical"220056")))
    (is (not (.equals #fhir/canonical"142334" "142334"))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/canonical"" "53c915fd"
      #fhir/canonical"foo" "e42e9c7e"
      #fhir/canonical{:id "foo"} "b039419d"
      #fhir/canonical{:id "foo" :value "foo"} "83587524"
      #fhir/canonical{:extension [#fhir/Extension{:url "foo"}]} "3f1c8be1"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/canonical"151819"
      nil))

  (testing "print"
    (are [c s] (= s (pr-str c))
      #fhir/canonical"142600"
      "#fhir/canonical\"142600\""

      #fhir/canonical{:id "211202"}
      "#fhir/canonical{:id \"211202\"}"

      #fhir/canonical{:value "213644"}
      "#fhir/canonical\"213644\""))

  (testing "toString"
    (satisfies-prop 10
      (prop/for-all [value fg/canonical-value]
        (= value (str (type/canonical value))))))

  (testing "SerializableString"
    (testing "getValue"
      (satisfies-prop 10
        (prop/for-all [value fg/canonical-value]
          (= value (.getValue ^SerializableString (type/canonical value))))))

    (testing "appendQuotedUTF8"
      (satisfies-prop 100
        (prop/for-all [value fg/canonical-value]
          (let [expected-buffer (.quoteAsUTF8 (JsonStringEncoder/getInstance) value)
                buffer (byte-array (count expected-buffer))]
            (.appendQuotedUTF8 ^SerializableString (type/canonical value) buffer 0)
            (= (bb/wrap expected-buffer) (bb/wrap buffer))))))

    (testing "asUnquotedUTF8"
      (satisfies-prop 100
        (prop/for-all [value fg/canonical-value]
          (= (bb/wrap (.encodeAsUTF8 (JsonStringEncoder/getInstance) ^String value))
             (bb/wrap (.asUnquotedUTF8 ^SerializableString (type/canonical value)))))))

    (testing "asQuotedUTF8"
      (satisfies-prop 100
        (prop/for-all [value fg/canonical-value]
          (= (bb/wrap (.quoteAsUTF8 (JsonStringEncoder/getInstance) value))
             (bb/wrap (.asQuotedUTF8 ^SerializableString (type/canonical value)))))))))

(deftest base64Binary-test
  (testing "base64Binary?"
    (are [x] (type/base64Binary x)
      #fhir/base64Binary""
      #fhir/base64Binary{:id "foo"}))

  (testing "type"
    (are [x] (= :fhir/base64Binary (type/type x))
      #fhir/base64Binary""
      #fhir/base64Binary{:id "foo"}))

  (testing "base64Binary"
    (is (= #fhir/base64Binary{:value "MTA1NjE0Cg=="} #fhir/base64Binary"MTA1NjE0Cg==")))

  (testing "interned"
    (is (not-interned? #fhir/base64Binary"MTA1NjE0Cg==" #fhir/base64Binary"MTA1NjE0Cg=="))

    (testing "with extension"
      (are [x y] (not-interned? x y)
        (type/base64Binary {:extension [internable-extension] :value "MTA1NjE0Cg=="})
        (type/base64Binary {:extension [internable-extension] :value "MTA1NjE0Cg=="}))

      (are [x y] (interned? x y)
        (type/base64Binary {:extension [internable-extension]})
        (type/base64Binary {:extension [internable-extension]}))))

  (testing "value"
    (are [x] (= "MTA1NjE0Cg==" (type/value x))
      #fhir/base64Binary"MTA1NjE0Cg=="
      #fhir/base64Binary{:id "foo" :value "MTA1NjE0Cg=="}))

  (testing "to-json"
    (is (= "\"MTA1NjE0Cg==\"" (gen-json-string #fhir/base64Binary"MTA1NjE0Cg=="))))

  (testing "to-xml"
    (is (= (sexp-value "MTA1NjE0Cg==") (type/to-xml #fhir/base64Binary"MTA1NjE0Cg=="))))

  (testing "equals"
    (is (let [base64Binary #fhir/base64Binary"MTA1NjE0Cg=="]
          (.equals base64Binary base64Binary)))
    (is (.equals #fhir/base64Binary"MTA1NjE0Cg==" #fhir/base64Binary"MTA1NjE0Cg=="))
    (is (not (.equals #fhir/base64Binary"MTA1NjE0Cg==" #fhir/base64Binary"YQo=")))
    (is (not (.equals #fhir/base64Binary"MTA1NjE0Cg==" "MTA1NjE0Cg=="))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/base64Binary"" "339ff20a"
      #fhir/base64Binary"YQo=" "ed565602"
      #fhir/base64Binary"MTA1NjE0Cg===" "24568b10"
      #fhir/base64Binary{:id "foo"} "331c84dc"
      #fhir/base64Binary{:extension [#fhir/Extension{:url "foo"}]} "4d9fc231"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/base64Binary"YQo="
      nil))

  (testing "print"
    (is (= "#fhir/base64Binary\"YQo=\"" (pr-str #fhir/base64Binary"YQo="))))

  (testing "toString"
    (satisfies-prop 10
      (prop/for-all [value fg/base64Binary-value]
        (= value (str (type/base64Binary value)))))))

(deftest instant-test
  (testing "instant?"
    (are [x] (type/instant? x)
      #fhir/instant"1970-01-02T00:00:00Z"
      #fhir/instant"1970-01-02T00:00:00+01:00"
      #fhir/instant{:id "foo"}
      #fhir/instant{:value "1970-01-02T00:00:00Z"}
      #fhir/instant{:value "1970-01-02T00:00:00+01:00"}))

  (testing "type"
    (are [x] (= :fhir/instant (type/type x))
      #fhir/instant"1970-01-02T00:00:00Z"
      #fhir/instant"1970-01-02T00:00:00+01:00"
      #fhir/instant{:id "foo"}
      #fhir/instant{:value "1970-01-02T00:00:00Z"}
      #fhir/instant{:value "1970-01-02T00:00:00+01:00"}))

  (testing "with extension"
    (testing "without value"
      (is (nil? (type/value #fhir/instant{:extension [#fhir/Extension{:url "url-130945"}]})))))

  (testing "instant"
    (is (= #fhir/instant{:value "1970-01-02T00:00:00Z"}
           #fhir/instant"1970-01-02T00:00:00Z")))

  (testing "interned"
    (is (not-interned? #fhir/instant"2020-01-01T00:00:00+02:00"
                       #fhir/instant"2020-01-01T00:00:00+02:00"))

    (is (not-interned? #fhir/instant"1970-01-02T00:00:00Z"
                       #fhir/instant"1970-01-02T00:00:00Z"))

    (testing "with extension"
      (are [x y] (not-interned? x y)
        (type/instant {:extension [internable-extension]
                       :value "1970-01-02T00:00:00Z"})
        (type/instant {:extension [internable-extension]
                       :value "1970-01-02T00:00:00Z"})

        (type/instant {:extension [not-internable-extension]})
        (type/instant {:extension [not-internable-extension]}))

      (are [x y] (interned? x y)
        (type/instant {:extension [internable-extension]})
        (type/instant {:extension [internable-extension]}))))

  (testing "value is a System.DateTime which is a OffsetDateTime"
    (are [x] (= (OffsetDateTime/of 2020 1 1 0 0 0 0 (ZoneOffset/ofHours 2)) (type/value x))
      #fhir/instant"2020-01-01T00:00:00+02:00"
      #fhir/instant{:id "foo" :value "2020-01-01T00:00:00+02:00"})

    (are [x] (= (OffsetDateTime/of 1970 1 1 0 0 0 0 ZoneOffset/UTC) (type/value x))
      #fhir/instant"1970-01-01T00:00:00Z"
      #fhir/instant{:id "foo" :value "1970-01-01T00:00:00Z"}))

  (testing "to-json"
    (are [instant json] (= json (gen-json-string instant))
      #fhir/instant"2020-01-01T00:00:00+02:00" "\"2020-01-01T00:00:00+02:00\""
      Instant/EPOCH "\"1970-01-01T00:00:00Z\""))

  (testing "to-xml"
    (is (= (sexp-value "2020-01-01T00:00:00+02:00")
           (type/to-xml #fhir/instant"2020-01-01T00:00:00+02:00")))
    (is (= (sexp-value "1970-01-01T00:00:00Z")
           (type/to-xml Instant/EPOCH))))

  (testing "equals"
    (is (let [instant #fhir/instant"2020-01-01T00:00:00+02:00"]
          (.equals instant instant)))
    (is (.equals #fhir/instant"2020-01-01T00:00:00+02:00"
                 #fhir/instant"2020-01-01T00:00:00+02:00"))
    (is (not (.equals #fhir/instant"2020-01-01T00:00:00+01:00"
                      #fhir/instant"2020-01-01T00:00:00+02:00")))
    (is (.equals Instant/EPOCH #fhir/instant"1970-01-01T00:00:00Z"))
    (is (.equals Instant/EPOCH #fhir/instant"1970-01-01T00:00:00+00:00")))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/instant"2020-01-01T00:00:00+00:00" "d81f6bc2"
      #fhir/instant"2020-01-01T00:00:00+01:00" "4225df0d"
      #fhir/instant"2020-01-01T00:00:00Z" "d81f6bc2"
      #fhir/instant"1970-01-01T00:00:00Z" "93344244"
      #fhir/instant{:value "1970-01-01T00:00:00Z"} "93344244"
      Instant/EPOCH "93344244"
      #fhir/instant{:id "foo"} "b4705bd6"
      #fhir/instant{:id "foo" :value "1970-01-01T00:00:00Z"} "e5a31add"
      #fhir/instant{:extension [#fhir/Extension{:url "foo"}]} "8a7f7ddc"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      Instant/EPOCH
      nil))

  (testing "print"
    (are [i s] (= s (pr-str i))
      #fhir/instant"2020-01-01T00:00:00Z"
      "#java/instant\"2020-01-01T00:00:00Z\""

      #fhir/instant"2020-01-01T00:00:00+01:00"
      "#fhir/instant\"2020-01-01T00:00:00+01:00\""

      #fhir/instant{:id "211213"}
      "#fhir/instant{:id \"211213\"}"

      #fhir/instant{:value "2020-01-01T00:00:00Z"}
      "#java/instant\"2020-01-01T00:00:00Z\""))

  (testing "toString"
    (is (= "2020-01-01T00:00:00Z" (str #fhir/instant"2020-01-01T00:00:00Z")))))

(deftest date-test
  (testing "with year precision"
    (testing "date?"
      (are [x] (type/date? x)
        #fhir/date"0001"
        #fhir/date"9999"
        #fhir/date"2022"
        #fhir/date{:id "foo"}))

    (testing "type"
      (are [x] (= :fhir/date (type/type x))
        #fhir/date"2022"
        #fhir/date{:id "foo"}))

    (testing "date"
      (is (= #fhir/date{:value "2022"} #fhir/date"2022")))

    (testing "interned"
      (is (not-interned? #fhir/date"2020" #fhir/date"2020"))

      (testing "with extension"
        (are [x y] (not-interned? x y)
          (type/date {:extension [internable-extension] :value "2022"})
          (type/date {:extension [internable-extension] :value "2022"})

          (type/date {:id "id-164735" :extension [internable-extension]})
          (type/date {:id "id-164735" :extension [internable-extension]})

          (type/date {:extension [not-internable-extension]})
          (type/date {:extension [not-internable-extension]}))

        (are [x y] (interned? x y)
          (type/date {:extension [internable-extension]})
          (type/date {:extension [internable-extension]}))))

    (testing "value"
      (are [x] (= #system/date"2020" (type/value x))
        #fhir/date"2020"
        #fhir/date{:id "foo" :value "2020"}))

    (testing "to-json"
      (are [date json] (= json (gen-json-string date))
        #fhir/date"0001" "\"0001\""
        #fhir/date"9999" "\"9999\""
        #fhir/date"2020" "\"2020\""))

    (testing "to-xml"
      (are [date xml] (= (sexp-value xml) (type/to-xml date))
        #fhir/date"0001" "0001"
        #fhir/date"9999" "9999"
        #fhir/date"2020" "2020"))

    (testing "equals"
      (is (.equals #fhir/date"2020" #fhir/date"2020"))
      (is (not (.equals #fhir/date"2020" #fhir/date"2021")))
      (is (not (.equals #fhir/date"2020" "2020"))))

    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/date"2020" "c92be432"
        #fhir/date{:value "2020"} "c92be432"
        #fhir/date{:id "foo"} "20832903"
        #fhir/date{:id "foo" :value "2020"} "e983029c"
        #fhir/date{:extension [#fhir/Extension{:url "foo"}]} "707470a9"))

    (testing "references"
      (are [x refs] (= refs (type/references x))
        #fhir/date"2020"
        nil)))

  (testing "with year-month precision"
    (testing "date?"
      (are [x] (type/date? x)
        #fhir/date"0001-01"
        #fhir/date"9999-12"
        #fhir/date"2022-05"
        #fhir/date{:id "foo"}))

    (testing "type"
      (are [x] (= :fhir/date (type/type x))
        #fhir/date"2022-05"
        #fhir/date{:id "foo"}))

    (testing "date"
      (is (= #fhir/date{:value "2022-05"} #fhir/date"2022-05")))

    (testing "interned"
      (is (not-interned? #fhir/date"2020-01" #fhir/date"2020-01")))

    (testing "value"
      (are [x] (= #system/date"2020-01" (type/value x))
        #fhir/date"2020-01"
        #fhir/date{:id "foo" :value "2020-01"}))

    (testing "to-json"
      (are [date json] (= json (gen-json-string date))
        #fhir/date"0001-01" "\"0001-01\""
        #fhir/date"9999-12" "\"9999-12\""
        #fhir/date"2020-01" "\"2020-01\""))

    (testing "to-xml"
      (are [date xml] (= (sexp-value xml) (type/to-xml date))
        #fhir/date"0001-01" "0001-01"
        #fhir/date"9999-12" "9999-12"
        #fhir/date"2020-01" "2020-01"))

    (testing "equals"
      (is (.equals #fhir/date"2020-01" #fhir/date"2020-01"))
      (is (not (.equals #fhir/date"2020-01" #fhir/date"2020-02")))
      (is (not (.equals #fhir/date"2020-01" "2020-01"))))

    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/date"2020-01" "fbcdf97f"
        #fhir/date{:value "2020-01"} "fbcdf97f"
        #fhir/date{:id "foo"} "20832903"
        #fhir/date{:id "foo" :value "2020-01"} "4e6aead7"
        #fhir/date{:extension [#fhir/Extension{:url "foo"}]} "707470a9"))

    (testing "references"
      (are [x refs] (= refs (type/references x))
        #fhir/date"2020-01"
        nil)))

  (testing "with date precision"
    (testing "date?"
      (are [x] (type/date? x)
        #fhir/date"2022-05-23"
        #fhir/date{:id "foo"}))

    (testing "type"
      (are [x] (= :fhir/date (type/type x))
        #fhir/date"2022-05-23"
        #fhir/date{:id "foo"}))

    (testing "date"
      (is (= #fhir/date{:value "2022-05-23"} #fhir/date"2022-05-23")))

    (testing "interned"
      (is (not-interned? #fhir/date"2020-01-01" #fhir/date"2020-01-01")))

    (testing "value"
      (are [x] (= #system/date"2020-01-02" (type/value x))
        #fhir/date"2020-01-02"
        #fhir/date{:id "foo" :value "2020-01-02"}))

    (testing "to-json"
      (are [date json] (= json (gen-json-string date))
        #fhir/date"0001-01-01" "\"0001-01-01\""
        #fhir/date"9999-12-31" "\"9999-12-31\"")

      (satisfies-prop 100
        (prop/for-all [date (s/gen :system/date)]
          (= (format "\"%s\"" date) (gen-json-string date)))))

    (testing "to-xml"
      (are [date xml] (= (sexp-value xml) (type/to-xml date))
        #fhir/date"0001-01-01" "0001-01-01"
        #fhir/date"9999-12-31" "9999-12-31")

      (satisfies-prop 100
        (prop/for-all [date (s/gen :system/date)]
          (= (sexp-value (str date)) (type/to-xml date)))))

    (testing "equals"
      (satisfies-prop 100
        (prop/for-all [date (s/gen :system/date)]
          (.equals ^Object date date)))
      (is (not (.equals #fhir/date"2020-01-01" #fhir/date"2020-01-02")))
      (is (not (.equals #fhir/date"2020-01-01" "2020-01-01"))))

    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/date"2020-01-01" "cd20e081"
        #fhir/date{:value "2020-01-01"} "cd20e081"
        #fhir/date{:id "foo"} "20832903"
        #fhir/date{:id "foo" :value "2020-01-01"} "ef736a41"
        #fhir/date{:extension [#fhir/Extension{:url "foo"}]} "707470a9"))

    (testing "references"
      (is (nil? (type/references #fhir/date"2020-01-01"))))))

(deftest dateTime-test
  (testing "with year precision"
    (testing "dateTime?"
      (are [x] (type/dateTime? x)
        #fhir/dateTime"0001"
        #fhir/dateTime"9999"
        #fhir/dateTime"2022"
        #fhir/dateTime"2022-01"
        #fhir/dateTime"2022-01-01"
        #fhir/dateTime{:id "foo"}
        #fhir/dateTime{:extension [#fhir/Extension{:url "foo"}]}))

    (testing "type"
      (are [x] (= :fhir/dateTime (type/type x))
        #fhir/dateTime"0001"
        #fhir/dateTime"9999"
        #fhir/dateTime"2022"
        #fhir/dateTime{:id "foo"}))

    (testing "dateTime"
      (is (= #fhir/dateTime{:value "2022"} #fhir/dateTime"2022")))

    (testing "interned"
      (is (not-interned? #fhir/dateTime"2020" #fhir/dateTime"2020")))

    (testing "value"
      (are [x] (= #system/date-time"2020" (type/value x))
        #fhir/dateTime"2020"
        #fhir/dateTime{:id "foo" :value "2020"}))

    (testing "to-json"
      (are [date-time json] (= json (gen-json-string date-time))
        #fhir/dateTime"0001" "\"0001\""
        #fhir/dateTime"9999" "\"9999\""
        #fhir/dateTime"2020" "\"2020\""))

    (testing "to-xml"
      (are [date-time xml] (= (sexp-value xml) (type/to-xml date-time))
        #fhir/dateTime"0001" "0001"
        #fhir/dateTime"9999" "9999"
        #fhir/dateTime"2020" "2020"))

    (testing "equals"
      (is (.equals #fhir/dateTime"2020" #fhir/dateTime"2020"))
      (is (not (.equals #fhir/dateTime"2020" #fhir/dateTime"2021")))
      (is (not (.equals #fhir/dateTime"2020" "2020"))))

    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/dateTime"2020" "41e906ff"
        #fhir/dateTime{:value "2020"} "41e906ff"
        #fhir/dateTime{:id "foo"} "fde903da"
        #fhir/dateTime{:id "foo" :value "2020"} "c7361227"
        #fhir/dateTime{:extension [#fhir/Extension{:url "foo"}]} "15062059"))

    (testing "references"
      (are [x refs] (= refs (type/references x))
        #fhir/dateTime"2020"
        nil)))

  (testing "with year-month precision"
    (testing "dateTime?"
      (are [x] (type/dateTime? x)
        #fhir/dateTime"2022-05"
        #fhir/dateTime{:id "foo"}))

    (testing "type"
      (are [x] (= :fhir/dateTime (type/type x))
        #fhir/dateTime"2022-05"
        #fhir/dateTime{:id "foo"}))

    (testing "dateTime"
      (is (= #fhir/dateTime{:value "2022-05"} #fhir/dateTime"2022-05")))

    (testing "interned"
      (is (not-interned? #fhir/dateTime"2022-05" #fhir/dateTime"2022-05")))

    (testing "value"
      (are [x] (= #system/date-time"2020-01" (type/value x))
        #fhir/dateTime"2020-01"
        #fhir/dateTime{:id "foo" :value "2020-01"}))

    (testing "to-json"
      (are [date-time json] (= json (gen-json-string date-time))
        #fhir/dateTime"0001-01" "\"0001-01\""
        #fhir/dateTime"9999-12" "\"9999-12\""
        #fhir/dateTime"2020-01" "\"2020-01\""))

    (testing "to-xml"
      (are [date-time xml] (= (sexp-value xml) (type/to-xml date-time))
        #fhir/dateTime"0001-01" "0001-01"
        #fhir/dateTime"9999-12" "9999-12"
        #fhir/dateTime"2020-01" "2020-01"))

    (testing "equals"
      (is (.equals #fhir/dateTime"2020-01" #fhir/dateTime"2020-01"))
      (is (not (.equals #fhir/dateTime"2020-01" #fhir/dateTime"2020-02")))
      (is (not (.equals #fhir/dateTime"2020-01" "2020-01"))))

    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/dateTime"2020-01" "9d6c5bd3"
        #fhir/dateTime{:value "2020-01"} "9d6c5bd3"
        #fhir/dateTime{:id "foo"} "fde903da"
        #fhir/dateTime{:id "foo" :value "2020-01"} "aa78aa13"
        #fhir/dateTime{:extension [#fhir/Extension{:url "foo"}]} "15062059"))

    (testing "references"
      (are [x refs] (= refs (type/references x))
        #fhir/dateTime"2020-01"
        nil)))

  (testing "with date precision"
    (testing "dateTime?"
      (are [x] (type/dateTime? x)
        #fhir/dateTime"2022-05-23"
        #fhir/dateTime{:id "foo"}))

    (testing "type"
      (are [x] (= :fhir/dateTime (type/type x))
        #fhir/dateTime"2022-05-23"
        #fhir/dateTime{:id "foo"}))

    (testing "dateTime"
      (is (= #fhir/dateTime{:value "2022-05-23"} #fhir/dateTime"2022-05-23")))

    (testing "interned"
      (is (not-interned? #fhir/dateTime"2022-05-23" #fhir/dateTime"2022-05-23")))

    (testing "value"
      (are [x] (= #system/date-time"2020-01-01" (type/value x))
        #fhir/dateTime"2020-01-01"
        #fhir/dateTime{:id "foo" :value "2020-01-01"}))

    (testing "to-json"
      (are [date-time json] (= json (gen-json-string date-time))
        #fhir/dateTime"0001-01-01" "\"0001-01-01\""
        #fhir/dateTime"9999-12-31" "\"9999-12-31\""
        #fhir/dateTime"2020-01-01" "\"2020-01-01\""))

    (testing "to-xml"
      (are [date-time xml] (= (sexp-value xml) (type/to-xml date-time))
        #fhir/dateTime"0001-01-01" "0001-01-01"
        #fhir/dateTime"9999-12-31" "9999-12-31"
        #fhir/dateTime"2020-01-01" "2020-01-01"))

    (testing "equals"
      (is (.equals #fhir/dateTime"2020-01-01" #fhir/dateTime"2020-01-01")))

    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/dateTime"2020-01-01" "39fe9bdb"
        #fhir/dateTime{:value "2020-01-01"} "39fe9bdb"
        #fhir/dateTime{:id "foo"} "fde903da"
        #fhir/dateTime{:id "foo" :value "2020-01-01"} "7e36d416"
        #fhir/dateTime{:extension [#fhir/Extension{:url "foo"}]} "15062059"))

    (testing "references"
      (are [x refs] (= refs (type/references x))
        #fhir/dateTime"2020-01-01"
        nil)))

  (testing "without timezone"
    (testing "dateTime?"
      (are [x] (type/dateTime? x)
        #fhir/dateTime"0001-01-01T00:00:00"
        #fhir/dateTime"9999-12-31T12:59:59"
        #fhir/dateTime"2020-01-01T00:00:00"
        #fhir/dateTime{:id "foo"}))

    (testing "type"
      (are [x] (= :fhir/dateTime (type/type x))
        #fhir/dateTime"2020-01-01T00:00:00"
        #fhir/dateTime{:id "foo"}))

    (testing "dateTime"
      (is (= #fhir/dateTime{:value "2020-01-01T00:00:00"} #fhir/dateTime"2020-01-01T00:00:00")))

    (testing "interned"
      (is (not-interned? #fhir/dateTime"2020-01-01T00:00:00"
                         #fhir/dateTime"2020-01-01T00:00:00")))

    (testing "to-json"
      (is (= "\"2020-01-01T00:00:00\""
             (gen-json-string #fhir/dateTime"2020-01-01T00:00:00"))))

    (testing "to-xml"
      (is (= (sexp-value "2020-01-01T00:00:00") (type/to-xml #fhir/dateTime"2020-01-01T00:00:00"))))

    (testing "equals"
      (is (.equals #fhir/dateTime"2020-01-01T00:00:00"
                   #fhir/dateTime"2020-01-01T00:00:00")))

    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/dateTime"2020-01-01T00:00:00" "da537591"
        #fhir/dateTime{:value "2020-01-01T00:00:00"} "da537591"
        #fhir/dateTime{:id "foo" :value "2020-01-01T00:00:00"} "f33b7808"))

    (testing "references"
      (are [x refs] (= refs (type/references x))
        #fhir/dateTime"2020-01-01T00:00:00"
        nil)))

  (testing "without timezone but millis"
    (testing "dateTime?"
      (are [x] (type/dateTime? x)
        #fhir/dateTime"2020-01-01T00:00:00.000"
        #fhir/dateTime{:id "foo"}))

    (testing "type"
      (are [x] (= :fhir/dateTime (type/type x))
        #fhir/dateTime"2020-01-01T00:00:00.000"
        #fhir/dateTime{:id "foo"}))

    (testing "dateTime"
      (is (= #fhir/dateTime{:value "2020-01-01T00:00:00.000"} #fhir/dateTime"2020-01-01T00:00:00.000")))

    (testing "interned"
      (is (not-interned? #fhir/dateTime"2020-01-01T00:00:00.000"
                         #fhir/dateTime"2020-01-01T00:00:00.000")))

    (testing "to-json"
      (is (= "\"2020-01-01T00:00:00.001\""
             (gen-json-string #fhir/dateTime"2020-01-01T00:00:00.001"))))

    (testing "to-xml"
      (is (= (sexp-value "2020-01-01T00:00:00.001") (type/to-xml #fhir/dateTime"2020-01-01T00:00:00.001"))))

    (testing "equals"
      (is (.equals #fhir/dateTime"2020-01-01T00:00:00.000"
                   #fhir/dateTime"2020-01-01T00:00:00.000")))

    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/dateTime"2020-01-01T00:00:00.000" "da537591"
        #fhir/dateTime{:value "2020-01-01T00:00:00.000"} "da537591"
        #fhir/dateTime{:id "foo" :value "2020-01-01T00:00:00.000"} "f33b7808"))

    (testing "references"
      (are [x refs] (= refs (type/references x))
        #fhir/dateTime"2020-01-01T00:00:00.000"
        nil)))

  (testing "with zulu timezone"
    (testing "dateTime?"
      (are [x] (type/dateTime? x)
        #fhir/dateTime"2020-01-01T00:00:00Z"
        #fhir/dateTime{:id "foo"}))

    (testing "type"
      (are [x] (= :fhir/dateTime (type/type x))
        #fhir/dateTime"2020-01-01T00:00:00Z"
        #fhir/dateTime{:id "foo"}))

    (testing "dateTime"
      (is (= #fhir/dateTime{:value "2020-01-01T00:00:00Z"} #fhir/dateTime"2020-01-01T00:00:00Z")))

    (testing "interned"
      (is (not-interned? #fhir/dateTime"2020-01-01T00:00:00Z"
                         #fhir/dateTime"2020-01-01T00:00:00Z")))

    (testing "to-json"
      (is (= "\"2020-01-01T00:00:00Z\""
             (gen-json-string #fhir/dateTime"2020-01-01T00:00:00Z"))))

    (testing "to-xml"
      (is (= (sexp-value "2020-01-01T00:00:00Z") (type/to-xml #fhir/dateTime"2020-01-01T00:00:00Z"))))

    (testing "equals"
      (is (.equals #fhir/dateTime"2020-01-01T00:00:00Z"
                   #fhir/dateTime"2020-01-01T00:00:00Z")))

    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/dateTime"2020-01-01T00:00:00Z" "d541a45"
        #fhir/dateTime{:value "2020-01-01T00:00:00Z"} "d541a45"
        #fhir/dateTime{:id "foo" :value "2020-01-01T00:00:00Z"} "14a5cd29"))

    (testing "references"
      (are [x refs] (= refs (type/references x))
        #fhir/dateTime"2020-01-01T00:00:00Z"
        nil)))

  (testing "with positive timezone offset"
    (testing "dateTime?"
      (are [x] (type/dateTime? x)
        #fhir/dateTime"2020-01-01T00:00:00+01:00"
        #fhir/dateTime{:id "foo"}))

    (testing "type"
      (are [x] (= :fhir/dateTime (type/type x))
        #fhir/dateTime"2020-01-01T00:00:00+01:00"
        #fhir/dateTime{:id "foo"}))

    (testing "dateTime"
      (is (= #fhir/dateTime{:value "2020-01-01T00:00:00+01:00"} #fhir/dateTime"2020-01-01T00:00:00+01:00")))

    (testing "interned"
      (is (not-interned? #fhir/dateTime"2020-01-01T00:00:00+01:00"
                         #fhir/dateTime"2020-01-01T00:00:00+01:00")))

    (testing "to-json"
      (is (= "\"2020-01-01T00:00:00+01:00\""
             (gen-json-string #fhir/dateTime"2020-01-01T00:00:00+01:00"))))

    (testing "to-xml"
      (is (= (sexp-value "2020-01-01T00:00:00+01:00") (type/to-xml #fhir/dateTime"2020-01-01T00:00:00+01:00"))))

    (testing "equals"
      (is (.equals #fhir/dateTime"2020-01-01T00:00:00+01:00"
                   #fhir/dateTime"2020-01-01T00:00:00+01:00")))

    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/dateTime"2020-01-01T00:00:00+01:00" "9c535d0d"
        #fhir/dateTime{:value "2020-01-01T00:00:00+01:00"} "9c535d0d"
        #fhir/dateTime{:id "foo" :value "2020-01-01T00:00:00+01:00"} "dbf5aa43"))

    (testing "references"
      (are [x refs] (= refs (type/references x))
        #fhir/dateTime"2020-01-01T00:00:00+01:00"
        nil)))

  (testing "with negative timezone offset"
    (testing "dateTime?"
      (are [x] (type/dateTime? x)
        #fhir/dateTime"2020-01-01T00:00:00-01:00"
        #fhir/dateTime{:id "foo"}))

    (testing "type"
      (are [x] (= :fhir/dateTime (type/type x))
        #fhir/dateTime"2020-01-01T00:00:00-01:00"
        #fhir/dateTime{:id "foo"}))

    (testing "dateTime"
      (is (= #fhir/dateTime{:value "2020-01-01T00:00:00-01:00"} #fhir/dateTime"2020-01-01T00:00:00-01:00")))

    (testing "interned"
      (is (not-interned? #fhir/dateTime"2020-01-01T00:00:00-01:00"
                         #fhir/dateTime"2020-01-01T00:00:00-01:00")))

    (testing "to-json"
      (is (= "\"2020-01-01T00:00:00-01:00\""
             (gen-json-string #fhir/dateTime"2020-01-01T00:00:00-01:00"))))

    (testing "to-xml"
      (is (= (sexp-value "2020-01-01T00:00:00-01:00") (type/to-xml #fhir/dateTime"2020-01-01T00:00:00-01:00"))))

    (testing "equals"
      (is (.equals #fhir/dateTime"2020-01-01T00:00:00-01:00"
                   #fhir/dateTime"2020-01-01T00:00:00-01:00")))

    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/dateTime"2020-01-01T00:00:00-01:00" "839fd8a6"
        #fhir/dateTime{:value "2020-01-01T00:00:00-01:00"} "839fd8a6"
        #fhir/dateTime{:id "foo" :value "2020-01-01T00:00:00-01:00"} "c3a7cc0e"))

    (testing "references"
      (are [x refs] (= refs (type/references x))
        #fhir/dateTime"2020-01-01T00:00:00-01:00"
        nil)))

  (testing "with zulu timezone and millis"
    (testing "dateTime?"
      (are [x] (type/dateTime? x)
        #fhir/dateTime"2020-01-01T00:00:00.001Z"
        #fhir/dateTime{:id "foo"}))

    (testing "type"
      (are [x] (= :fhir/dateTime (type/type x))
        #fhir/dateTime"2020-01-01T00:00:00.001Z"
        #fhir/dateTime{:id "foo"}))

    (testing "dateTime"
      (is (= #fhir/dateTime{:value "2020-01-01T00:00:00.001Z"} #fhir/dateTime"2020-01-01T00:00:00.001Z")))

    (testing "interned"
      (is (not-interned? #fhir/dateTime"2020-01-01T00:00:00.001Z"
                         #fhir/dateTime"2020-01-01T00:00:00.001Z")))

    (testing "value is a System.DateTime which is a OffsetDateTime"
      (is (= (OffsetDateTime/of 2020 1 1 0 0 0 1000000 ZoneOffset/UTC)
             (type/value #fhir/dateTime"2020-01-01T00:00:00.001Z"))))

    (testing "to-json"
      (is (= "\"2020-01-01T00:00:00.001Z\""
             (gen-json-string #fhir/dateTime"2020-01-01T00:00:00.001Z"))))

    (testing "to-xml"
      (is (= (sexp-value "2020-01-01T00:00:00.001Z") (type/to-xml #fhir/dateTime"2020-01-01T00:00:00.001Z"))))

    (testing "equals"
      (is (.equals #fhir/dateTime"2020-01-01T00:00:00.001Z"
                   #fhir/dateTime"2020-01-01T00:00:00.001Z")))

    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/dateTime"2020-01-01T00:00:00.001Z" "f46a0b1b"
        #fhir/dateTime{:value "2020-01-01T00:00:00.001Z"} "f46a0b1b"
        #fhir/dateTime{:id "foo" :value "2020-01-01T00:00:00.001Z"} "c6a5ea73"))

    (testing "references"
      (are [x refs] (= refs (type/references x))
        #fhir/dateTime"2020-01-01T00:00:00.001Z"
        nil)))

  (testing "with extensions"
    (let [extended-date-time (type/dateTime {:extension [string-extension] :value "2020"})
          extended-date-time-element (xml-node/element nil {:value "2020"} string-extension)]
      (testing "date-time?"
        (is (type/dateTime? extended-date-time)))

      (testing "type"
        (is (= :fhir/dateTime (type/type extended-date-time))))

      (testing "interned"
        (is (not-interned? (type/dateTime {:extension [string-extension] :value "2020"})
                           (type/dateTime {:extension [string-extension] :value "2020"}))))

      (testing "value"
        (is (= #system/date-time"2020" (type/value extended-date-time))))

      (testing "to-xml"
        (is (= extended-date-time-element (type/to-xml extended-date-time))))

      (testing "equals"
        (is (.equals ^Object (type/dateTime {:extension [string-extension] :value "2020"}) extended-date-time)))

      (testing "hash-into"
        (are [x hex] (= hex (murmur3 x))
          extended-date-time "c10cb51"))

      (testing "references"
        (are [x refs] (= refs (type/references x))
          extended-date-time
          [])))))

(deftest time-test
  (testing "time?"
    (are [x] (type/time? x)
      #fhir/time"15:27:45"
      #fhir/time{:id "foo"}))

  (testing "type"
    (are [x] (= :fhir/time (type/type x))
      #fhir/time"15:27:45"
      #fhir/time{:id "foo"}))

  (testing "time"
    (is (= #fhir/time{:value "15:27:45"} #fhir/time"15:27:45")))

  (testing "interned"
    (is (not-interned? #fhir/time"13:53:21" #fhir/time"13:53:21")))

  (testing "value is a System.Time which is a LocalTime"
    (are [x] (= (LocalTime/of 13 53 21) (type/value x))
      #fhir/time"13:53:21"
      #fhir/time{:id "foo" :value "13:53:21"}))

  (testing "to-json"
    (is (= "\"13:53:21\"" (gen-json-string #fhir/time"13:53:21"))))

  (testing "to-xml"
    (is (= (sexp-value "13:53:21")
           (type/to-xml #fhir/time"13:53:21"))))

  (testing "equals"
    (is (.equals #fhir/time"13:53:21" #fhir/time"13:53:21"))
    (is (not (.equals #fhir/time"13:53:21" #fhir/time"13:53:22")))
    (is (not (.equals #fhir/time"13:53:21" "13:53:21"))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/time"13:53:21" "faa37be9"
      #fhir/time{:value "13:53:21"} "faa37be9"
      #fhir/time{:id "foo"} "1547f086"
      #fhir/time{:id "foo" :value "13:53:21"} "52a81d69"
      #fhir/time{:extension [#fhir/Extension{:url "foo"}]} "9e94d20a"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/time"13:53:21"
      nil)))

(def gender-extension
  #fhir/Extension
   {:url #fhir/uri"http://fhir.de/StructureDefinition/gender-amtlich-de"
    :value
    #fhir/Coding
     {:system #fhir/uri"http://fhir.de/CodeSystem/gender-amtlich-de"
      :code #fhir/code"D"
      :display #fhir/string"divers"}})

(def extended-gender-code
  (type/code {:extension [gender-extension] :value "other"}))

(def extended-gender-code-element
  (xml-node/element nil {:value "other"} gender-extension))

(deftest code-test
  (testing "code?"
    (are [x] (type/code? x)
      #fhir/code""
      #fhir/code{:id "foo"}))

  (testing "type"
    (are [x] (= :fhir/code (type/type x))
      #fhir/code""
      #fhir/code{:id "foo"}))

  (testing "interned"
    (is (interned? #fhir/code"code-123745" #fhir/code"code-123745"))

    (testing "instances with id's are not interned"
      (is (not-interned? #fhir/code{:id "id-171649" :value "code-123745"}
                         #fhir/code{:id "id-171649" :value "code-123745"})))

    (testing "instances with interned extensions are interned"
      (is (interned? #fhir/code{:extension
                                [#fhir/Extension{:url "url-171902"
                                                 :value true}]
                                :value "code-123745"}
                     #fhir/code{:extension
                                [#fhir/Extension{:url "url-171902"
                                                 :value true}]
                                :value "code-123745"}))))

  (testing "value"
    (are [x] (= "code-123745" (type/value x))
      #fhir/code"code-123745"
      #fhir/code{:id "foo" :value "code-123745"}))

  (testing "to-json"
    (are [code json] (= json (gen-json-string code))
      #fhir/code"code-123745" "\"code-123745\""))

  (testing "to-xml"
    (is (= (sexp-value "code-123745")
           (type/to-xml #fhir/code"code-123745")))
    (is (= extended-gender-code-element (type/to-xml extended-gender-code))))

  (testing "equals"
    (is (.equals #fhir/code"175726" #fhir/code"175726"))
    (is (not (.equals #fhir/code"175726" #fhir/code"165817")))
    (is (not (.equals #fhir/code"175726" "175726"))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/code"175726" "9c96c20f"
      #fhir/code{:value "175726"} "9c96c20f"
      #fhir/code{:id "170837"} "70f42552"
      #fhir/code{:id "170837" :value "175726"} "fc8af973"
      #fhir/code{:extension [#fhir/Extension{:url "181911"}]} "838ce6ff"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/code"code-150839"
      nil

      #fhir/code
       {:extension
        [#fhir/Extension
          {:value #fhir/Reference{:reference "Patient/1"}}]}
      [["Patient" "1"]]))

  (testing "print"
    (is (= "#fhir/code\"175718\"" (pr-str #fhir/code"175718"))))

  (testing "toString"
    (satisfies-prop 10
      (prop/for-all [value fg/code-value]
        (= value (str (type/code value))))))

  (testing "SerializableString"
    (testing "getValue"
      (satisfies-prop 10
        (prop/for-all [value fg/code-value]
          (= value (.getValue ^SerializableString (type/code value))))))

    (testing "appendQuotedUTF8"
      (satisfies-prop 100
        (prop/for-all [value fg/code-value]
          (let [expected-buffer (.quoteAsUTF8 (JsonStringEncoder/getInstance) value)
                buffer (byte-array (count expected-buffer))]
            (.appendQuotedUTF8 ^SerializableString (type/code value) buffer 0)
            (= (bb/wrap expected-buffer) (bb/wrap buffer))))))

    (testing "asUnquotedUTF8"
      (satisfies-prop 100
        (prop/for-all [value fg/code-value]
          (= (bb/wrap (.encodeAsUTF8 (JsonStringEncoder/getInstance) ^String value))
             (bb/wrap (.asUnquotedUTF8 ^SerializableString (type/code value)))))))

    (testing "asQuotedUTF8"
      (satisfies-prop 100
        (prop/for-all [value fg/code-value]
          (= (bb/wrap (.quoteAsUTF8 (JsonStringEncoder/getInstance) value))
             (bb/wrap (.asQuotedUTF8 ^SerializableString (type/code value)))))))))

(deftest oid-test
  (testing "oid?"
    (are [x] (type/oid? x)
      #fhir/oid""
      #fhir/oid{:id "foo"}))

  (testing "type"
    (are [x] (= :fhir/oid (type/type x))
      #fhir/oid""
      #fhir/oid{:id "foo"}))

  (testing "oid"
    (is (= #fhir/oid{:value "182040"} #fhir/oid"182040")))

  (testing "interned"
    (is (not-interned? #fhir/oid"oid-123745" #fhir/oid"oid-123745")))

  (testing "value"
    (are [x] (= "oid-123745" (type/value x))
      #fhir/oid"oid-123745"
      #fhir/oid{:id "foo" :value "oid-123745"}))

  (testing "to-json"
    (is (= "\"oid-123745\"" (gen-json-string #fhir/oid"oid-123745"))))

  (testing "to-xml"
    (is (= (sexp-value "oid-123745")
           (type/to-xml #fhir/oid"oid-123745"))))

  (testing "equals"
    (is (let [oid #fhir/oid"175726"] (.equals oid oid)))
    (is (.equals #fhir/oid"175726" #fhir/oid"175726"))
    (is (not (.equals #fhir/oid"175726" #fhir/oid"171055")))
    (is (not (.equals #fhir/oid"175726" "175726"))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/oid"175726" "a73ea817"
      #fhir/oid{:value "175726"} "a73ea817"
      #fhir/oid{:id "foo"} "4daaecfb"
      #fhir/oid{:id "foo" :value "175726"} "5e076060"
      #fhir/oid{:extension [#fhir/Extension{:url "foo"}]} "c114dd42"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/oid"151329"
      nil))

  (testing "print"
    (is (= "#fhir/oid\"175718\"" (pr-str #fhir/oid"175718")))))

(deftest id-test
  (testing "id?"
    (are [x] (type/id? x)
      #fhir/id""
      #fhir/id{:id "foo"}))

  (testing "type"
    (are [x] (= :fhir/id (type/type x))
      #fhir/id""
      #fhir/id{:id "foo"}))

  (testing "id"
    (is (= #fhir/id{:value "182040"} #fhir/id"182040")))

  (testing "interned"
    (is (not-interned? #fhir/id"id-123745" #fhir/id"id-123745")))

  (testing "value"
    (are [x] (= "id-123745" (type/value x))
      #fhir/id"id-123745"
      #fhir/id{:id "foo" :value "id-123745"}))

  (testing "to-json"
    (is (= "\"id-123745\"" (gen-json-string #fhir/id"id-123745"))))

  (testing "to-xml"
    (is (= (sexp-value "id-123745")
           (type/to-xml #fhir/id"id-123745"))))

  (testing "equals"
    (is (let [id #fhir/id"175726"] (.equals id id)))
    (is (.equals #fhir/id"175726" #fhir/id"175726"))
    (is (not (.equals #fhir/id"175726" #fhir/id"171108")))
    (is (not (.equals #fhir/id"175726" "175726"))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/id"175726" "e56cbac6"
      #fhir/id{:value "175726"} "e56cbac6"
      #fhir/id{:id "foo"} "59a2c68a"
      #fhir/id{:id "foo" :value "175726"} "3dbaa84e"
      #fhir/id{:extension [#fhir/Extension{:url "foo"}]} "1e8120f7"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/id"151408"
      nil))

  (testing "print"
    (is (= "#fhir/id\"175718\"" (pr-str #fhir/id"175718")))))

(deftest markdown-test
  (testing "markdown?"
    (are [x] (type/markdown? x)
      #fhir/markdown""
      #fhir/markdown{:id "foo"}))

  (testing "type"
    (are [x] (= :fhir/markdown (type/type x))
      #fhir/markdown""
      #fhir/markdown{:id "foo"}))

  (testing "markdown"
    (is (= #fhir/markdown{:value "182040"} #fhir/markdown"182040")))

  (testing "interned"
    (is (not-interned? #fhir/markdown"markdown-123745"
                       #fhir/markdown"markdown-123745")))

  (testing "value"
    (are [x] (= "markdown-123745" (type/value x))
      #fhir/markdown"markdown-123745"
      #fhir/markdown{:id "foo" :value "markdown-123745"}))

  (testing "to-json"
    (is (= "\"markdown-123745\""
           (gen-json-string #fhir/markdown"markdown-123745"))))

  (testing "to-xml"
    (is (= (sexp-value "markdown-123745")
           (type/to-xml #fhir/markdown"markdown-123745"))))

  (testing "equals"
    (is (let [markdown #fhir/markdown"175726"] (.equals markdown markdown)))
    (is (.equals #fhir/markdown"175726" #fhir/markdown"175726"))
    (is (not (.equals #fhir/markdown"175726" #fhir/markdown"171153")))
    (is (not (.equals #fhir/markdown"175726" "175726"))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/markdown"175726" "444928f7"
      #fhir/markdown{:value "175726"} "444928f7"
      #fhir/markdown{:id "foo"} "999ebb88"
      #fhir/markdown{:id "foo" :value "175726"} "c9b526e9"
      #fhir/markdown{:extension [#fhir/Extension{:url "foo"}]} "8d0712c5"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/markdown"151424"
      nil))

  (testing "print"
    (is (= "#fhir/markdown\"175718\"" (pr-str #fhir/markdown"175718")))))

(deftest unsignedInt-test
  (testing "unsignedInt?"
    (are [x] (type/unsignedInt? x)
      #fhir/unsignedInt 0
      #fhir/unsignedInt{:id "foo"}))

  (testing "type"
    (are [x] (= :fhir/unsignedInt (type/type x))
      #fhir/unsignedInt 0
      #fhir/unsignedInt{:id "foo"}))

  (testing "unsignedInt"
    (is (= #fhir/unsignedInt{:value 160845} #fhir/unsignedInt 160845)))

  (testing "interned"
    (is (not-interned? #fhir/unsignedInt 160845
                       #fhir/unsignedInt 160845)))

  (testing "value"
    (are [x] (= 160845 (type/value x))
      #fhir/unsignedInt 160845
      #fhir/unsignedInt{:id "foo" :value 160845}))

  (testing "to-json"
    (is (= "160845" (gen-json-string #fhir/unsignedInt 160845))))

  (testing "to-xml"
    (is (= (sexp-value "160845")
           (type/to-xml #fhir/unsignedInt 160845))))

  (testing "equals"
    (is (.equals #fhir/unsignedInt 160845 #fhir/unsignedInt 160845))
    (is (not (.equals #fhir/unsignedInt 160845 #fhir/unsignedInt 171218)))
    (is (not (.equals #fhir/unsignedInt 160845 160845))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/unsignedInt 160845 "10a52aa2"
      #fhir/unsignedInt{:value 160845} "10a52aa2"
      #fhir/unsignedInt{:id "foo"} "7a1f86be"
      #fhir/unsignedInt{:id "foo" :value 160845} "b38b1609"
      #fhir/unsignedInt{:extension [#fhir/Extension{:url "foo"}]} "8117a763"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/unsignedInt 151440
      nil))

  (testing "print"
    (testing "print"
      (are [x s] (= (pr-str x) s)
        #fhir/unsignedInt 192629
        "#fhir/unsignedInt 192629"

        #fhir/unsignedInt{:id "id-192647"}
        "#fhir/unsignedInt{:id \"id-192647\"}"

        #fhir/unsignedInt{:id "id-192703" :value 192711}
        "#fhir/unsignedInt{:id \"id-192703\", :value 192711}"

        #fhir/unsignedInt{:extension [#fhir/Extension{:url "url-192724"}]}
        "#fhir/unsignedInt{:extension [#fhir/Extension{:url \"url-192724\"}]}"))))

(deftest positiveInt-test
  (testing "positiveInt?"
    (are [x] (type/positiveInt? x)
      #fhir/positiveInt 0
      #fhir/positiveInt{:id "foo"}))

  (testing "type"
    (are [x] (= :fhir/positiveInt (type/type x))
      #fhir/positiveInt 0
      #fhir/positiveInt{:id "foo"}))

  (testing "positiveInt"
    (is (= #fhir/positiveInt{:value 160845} #fhir/positiveInt 160845)))

  (testing "interned"
    (is (not-interned? #fhir/positiveInt 160845
                       #fhir/positiveInt 160845)))

  (testing "value"
    (are [x] (= 160845 (type/value x))
      #fhir/positiveInt 160845
      #fhir/positiveInt{:id "foo" :value 160845}))

  (testing "to-json"
    (is (= "160845" (gen-json-string #fhir/positiveInt 160845))))

  (testing "to-xml"
    (is (= (sexp-value "160845")
           (type/to-xml #fhir/positiveInt 160845))))

  (testing "equals"
    (is (.equals #fhir/positiveInt 160845 #fhir/positiveInt 160845))
    (is (not (.equals #fhir/positiveInt 160845 #fhir/positiveInt 171237)))
    (is (not (.equals #fhir/positiveInt 160845 160845))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/positiveInt 160845 "8c218d7d"
      #fhir/positiveInt{:value 160845} "8c218d7d"
      #fhir/positiveInt{:id "foo"} "3f7dbd4e"
      #fhir/positiveInt{:id "foo" :value 160845} "8f325fc8"
      #fhir/positiveInt{:extension [#fhir/Extension{:url "foo"}]} "7c036682"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/positiveInt 151500
      nil))

  (testing "print"
    (is (= "#fhir/positiveInt 160845" (pr-str #fhir/positiveInt 160845)))))

(deftest uuid-test
  (testing "uuid?"
    (are [x] (type/uuid? x)
      #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"
      #fhir/uuid{:id "foo"}))

  (testing "type"
    (are [x] (= :fhir/uuid (type/type x))
      #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"
      #fhir/uuid{:id "foo"}))

  (testing "uuid"
    (is (= #fhir/uuid{:value "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"} #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3")))

  (testing "interned"
    (is (not-interned? #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"
                       #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"))

    (testing "with extension"
      (are [x y] (not-interned? x y)
        (type/uuid {:extension [internable-extension] :value "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"})
        (type/uuid {:extension [internable-extension] :value "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"})

        (type/uuid {:id "id-164735" :extension [internable-extension]})
        (type/uuid {:id "id-164735" :extension [internable-extension]})

        (type/uuid {:extension [not-internable-extension]})
        (type/uuid {:extension [not-internable-extension]}))

      (are [x y] (interned? x y)
        (type/uuid {:extension [internable-extension]})
        (type/uuid {:extension [internable-extension]}))))

  (testing "value"
    (are [x] (= "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3" (type/value x))
      #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"
      #fhir/uuid{:id "foo" :value "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"}))

  (testing "to-json"
    (is (= "\"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3\""
           (gen-json-string #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"))))

  (testing "to-xml"
    (is (= (sexp-value "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3")
           (type/to-xml #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"))))

  (testing "equals"
    (is (.equals #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"
                 #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"))
    (is (not (.equals #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"
                      #fhir/uuid"urn:uuid:ccd4a49d-a288-4387-b842-56dd0f896851")))
    (is (not (.equals #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"
                      "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3" "f894ff2b"
      #fhir/uuid{:value "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"} "f894ff2b"
      #fhir/uuid{:id "foo"} "3b18b5b7"
      #fhir/uuid{:id "foo" :value "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"} "c23eebae"
      #fhir/uuid{:extension [#fhir/Extension{:url "foo"}]} "9160d648"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/uuid"urn:uuid:89ddf6ab-8813-4c75-9500-dd07560fe817"
      nil)))

(def xhtml-element
  (sexp
   [::xhtml/div {:xmlns "http://www.w3.org/1999/xhtml"}
    [::xhtml/p "FHIR is cool."]]))

(deftest xhtml-test
  (testing "xhtml?"
    (is (type/xhtml? #fhir/xhtml"xhtml-123745")))

  (testing "from XML"
    (is (= #fhir/xhtml"<div xmlns=\"http://www.w3.org/1999/xhtml\"><p>FHIR is cool.</p></div>"
           (type/xml->Xhtml xhtml-element))))

  (testing "type"
    (is (= :fhir/xhtml (type/type #fhir/xhtml""))))

  (testing "interned"
    (is (not-interned? #fhir/xhtml"xhtml-123745"
                       #fhir/xhtml"xhtml-123745")))

  (testing "value"
    (is (= "xhtml-123745" (type/value #fhir/xhtml"xhtml-123745"))))

  (testing "to-json"
    (is (= "\"xhtml-123745\"" (gen-json-string #fhir/xhtml"xhtml-123745"))))

  (testing "to-xml"
    (testing "plain text"
      (is (= (xml/emit-str (type/to-xml #fhir/xhtml"xhtml-123745"))
             "<?xml version='1.0' encoding='UTF-8'?><div xmlns=\"http://www.w3.org/1999/xhtml\">xhtml-123745</div>")))

    (testing "not closed tag"
      (is (= (xml/emit-str (type/to-xml #fhir/xhtml"<foo>"))
             "<?xml version='1.0' encoding='UTF-8'?><div xmlns=\"http://www.w3.org/1999/xhtml\">&lt;foo></div>")))

    (testing "invalid tag"
      (is (= (xml/emit-str (type/to-xml #fhir/xhtml"<foo"))
             "<?xml version='1.0' encoding='UTF-8'?><div xmlns=\"http://www.w3.org/1999/xhtml\">&lt;foo</div>")))

    (testing "CDATA"
      (is (= (xml/emit-str (type/to-xml #fhir/xhtml"<![CDATA[foo]]>"))
             "<?xml version='1.0' encoding='UTF-8'?><div xmlns=\"http://www.w3.org/1999/xhtml\">&lt;![CDATA[foo]]&gt;</div>")))

    (testing "CDATA end"
      (is (= (xml/emit-str (type/to-xml #fhir/xhtml"]]>"))
             "<?xml version='1.0' encoding='UTF-8'?><div xmlns=\"http://www.w3.org/1999/xhtml\">]]&gt;</div>")))

    (is (= xhtml-element (type/to-xml #fhir/xhtml"<div xmlns=\"http://www.w3.org/1999/xhtml\"><p>FHIR is cool.</p></div>"))))

  (testing "equals"
    (is (.equals #fhir/xhtml"175726" #fhir/xhtml"175726"))
    (is (not (.equals #fhir/xhtml"175726" #fhir/xhtml"171511")))
    (is (not (.equals #fhir/xhtml"175726" "175726"))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/xhtml"175726" "e90ddf05"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/xhtml"151551"
      nil))

  (testing "print"
    (is (= "#fhir/xhtml\"175718\"" (pr-str #fhir/xhtml"175718"))))

  (testing "toString"
    (is (= "175718" (str #fhir/xhtml"175718")))))

(deftest attachment-test
  (testing "type"
    (is (= :fhir/Attachment (type/type #fhir/Attachment{}))))

  (testing "interned"
    (are [x y] (not-interned? x y)
      #fhir/Attachment{:id "foo"}
      #fhir/Attachment{:id "foo"})

    (are [x y] (interned? x y)
      #fhir/Attachment{:extension [#fhir/Extension{:url "foo" :value #fhir/code"bar"}]}
      #fhir/Attachment{:extension [#fhir/Extension{:url "foo" :value #fhir/code"bar"}]}))

  (testing "primary/secondary content"
    (is (true? (p/-has-primary-content #fhir/Attachment{})))
    (is (false? (p/-has-secondary-content #fhir/Attachment{}))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/Attachment{}
      "af56fc23"

      #fhir/Attachment{:id "id-204201"}
      "fb9177d8"

      #fhir/Attachment{:extension [#fhir/Extension{}]}
      "4947bc16"

      #fhir/Attachment{:contentType #fhir/code"text/plain"}
      "21d5985e"

      #fhir/Attachment{:language #fhir/code"de"}
      "223e2e7f"

      #fhir/Attachment{:data #fhir/base64Binary"MTA1NjE0Cg=="}
      "d2a23543"

      #fhir/Attachment{:url #fhir/url"url-210424"}
      "67f9de2f"

      #fhir/Attachment{:size #fhir/unsignedInt 1}
      "180724c5"

      #fhir/Attachment{:hash #fhir/base64Binary"MTA1NjE0Cg=="}
      "26e1ef66"

      #fhir/Attachment{:title "title-210622"}
      "fce4d064"

      #fhir/Attachment{:creation #fhir/dateTime"2021"}
      "1f9bf068"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/Attachment{}
      []))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Attachment{} "#fhir/Attachment{}"
      #fhir/Attachment{:id "212329"} "#fhir/Attachment{:id \"212329\"}")))

(deftest extension-test
  (testing "type"
    (is (= :fhir/Extension (type/type #fhir/Extension{}))))

  (testing "interned"
    (testing "instances with code values are interned"
      (are [x y] (interned? x y)
        #fhir/Extension{:url "foo" :value #fhir/code"bar"}
        #fhir/Extension{:url "foo" :value #fhir/code"bar"}))

    (testing "instances with code values and interned extensions are interned"
      (are [x y] (interned? x y)
        #fhir/Extension
         {:extension [#fhir/Extension{:url "foo" :value #fhir/code"bar"}]
          :url "foo"
          :value #fhir/code"bar"}
        #fhir/Extension
         {:extension [#fhir/Extension{:url "foo" :value #fhir/code"bar"}]
          :url "foo"
          :value #fhir/code"bar"}))

    (testing "instances with code values but id's are not interned"
      (are [x y] (not-interned? x y)
        #fhir/Extension{:id "foo" :url "bar" :value #fhir/code"baz"}
        #fhir/Extension{:id "foo" :url "bar" :value #fhir/code"baz"}))

    (testing "instances with string values are not interned"
      (are [x y] (not-interned? x y)
        #fhir/Extension{:url "foo" :value "bar"}
        #fhir/Extension{:url "foo" :value "bar"})))

  (testing "primary/secondary content"
    (is (true? (p/-has-primary-content #fhir/Extension{})))
    (is (false? (p/-has-secondary-content #fhir/Extension{}))))

  (testing "to-json"
    (are [code json] (= json (gen-json-string code))
      #fhir/Extension{} "{}"
      #fhir/Extension{:id "id-162531"} "{\"id\":\"id-162531\"}"))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/Extension{}
      "9b6ea882"

      #fhir/Extension{:id "id-204201"}
      "495cd278"

      #fhir/Extension{:extension [#fhir/Extension{}]}
      "af661e95"

      #fhir/Extension{:extension [#fhir/Extension{} #fhir/Extension{}]}
      "96fd01bd"

      #fhir/Extension{:url "url-130945"}
      "8204427a"

      #fhir/Extension{:value #fhir/code"value-130953"}
      "befce87a"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/Extension{}
      []))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Extension{} "#fhir/Extension{}"
      #fhir/Extension{:id "212329"} "#fhir/Extension{:id \"212329\"}")))

(defn- recreate
  "Takes `x`, a complex type and recreates it from its components using
  `constructor`."
  [constructor x]
  (constructor (into {} (remove (comp nil? val)) x)))

(def ^:private string-extension-gen
  (fg/extension :value (fg/string :value fg/string-value)))

(deftest coding-test
  (testing "type"
    (is (= :fhir/Coding (type/type #fhir/Coding{}))))

  (testing "interned"
    (satisfies-prop 100
      (prop/for-all [x (fg/coding :extension (fg/extensions :value (fg/code)))]
        (interned? x (recreate type/coding x))))

    (testing "instances with id's are not interned"
      (satisfies-prop 100
        (prop/for-all [x (fg/coding :id fg/id-value)]
          (not-interned? x (recreate type/coding x)))))

    (testing "instances with not interned extensions are not interned"
      (satisfies-prop 100
        (prop/for-all [x (fg/coding :extension (gen/vector string-extension-gen 1))]
          (not-interned? x (recreate type/coding x))))))

  (testing "primary/secondary content"
    (is (true? (p/-has-primary-content #fhir/Coding{})))
    (is (false? (p/-has-secondary-content #fhir/Coding{}))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/Coding{}
      "24e7e891"

      #fhir/Coding{:id "id-204201"}
      "c1c82c65"

      #fhir/Coding{:extension [#fhir/Extension{}]}
      "e1d440bb"

      #fhir/Coding{:system #fhir/uri"system-202808"}
      "da808d2d"

      #fhir/Coding{:version #fhir/uri"version-154317"}
      "93fc58d9"

      #fhir/Coding{:code #fhir/code"code-202828"}
      "74e3328d"

      #fhir/Coding{:display #fhir/string"display-154256"}
      "baac923d"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/Coding{}
      []))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Coding{} "#fhir/Coding{}"
      #fhir/Coding{:id "212329"} "#fhir/Coding{:id \"212329\"}")))

(deftest codeable-concept-test
  (testing "type"
    (is (= :fhir/CodeableConcept (type/type #fhir/CodeableConcept{}))))

  (testing "interned"
    (satisfies-prop 100
      (prop/for-all [x (fg/codeable-concept :extension (fg/extensions :value (fg/code)))]
        (interned? x (recreate type/codeable-concept x))))

    (testing "instances with id's are not interned"
      (satisfies-prop 100
        (prop/for-all [x (fg/codeable-concept :id fg/id-value)]
          (not-interned? x (recreate type/codeable-concept x)))))

    (testing "instances with not interned extensions are not interned"
      (satisfies-prop 100
        (prop/for-all [x (fg/codeable-concept :extension (gen/vector string-extension-gen 1))]
          (not-interned? x (recreate type/codeable-concept x))))))

  (testing "primary/secondary content"
    (is (true? (p/-has-primary-content #fhir/CodeableConcept{})))
    (is (false? (p/-has-secondary-content #fhir/CodeableConcept{}))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/CodeableConcept{}
      "9b6ea882"

      #fhir/CodeableConcept{:id "id-141755"}
      "d9ac742f"

      #fhir/CodeableConcept{:extension [#fhir/Extension{}]}
      "af661e95"

      #fhir/CodeableConcept{:coding [#fhir/Coding{}]}
      "9c4509ed"

      #fhir/CodeableConcept{:text #fhir/string"text-153829"}
      "fe2e61f1"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/CodeableConcept{}
      []))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/CodeableConcept{} "#fhir/CodeableConcept{}"
      #fhir/CodeableConcept{:id "212329"} "#fhir/CodeableConcept{:id \"212329\"}")))

(deftest quantity-test
  (testing "type"
    (is (= :fhir/Quantity (type/type #fhir/Quantity{}))))

  (testing "interned"
    (are [x y] (not-interned? x y)
      #fhir/Quantity{:id "foo"}
      #fhir/Quantity{:id "foo"}

      #fhir/Quantity{:extension [#fhir/Extension{:url "foo" :value "bar"}]}
      #fhir/Quantity{:extension [#fhir/Extension{:url "foo" :value "bar"}]}

      #fhir/Quantity{:value #fhir/decimal 1M}
      #fhir/Quantity{:value #fhir/decimal 1M})

    (are [x y] (interned? x y)
      #fhir/Quantity{:extension [#fhir/Extension{:url "foo" :value #fhir/code"bar"}]}
      #fhir/Quantity{:extension [#fhir/Extension{:url "foo" :value #fhir/code"bar"}]}

      #fhir/Quantity{:comparator #fhir/code"foo"}
      #fhir/Quantity{:comparator #fhir/code"foo"}

      #fhir/Quantity{:unit #fhir/string"foo"}
      #fhir/Quantity{:unit #fhir/string"foo"}

      #fhir/Quantity{:system #fhir/uri"foo"}
      #fhir/Quantity{:system #fhir/uri"foo"}

      #fhir/Quantity{:code #fhir/code"foo"}
      #fhir/Quantity{:code #fhir/code"foo"}))

  (testing "primary/secondary content"
    (is (true? (p/-has-primary-content #fhir/Quantity{})))
    (is (false? (p/-has-secondary-content #fhir/Quantity{}))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/Quantity{}
      "1ddef3ed"

      #fhir/Quantity{:id "id-141848"}
      "abb59da1"

      #fhir/Quantity{:extension [#fhir/Extension{}]}
      "4f5028ac"

      #fhir/Quantity{:value #fhir/decimal 1M}
      "4adf97ab"

      #fhir/Quantity{:comparator #fhir/code"comparator-153342"}
      "6339e3e8"

      #fhir/Quantity{:unit #fhir/string"unit-153351"}
      "d8f92891"

      #fhir/Quantity{:system #fhir/uri"system-153337"}
      "98f918ba"

      #fhir/Quantity{:code #fhir/code"code-153427"}
      "7ff49528"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/Quantity{}
      []))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Quantity{} "#fhir/Quantity{}"
      #fhir/Quantity{:id "212329"} "#fhir/Quantity{:id \"212329\"}")))

(deftest ratio-test
  (testing "type"
    (is (= :fhir/Ratio (type/type #fhir/Ratio{}))))

  (testing "interned"
    (are [x y] (not-interned? x y)
      #fhir/Ratio{:id "foo"}
      #fhir/Ratio{:id "foo"}

      #fhir/Ratio{:extension [#fhir/Extension{:url "foo" :value "bar"}]}
      #fhir/Ratio{:extension [#fhir/Extension{:url "foo" :value "bar"}]}

      #fhir/Ratio{:numerator #fhir/Quantity{:value #fhir/decimal 1M}}
      #fhir/Ratio{:numerator #fhir/Quantity{:value #fhir/decimal 1M}}

      #fhir/Ratio{:denominator #fhir/Quantity{:value #fhir/decimal 1M}}
      #fhir/Ratio{:denominator #fhir/Quantity{:value #fhir/decimal 1M}})

    (are [x y] (interned? x y)
      #fhir/Ratio{:extension [#fhir/Extension{:url "foo" :value #fhir/code"bar"}]}
      #fhir/Ratio{:extension [#fhir/Extension{:url "foo" :value #fhir/code"bar"}]}

      #fhir/Ratio{:numerator #fhir/Quantity{:code #fhir/code"foo"}}
      #fhir/Ratio{:numerator #fhir/Quantity{:code #fhir/code"foo"}}

      #fhir/Ratio{:denominator #fhir/Quantity{:code #fhir/code"foo"}}
      #fhir/Ratio{:denominator #fhir/Quantity{:code #fhir/code"foo"}}))

  (testing "primary/secondary content"
    (is (true? (p/-has-primary-content #fhir/Ratio{})))
    (is (false? (p/-has-secondary-content #fhir/Ratio{}))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/Ratio{}
      "d271c07f"

      #fhir/Ratio{:id "id-130710"}
      "e3c0ee3c"

      #fhir/Ratio{:extension [#fhir/Extension{}]}
      "23473d24"

      #fhir/Ratio{:numerator #fhir/Quantity{:value #fhir/decimal 1M}}
      "fbf83a67"

      #fhir/Ratio{:denominator #fhir/Quantity{:value #fhir/decimal 1M}}
      "7f2075fb"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/Ratio{}
      []))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Ratio{} "#fhir/Ratio{}"
      #fhir/Ratio{:id "212329"} "#fhir/Ratio{:id \"212329\"}")))

(deftest period-test
  (testing "type"
    (is (= :fhir/Period (type/type #fhir/Period{}))))

  (testing "interned"
    (are [x y] (not-interned? x y)
      #fhir/Period{:id "foo"}
      #fhir/Period{:id "foo"}

      #fhir/Period{:extension [#fhir/Extension{:url "foo" :value "bar"}]}
      #fhir/Period{:extension [#fhir/Extension{:url "foo" :value "bar"}]}

      #fhir/Period{:start #fhir/dateTime"2020"}
      #fhir/Period{:start #fhir/dateTime"2020"})

    (are [x y] (interned? x y)
      #fhir/Period{:extension [#fhir/Extension{:url "foo" :value #fhir/code"bar"}]}
      #fhir/Period{:extension [#fhir/Extension{:url "foo" :value #fhir/code"bar"}]}))

  (testing "primary/secondary content"
    (is (true? (p/-has-primary-content #fhir/Period{})))
    (is (false? (p/-has-secondary-content #fhir/Period{}))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/Period{}
      "e5f76205"

      #fhir/Period{:id "id-130710"}
      "29c53420"

      #fhir/Period{:extension [#fhir/Extension{}]}
      "92e4ba37"

      #fhir/Period{:start #fhir/dateTime"2020"}
      "f1b7c952"

      #fhir/Period{:end #fhir/dateTime"2020"}
      "434787dd"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/Period{}
      []))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Period{} "#fhir/Period{}"
      #fhir/Period{:id "212329"} "#fhir/Period{:id \"212329\"}")))

(deftest identifier-test
  (testing "type"
    (is (= :fhir/Identifier (type/type #fhir/Identifier{}))))

  (testing "interned"
    (are [x y] (not-interned? x y)
      #fhir/Identifier{:id "foo"}
      #fhir/Identifier{:id "foo"}

      #fhir/Identifier{:extension [#fhir/Extension{:url "foo" :value "bar"}]}
      #fhir/Identifier{:extension [#fhir/Extension{:url "foo" :value "bar"}]}

      #fhir/Identifier{:value "foo"}
      #fhir/Identifier{:value "foo"})

    (are [x y] (interned? x y)
      #fhir/Identifier{:extension [#fhir/Extension{:url "foo" :value #fhir/code"bar"}]}
      #fhir/Identifier{:extension [#fhir/Extension{:url "foo" :value #fhir/code"bar"}]}

      #fhir/Identifier{:use #fhir/code"foo"}
      #fhir/Identifier{:use #fhir/code"foo"}))

  (testing "primary/secondary content"
    (is (true? (p/-has-primary-content #fhir/Identifier{})))
    (is (false? (p/-has-secondary-content #fhir/Identifier{}))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/Identifier{}
      "14336e1c"

      #fhir/Identifier{:id "id-130739"}
      "57c166f0"

      #fhir/Identifier{:extension [#fhir/Extension{}]}
      "810b77ff"

      #fhir/Identifier{:use #fhir/code"use-155144"}
      "4bf89602"

      #fhir/Identifier{:type #fhir/CodeableConcept{}}
      "736db874"

      #fhir/Identifier{:system #fhir/uri"system-145514"}
      "acbabb5d"

      #fhir/Identifier{:value "value-145509"}
      "de7e521f"

      #fhir/Identifier{:period #fhir/Period{}}
      "8a73bfa3"

      #fhir/Identifier{:assigner #fhir/Reference{}}
      "aa994e1e"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/Identifier{}
      []))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Identifier{} "#fhir/Identifier{}"
      #fhir/Identifier{:id "212329"} "#fhir/Identifier{:id \"212329\"}")))

(deftest human-name-test
  (testing "type"
    (is (= :fhir/HumanName (type/type #fhir/HumanName{}))))

  (testing "interned"
    (are [x y] (not-interned? x y)
      #fhir/HumanName{:id "foo"}
      #fhir/HumanName{:id "foo"}

      #fhir/HumanName{:extension [#fhir/Extension{:url "foo" :value "bar"}]}
      #fhir/HumanName{:extension [#fhir/Extension{:url "foo" :value "bar"}]}

      #fhir/HumanName{:text "foo"}
      #fhir/HumanName{:text "foo"}

      #fhir/HumanName{:family "foo"}
      #fhir/HumanName{:family "foo"})

    (are [x y] (interned? x y)
      #fhir/HumanName{:extension [#fhir/Extension{:url "foo" :value #fhir/code"bar"}]}
      #fhir/HumanName{:extension [#fhir/Extension{:url "foo" :value #fhir/code"bar"}]}

      #fhir/HumanName{:use #fhir/code"foo"}
      #fhir/HumanName{:use #fhir/code"foo"}))

  (testing "primary/secondary content"
    (is (true? (p/-has-primary-content #fhir/HumanName{})))
    (is (false? (p/-has-secondary-content #fhir/HumanName{}))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/HumanName{}
      "af56fc23"

      #fhir/HumanName{:id "id-130739"}
      "ebba60f8"

      #fhir/HumanName{:extension [#fhir/Extension{}]}
      "4947bc16"

      #fhir/HumanName{:use #fhir/code"use-155144"}
      "60b2b58c"

      #fhir/HumanName{:text "text-212402"}
      "b9ab5f61"

      #fhir/HumanName{:family "family-212422"}
      "915831d8"

      #fhir/HumanName{:given ["given-212441"]}
      "e26a58ee"

      #fhir/HumanName{:given ["given-212448" "given-212454"]}
      "b46d5198"

      #fhir/HumanName{:prefix ["prefix-212514"]}
      "1a411067"

      #fhir/HumanName{:prefix ["prefix-212523" "prefix-212525"]}
      "32529f07"

      #fhir/HumanName{:suffix ["suffix-212542"]}
      "3181f719"

      #fhir/HumanName{:suffix ["suffix-212547" "suffix-212554"]}
      "69ca06e0"

      #fhir/HumanName{:period #fhir/Period{}}
      "18b2a823"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/HumanName{}
      []))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/HumanName{} "#fhir/HumanName{}"
      #fhir/HumanName{:id "212625"} "#fhir/HumanName{:id \"212625\"}")))

(deftest address-test
  (testing "type"
    (is (= :fhir/Address (type/type #fhir/Address{}))))

  (testing "interned"
    (are [x y] (not-interned? x y)
      #fhir/Address{:id "foo"}
      #fhir/Address{:id "foo"}

      #fhir/Address{:extension [#fhir/Extension{:url "foo" :value "bar"}]}
      #fhir/Address{:extension [#fhir/Extension{:url "foo" :value "bar"}]}

      #fhir/Address{:text "foo"}
      #fhir/Address{:text "foo"})

    (are [x y] (interned? x y)
      #fhir/Address{:extension [#fhir/Extension{:url "foo" :value #fhir/code"bar"}]}
      #fhir/Address{:extension [#fhir/Extension{:url "foo" :value #fhir/code"bar"}]}

      #fhir/Address{:use #fhir/code"foo"}
      #fhir/Address{:use #fhir/code"foo"}

      #fhir/Address{:type #fhir/code"foo"}
      #fhir/Address{:type #fhir/code"foo"}))

  (testing "primary/secondary content"
    (is (true? (p/-has-primary-content #fhir/Address{})))
    (is (false? (p/-has-secondary-content #fhir/Address{}))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/Address{}
      "4a6b5e4f"

      #fhir/Address{:id "id-130739"}
      "bd6a5731"

      #fhir/Address{:extension [#fhir/Extension{}]}
      "2a3786e7"

      #fhir/Address{:use #fhir/code"use-155144"}
      "b6cf1d48"

      #fhir/Address{:type #fhir/code"type-084442"}
      "54c286c3"

      #fhir/Address{:text "text-212402"}
      "15baed84"

      #fhir/Address{:line ["line-212441"]}
      "eafac0f1"

      #fhir/Address{:line ["line-212448" "line-212454"]}
      "62f4cf8f"

      #fhir/Address{:city "city-084705"}
      "9765a1e9"

      #fhir/Address{:district "district-084717"}
      "9e6dc6b8"

      #fhir/Address{:state "state-084729"}
      "17a7640f"

      #fhir/Address{:postalCode "postalCode-084832"}
      "8880561c"

      #fhir/Address{:country "country-084845"}
      "57c51a7d"

      #fhir/Address{:period #fhir/Period{}}
      "fb17905a"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/Address{}
      []))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Address{} "#fhir/Address{}"
      #fhir/Address{:id "084856"} "#fhir/Address{:id \"084856\"}")))

(deftest reference-test
  (testing "type"
    (is (= :fhir/Reference (type/type #fhir/Reference{}))))

  (testing "interned"
    (are [x y] (not-interned? x y)
      #fhir/Reference{:id "foo"}
      #fhir/Reference{:id "foo"}

      #fhir/Reference{:extension [#fhir/Extension{:url "foo" :value "bar"}]}
      #fhir/Reference{:extension [#fhir/Extension{:url "foo" :value "bar"}]}

      #fhir/Reference{:reference "foo"}
      #fhir/Reference{:reference "foo"}

      #fhir/Reference{:identifier #fhir/Identifier{:value "foo"}}
      #fhir/Reference{:identifier #fhir/Identifier{:value "foo"}}

      #fhir/Reference{:display "foo"}
      #fhir/Reference{:display "foo"})

    (are [x y] (interned? x y)
      #fhir/Reference{:extension [#fhir/Extension{:url "foo" :value #fhir/code"bar"}]}
      #fhir/Reference{:extension [#fhir/Extension{:url "foo" :value #fhir/code"bar"}]}

      #fhir/Reference{:type #fhir/code"foo"}
      #fhir/Reference{:type #fhir/code"foo"}))

  (testing "primary/secondary content"
    (is (true? (p/-has-primary-content #fhir/Reference{})))
    (is (false? (p/-has-secondary-content #fhir/Reference{}))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/Reference{}
      "6498613c"

      #fhir/Reference{:id "id-130802"}
      "a48cca5a"

      #fhir/Reference{:extension [#fhir/Extension{}]}
      "210e3eb7"

      #fhir/Reference{:reference #fhir/string"Patient/0"}
      "cd80b8ac"

      #fhir/Reference{:type #fhir/uri"type-161222"}
      "2fe271cd"

      #fhir/Reference{:identifier #fhir/Identifier{}}
      "eb066d27"

      #fhir/Reference{:display #fhir/string"display-161314"}
      "543cf75f"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/Reference{}
      []

      #fhir/Reference{:extension [#fhir/Extension{}]}
      []

      #fhir/Reference
       {:extension
        [#fhir/Extension
          {:value #fhir/Reference
                   {:reference #fhir/string"Patient/1"}}]}
      [["Patient" "1"]]

      #fhir/Reference{:reference #fhir/string"Patient/0"}
      [["Patient" "0"]]

      #fhir/Reference{:reference #fhir/string"Patient"}
      []

      #fhir/Reference{:reference #fhir/string""}
      []

      #fhir/Reference
       {:extension
        [#fhir/Extension
          {:value #fhir/Reference
                   {:reference #fhir/string"Patient/1"}}]
        :reference #fhir/string"Patient/0"}
      [["Patient" "0"] ["Patient" "1"]]

      #fhir/Reference
       {:reference #fhir/string{:extension [#fhir/Extension{:url "foo"}]}}
      []

      #fhir/Reference
       {:reference #fhir/string{:extension [#fhir/Extension{:url "foo"}]
                                :value "Patient/0"}}
      [["Patient" "0"]]))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Reference{} "#fhir/Reference{}"
      #fhir/Reference{:id "212329"} "#fhir/Reference{:id \"212329\"}")))

(deftest meta-test
  (testing "type"
    (is (= :fhir/Meta (type/type #fhir/Meta{}))))

  (testing "interned"
    (are [x y] (not-interned? x y)
      #fhir/Meta{:id "foo"}
      #fhir/Meta{:id "foo"}

      #fhir/Meta{:extension [#fhir/Extension{:url "foo" :value "bar"}]}
      #fhir/Meta{:extension [#fhir/Extension{:url "foo" :value "bar"}]}

      #fhir/Meta{:versionId #fhir/id"foo"}
      #fhir/Meta{:versionId #fhir/id"foo"}

      #fhir/Meta{:lastUpdated #fhir/instant"2020-01-01T00:00:00Z"}
      #fhir/Meta{:lastUpdated #fhir/instant"2020-01-01T00:00:00Z"})

    (are [x y] (interned? x y)
      #fhir/Meta{:extension [#fhir/Extension{:url "foo" :value #fhir/code"bar"}]}
      #fhir/Meta{:extension [#fhir/Extension{:url "foo" :value #fhir/code"bar"}]}

      #fhir/Meta{:source #fhir/uri"foo"}
      #fhir/Meta{:source #fhir/uri"foo"}

      #fhir/Meta{:profile #fhir/canonical"foo"}
      #fhir/Meta{:profile #fhir/canonical"foo"}

      #fhir/Meta{:security [#fhir/Coding{:system #fhir/uri"foo" :code #fhir/code"bar"}]}
      #fhir/Meta{:security [#fhir/Coding{:system #fhir/uri"foo" :code #fhir/code"bar"}]}

      #fhir/Meta{:tag [#fhir/Coding{:system #fhir/uri"foo" :code #fhir/code"bar"}]}
      #fhir/Meta{:tag [#fhir/Coding{:system #fhir/uri"foo" :code #fhir/code"bar"}]}))

  (testing "primary/secondary content"
    (is (true? (p/-has-primary-content #fhir/Meta{})))
    (is (false? (p/-has-secondary-content #fhir/Meta{}))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/Meta{}
      "cbae28fd"

      #fhir/Meta{:id "id-130825"}
      "c2c18a00"

      #fhir/Meta{:extension [#fhir/Extension{}]}
      "aaf41f94"

      #fhir/Meta{:versionId #fhir/id"versionId-161415"}
      "9edaa9b"

      (type/meta {:lastUpdated Instant/EPOCH})
      "38b8dfe3"

      #fhir/Meta{:source #fhir/uri"source-161629"}
      "bc99bc82"

      #fhir/Meta{:profile [#fhir/canonical"profile-uri-145024"]}
      "b13c3d52"

      #fhir/Meta{:security [#fhir/Coding{}]}
      "9b7633bc"

      #fhir/Meta{:tag [#fhir/Coding{}]}
      "96e4e336"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/Meta{}
      []

      #fhir/Meta{:extension [#fhir/Extension{}]}
      []

      #fhir/Meta
       {:extension
        [#fhir/Extension{:value #fhir/Reference{:reference "Patient/2"}}]}
      [["Patient" "2"]]))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Meta{} "#fhir/Meta{}"
      #fhir/Meta{:id "212329"} "#fhir/Meta{:id \"212329\"}")))

(deftest bundle-entry-search-test
  (testing "type"
    (is (= :fhir.Bundle.entry/search (type/type #fhir/BundleEntrySearch{}))))

  (testing "interned"
    (are [x y] (not-interned? x y)
      #fhir/BundleEntrySearch{:id "foo"}
      #fhir/BundleEntrySearch{:id "foo"}

      #fhir/BundleEntrySearch{:extension [#fhir/Extension{:url "foo" :value "bar"}]}
      #fhir/BundleEntrySearch{:extension [#fhir/Extension{:url "foo" :value "bar"}]}

      #fhir/BundleEntrySearch{:score #fhir/decimal 1M}
      #fhir/BundleEntrySearch{:score #fhir/decimal 1M})

    (are [x y] (interned? x y)
      #fhir/BundleEntrySearch{:extension [#fhir/Extension{:url "foo" :value #fhir/code"bar"}]}
      #fhir/BundleEntrySearch{:extension [#fhir/Extension{:url "foo" :value #fhir/code"bar"}]}

      #fhir/BundleEntrySearch{:mode #fhir/code"match"}
      #fhir/BundleEntrySearch{:mode #fhir/code"match"}))

  (testing "primary/secondary content"
    (is (true? (p/-has-primary-content #fhir/BundleEntrySearch{})))
    (is (false? (p/-has-secondary-content #fhir/BundleEntrySearch{}))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/BundleEntrySearch{}
      "f945531f"

      #fhir/BundleEntrySearch{:id "id-130825"}
      "6b1b9201"

      #fhir/BundleEntrySearch{:extension [#fhir/Extension{}]}
      "f24daf4f"

      #fhir/BundleEntrySearch{:mode #fhir/code"match"}
      "5912b48c"

      #fhir/BundleEntrySearch{:score 1M}
      "2b2509dc"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/BundleEntrySearch{}
      []))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/BundleEntrySearch{} "#fhir/BundleEntrySearch{}"
      #fhir/BundleEntrySearch{:id "212329"} "#fhir/BundleEntrySearch{:id \"212329\"}")))
