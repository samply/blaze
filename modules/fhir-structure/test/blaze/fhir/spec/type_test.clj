(ns blaze.fhir.spec.type-test
  (:require
   [blaze.fhir.spec.generators :as fg]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.spec.type-spec]
   [blaze.fhir.spec.type.system.spec]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.data.xml :as xml]
   [clojure.data.xml.name :as xml-name]
   [clojure.data.xml.node :as xml-node]
   [clojure.data.xml.prxml :as prxml]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [cognitect.anomalies :as anom]
   [jsonista.core :as j]
   [juxt.iota :refer [given]])
  (:import
   [blaze.fhir.spec.type Base Primitive]
   [blaze.fhir.spec.type.system DateTime]
   [com.fasterxml.jackson.databind ObjectMapper]
   [com.fasterxml.jackson.databind.module SimpleModule]
   [com.fasterxml.jackson.databind.ser.std StdSerializer]
   [com.google.common.hash Hashing]))

(xml-name/alias-uri 'f "http://hl7.org/fhir")
(xml-name/alias-uri 'xhtml "http://www.w3.org/1999/xhtml")

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn murmur3 [x]
  (let [hasher (.newHasher (Hashing/murmur3_32_fixed))]
    (Base/hashInto x hasher)
    (Integer/toHexString (.asInt (.hash hasher)))))

(def ^:private object-serializer
  (proxy [StdSerializer] [Object]
    (serialize [obj generator _]
      (.serializeJsonPrimitiveValue ^Primitive obj generator))))

(def ^:private fhir-module
  (doto (SimpleModule.)
    (.addSerializer Object object-serializer)))

(def ^:private object-mapper
  (doto (ObjectMapper.)
    (.registerModule fhir-module)))

(defn- gen-json-value [x]
  (-> (j/write-value-as-bytes x object-mapper)
      (j/read-value (j/object-mapper {:decode-key-fn true
                                      :bigdecimals true}))))

(def ^:private sexp prxml/sexp-as-element)

(defn- sexp-value [value]
  (sexp [nil {:value value}]))

(def ^:private string-extension
  #fhir/Extension{:url "foo" :valueString #fhir/string "bar"})

(defn interned? [x y]
  (and (identical? x y) (Base/isInterned x) (Base/isInterned y)))

(defn not-interned? [x y]
  (and (= x y)
       (not (identical? x y))
       (not (Base/isInterned x))
       (not (Base/isInterned y))))

(def ^:private internable-extension
  #fhir/Extension{:url "url-130945" :value #fhir/code "value-130953"})

(def ^:private not-internable-extension
  #fhir/Extension{:url "url-205325" :value #fhir/string "value-205336"})

(deftest boolean-test
  (testing "boolean?"
    (are [x] (type/boolean? x)
      #fhir/boolean true
      #fhir/boolean{:id "foo"}))

  (testing "invalid"
    (given (st/with-instrument-disabled (type/boolean "a"))
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid boolean value `a`."))

  (testing "type"
    (are [x] (= :fhir/boolean (:fhir/type x))
      #fhir/boolean true
      #fhir/boolean{:id "foo"}))

  (testing "Boolean"
    (is (= #fhir/boolean{:value true} #fhir/boolean true)))

  (testing "interning"
    (is (interned? #fhir/boolean true #fhir/boolean true))

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

  (testing "assoc id"
    (testing "non-extended"
      (is (= (assoc #fhir/boolean true :id "id-111030")
             #fhir/boolean{:id "id-111030" :value true})))

    (testing "already extended"
      (is (= (assoc #fhir/boolean{:id "foo"} :id "bar")
             #fhir/boolean{:id "bar"}))
      (is (= (assoc #fhir/boolean{:extension [#fhir/Extension{:url "foo"}]} :id "id-111902")
             #fhir/boolean{:id "id-111902" :extension [#fhir/Extension{:url "foo"}]}))))

  (testing "assoc extension"
    (testing "non-extended"
      (is (= (assoc #fhir/boolean true :extension [#fhir/Extension{:url "foo"}])
             #fhir/boolean{:extension [#fhir/Extension{:url "foo"}] :value true})))

    (testing "already extended"
      (is (= (assoc #fhir/boolean{:id "id-111953"} :extension [#fhir/Extension{:url "foo"}])
             #fhir/boolean{:id "id-111953" :extension [#fhir/Extension{:url "foo"}]}))
      (is (= (assoc #fhir/boolean{:extension [#fhir/Extension{:url "foo"}]} :extension [#fhir/Extension{:url "bar"}])
             #fhir/boolean{:extension [#fhir/Extension{:url "bar"}]}))))

  (testing "id"
    (are [x res] (= res (:id x))
      #fhir/boolean true nil
      #fhir/boolean{:id "foo"} "foo"))

  (testing "value"
    (are [x] (true? (:value x))
      #fhir/boolean true
      #fhir/boolean{:id "foo" :value true}))

  (testing "assoc value"
    (is (= #fhir/boolean false (assoc #fhir/boolean true :value false))))

  (testing "metadata"
    (is (nil? (meta #fhir/boolean true)))
    (is (= {:foo "bar"} (meta (with-meta #fhir/boolean true {:foo "bar"})))))

  (testing "to-json"
    (is (true? (gen-json-value #fhir/boolean true)))
    (is (false? (gen-json-value #fhir/boolean false))))

  (testing "to-xml"
    (are [b s] (= (sexp-value s) (type/to-xml b))
      #fhir/boolean true "true"
      #fhir/boolean false "false"))

  (testing "equals"
    (is (= #fhir/boolean{:extension [#fhir/Extension{:url ""}] :value false}
           #fhir/boolean{:extension [#fhir/Extension{:url ""}] :value false})))

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

  (testing "mem-size"
    (are [x mem-size] (= mem-size (Base/memSize x))
      #fhir/boolean true 0
      #fhir/boolean{:id "foo"} 80))

  (testing "references"
    (is (empty? (type/references #fhir/boolean true))))

  (testing "print"
    (are [x s] (= (pr-str x) s)
      #fhir/boolean true "#fhir/boolean true"
      #fhir/boolean{:id "foo"} "#fhir/boolean{:id \"foo\"}")))

(deftest integer-test
  (testing "integer?"
    (are [x] (type/integer? x)
      #fhir/integer -1
      #fhir/integer 0
      #fhir/integer 1
      #fhir/integer{:id "foo"}))

  (testing "invalid"
    (given (st/with-instrument-disabled (type/integer "a"))
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid integer value `a`.")

    (testing "too large"
      (doseq [x [(inc Integer/MAX_VALUE) {:value (inc Integer/MAX_VALUE)}]]
        (given (type/integer x)
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid integer value `2147483648`."))))

  (testing "type"
    (are [x] (= :fhir/integer (:fhir/type x))
      #fhir/integer 1
      #fhir/integer{:id "foo"}))

  (testing "Integer"
    (is (= #fhir/integer{:value 1} #fhir/integer 1)))

  (testing "interning"
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

  (testing "assoc id"
    (testing "non-extended"
      (is (= (assoc #fhir/integer 1 :id "id-111030")
             #fhir/integer{:id "id-111030" :value 1})))

    (testing "already extended"
      (is (= (assoc #fhir/integer{:id "foo"} :id "bar")
             #fhir/integer{:id "bar"}))
      (is (= (assoc #fhir/integer{:extension [#fhir/Extension{:url "foo"}]} :id "id-111902")
             #fhir/integer{:id "id-111902" :extension [#fhir/Extension{:url "foo"}]}))))

  (testing "assoc extension"
    (testing "non-extended"
      (is (= (assoc #fhir/integer 1 :extension [#fhir/Extension{:url "foo"}])
             #fhir/integer{:extension [#fhir/Extension{:url "foo"}] :value 1})))

    (testing "already extended"
      (is (= (assoc #fhir/integer{:id "id-111953"} :extension [#fhir/Extension{:url "foo"}])
             #fhir/integer{:id "id-111953" :extension [#fhir/Extension{:url "foo"}]}))
      (is (= (assoc #fhir/integer{:extension [#fhir/Extension{:url "foo"}]} :extension [#fhir/Extension{:url "bar"}])
             #fhir/integer{:extension [#fhir/Extension{:url "bar"}]}))))

  (testing "value"
    (are [x] (= 1 (:value x))
      #fhir/integer 1
      #fhir/integer{:id "foo" :value 1}))

  (testing "assoc value"
    (is (= #fhir/integer 2 (assoc #fhir/integer 1 :value 2))))

  (testing "to-json"
    (is (= 1 (gen-json-value #fhir/integer 1))))

  (testing "to-xml"
    (is (= (sexp-value "1") (type/to-xml #fhir/integer 1))))

  (testing "hash-into"
    (are [i hex] (= hex (murmur3 i))
      #fhir/integer 0 "ab61a435"
      #fhir/integer 1 "f9ff6b7c"
      #fhir/integer{:id "foo"} "667e7a1b"
      #fhir/integer{:id "foo" :value 0} "fdd4f126"
      #fhir/integer{:extension [#fhir/Extension{:url "foo"}]} "b353ef83"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/integer 0 24
      #fhir/integer{:id "foo"} 88))

  (testing "references"
    (is (empty? (type/references #fhir/integer 0))))

  (testing "print"
    (are [x s] (= (pr-str x) s)
      #fhir/integer 0 "#fhir/integer 0"
      #fhir/integer{:id "foo"} "#fhir/integer{:id \"foo\"}")))

(deftest string-test
  (testing "string?"
    (are [x] (type/string? x)
      #fhir/string ""
      #fhir/string{:id "foo"}))

  (testing "type"
    (are [x] (= :fhir/string (:fhir/type x))
      #fhir/string ""
      #fhir/string{:id "foo"}))

  (testing "string"
    (is (= #fhir/string{:value "181312"} #fhir/string "181312")))

  (testing "interning"
    (are [x y] (interned? x y)
      (type/string {:extension [internable-extension]})
      (type/string {:extension [internable-extension]})

      #fhir/string "1234" #fhir/string "1234"

      #fhir/string "1234" (assoc #fhir/string "5678" :value "1234"))

    (are [x y] (not-interned? x y)
      #fhir/string "165645" #fhir/string "165645"

      (type/string {:extension [internable-extension] :value "174230"})
      (type/string {:extension [internable-extension] :value "174230"})))

  (testing "assoc id"
    (are [s id r] (= r (assoc s :id id))
      #fhir/string "165645" "id-111030"
      #fhir/string{:id "id-111030" :value "165645"}

      #fhir/string{:id "foo"} "bar"
      #fhir/string{:id "bar"}

      #fhir/string{:extension [#fhir/Extension{:url "foo"}]} "id-111902"
      #fhir/string{:id "id-111902" :extension [#fhir/Extension{:url "foo"}]}))

  (testing "assoc extension"
    (testing "non-extended"
      (is (= (assoc #fhir/string "165645" :extension [#fhir/Extension{:url "foo"}])
             #fhir/string{:extension [#fhir/Extension{:url "foo"}] :value "165645"})))

    (testing "already extended"
      (is (= (assoc #fhir/string{:id "id-111953"} :extension [#fhir/Extension{:url "foo"}])
             #fhir/string{:id "id-111953" :extension [#fhir/Extension{:url "foo"}]}))
      (is (= (assoc #fhir/string{:extension [#fhir/Extension{:url "foo"}]} :extension [#fhir/Extension{:url "bar"}])
             #fhir/string{:extension [#fhir/Extension{:url "bar"}]}))))

  (testing "value"
    (are [x] (= "175227" (:value x))
      #fhir/string "175227"
      #fhir/string{:value "175227"}))

  (testing "assoc value"
    (is (= #fhir/string "bar" (assoc #fhir/string "foo" :value "bar"))))

  (testing "to-json"
    (is (= "105406" (gen-json-value #fhir/string "105406"))))

  (testing "to-xml"
    (is (= (sexp-value "121344") (type/to-xml #fhir/string "121344"))))

  (testing "equals"
    (is (= #fhir/string "foo" #fhir/string{:value "foo"})))

  (testing "hash-into"
    (are [s hex] (= hex (murmur3 s))
      #fhir/string "" "126916b"
      #fhir/string "foo" "ba7851a6"
      #fhir/string{:value "foo"} "ba7851a6"
      #fhir/string{:id "foo"} "88650112"
      #fhir/string{:id "foo" :value "foo"} "28b14e8f"
      #fhir/string{:extension [#fhir/Extension{:url "foo"}]} "b2f98d95"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/string{} 0
      #fhir/string{:id "foo"} 80
      #fhir/string{:extension [#fhir/Extension{:url "foo"}]} 0
      #fhir/string "" 0
      #fhir/string "1234" 0
      #fhir/string "12345" 64
      #fhir/string "123456" 64
      #fhir/string "1234567" 64
      #fhir/string "12345678" 64
      #fhir/string "123456789" 64
      #fhir/string "1234567890" 64
      #fhir/string "12345678901" 64
      #fhir/string "123456789012" 64
      #fhir/string "1234567890123" 72))

  (testing "references"
    (is (empty? (type/references #fhir/string "151736"))))

  (testing "print"
    (are [x s] (= (pr-str x) s)
      #fhir/string "1234" "#fhir/string-interned \"1234\""
      #fhir/string-interned "12345" "#fhir/string-interned \"12345\""
      #fhir/string "142600" "#fhir/string \"142600\""
      #fhir/string{:id "0"} "#fhir/string{:id \"0\"}"
      #fhir/string{:extension [#fhir/Extension{:url "foo"}]} "#fhir/string-interned{:extension [#fhir/Extension{:url \"foo\"}]}")))

(deftest decimal-test
  (testing "decimal?"
    (are [x] (type/decimal? x)
      #fhir/decimal 1M
      #fhir/decimal{:id "foo"}))

  (testing "type"
    (are [x] (= :fhir/decimal (:fhir/type x))
      #fhir/decimal 1M
      #fhir/decimal{:id "foo"}))

  (testing "Decimal"
    (is (= #fhir/decimal{:value 1M} #fhir/decimal 1M)))

  (testing "interning"
    (is (not-interned? #fhir/decimal 165746M #fhir/decimal 165746M)))

  (testing "assoc id"
    (testing "non-extended"
      (is (= (assoc #fhir/decimal 1M :id "id-111030")
             #fhir/decimal{:id "id-111030" :value 1M})))

    (testing "already extended"
      (is (= (assoc #fhir/decimal{:id "foo"} :id "bar")
             #fhir/decimal{:id "bar"}))
      (is (= (assoc #fhir/decimal{:extension [#fhir/Extension{:url "foo"}]} :id "id-111902")
             #fhir/decimal{:id "id-111902" :extension [#fhir/Extension{:url "foo"}]}))))

  (testing "assoc extension"
    (testing "non-extended"
      (is (= (assoc #fhir/decimal 1M :extension [#fhir/Extension{:url "foo"}])
             #fhir/decimal{:extension [#fhir/Extension{:url "foo"}] :value 1M})))

    (testing "already extended"
      (is (= (assoc #fhir/decimal{:id "id-111953"} :extension [#fhir/Extension{:url "foo"}])
             #fhir/decimal{:id "id-111953" :extension [#fhir/Extension{:url "foo"}]}))
      (is (= (assoc #fhir/decimal{:extension [#fhir/Extension{:url "foo"}]} :extension [#fhir/Extension{:url "bar"}])
             #fhir/decimal{:extension [#fhir/Extension{:url "bar"}]}))))

  (testing "value"
    (are [x] (= 1M (:value x))
      #fhir/decimal 1M
      #fhir/decimal{:id "foo" :value 1M}))

  (testing "assoc value"
    (is (= #fhir/decimal 2M (assoc #fhir/decimal 1M :value 2M))))

  (testing "merge"
    (is (= #fhir/decimal 2M (merge #fhir/decimal 1M {:value 2M})))
    (is (= #fhir/decimal{:id "id-153510" :value 2M} (merge #fhir/decimal 1M {:id "id-153510" :value 2M}))))

  (testing "to-json"
    (are [decimal json] (= json (gen-json-value decimal))
      #fhir/decimal 1M 1
      #fhir/decimal 1.1M 1.1M))

  (testing "to-xml"
    (is (= (sexp-value "1.1") (type/to-xml #fhir/decimal 1.1M))))

  (testing "hash-into"
    (are [d hex] (= hex (murmur3 d))
      #fhir/decimal 0M "7e564b82"
      #fhir/decimal 1M "f2f4ddc7"
      #fhir/decimal{:value 1M} "f2f4ddc7"
      #fhir/decimal{:id "foo"} "86b1bd0c"
      #fhir/decimal{:id "foo" :value 0M} "4e9f9211"
      #fhir/decimal{:extension [#fhir/Extension{:url "foo"}]} "df35c8c9"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/decimal 0M 48
      #fhir/decimal{:id "foo"} 80))

  (testing "references"
    (is (empty? (type/references #fhir/decimal 0M))))

  (testing "print"
    (are [x s] (= (pr-str x) s)
      #fhir/decimal 0M "#fhir/decimal 0M"
      #fhir/decimal{:id "foo"} "#fhir/decimal{:id \"foo\"}")))

(deftest uri-test
  (testing "uri?"
    (are [x] (type/uri? x)
      #fhir/uri ""
      #fhir/uri{:id "foo"}))

  (testing "type"
    (are [x] (= :fhir/uri (:fhir/type x))
      #fhir/uri ""
      #fhir/uri{:id "foo"}))

  (testing "uri"
    (is (= #fhir/uri{:value "181424"} #fhir/uri "181424")))

  (testing "interning"
    (is (interned? #fhir/uri-interned "165823" #fhir/uri-interned "165823"))
    (is (interned? (type/uri-interned {:extension [] :value "145932"})
                   (type/uri-interned "145932")))

    (testing "with extension"
      (are [x y] (interned? x y)
        (type/uri-interned {:extension [internable-extension]})
        (type/uri-interned {:extension [internable-extension]})

        (type/uri-interned {:extension [internable-extension] :value "185838"})
        (type/uri-interned {:extension [internable-extension] :value "185838"}))

      (are [x y] (not-interned? x y)
        (type/uri-interned {:extension [not-internable-extension]})
        (type/uri-interned {:extension [not-internable-extension]})

        (type/uri-interned {:extension [not-internable-extension] :value "185838"})
        (type/uri-interned {:extension [not-internable-extension] :value "185838"}))))

  (testing "assoc id"
    (testing "non-extended"
      (is (= (assoc #fhir/uri "165645" :id "id-111030")
             #fhir/uri{:id "id-111030" :value "165645"})))

    (testing "already extended"
      (is (= (assoc #fhir/uri{:id "foo"} :id "bar")
             #fhir/uri{:id "bar"}))
      (is (= (assoc #fhir/uri{:extension [#fhir/Extension{:url "foo"}]} :id "id-111902")
             #fhir/uri{:id "id-111902" :extension [#fhir/Extension{:url "foo"}]}))))

  (testing "assoc extension"
    (testing "non-extended"
      (is (= (assoc #fhir/uri "165645" :extension [#fhir/Extension{:url "foo"}])
             #fhir/uri{:extension [#fhir/Extension{:url "foo"}] :value "165645"})))

    (testing "already extended"
      (is (= (assoc #fhir/uri{:id "id-111953"} :extension [#fhir/Extension{:url "foo"}])
             #fhir/uri{:id "id-111953" :extension [#fhir/Extension{:url "foo"}]}))
      (is (= (assoc #fhir/uri{:extension [#fhir/Extension{:url "foo"}]} :extension [#fhir/Extension{:url "bar"}])
             #fhir/uri{:extension [#fhir/Extension{:url "bar"}]}))))

  (testing "value"
    (are [x] (= "105614" (:value x) (:value x) (:value x ::foo))
      #fhir/uri "105614"
      #fhir/uri{:id "foo" :value "105614"}))

  (testing "assoc value"
    (is (= #fhir/uri "bar" (assoc #fhir/uri "foo" :value "bar"))))

  (testing "lookup"
    (testing "other keys are not found"
      (is (= ::not-found (::other-key #fhir/uri "foo" ::not-found)))))

  (testing "to-json"
    (is (= "105846" (gen-json-value #fhir/uri "105846"))))

  (testing "to-xml"
    (is (= (sexp-value "105846") (type/to-xml #fhir/uri "105846"))))

  (testing "equals"
    (is (= #fhir/uri "142334" #fhir/uri "142334"))
    (is (not= #fhir/uri "142334" #fhir/uri "215930"))
    (is (not= #fhir/uri "142334" "142334")))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/uri "" "51a99a01"
      #fhir/uri "foo" "dc60f982"
      #fhir/uri{:value "foo"} "dc60f982"
      #fhir/uri{:id "foo"} "7c797680"
      #fhir/uri{:id "foo" :value "foo"} "52e1c640"
      #fhir/uri{:extension [#fhir/Extension{:url "foo"}]} "435d07d9"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/uri "" 0
      #fhir/uri{:id "foo"} 80))

  (testing "references"
    (is (empty? (type/references #fhir/uri "151758"))))

  (testing "print"
    (are [uri s] (= (pr-str uri) s)
      #fhir/uri "1426" "#fhir/uri-interned \"1426\""
      #fhir/uri-interned "142600" "#fhir/uri-interned \"142600\""
      #fhir/uri "142600" "#fhir/uri \"142600\""
      #fhir/uri{:id "0"} "#fhir/uri{:id \"0\"}"
      #fhir/uri{:extension [#fhir/Extension{:url "foo"}]} "#fhir/uri-interned{:extension [#fhir/Extension{:url \"foo\"}]}")))

(deftest url-test
  (testing "url?"
    (are [x] (type/url? x)
      #fhir/url ""
      #fhir/url{}))

  (testing "type"
    (are [x] (= :fhir/url (:fhir/type x))
      #fhir/url ""
      #fhir/url{}))

  (testing "interning"
    (is (not-interned? #fhir/url "165852" #fhir/url "165852"))

    (testing "with extension"
      (are [x y] (not-interned? x y)
        (type/url {:extension [internable-extension] :value "185838"})
        (type/url {:extension [internable-extension] :value "185838"}))

      (are [x y] (interned? x y)
        (type/url {:extension [internable-extension]})
        (type/url {:extension [internable-extension]}))))

  (testing "assoc id"
    (testing "non-extended"
      (is (= (assoc #fhir/url "165645" :id "id-111030")
             #fhir/url{:id "id-111030" :value "165645"})))

    (testing "already extended"
      (is (= (assoc #fhir/url{:id "foo"} :id "bar")
             #fhir/url{:id "bar"}))
      (is (= (assoc #fhir/url{:extension [#fhir/Extension{:url "foo"}]} :id "id-111902")
             #fhir/url{:id "id-111902" :extension [#fhir/Extension{:url "foo"}]}))))

  (testing "assoc extension"
    (testing "non-extended"
      (is (= (assoc #fhir/url "165645" :extension [#fhir/Extension{:url "foo"}])
             #fhir/url{:extension [#fhir/Extension{:url "foo"}] :value "165645"})))

    (testing "already extended"
      (is (= (assoc #fhir/url{:id "id-111953"} :extension [#fhir/Extension{:url "foo"}])
             #fhir/url{:id "id-111953" :extension [#fhir/Extension{:url "foo"}]}))
      (is (= (assoc #fhir/url{:extension [#fhir/Extension{:url "foo"}]} :extension [#fhir/Extension{:url "bar"}])
             #fhir/url{:extension [#fhir/Extension{:url "bar"}]}))))

  (testing "value"
    (are [x] (= "105614" (:value x) (:value x) (:value x ::foo))
      #fhir/url "105614"
      #fhir/url{:id "foo" :value "105614"}))

  (testing "assoc value"
    (is (= #fhir/url "bar" (assoc #fhir/url "foo" :value "bar"))))

  (testing "lookup"
    (testing "other keys are not found"
      (is (= ::not-found (::other-key #fhir/url "foo" ::not-found)))))

  (testing "to-json"
    (is (= "105846" (gen-json-value #fhir/url "105846"))))

  (testing "to-xml"
    (is (= (sexp-value "105846") (type/to-xml #fhir/url "105846"))))

  (testing "equals"
    (is (let [url #fhir/url "142334"] (= url url)))
    (is (= #fhir/url "142334" #fhir/url "142334"))
    (is (not= #fhir/url "142334" #fhir/url "220025"))
    (is (not= #fhir/url "142334" "142334")))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/url "" "39b10d82"
      #fhir/url "foo" "7acc4e54"
      #fhir/url{:id "foo"} "78133d84"
      #fhir/url{:id "foo" :value "foo"} "43940bd2"
      #fhir/url{:extension [#fhir/Extension{:url "foo"}]} "95f50bf4"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/url "" 56
      #fhir/url "1234" 56
      #fhir/url "12345" 64
      #fhir/url{:id "foo"} 80))

  (testing "references"
    (is (empty? (type/references #fhir/url "151809"))))

  (testing "print"
    (are [x s] (= (pr-str x) s)
      #fhir/url "142600"
      "#fhir/url \"142600\""

      #fhir/url{:id "id-191655"}
      "#fhir/url{:id \"id-191655\"}"

      #fhir/url{:id "id-191655" :value "191802"}
      "#fhir/url{:id \"id-191655\" :value \"191802\"}"

      #fhir/url{:extension [#fhir/Extension{:url "url-191551"}]}
      "#fhir/url{:extension [#fhir/Extension{:url \"url-191551\"}]}")))

(deftest canonical-test
  (testing "canonical?"
    (are [x] (type/canonical? x)
      #fhir/canonical ""
      #fhir/canonical{:id "foo"}))

  (testing "type"
    (are [x] (= :fhir/canonical (:fhir/type x))
      #fhir/canonical ""
      #fhir/canonical{:id "foo"}))

  (testing "canonical"
    (is (= #fhir/canonical{:value "182040"} #fhir/canonical "182040")))

  (testing "interning"
    (is (interned? #fhir/canonical "165936" #fhir/canonical "165936"))

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

  (testing "assoc id"
    (testing "non-extended"
      (is (= (assoc #fhir/canonical "165645" :id "id-111030")
             #fhir/canonical{:id "id-111030" :value "165645"})))

    (testing "already extended"
      (is (= (assoc #fhir/canonical{:id "foo"} :id "bar")
             #fhir/canonical{:id "bar"}))
      (is (= (assoc #fhir/canonical{:extension [#fhir/Extension{:url "foo"}]} :id "id-111902")
             #fhir/canonical{:id "id-111902" :extension [#fhir/Extension{:url "foo"}]}))))

  (testing "assoc extension"
    (testing "non-extended"
      (is (= (assoc #fhir/canonical "165645" :extension [#fhir/Extension{:url "foo"}])
             #fhir/canonical{:extension [#fhir/Extension{:url "foo"}] :value "165645"})))

    (testing "already extended"
      (is (= (assoc #fhir/canonical{:id "id-111953"} :extension [#fhir/Extension{:url "foo"}])
             #fhir/canonical{:id "id-111953" :extension [#fhir/Extension{:url "foo"}]}))
      (is (= (assoc #fhir/canonical{:extension [#fhir/Extension{:url "foo"}]} :extension [#fhir/Extension{:url "bar"}])
             #fhir/canonical{:extension [#fhir/Extension{:url "bar"}]}))))

  (testing "value"
    (are [x] (= "105614" (:value x) (:value x) (:value x ::foo))
      #fhir/canonical "105614"
      #fhir/canonical{:id "foo" :value "105614"}))

  (testing "assoc value"
    (is (= #fhir/canonical "bar" (assoc #fhir/canonical "foo" :value "bar"))))

  (testing "lookup"
    (testing "other keys are not found"
      (is (= ::not-found (::other-key #fhir/canonical "foo" ::not-found)))))

  (testing "to-json"
    (is (= "105846" (gen-json-value #fhir/canonical "105846"))))

  (testing "to-xml"
    (is (= (sexp-value "105846") (type/to-xml #fhir/canonical "105846"))))

  (testing "equals"
    (is (= #fhir/canonical "142334" #fhir/canonical "142334"))
    (is (not= #fhir/canonical "142334" #fhir/canonical "220056"))
    (is (not= #fhir/canonical "142334" "142334")))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/canonical "" "53c915fd"
      #fhir/canonical "foo" "e42e9c7e"
      #fhir/canonical{:id "foo"} "b039419d"
      #fhir/canonical{:id "foo" :value "foo"} "83587524"
      #fhir/canonical{:extension [#fhir/Extension{:url "foo"}]} "3f1c8be1"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/canonical "" 0
      #fhir/canonical{:id "foo"} 80
      #fhir/canonical{:id "foo" :value ""} 152))

  (testing "references"
    (is (empty? (type/references #fhir/canonical "151819"))))

  (testing "print"
    (are [c s] (= s (pr-str c))
      #fhir/canonical "142600"
      "#fhir/canonical \"142600\""

      #fhir/canonical{:id "211202"}
      "#fhir/canonical{:id \"211202\"}"

      #fhir/canonical{:value "213644"}
      "#fhir/canonical \"213644\"")))

(deftest base64Binary-test
  (testing "base64Binary?"
    (are [x] (type/base64Binary? x)
      #fhir/base64Binary ""
      #fhir/base64Binary{:id "foo"}))

  (testing "type"
    (are [x] (= :fhir/base64Binary (:fhir/type x))
      #fhir/base64Binary ""
      #fhir/base64Binary{:id "foo"}))

  (testing "base64Binary"
    (is (= #fhir/base64Binary{:value "MTA1NjE0Cg=="} #fhir/base64Binary "MTA1NjE0Cg==")))

  (testing "interning"
    (is (not-interned? #fhir/base64Binary "MTA1NjE0Cg==" #fhir/base64Binary "MTA1NjE0Cg=="))

    (testing "with extension"
      (are [x y] (not-interned? x y)
        (type/base64Binary {:extension [internable-extension] :value "MTA1NjE0Cg=="})
        (type/base64Binary {:extension [internable-extension] :value "MTA1NjE0Cg=="}))

      (are [x y] (interned? x y)
        (type/base64Binary {:extension [internable-extension]})
        (type/base64Binary {:extension [internable-extension]}))))

  (testing "assoc id"
    (testing "non-extended"
      (is (= (assoc #fhir/base64Binary "MTA1NjE0Cg==" :id "id-111030")
             #fhir/base64Binary{:id "id-111030" :value "MTA1NjE0Cg=="})))

    (testing "already extended"
      (is (= (assoc #fhir/base64Binary{:id "foo"} :id "bar")
             #fhir/base64Binary{:id "bar"}))
      (is (= (assoc #fhir/base64Binary{:extension [#fhir/Extension{:url "foo"}]} :id "id-111902")
             #fhir/base64Binary{:id "id-111902" :extension [#fhir/Extension{:url "foo"}]}))))

  (testing "assoc extension"
    (testing "non-extended"
      (is (= (assoc #fhir/base64Binary "MTA1NjE0Cg==" :extension [#fhir/Extension{:url "foo"}])
             #fhir/base64Binary{:extension [#fhir/Extension{:url "foo"}] :value "MTA1NjE0Cg=="})))

    (testing "already extended"
      (is (= (assoc #fhir/base64Binary{:id "id-111953"} :extension [#fhir/Extension{:url "foo"}])
             #fhir/base64Binary{:id "id-111953" :extension [#fhir/Extension{:url "foo"}]}))
      (is (= (assoc #fhir/base64Binary{:extension [#fhir/Extension{:url "foo"}]} :extension [#fhir/Extension{:url "bar"}])
             #fhir/base64Binary{:extension [#fhir/Extension{:url "bar"}]}))))

  (testing "value"
    (are [x] (= "MTA1NjE0Cg==" (:value x) (:value x) (:value x ::foo))
      #fhir/base64Binary "MTA1NjE0Cg=="
      #fhir/base64Binary{:id "foo" :value "MTA1NjE0Cg=="}))

  (testing "assoc value"
    (is (= #fhir/base64Binary "bar" (assoc #fhir/base64Binary "foo" :value "bar"))))

  (testing "lookup"
    (testing "other keys are not found"
      (is (= ::not-found (::other-key #fhir/base64Binary "foo" ::not-found)))))

  (testing "to-json"
    (is (= "MTA1NjE0Cg==" (gen-json-value #fhir/base64Binary "MTA1NjE0Cg=="))))

  (testing "to-xml"
    (is (= (sexp-value "MTA1NjE0Cg==") (type/to-xml #fhir/base64Binary "MTA1NjE0Cg=="))))

  (testing "equals"
    (is (let [base64Binary #fhir/base64Binary "MTA1NjE0Cg=="] (= base64Binary base64Binary)))
    (is (= #fhir/base64Binary "MTA1NjE0Cg==" #fhir/base64Binary "MTA1NjE0Cg=="))
    (is (not= #fhir/base64Binary "MTA1NjE0Cg==" #fhir/base64Binary "YQo="))
    (is (not= #fhir/base64Binary "MTA1NjE0Cg==" "MTA1NjE0Cg==")))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/base64Binary "" "339ff20a"
      #fhir/base64Binary "YQo=" "ed565602"
      #fhir/base64Binary "MTA1NjE0Cg===" "24568b10"
      #fhir/base64Binary{:id "foo"} "331c84dc"
      #fhir/base64Binary{:extension [#fhir/Extension{:url "foo"}]} "4d9fc231"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/base64Binary "" 56
      #fhir/base64Binary "YQo" 56
      #fhir/base64Binary "MTA1NjE0Cg===" 72
      #fhir/base64Binary{:id "foo"} 80))

  (testing "references"
    (is (empty? (type/references #fhir/base64Binary "YQo="))))

  (testing "print"
    (are [x s] (= (pr-str x) s)
      #fhir/base64Binary "YQo" "#fhir/base64Binary \"YQo\""
      #fhir/base64Binary{:id "foo"} "#fhir/base64Binary{:id \"foo\"}")))

(deftest instant-test
  (testing "instant?"
    (are [x] (type/instant? x)
      #fhir/instant #system/date-time "1970-01-02T00:00:00Z"
      #fhir/instant #system/date-time "1970-01-02T00:00:00+01:00"
      #fhir/instant{:id "foo"}
      #fhir/instant{:value #system/date-time "1970-01-02T00:00:00Z"}
      #fhir/instant{:value #system/date-time "1970-01-02T00:00:00+01:00"}))

  (testing "type"
    (are [x] (= :fhir/instant (:fhir/type x))
      #fhir/instant #system/date-time "1970-01-02T00:00:00Z"
      #fhir/instant #system/date-time "1970-01-02T00:00:00+01:00"
      #fhir/instant{:id "foo"}
      #fhir/instant{:value #system/date-time "1970-01-02T00:00:00Z"}
      #fhir/instant{:value #system/date-time "1970-01-02T00:00:00+01:00"}))

  (testing "with extension"
    (testing "without value"
      (is (nil? (:value #fhir/instant{:extension [#fhir/Extension{:url "url-130945"}]})))))

  (testing "instant"
    (is (= #fhir/instant{:value #system/date-time "1970-01-02T00:00:00Z"}
           #fhir/instant #system/date-time "1970-01-02T00:00:00Z")))

  (testing "interning"
    (is (not-interned? #fhir/instant #system/date-time "2020-01-01T00:00:00+02:00"
                       #fhir/instant #system/date-time "2020-01-01T00:00:00+02:00"))

    (is (not-interned? #fhir/instant #system/date-time "1970-01-02T00:00:00Z"
                       #fhir/instant #system/date-time "1970-01-02T00:00:00Z"))

    (testing "with extension"
      (are [x y] (not-interned? x y)
        (type/instant {:extension [internable-extension]
                       :value #system/date-time "1970-01-02T00:00:00Z"})
        (type/instant {:extension [internable-extension]
                       :value #system/date-time "1970-01-02T00:00:00Z"})

        (type/instant {:extension [not-internable-extension]})
        (type/instant {:extension [not-internable-extension]}))

      (are [x y] (interned? x y)
        (type/instant {:extension [internable-extension]})
        (type/instant {:extension [internable-extension]}))))

  (testing "assoc id"
    (testing "non-extended"
      (is (= (assoc #fhir/instant #system/date-time "1970-01-02T00:00:00Z" :id "id-111030")
             #fhir/instant{:id "id-111030" :value #system/date-time "1970-01-02T00:00:00Z"})))

    (testing "already extended"
      (is (= (assoc #fhir/instant{:id "foo"} :id "bar")
             #fhir/instant{:id "bar"}))
      (is (= (assoc #fhir/instant{:extension [#fhir/Extension{:url "foo"}]} :id "id-111902")
             #fhir/instant{:id "id-111902" :extension [#fhir/Extension{:url "foo"}]}))))

  (testing "assoc extension"
    (testing "non-extended"
      (is (= (assoc #fhir/instant #system/date-time "1970-01-02T00:00:00Z" :extension [#fhir/Extension{:url "foo"}])
             #fhir/instant{:extension [#fhir/Extension{:url "foo"}] :value #system/date-time "1970-01-02T00:00:00Z"})))

    (testing "already extended"
      (is (= (assoc #fhir/instant{:id "id-111953"} :extension [#fhir/Extension{:url "foo"}])
             #fhir/instant{:id "id-111953" :extension [#fhir/Extension{:url "foo"}]}))
      (is (= (assoc #fhir/instant{:extension [#fhir/Extension{:url "foo"}]} :extension [#fhir/Extension{:url "bar"}])
             #fhir/instant{:extension [#fhir/Extension{:url "bar"}]}))))

  (testing "value"
    (are [x] (= #system/date-time "2020-01-01T00:00:00+02:00" (:value x) (:value x) (:value x ::foo))
      #fhir/instant #system/date-time "2020-01-01T00:00:00+02:00"
      #fhir/instant{:id "foo" :value #system/date-time "2020-01-01T00:00:00+02:00"})

    (are [x] (= #system/date-time "1970-01-01T00:00:00Z" (:value x) (:value x) (:value x ::foo))
      #fhir/instant #system/date-time "1970-01-01T00:00:00Z"
      #fhir/instant{:id "foo" :value #system/date-time "1970-01-01T00:00:00Z"}))

  (testing "assoc value"
    (is (= #fhir/instant #system/date-time "1970-01-02T00:00:00Z" (assoc #fhir/instant #system/date-time "2020-01-01T00:00:00+02:00" :value #system/date-time "1970-01-02T00:00:00Z"))))

  (testing "to-json"
    (are [instant json] (= json (gen-json-value instant))
      #fhir/instant #system/date-time "2020-01-01T00:00:00.123456789+02:00" "2020-01-01T00:00:00.123456789+02:00"
      #fhir/instant #system/date-time "2020-01-01T00:00:00+02:00" "2020-01-01T00:00:00+02:00"
      #fhir/instant #system/date-time "1970-01-01T00:00:00Z" "1970-01-01T00:00:00Z"))

  (testing "to-xml"
    (is (= (sexp-value "2020-01-01T00:00:00+02:00")
           (type/to-xml #fhir/instant #system/date-time "2020-01-01T00:00:00+02:00")))
    (is (= (sexp-value "1970-01-01T00:00:00Z")
           (type/to-xml #fhir/instant #system/date-time "1970-01-01T00:00:00Z"))))

  (testing "equals"
    (is (let [instant #fhir/instant #system/date-time "2020-01-01T00:00:00+02:00"] (= instant instant)))
    (is (= #fhir/instant #system/date-time "2020-01-01T00:00:00+02:00"
           #fhir/instant #system/date-time "2020-01-01T00:00:00+02:00"))
    (is (not= #fhir/instant #system/date-time "2020-01-01T00:00:00+01:00"
              #fhir/instant #system/date-time "2020-01-01T00:00:00+02:00"))
    (is (= #fhir/instant #system/date-time "1970-01-01T00:00:00Z" #fhir/instant #system/date-time "1970-01-01T00:00:00Z")))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/instant #system/date-time "2020-01-01T00:00:00+00:00" "d81f6bc2"
      #fhir/instant #system/date-time "2020-01-01T00:00:00+01:00" "4225df0d"
      #fhir/instant #system/date-time "2020-01-01T00:00:00Z" "d81f6bc2"
      #fhir/instant #system/date-time "1970-01-01T00:00:00Z" "93344244"
      #fhir/instant{:value #system/date-time "1970-01-01T00:00:00Z"} "93344244"
      #fhir/instant{:id "foo"} "b4705bd6"
      #fhir/instant{:id "foo" :value #system/date-time "1970-01-01T00:00:00Z"} "6ae7daa"
      #fhir/instant{:extension [#fhir/Extension{:url "foo"}]} "8a7f7ddc"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/instant #system/date-time "2020-01-01T00:00:00+00:00" 80
      #fhir/instant{:id "foo"} 80))

  (testing "references"
    (is (empty? (type/references #fhir/instant #system/date-time "1970-01-01T00:00:00Z"))))

  (testing "print"
    (are [i s] (= s (pr-str i))
      #fhir/instant #system/date-time "2020-01-01T00:00:00Z"
      "#fhir/instant #system/date-time \"2020-01-01T00:00:00Z\""

      #fhir/instant #system/date-time "2020-01-01T00:00:00+01:00"
      "#fhir/instant #system/date-time \"2020-01-01T00:00:00+01:00\""

      #fhir/instant{:id "211213"}
      "#fhir/instant{:id \"211213\"}"

      #fhir/instant{:value #system/date-time "2020-01-01T00:00:00Z"}
      "#fhir/instant #system/date-time \"2020-01-01T00:00:00Z\"")))

(deftest date-test
  (testing "with year precision"
    (testing "date?"
      (are [x] (type/date? x)
        #fhir/date #system/date "0001"
        #fhir/date #system/date "9999"
        #fhir/date #system/date "2022"
        #fhir/date{:id "foo"}))

    (testing "type"
      (are [x] (= :fhir/date (:fhir/type x))
        #fhir/date #system/date "2022"
        #fhir/date{:id "foo"}))

    (testing "date"
      (is (= #fhir/date{:value #system/date "2022"} #fhir/date #system/date "2022"))
      (is (= #fhir/date{:value #system/date "2022"} #fhir/date #system/date "2022")))

    (testing "interning"
      (is (not-interned? #fhir/date #system/date "2020" #fhir/date #system/date "2020"))

      (testing "with extension"
        (are [x y] (not-interned? x y)
          (type/date {:extension [internable-extension] :value #system/date "2022"})
          (type/date {:extension [internable-extension] :value #system/date "2022"})

          (type/date {:id "id-164735" :extension [internable-extension]})
          (type/date {:id "id-164735" :extension [internable-extension]})

          (type/date {:extension [not-internable-extension]})
          (type/date {:extension [not-internable-extension]}))

        (are [x y] (interned? x y)
          (type/date {:extension [internable-extension]})
          (type/date {:extension [internable-extension]}))))

    (testing "assoc id"
      (testing "non-extended"
        (is (= (assoc #fhir/date #system/date "2020" :id "id-111030")
               #fhir/date{:id "id-111030" :value #system/date "2020"})))

      (testing "already extended"
        (is (= (assoc #fhir/date{:id "foo"} :id "bar")
               #fhir/date{:id "bar"}))
        (is (= (assoc #fhir/date{:extension [#fhir/Extension{:url "foo"}]} :id "id-111902")
               #fhir/date{:id "id-111902" :extension [#fhir/Extension{:url "foo"}]}))))

    (testing "assoc extension"
      (testing "non-extended"
        (is (= (assoc #fhir/date #system/date "2020" :extension [#fhir/Extension{:url "foo"}])
               #fhir/date{:extension [#fhir/Extension{:url "foo"}] :value #system/date "2020"})))
      (testing "already extended"
        (is (= (assoc #fhir/date{:id "id-111953"} :extension [#fhir/Extension{:url "foo"}])
               #fhir/date{:id "id-111953" :extension [#fhir/Extension{:url "foo"}]}))      (is (= (assoc #fhir/date{:extension [#fhir/Extension{:url "foo"}]} :extension [#fhir/Extension{:url "bar"}])
                                                                                                  #fhir/date{:extension [#fhir/Extension{:url "bar"}]}))))

    (testing "value"
      (are [x] (= #system/date "2020" (:value x) (:value x) (:value x ::foo))
        #fhir/date #system/date "2020"
        #fhir/date{:id "foo" :value #system/date "2020"}))

    (testing "assoc value"
      (testing "non-extended"
        (is (= (assoc #fhir/date #system/date "2020" :value #system/date "2022")
               #fhir/date #system/date "2022"))
        (is (= (assoc #fhir/date #system/date "2020" :value #system/date "2022-03")
               #fhir/date #system/date "2022-03"))
        (is (= (assoc #fhir/date #system/date "2020" :value #system/date "2022-03-16")
               #fhir/date #system/date "2022-03-16")))

      (testing "already extended"
        (is (= (assoc #fhir/date{:id "foo"} :value #system/date "2020")
               #fhir/date{:id "foo" :value #system/date "2020"}))))

    (testing "lookup"
      (testing "other keys are not found"
        (is (= ::not-found (::other-key #fhir/date #system/date "1970" ::not-found)))))

    (testing "to-json"
      (are [date json] (= json (gen-json-value date))
        #fhir/date #system/date "0001" "0001"
        #fhir/date #system/date "9999" "9999"
        #fhir/date #system/date "2020" "2020"))

    (testing "to-xml"
      (are [date xml] (= (sexp-value xml) (type/to-xml date))
        #fhir/date #system/date "0001" "0001"
        #fhir/date #system/date "9999" "9999"
        #fhir/date #system/date "2020" "2020"))

    (testing "equals"
      (is (= #fhir/date #system/date "0001" #fhir/date{:id nil :value #system/date "0001"}))
      (is (= #fhir/date #system/date "2020" #fhir/date #system/date "2020"))
      (is (not= #fhir/date #system/date "2020" #fhir/date #system/date "2021"))
      (is (not= #fhir/date #system/date "2020" #system/date "2020")))

    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/date #system/date "2020" "c92be432"
        #fhir/date{:value #system/date "2020"} "c92be432"
        #fhir/date{:id "foo"} "20832903"
        #fhir/date{:id "foo" :value #system/date "2020"} "e983029c"
        #fhir/date{:extension [#fhir/Extension{:url "foo"}]} "707470a9"))

    (testing "mem-size"
      (are [s mem-size] (= mem-size (Base/memSize s))
        #fhir/date #system/date "2020" 32
        #fhir/date{:id "foo"} 80))

    (testing "references"
      (is (empty? (type/references #fhir/date #system/date "2020"))))

    (testing "print"
      (are [x s] (= (pr-str x) s)
        #fhir/date #system/date "2020" "#fhir/date #system/date \"2020\""
        #fhir/date{:id "foo"} "#fhir/date{:id \"foo\"}")))

  (testing "with year-month precision"
    (testing "date?"
      (are [x] (type/date? x)
        #fhir/date #system/date "0001-01"
        #fhir/date #system/date "9999-12"
        #fhir/date #system/date "2022-05"
        #fhir/date{:id "foo"}))

    (testing "type"
      (are [x] (= :fhir/date (:fhir/type x))
        #fhir/date #system/date "2022-05"
        #fhir/date{:id "foo"}))

    (testing "date"
      (is (= #fhir/date{:value #system/date "2022-05"} #fhir/date #system/date "2022-05")))

    (testing "interning"
      (is (not-interned? #fhir/date #system/date "2020-01" #fhir/date #system/date "2020-01")))

    (testing "value"
      (are [x] (= #system/date "2020-01" (:value x) (:value x) (:value x ::foo))
        #fhir/date #system/date "2020-01"
        #fhir/date{:id "foo" :value #system/date "2020-01"}))

    (testing "assoc value"
      (testing "non-extended"
        (is (= (assoc #fhir/date #system/date "2020-01" :value #system/date "2022")
               #fhir/date #system/date "2022"))
        (is (= (assoc #fhir/date #system/date "2020-01" :value #system/date "2022-03")
               #fhir/date #system/date "2022-03"))
        (is (= (assoc #fhir/date #system/date "2020-01" :value #system/date "2022-03-16")
               #fhir/date #system/date "2022-03-16")))

      (testing "already extended"
        (is (= (assoc #fhir/date{:id "foo"} :value #system/date "2020-01")
               #fhir/date{:id "foo" :value #system/date "2020-01"}))))

    (testing "lookup"
      (testing "other keys are not found"
        (is (= ::not-found (::other-key #fhir/date #system/date "1970-01" ::not-found)))))

    (testing "to-json"
      (are [date json] (= json (gen-json-value date))
        #fhir/date #system/date "0001-01" "0001-01"
        #fhir/date #system/date "9999-12" "9999-12"
        #fhir/date #system/date "2020-01" "2020-01"))

    (testing "to-xml"
      (are [date xml] (= (sexp-value xml) (type/to-xml date))
        #fhir/date #system/date "0001-01" "0001-01"
        #fhir/date #system/date "9999-12" "9999-12"
        #fhir/date #system/date "2020-01" "2020-01"))

    (testing "equals"
      (is (= #fhir/date #system/date "2020-01" #fhir/date #system/date "2020-01"))
      (is (not= #fhir/date #system/date "2020-01" #fhir/date #system/date "2020-02"))
      (is (not= #fhir/date #system/date "2020-01" #system/date "2020-01")))

    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/date #system/date "2020-01" "fbcdf97f"
        #fhir/date{:value #system/date "2020-01"} "fbcdf97f"
        #fhir/date{:id "foo"} "20832903"
        #fhir/date{:id "foo" :value #system/date "2020-01"} "4e6aead7"
        #fhir/date{:extension [#fhir/Extension{:url "foo"}]} "707470a9"))

    (testing "mem-size"
      (are [s mem-size] (= mem-size (Base/memSize s))
        #fhir/date #system/date "2020-01" 32
        #fhir/date{:id "foo"} 80))

    (testing "references"
      (is (empty? (type/references #fhir/date #system/date "2020-01"))))

    (testing "print"
      (is (= "#fhir/date #system/date \"2020-01\"" (pr-str #fhir/date #system/date "2020-01")))))

  (testing "with date precision"
    (testing "date?"
      (are [x] (type/date? x)
        #fhir/date #system/date "2022-05-23"
        #fhir/date{:id "foo"}))

    (testing "type"
      (are [x] (= :fhir/date (:fhir/type x))
        #fhir/date #system/date "2022-05-23"
        #fhir/date{:id "foo"}))

    (testing "date"
      (is (= #fhir/date{:value #system/date "2022-05-23"} #fhir/date #system/date "2022-05-23")))

    (testing "interning"
      (is (not-interned? #fhir/date #system/date "2020-01-01" #fhir/date #system/date "2020-01-01")))

    (testing "value"
      (are [x] (= #system/date "2020-01-02" (:value x) (:value x) (:value x ::foo))
        #fhir/date #system/date "2020-01-02"
        #fhir/date{:id "foo" :value #system/date "2020-01-02"}))

    (testing "assoc value"
      (testing "non-extended"
        (is (= (assoc #fhir/date #system/date "2022-05-23" :value #system/date "2022")
               #fhir/date #system/date "2022"))
        (is (= (assoc #fhir/date #system/date "2022-05-23" :value #system/date "2022-03")
               #fhir/date #system/date "2022-03"))
        (is (= (assoc #fhir/date #system/date "2022-05-23" :value #system/date "2022-03-16")
               #fhir/date #system/date "2022-03-16")))

      (testing "already extended"
        (is (= (assoc #fhir/date{:id "foo"} :value #system/date "2022-05-23")
               #fhir/date{:id "foo" :value #system/date "2022-05-23"}))))

    (testing "lookup"
      (testing "other keys are not found"
        (is (= ::not-found (::other-key #fhir/date #system/date "1970-01-01" ::not-found)))))

    (testing "to-json"
      (are [date json] (= json (gen-json-value date))
        #fhir/date #system/date "0001-01-01" "0001-01-01"
        #fhir/date #system/date "9999-12-31" "9999-12-31")

      (satisfies-prop 100
        (prop/for-all [date fg/date-value]
          (= (str date) (gen-json-value (type/date date))))))

    (testing "to-xml"
      (are [date xml] (= (sexp-value xml) (type/to-xml date))
        #fhir/date #system/date "0001-01-01" "0001-01-01"
        #fhir/date #system/date "9999-12-31" "9999-12-31")

      (satisfies-prop 100
        (prop/for-all [date fg/date-value]
          (= (sexp-value (str date)) (type/to-xml (type/date date))))))

    (testing "equals"
      (satisfies-prop 100
        (prop/for-all [date fg/date-value]
          (= date date)))
      (is (not= #fhir/date #system/date "2020-01-01" #fhir/date #system/date "2020-01-02"))
      (is (not= #fhir/date #system/date "2020-01-01" #system/date "2020-01-01")))

    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/date #system/date "2020-01-01" "cd20e081"
        #fhir/date{:value #system/date "2020-01-01"} "cd20e081"
        #fhir/date{:id "foo"} "20832903"
        #fhir/date{:id "foo" :value #system/date "2020-01-01"} "ef736a41"
        #fhir/date{:extension [#fhir/Extension{:url "foo"}]} "707470a9"))

    (testing "mem-size"
      (are [s mem-size] (= mem-size (Base/memSize s))
        #fhir/date #system/date "2020-01-01" 32
        #fhir/date{:id "foo"} 80))

    (testing "references"
      (is (empty? (type/references #fhir/date #system/date "2020-01-01"))))

    (testing "print"
      (is (= "#fhir/date #system/date \"2020-01-01\"" (pr-str #fhir/date #system/date "2020-01-01"))))))

(deftest dateTime-test
  (testing "with year precision"
    (testing "dateTime?"
      (are [x] (type/dateTime? x)
        #fhir/dateTime #system/date-time "0001"
        #fhir/dateTime #system/date-time "9999"
        #fhir/dateTime #system/date-time "2022"
        #fhir/dateTime #system/date-time "2022-01"
        #fhir/dateTime #system/date-time "2022-01-01"
        #fhir/dateTime{:id "foo"}
        #fhir/dateTime{:extension [#fhir/Extension{:url "foo"}]}))

    (testing "type"
      (are [x] (= :fhir/dateTime (:fhir/type x))
        #fhir/dateTime #system/date-time "0001"
        #fhir/dateTime #system/date-time "9999"
        #fhir/dateTime #system/date-time "2022"
        #fhir/dateTime{:id "foo"}))

    (testing "dateTime"
      (is (= #fhir/dateTime{:value #system/date-time "2022"} #fhir/dateTime #system/date-time "2022")))

    (testing "interning"
      (is (not-interned? #fhir/dateTime #system/date-time "2020" #fhir/dateTime #system/date-time "2020"))

      (testing "with extension"
        (are [x y] (not-interned? x y)
          (type/dateTime {:extension [internable-extension] :value #system/date-time "2022"})
          (type/dateTime {:extension [internable-extension] :value #system/date-time "2022"})

          (type/dateTime {:id "id-164735" :extension [internable-extension]})
          (type/dateTime {:id "id-164735" :extension [internable-extension]})

          (type/dateTime {:extension [not-internable-extension]})
          (type/dateTime {:extension [not-internable-extension]}))

        (are [x y] (interned? x y)
          (type/dateTime {:extension [internable-extension]})
          (type/dateTime {:extension [internable-extension]}))))

    (testing "value"
      (are [x] (= #system/date-time "2020" (:value x) (:value x) (:value x ::foo))
        #fhir/dateTime #system/date-time "2020"
        #fhir/dateTime{:id "foo" :value #system/date-time "2020"}))

    (testing "assoc value"
      (testing "non-extended"
        (is (= (assoc #fhir/dateTime #system/date-time "2021" :value #system/date-time "2022")
               #fhir/dateTime #system/date-time "2022"))
        (is (= (assoc #fhir/dateTime #system/date-time "2021" :value #system/date-time "2022-03")
               #fhir/dateTime #system/date-time "2022-03"))
        (is (= (assoc #fhir/dateTime #system/date-time "2021" :value #system/date-time "2022-03-16")
               #fhir/dateTime #system/date-time "2022-03-16")))

      (testing "already extended"
        (is (= (assoc #fhir/dateTime{:id "foo"} :value #system/date-time "2020")
               #fhir/dateTime{:id "foo" :value #system/date-time "2020"}))))

    (testing "lookup"
      (testing "other keys are not found"
        (is (= ::not-found (::other-key #fhir/dateTime #system/date-time "1970" ::not-found)))))

    (testing "to-json"
      (are [date-time json] (= json (gen-json-value date-time))
        #fhir/dateTime #system/date-time "0001" "0001"
        #fhir/dateTime #system/date-time "9999" "9999"
        #fhir/dateTime #system/date-time "2020" "2020")

      (satisfies-prop 100
        (prop/for-all [date-time (fg/dateTime-value)]
          (= (DateTime/toString date-time) (gen-json-value (type/dateTime date-time))))))

    (testing "to-xml"
      (are [date-time xml] (= (sexp-value xml) (type/to-xml date-time))
        #fhir/dateTime #system/date-time "0001" "0001"
        #fhir/dateTime #system/date-time "9999" "9999"
        #fhir/dateTime #system/date-time "2020" "2020"))

    (testing "equals"
      (is (= #fhir/dateTime #system/date-time "2020" #fhir/dateTime #system/date-time "2020"))
      (is (not= #fhir/dateTime #system/date-time "2020" #fhir/dateTime #system/date-time "2021"))
      (is (not= #fhir/dateTime #system/date-time "2020" #system/date-time "2020")))

    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/dateTime #system/date-time "2020" "41e906ff"
        #fhir/dateTime{:value #system/date-time "2020"} "41e906ff"
        #fhir/dateTime{:id "foo"} "fde903da"
        #fhir/dateTime{:id "foo" :value #system/date-time "2020"} "c7361227"
        #fhir/dateTime{:extension [#fhir/Extension{:url "foo"}]} "15062059"))

    (testing "mem-size"
      (are [s mem-size] (= mem-size (Base/memSize s))
        #fhir/dateTime #system/date-time "2020" 32
        #fhir/dateTime{:id "foo"} 80))

    (testing "references"
      (is (empty? (type/references #fhir/dateTime #system/date-time "2020"))))

    (testing "print"
      (are [x s] (= (pr-str x) s)
        #fhir/dateTime #system/date-time "2020" "#fhir/dateTime #system/date-time \"2020\""
        #fhir/dateTime{:id "foo"} "#fhir/dateTime{:id \"foo\"}")))

  (testing "with year-month precision"
    (testing "dateTime?"
      (are [x] (type/dateTime? x)
        #fhir/dateTime #system/date-time "2022-05"
        #fhir/dateTime{:id "foo"}))

    (testing "type"
      (are [x] (= :fhir/dateTime (:fhir/type x))
        #fhir/dateTime #system/date-time "2022-05"
        #fhir/dateTime{:id "foo"}))

    (testing "dateTime"
      (is (= #fhir/dateTime{:value #system/date-time "2022-05"} #fhir/dateTime #system/date-time "2022-05")))

    (testing "interning"
      (is (not-interned? #fhir/dateTime #system/date-time "2022-05" #fhir/dateTime #system/date-time "2022-05")))

    (testing "value"
      (are [x] (= #system/date-time "2020-01" (:value x) (:value x) (:value x ::foo))
        #fhir/dateTime #system/date-time "2020-01"
        #fhir/dateTime{:id "foo" :value #system/date-time "2020-01"}))

    (testing "assoc value"
      (testing "non-extended"
        (is (= (assoc #fhir/dateTime #system/date-time "2021-04" :value #system/date-time "2022")
               #fhir/dateTime #system/date-time "2022"))
        (is (= (assoc #fhir/dateTime #system/date-time "2021-04" :value #system/date-time "2022-03")
               #fhir/dateTime #system/date-time "2022-03"))
        (is (= (assoc #fhir/dateTime #system/date-time "2021-04" :value #system/date-time "2022-03-16")
               #fhir/dateTime #system/date-time "2022-03-16")))

      (testing "already extended"
        (is (= (assoc #fhir/dateTime{:id "foo"} :value #system/date-time "2020-04")
               #fhir/dateTime{:id "foo" :value #system/date-time "2020-04"}))))

    (testing "lookup"
      (testing "other keys are not found"
        (is (= ::not-found (::other-key #fhir/dateTime #system/date-time "1970-01" ::not-found)))))

    (testing "to-json"
      (are [date-time json] (= json (gen-json-value date-time))
        #fhir/dateTime #system/date-time "0001-01" "0001-01"
        #fhir/dateTime #system/date-time "9999-12" "9999-12"
        #fhir/dateTime #system/date-time "2020-01" "2020-01"))

    (testing "to-xml"
      (are [date-time xml] (= (sexp-value xml) (type/to-xml date-time))
        #fhir/dateTime #system/date-time "0001-01" "0001-01"
        #fhir/dateTime #system/date-time "9999-12" "9999-12"
        #fhir/dateTime #system/date-time "2020-01" "2020-01"))

    (testing "equals"
      (is (= #fhir/dateTime #system/date-time "2020-01" #fhir/dateTime #system/date-time "2020-01"))
      (is (not= #fhir/dateTime #system/date-time "2020-01" #fhir/dateTime #system/date-time "2020-02"))
      (is (not= #fhir/dateTime #system/date-time "2020-01" #system/date-time "2020-01")))

    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/dateTime #system/date-time "2020-01" "9d6c5bd3"
        #fhir/dateTime{:value #system/date-time "2020-01"} "9d6c5bd3"
        #fhir/dateTime{:id "foo"} "fde903da"
        #fhir/dateTime{:id "foo" :value #system/date-time "2020-01"} "aa78aa13"
        #fhir/dateTime{:extension [#fhir/Extension{:url "foo"}]} "15062059"))

    (testing "mem-size"
      (are [s mem-size] (= mem-size (Base/memSize s))
        #fhir/dateTime #system/date-time "2020-01" 32
        #fhir/dateTime{:id "foo"} 80))

    (testing "references"
      (is (empty? (type/references #fhir/dateTime #system/date-time "2020-01"))))

    (testing "print"
      (is (= "#fhir/dateTime #system/date-time \"2020-01\"" (pr-str #fhir/dateTime #system/date-time "2020-01")))))

  (testing "with date precision"
    (testing "dateTime?"
      (are [x] (type/dateTime? x)
        #fhir/dateTime #system/date-time "2022-05-23"
        #fhir/dateTime{:id "foo"}))

    (testing "type"
      (are [x] (= :fhir/dateTime (:fhir/type x))
        #fhir/dateTime #system/date-time "2022-05-23"
        #fhir/dateTime{:id "foo"}))

    (testing "dateTime"
      (is (= #fhir/dateTime{:value #system/date-time "2022-05-23"} #fhir/dateTime #system/date-time "2022-05-23")))

    (testing "interning"
      (is (not-interned? #fhir/dateTime #system/date-time "2022-05-23" #fhir/dateTime #system/date-time "2022-05-23")))

    (testing "value"
      (are [x] (= #system/date-time "2022-05-23" (:value x) (:value x) (:value x ::foo))
        #fhir/dateTime #system/date-time "2022-05-23"
        #fhir/dateTime{:id "foo" :value #system/date-time "2022-05-23"}))

    (testing "assoc value"
      (testing "non-extended"
        (is (= (assoc #fhir/dateTime #system/date-time "2022-05-23" :value #system/date-time "2022")
               #fhir/dateTime #system/date-time "2022"))
        (is (= (assoc #fhir/dateTime #system/date-time "2022-05-23" :value #system/date-time "2022-03")
               #fhir/dateTime #system/date-time "2022-03"))
        (is (= (assoc #fhir/dateTime #system/date-time "2022-05-23" :value #system/date-time "2022-03-16")
               #fhir/dateTime #system/date-time "2022-03-16")))

      (testing "already extended"
        (is (= (assoc #fhir/dateTime{:id "foo"} :value #system/date-time "2022-05-23")
               #fhir/dateTime{:id "foo" :value #system/date-time "2022-05-23"}))))

    (testing "lookup"
      (testing "other keys are not found"
        (is (= ::not-found (::other-key #fhir/dateTime #system/date-time "1970-01-01" ::not-found)))))

    (testing "to-json"
      (are [date-time json] (= json (gen-json-value date-time))
        #fhir/dateTime #system/date-time "0001-01-01" "0001-01-01"
        #fhir/dateTime #system/date-time "9999-12-31" "9999-12-31"
        #fhir/dateTime #system/date-time "2020-01-01" "2020-01-01"))

    (testing "to-xml"
      (are [date-time xml] (= (sexp-value xml) (type/to-xml date-time))
        #fhir/dateTime #system/date-time "0001-01-01" "0001-01-01"
        #fhir/dateTime #system/date-time "9999-12-31" "9999-12-31"
        #fhir/dateTime #system/date-time "2020-01-01" "2020-01-01"))

    (testing "equals"
      (is (= #fhir/dateTime #system/date-time "2020-01-01" #fhir/dateTime #system/date-time "2020-01-01")))

    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/dateTime #system/date-time "2020-01-01" "39fe9bdb"
        #fhir/dateTime{:value #system/date-time "2020-01-01"} "39fe9bdb"
        #fhir/dateTime{:id "foo"} "fde903da"
        #fhir/dateTime{:id "foo" :value #system/date-time "2020-01-01"} "7e36d416"
        #fhir/dateTime{:extension [#fhir/Extension{:url "foo"}]} "15062059"))

    (testing "mem-size"
      (are [s mem-size] (= mem-size (Base/memSize s))
        #fhir/dateTime #system/date-time "2020-01-01" 32
        #fhir/dateTime{:id "foo"} 80))

    (testing "references"
      (is (empty? (type/references #fhir/dateTime #system/date-time "2020-01-01"))))

    (testing "print"
      (is (= "#fhir/dateTime #system/date-time \"2020-01-01\"" (pr-str #fhir/dateTime #system/date-time "2020-01-01")))))

  (testing "without timezone"
    (testing "dateTime?"
      (are [x] (type/dateTime? x)
        #fhir/dateTime #system/date-time "0001-01-01T00:00:00"
        #fhir/dateTime #system/date-time "9999-12-31T12:59:59"
        #fhir/dateTime #system/date-time "2020-01-01T00:00:00"
        #fhir/dateTime{:id "foo"}))

    (testing "type"
      (are [x] (= :fhir/dateTime (:fhir/type x))
        #fhir/dateTime #system/date-time "2020-01-01T00:00:00"
        #fhir/dateTime{:id "foo"}))

    (testing "dateTime"
      (is (= #fhir/dateTime{:value #system/date-time "2020-01-01T00:00:00"} #fhir/dateTime #system/date-time "2020-01-01T00:00:00")))

    (testing "interning"
      (is (not-interned? #fhir/dateTime #system/date-time "2020-01-01T00:00:00"
                         #fhir/dateTime #system/date-time "2020-01-01T00:00:00")))

    (testing "to-json"
      (is (= "2020-01-01T00:00:00" (gen-json-value #fhir/dateTime #system/date-time "2020-01-01T00:00:00"))))

    (testing "to-xml"
      (is (= (sexp-value "2020-01-01T00:00:00") (type/to-xml #fhir/dateTime #system/date-time "2020-01-01T00:00:00"))))

    (testing "equals"
      (is (= #fhir/dateTime #system/date-time "2020-01-01T00:00:00"
             #fhir/dateTime #system/date-time "2020-01-01T00:00:00")))

    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/dateTime #system/date-time "2020-01-01T00:00:00" "da537591"
        #fhir/dateTime{:value #system/date-time "2020-01-01T00:00:00"} "da537591"
        #fhir/dateTime{:id "foo" :value #system/date-time "2020-01-01T00:00:00"} "f33b7808"))

    (testing "mem-size"
      (are [s mem-size] (= mem-size (Base/memSize s))
        #fhir/dateTime #system/date-time "2020-01-01T00:00:00" 64
        #fhir/dateTime{:id "foo"} 80))

    (testing "references"
      (is (empty? (type/references #fhir/dateTime #system/date-time "2020-01-01T00:00:00")))))

  (testing "without timezone but millis"
    (testing "dateTime?"
      (are [x] (type/dateTime? x)
        #fhir/dateTime #system/date-time "2020-01-01T00:00:00.000"
        #fhir/dateTime{:id "foo"}))

    (testing "type"
      (are [x] (= :fhir/dateTime (:fhir/type x))
        #fhir/dateTime #system/date-time "2020-01-01T00:00:00.000"
        #fhir/dateTime{:id "foo"}))

    (testing "dateTime"
      (is (= #fhir/dateTime{:value #system/date-time "2020-01-01T00:00:00.000"} #fhir/dateTime #system/date-time "2020-01-01T00:00:00.000")))

    (testing "interning"
      (is (not-interned? #fhir/dateTime #system/date-time "2020-01-01T00:00:00.000"
                         #fhir/dateTime #system/date-time "2020-01-01T00:00:00.000")))

    (testing "to-json"
      (is (= "2020-01-01T00:00:00.001" (gen-json-value #fhir/dateTime #system/date-time "2020-01-01T00:00:00.001"))))

    (testing "to-xml"
      (is (= (sexp-value "2020-01-01T00:00:00.001") (type/to-xml #fhir/dateTime #system/date-time "2020-01-01T00:00:00.001"))))

    (testing "equals"
      (is (= #fhir/dateTime #system/date-time "2020-01-01T00:00:00.000"
             #fhir/dateTime #system/date-time "2020-01-01T00:00:00.000")))

    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/dateTime #system/date-time "2020-01-01T00:00:00.000" "da537591"
        #fhir/dateTime{:value #system/date-time "2020-01-01T00:00:00.000"} "da537591"
        #fhir/dateTime{:id "foo" :value #system/date-time "2020-01-01T00:00:00.000"} "f33b7808"))

    (testing "mem-size"
      (are [s mem-size] (= mem-size (Base/memSize s))
        #fhir/dateTime #system/date-time "2020-01-01T00:00:00.000" 64
        #fhir/dateTime{:id "foo"} 80))

    (testing "references"
      (is (empty? (type/references #fhir/dateTime #system/date-time "2020-01-01T00:00:00.000")))))

  (testing "with zulu timezone"
    (testing "dateTime?"
      (are [x] (type/dateTime? x)
        #fhir/dateTime #system/date-time "2020-01-01T00:00:00Z"
        #fhir/dateTime{:id "foo"}))

    (testing "type"
      (are [x] (= :fhir/dateTime (:fhir/type x))
        #fhir/dateTime #system/date-time "2020-01-01T00:00:00Z"
        #fhir/dateTime{:id "foo"}))

    (testing "dateTime"
      (is (= #fhir/dateTime{:value #system/date-time "2020-01-01T00:00:00Z"} #fhir/dateTime #system/date-time "2020-01-01T00:00:00Z")))

    (testing "interning"
      (is (not-interned? #fhir/dateTime #system/date-time "2020-01-01T00:00:00Z"
                         #fhir/dateTime #system/date-time "2020-01-01T00:00:00Z")))

    (testing "to-json"
      (is (= "2020-01-01T00:00:00Z" (gen-json-value #fhir/dateTime #system/date-time "2020-01-01T00:00:00Z"))))

    (testing "to-xml"
      (is (= (sexp-value "2020-01-01T00:00:00Z") (type/to-xml #fhir/dateTime #system/date-time "2020-01-01T00:00:00Z"))))

    (testing "equals"
      (is (= #fhir/dateTime #system/date-time "2020-01-01T00:00:00Z"
             #fhir/dateTime #system/date-time "2020-01-01T00:00:00Z")))

    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/dateTime #system/date-time "2020-01-01T00:00:00Z" "d541a45"
        #fhir/dateTime{:value #system/date-time "2020-01-01T00:00:00Z"} "d541a45"
        #fhir/dateTime{:id "foo" :value #system/date-time "2020-01-01T00:00:00Z"} "14a5cd29"))

    (testing "mem-size"
      (are [s mem-size] (= mem-size (Base/memSize s))
        #fhir/dateTime #system/date-time "2020-01-01T00:00:00Z" 80
        #fhir/dateTime{:id "foo"} 80))

    (testing "references"
      (is (empty? (type/references #fhir/dateTime #system/date-time "2020-01-01T00:00:00Z")))))

  (testing "with positive timezone offset"
    (testing "dateTime?"
      (are [x] (type/dateTime? x)
        #fhir/dateTime #system/date-time "2020-01-01T00:00:00+01:00"
        #fhir/dateTime{:id "foo"}))

    (testing "type"
      (are [x] (= :fhir/dateTime (:fhir/type x))
        #fhir/dateTime #system/date-time "2020-01-01T00:00:00+01:00"
        #fhir/dateTime{:id "foo"}))

    (testing "dateTime"
      (is (= #fhir/dateTime{:value #system/date-time "2020-01-01T00:00:00+01:00"} #fhir/dateTime #system/date-time "2020-01-01T00:00:00+01:00")))

    (testing "interning"
      (is (not-interned? #fhir/dateTime #system/date-time "2020-01-01T00:00:00+01:00"
                         #fhir/dateTime #system/date-time "2020-01-01T00:00:00+01:00")))

    (testing "to-json"
      (is (= "2020-01-01T00:00:00+01:00" (gen-json-value #fhir/dateTime #system/date-time "2020-01-01T00:00:00+01:00"))))

    (testing "to-xml"
      (is (= (sexp-value "2020-01-01T00:00:00+01:00") (type/to-xml #fhir/dateTime #system/date-time "2020-01-01T00:00:00+01:00"))))

    (testing "equals"
      (is (= #fhir/dateTime #system/date-time "2020-01-01T00:00:00+01:00"
             #fhir/dateTime #system/date-time "2020-01-01T00:00:00+01:00")))

    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/dateTime #system/date-time "2020-01-01T00:00:00+01:00" "9c535d0d"
        #fhir/dateTime{:value #system/date-time "2020-01-01T00:00:00+01:00"} "9c535d0d"
        #fhir/dateTime{:id "foo" :value #system/date-time "2020-01-01T00:00:00+01:00"} "dbf5aa43"))

    (testing "mem-size"
      (are [s mem-size] (= mem-size (Base/memSize s))
        #fhir/dateTime #system/date-time "2020-01-01T00:00:00+01:00" 80
        #fhir/dateTime{:id "foo"} 80))

    (testing "references"
      (is (empty? (type/references #fhir/dateTime #system/date-time "2020-01-01T00:00:00+01:00")))))

  (testing "with negative timezone offset"
    (testing "dateTime?"
      (are [x] (type/dateTime? x)
        #fhir/dateTime #system/date-time "2020-01-01T00:00:00-01:00"
        #fhir/dateTime{:id "foo"}))

    (testing "type"
      (are [x] (= :fhir/dateTime (:fhir/type x))
        #fhir/dateTime #system/date-time "2020-01-01T00:00:00-01:00"
        #fhir/dateTime{:id "foo"}))

    (testing "dateTime"
      (is (= #fhir/dateTime{:value #system/date-time "2020-01-01T00:00:00-01:00"} #fhir/dateTime #system/date-time "2020-01-01T00:00:00-01:00")))

    (testing "interning"
      (is (not-interned? #fhir/dateTime #system/date-time "2020-01-01T00:00:00-01:00"
                         #fhir/dateTime #system/date-time "2020-01-01T00:00:00-01:00")))

    (testing "to-json"
      (is (= "2020-01-01T00:00:00-01:00" (gen-json-value #fhir/dateTime #system/date-time "2020-01-01T00:00:00-01:00"))))

    (testing "to-xml"
      (is (= (sexp-value "2020-01-01T00:00:00-01:00") (type/to-xml #fhir/dateTime #system/date-time "2020-01-01T00:00:00-01:00"))))

    (testing "equals"
      (is (= #fhir/dateTime #system/date-time "2020-01-01T00:00:00-01:00"
             #fhir/dateTime #system/date-time "2020-01-01T00:00:00-01:00")))

    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/dateTime #system/date-time "2020-01-01T00:00:00-01:00" "839fd8a6"
        #fhir/dateTime{:value #system/date-time "2020-01-01T00:00:00-01:00"} "839fd8a6"
        #fhir/dateTime{:id "foo" :value #system/date-time "2020-01-01T00:00:00-01:00"} "c3a7cc0e"))

    (testing "mem-size"
      (are [s mem-size] (= mem-size (Base/memSize s))
        #fhir/dateTime #system/date-time "2020-01-01T00:00:00-01:00" 80
        #fhir/dateTime{:id "foo"} 80))

    (testing "references"
      (is (empty? (type/references #fhir/dateTime #system/date-time "2020-01-01T00:00:00-01:00")))))

  (testing "with zulu timezone and millis"
    (testing "dateTime?"
      (are [x] (type/dateTime? x)
        #fhir/dateTime #system/date-time "2020-01-01T00:00:00.001Z"
        #fhir/dateTime{:id "foo"}))

    (testing "type"
      (are [x] (= :fhir/dateTime (:fhir/type x))
        #fhir/dateTime #system/date-time "2020-01-01T00:00:00.001Z"
        #fhir/dateTime{:id "foo"}))

    (testing "dateTime"
      (is (= #fhir/dateTime{:value #system/date-time "2020-01-01T00:00:00.001Z"} #fhir/dateTime #system/date-time "2020-01-01T00:00:00.001Z")))

    (testing "interning"
      (is (not-interned? #fhir/dateTime #system/date-time "2020-01-01T00:00:00.001Z"
                         #fhir/dateTime #system/date-time "2020-01-01T00:00:00.001Z")))

    (testing "value"
      (is (= #system/date-time "2020-01-01T00:00:00.001Z" (:value #fhir/dateTime #system/date-time "2020-01-01T00:00:00.001Z"))))

    (testing "to-json"
      (is (= "2020-01-01T00:00:00.001Z" (gen-json-value #fhir/dateTime #system/date-time "2020-01-01T00:00:00.001Z"))))

    (testing "to-xml"
      (is (= (sexp-value "2020-01-01T00:00:00.001Z") (type/to-xml #fhir/dateTime #system/date-time "2020-01-01T00:00:00.001Z"))))

    (testing "equals"
      (is (= #fhir/dateTime #system/date-time "2020-01-01T00:00:00.001Z"
             #fhir/dateTime #system/date-time "2020-01-01T00:00:00.001Z")))

    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/dateTime #system/date-time "2020-01-01T00:00:00.001Z" "f46a0b1b"
        #fhir/dateTime{:value #system/date-time "2020-01-01T00:00:00.001Z"} "f46a0b1b"
        #fhir/dateTime{:id "foo" :value #system/date-time "2020-01-01T00:00:00.001Z"} "c6a5ea73"))

    (testing "mem-size"
      (are [s mem-size] (= mem-size (Base/memSize s))
        #fhir/dateTime #system/date-time "2020-01-01T00:00:00.001Z" 80
        #fhir/dateTime{:id "foo"} 80))

    (testing "references"
      (is (empty? (type/references #fhir/dateTime #system/date-time "2020-01-01T00:00:00.001Z")))))

  (testing "with extensions"
    (let [extended-date-time (type/dateTime {:extension [string-extension] :value #system/date-time "2020"})
          extended-date-time-element (xml-node/element nil {:value "2020"} string-extension)]
      (testing "date-time?"
        (is (type/dateTime? extended-date-time)))

      (testing "type"
        (is (= :fhir/dateTime (:fhir/type extended-date-time))))

      (testing "interning"
        (is (not-interned? (type/dateTime {:extension [string-extension] :value #system/date-time "2020"})
                           (type/dateTime {:extension [string-extension] :value #system/date-time "2020"}))))

      (testing "value"
        (is (= #system/date-time "2020" (:value extended-date-time))))

      (testing "to-xml"
        (is (= extended-date-time-element (type/to-xml extended-date-time))))

      (testing "equals"
        (is (= (type/dateTime {:extension [string-extension] :value #system/date-time "2020"}) extended-date-time)))

      (testing "hash-into"
        (are [x hex] (= hex (murmur3 x))
          extended-date-time "f1c7cff4"))

      (testing "mem-size"
        (are [s mem-size] (= mem-size (Base/memSize s))
          extended-date-time 32))

      (testing "references"
        (is (empty? (type/references extended-date-time)))))))

(deftest time-test
  (testing "time?"
    (are [x] (type/time? x)
      #fhir/time #system/time "15:27:45"
      #fhir/time{:id "foo"}))

  (testing "type"
    (are [x] (= :fhir/time (:fhir/type x))
      #fhir/time #system/time "15:27:45"
      #fhir/time{:id "foo"}))

  (testing "time"
    (is (= #fhir/time{:value #system/time "15:27:45"} #fhir/time #system/time "15:27:45")))

  (testing "interning"
    (is (not-interned? #fhir/time #system/time "13:53:21" #fhir/time #system/time "13:53:21")))

  (testing "assoc id"
    (testing "non-extended"
      (is (= (assoc #fhir/time #system/time "13:53:21" :id "id-111030")
             #fhir/time{:id "id-111030" :value #system/time "13:53:21"})))

    (testing "already extended"
      (is (= (assoc #fhir/time{:id "foo"} :id "bar")
             #fhir/time{:id "bar"}))
      (is (= (assoc #fhir/time{:extension [#fhir/Extension{:url "foo"}]} :id "id-111902")
             #fhir/time{:id "id-111902" :extension [#fhir/Extension{:url "foo"}]}))))

  (testing "assoc extension"
    (testing "non-extended"
      (is (= (assoc #fhir/time #system/time "13:53:21" :extension [#fhir/Extension{:url "foo"}])
             #fhir/time{:extension [#fhir/Extension{:url "foo"}] :value #system/time "13:53:21"})))

    (testing "already extended"
      (is (= (assoc #fhir/time{:id "id-111953"} :extension [#fhir/Extension{:url "foo"}])
             #fhir/time{:id "id-111953" :extension [#fhir/Extension{:url "foo"}]}))
      (is (= (assoc #fhir/time{:extension [#fhir/Extension{:url "foo"}]} :extension [#fhir/Extension{:url "bar"}])
             #fhir/time{:extension [#fhir/Extension{:url "bar"}]}))))

  (testing "value is a System.Time which is a LocalTime"
    (are [x] (= #system/time "13:53:21" (:value x))
      #fhir/time #system/time "13:53:21"
      #fhir/time{:id "foo" :value #system/time "13:53:21"}))

  (testing "assoc value"

    (testing "non-extended"

      (is (= (assoc #fhir/time #system/time "13:53:21" :value #system/time "13:34:45")

             #fhir/time #system/time "13:34:45")))

    (testing "already extended"

      (is (= (assoc #fhir/time{:id "foo"} :value #system/time "13:34:45")

             (type/time {:id "foo" :value #system/time "13:34:45"})))))

  (testing "to-json"
    (is (= "13:53:21" (gen-json-value #fhir/time #system/time "13:53:21"))))

  (testing "to-xml"
    (is (= (sexp-value "13:53:21")
           (type/to-xml #fhir/time #system/time "13:53:21"))))

  (testing "equals"
    (is (= #fhir/time #system/time "13:53:21" #fhir/time #system/time "13:53:21"))
    (is (not= #fhir/time #system/time "13:53:21" #fhir/time #system/time "13:53:22"))
    (is (not= #fhir/time #system/time "13:53:21" "13:53:21")))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/time #system/time "13:53:21" "faa37be9"
      #fhir/time{:value #system/time "13:53:21"} "faa37be9"
      #fhir/time{:id "foo"} "1547f086"
      #fhir/time{:id "foo" :value #system/time "13:53:21"} "52a81d69"
      #fhir/time{:extension [#fhir/Extension{:url "foo"}]} "9e94d20a"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/time #system/time "13:53:21" 32
      #fhir/time{:id "foo"} 80))

  (testing "references"
    (is (empty? (type/references #fhir/time #system/time "13:53:21"))))

  (testing "print"
    (are [x s] (= (pr-str x) s)
      #fhir/time #system/time "13:53:21" "#fhir/time #system/time \"13:53:21\""
      #fhir/time{:id "foo"} "#fhir/time{:id \"foo\"}")))

(def gender-extension
  #fhir/Extension
   {:url "http://fhir.de/StructureDefinition/gender-amtlich-de"
    :value
    #fhir/Coding
     {:system #fhir/uri "http://fhir.de/CodeSystem/gender-amtlich-de"
      :code #fhir/code "D"
      :display #fhir/string "divers"}})

(def extended-gender-code
  (type/code {:extension [gender-extension] :value "other"}))

(def extended-gender-code-element
  (xml-node/element nil {:value "other"} gender-extension))

(deftest code-test
  (testing "code?"
    (are [x] (type/code? x)
      #fhir/code ""
      #fhir/code{:id "foo"}))

  (testing "type"
    (are [x] (= :fhir/code (:fhir/type x))
      #fhir/code ""
      #fhir/code{:id "foo"}))

  (testing "interning"
    (are [x y] (interned? x y)
      (type/code {:extension [internable-extension]})
      (type/code {:extension [internable-extension]})

      #fhir/code "1234" #fhir/code "1234"

      #fhir/code "1234" (assoc #fhir/code "5678" :value "1234"))

    (is (interned? #fhir/code "code-123745" #fhir/code "code-123745"))
    (is (interned? (type/code {:extension [] :value "code-123745"})
                   (type/code "code-123745")))

    (testing "instances with id's are not interned"
      (is (not-interned? #fhir/code{:id "id-171649" :value "code-123745"}
                         #fhir/code{:id "id-171649" :value "code-123745"})))

    (testing "instances with interned extensions are interned"
      (is (interned? #fhir/code{:extension
                                [#fhir/Extension{:url "url-171902"
                                                 :value #fhir/boolean true}]
                                :value "code-123745"}
                     #fhir/code{:extension
                                [#fhir/Extension{:url "url-171902"
                                                 :value #fhir/boolean true}]
                                :value "code-123745"}))))

  (testing "assoc id"
    (testing "non-extended"
      (is (= (assoc #fhir/code "165645" :id "id-111030")
             #fhir/code{:id "id-111030" :value "165645"})))

    (testing "already extended"
      (is (= (assoc #fhir/code{:id "foo"} :id "bar")
             #fhir/code{:id "bar"}))
      (is (= (assoc #fhir/code{:extension [#fhir/Extension{:url "foo"}]} :id "id-111902")
             #fhir/code{:id "id-111902" :extension [#fhir/Extension{:url "foo"}]}))))

  (testing "assoc extension"
    (testing "non-extended"
      (is (= (assoc #fhir/code "165645" :extension [#fhir/Extension{:url "foo"}])
             #fhir/code{:extension [#fhir/Extension{:url "foo"}] :value "165645"})))

    (testing "already extended"
      (is (= (assoc #fhir/code{:id "id-111953"} :extension [#fhir/Extension{:url "foo"}])
             #fhir/code{:id "id-111953" :extension [#fhir/Extension{:url "foo"}]}))
      (is (= (assoc #fhir/code{:extension [#fhir/Extension{:url "foo"}]} :extension [#fhir/Extension{:url "bar"}])
             #fhir/code{:extension [#fhir/Extension{:url "bar"}]}))))

  (testing "value"
    (are [x] (= "code-123745" (:value x) (:value x) (:value x ::foo))
      #fhir/code "code-123745"
      #fhir/code{:id "foo" :value "code-123745"}))

  (testing "assoc value"
    (are [code value] (identical? code (assoc code :value value))
      #fhir/code "code-165634" "code-165634")

    (testing "non-extended"
      (is (= (assoc #fhir/code "code-165634" :value "code-165643")
             #fhir/code "code-165643")))

    (testing "already extended"
      (is (= (assoc #fhir/code{:id "foo"} :value "code-171046")
             #fhir/code{:id "foo" :value "code-171046"}))))

  (testing "lookup"
    (testing "other keys are not found"
      (is (= ::not-found (::other-key #fhir/code "foo" ::not-found)))))

  (testing "metadata"
    (is (nil? (meta #fhir/code "code-123745")))
    (is (= {:foo "bar"} (meta (with-meta #fhir/code "code-123745" {:foo "bar"}))))
    (is (= {:foo "bar"} (meta ^{:foo "bar"} #fhir/code "code-123745")))
    (is (= (with-meta #fhir/code "code-123745" {:x "a"})
           (with-meta #fhir/code "code-123745" {:x "b"}))))

  (testing "to-json"
    (are [code json] (= json (gen-json-value code))
      #fhir/code "code-123745" "code-123745"))

  (testing "to-xml"
    (is (= (sexp-value "code-123745")
           (type/to-xml #fhir/code "code-123745")))
    (is (= extended-gender-code-element (type/to-xml extended-gender-code))))

  (testing "equals"
    (is (= #fhir/code "175726" #fhir/code "175726"))
    (is (identical? #fhir/code "175726" #fhir/code "175726"))
    (is (not= #fhir/code "175726" #fhir/code "165817"))
    (is (not= #fhir/code "175726" "175726")))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/code "175726" "9c96c20f"
      #fhir/code{:value "175726"} "9c96c20f"
      #fhir/code{:id "170837"} "70f42552"
      #fhir/code{:id "170837" :value "175726"} "fc8af973"
      #fhir/code{:extension [#fhir/Extension{:url "181911"}]} "838ce6ff"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/code "175726" 0
      #fhir/code{:id "foo"} 80))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/code "code-150839"
      []

      #fhir/code
       {:extension
        [#fhir/Extension
          {:value #fhir/Reference{:reference #fhir/string "Patient/1"}}]}
      [["Patient" "1"]]))

  (testing "print"
    (is (= "#fhir/code \"175718\"" (pr-str #fhir/code "175718")))
    (is (= "#fhir/code{:id \"170837\"}" (pr-str #fhir/code{:id "170837"})))
    (is (= "#fhir/code{:id \"170837\" :value \"175718\"}" (pr-str #fhir/code{:id "170837" :value "175718"})))
    (is (= "#fhir/code{:extension [#fhir/Extension{:url \"181911\"}]}"
           (pr-str #fhir/code{:extension [#fhir/Extension{:url "181911"}]})))
    (is (= "#fhir/code{:id \"170837\" :extension [#fhir/Extension{:url \"181911\"}]}"
           (pr-str #fhir/code{:id "170837" :extension [#fhir/Extension{:url "181911"}]})))))

(deftest oid-test
  (testing "oid?"
    (are [x] (type/oid? x)
      #fhir/oid ""
      #fhir/oid{:id "foo"}))

  (testing "type"
    (are [x] (= :fhir/oid (:fhir/type x))
      #fhir/oid ""
      #fhir/oid{:id "foo"}))

  (testing "oid"
    (is (= #fhir/oid{:value "182040"} #fhir/oid "182040")))

  (testing "interning"
    (is (not-interned? #fhir/oid "oid-123745" #fhir/oid "oid-123745")))

  (testing "assoc id"
    (testing "non-extended"
      (is (= (assoc #fhir/oid "165645" :id "id-111030")
             #fhir/oid{:id "id-111030" :value "165645"})))

    (testing "already extended"
      (is (= (assoc #fhir/oid{:id "foo"} :id "bar")
             #fhir/oid{:id "bar"}))
      (is (= (assoc #fhir/oid{:extension [#fhir/Extension{:url "foo"}]} :id "id-111902")
             #fhir/oid{:id "id-111902" :extension [#fhir/Extension{:url "foo"}]}))))

  (testing "assoc extension"
    (testing "non-extended"
      (is (= (assoc #fhir/oid "165645" :extension [#fhir/Extension{:url "foo"}])
             #fhir/oid{:extension [#fhir/Extension{:url "foo"}] :value "165645"})))

    (testing "already extended"
      (is (= (assoc #fhir/oid{:id "id-111953"} :extension [#fhir/Extension{:url "foo"}])
             #fhir/oid{:id "id-111953" :extension [#fhir/Extension{:url "foo"}]}))
      (is (= (assoc #fhir/oid{:extension [#fhir/Extension{:url "foo"}]} :extension [#fhir/Extension{:url "bar"}])
             #fhir/oid{:extension [#fhir/Extension{:url "bar"}]}))))

  (testing "value"
    (are [x] (= "oid-123745" (:value x) (:value x) (:value x ::foo))
      #fhir/oid "oid-123745"
      #fhir/oid{:id "foo" :value "oid-123745"}))

  (testing "assoc value"
    (testing "non-extended"
      (is (= (assoc #fhir/oid "oid-165634" :value "oid-165643")
             #fhir/oid "oid-165643")))

    (testing "already extended"
      (is (= (assoc #fhir/oid{:id "foo"} :value "oid-171046")
             #fhir/oid{:id "foo" :value "oid-171046"}))))

  (testing "lookup"
    (testing "other keys are not found"
      (is (= ::not-found (::other-key #fhir/oid "foo" ::not-found)))))

  (testing "to-json"
    (is (= "oid-123745" (gen-json-value #fhir/oid "oid-123745"))))

  (testing "to-xml"
    (is (= (sexp-value "oid-123745")
           (type/to-xml #fhir/oid "oid-123745"))))

  (testing "equals"
    (is (let [oid #fhir/oid "175726"] (= oid oid)))
    (is (= #fhir/oid "175726" #fhir/oid "175726"))
    (is (not= #fhir/oid "175726" #fhir/oid "171055"))
    (is (not= #fhir/oid "175726" "175726")))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/oid "175726" "a73ea817"
      #fhir/oid{:value "175726"} "a73ea817"
      #fhir/oid{:id "foo"} "4daaecfb"
      #fhir/oid{:id "foo" :value "175726"} "5e076060"
      #fhir/oid{:extension [#fhir/Extension{:url "foo"}]} "c114dd42"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/oid "175726" 64
      #fhir/oid{:id "foo"} 80))

  (testing "references"
    (is (empty? (type/references #fhir/oid "151329"))))

  (testing "print"
    (are [x s] (= (pr-str x) s)
      #fhir/oid "175726" "#fhir/oid \"175726\""
      #fhir/oid{:id "foo"} "#fhir/oid{:id \"foo\"}")))

(deftest id-test
  (testing "id?"
    (are [x] (type/id? x)
      #fhir/id ""
      #fhir/id{:id "foo"}))

  (testing "type"
    (are [x] (= :fhir/id (:fhir/type x))
      #fhir/id ""
      #fhir/id{:id "foo"}))

  (testing "id"
    (is (= #fhir/id{:value "182040"} #fhir/id "182040")))

  (testing "interning"
    (is (not-interned? #fhir/id "id-123745" #fhir/id "id-123745")))

  (testing "assoc id"
    (testing "non-extended"
      (is (= (assoc #fhir/id "165645" :id "id-111030")
             #fhir/id{:id "id-111030" :value "165645"})))

    (testing "already extended"
      (is (= (assoc #fhir/id{:id "foo"} :id "bar")
             #fhir/id{:id "bar"}))
      (is (= (assoc #fhir/id{:extension [#fhir/Extension{:url "foo"}]} :id "id-111902")
             #fhir/id{:id "id-111902" :extension [#fhir/Extension{:url "foo"}]}))))

  (testing "assoc extension"
    (testing "non-extended"
      (is (= (assoc #fhir/id "165645" :extension [#fhir/Extension{:url "foo"}])
             #fhir/id{:extension [#fhir/Extension{:url "foo"}] :value "165645"})))

    (testing "already extended"
      (is (= (assoc #fhir/id{:id "id-111953"} :extension [#fhir/Extension{:url "foo"}])
             #fhir/id{:id "id-111953" :extension [#fhir/Extension{:url "foo"}]}))
      (is (= (assoc #fhir/id{:extension [#fhir/Extension{:url "foo"}]} :extension [#fhir/Extension{:url "bar"}])
             #fhir/id{:extension [#fhir/Extension{:url "bar"}]}))))

  (testing "value"
    (are [x] (= "id-123745" (:value x) (:value x) (:value x ::foo))
      #fhir/id "id-123745"
      #fhir/id{:id "foo" :value "id-123745"}))

  (testing "assoc value"
    (testing "non-extended"
      (is (= (assoc #fhir/id "id-165634" :value "id-165643")
             #fhir/id "id-165643")))

    (testing "already extended"
      (is (= (assoc #fhir/id{:id "foo"} :value "id-171046")
             #fhir/id{:id "foo" :value "id-171046"}))))

  (testing "lookup"
    (testing "other keys are not found"
      (is (= ::not-found (::other-key #fhir/id "foo" ::not-found)))))

  (testing "to-json"
    (is (= "id-123745" (gen-json-value #fhir/id "id-123745"))))

  (testing "to-xml"
    (is (= (sexp-value "id-123745")
           (type/to-xml #fhir/id "id-123745"))))

  (testing "equals"
    (is (let [id #fhir/id "175726"] (= id id)))
    (is (= #fhir/id "175726" #fhir/id "175726"))
    (is (not= #fhir/id "175726" #fhir/id "171108"))
    (is (not= #fhir/id "175726" "175726")))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/id "175726" "e56cbac6"
      #fhir/id{:value "175726"} "e56cbac6"
      #fhir/id{:id "foo"} "59a2c68a"
      #fhir/id{:id "foo" :value "175726"} "3dbaa84e"
      #fhir/id{:extension [#fhir/Extension{:url "foo"}]} "1e8120f7"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/id "175726" 64
      #fhir/id{:id "foo"} 80))

  (testing "references"
    (is (empty? (type/references #fhir/id "151408"))))

  (testing "print"
    (are [x s] (= (pr-str x) s)
      #fhir/id "175726" "#fhir/id \"175726\""
      #fhir/id{:id "foo"} "#fhir/id{:id \"foo\"}")))

(deftest markdown-test
  (testing "markdown?"
    (are [x] (type/markdown? x)
      #fhir/markdown ""
      #fhir/markdown{:id "foo"}))

  (testing "type"
    (are [x] (= :fhir/markdown (:fhir/type x))
      #fhir/markdown ""
      #fhir/markdown{:id "foo"}))

  (testing "markdown"
    (is (= #fhir/markdown{:value "182040"} #fhir/markdown "182040")))

  (testing "interning"
    (is (not-interned? #fhir/markdown "markdown-123745"
                       #fhir/markdown "markdown-123745")))

  (testing "assoc id"
    (testing "non-extended"
      (is (= (assoc #fhir/markdown "165645" :id "id-111030")
             #fhir/markdown{:id "id-111030" :value "165645"})))

    (testing "already extended"
      (is (= (assoc #fhir/markdown{:id "foo"} :id "bar")
             #fhir/markdown{:id "bar"}))
      (is (= (assoc #fhir/markdown{:extension [#fhir/Extension{:url "foo"}]} :id "id-111902")
             #fhir/markdown{:id "id-111902" :extension [#fhir/Extension{:url "foo"}]}))))

  (testing "assoc extension"
    (testing "non-extended"
      (is (= (assoc #fhir/markdown "165645" :extension [#fhir/Extension{:url "foo"}])
             #fhir/markdown{:extension [#fhir/Extension{:url "foo"}] :value "165645"})))

    (testing "already extended"
      (is (= (assoc #fhir/markdown{:id "id-111953"} :extension [#fhir/Extension{:url "foo"}])
             #fhir/markdown{:id "id-111953" :extension [#fhir/Extension{:url "foo"}]}))
      (is (= (assoc #fhir/markdown{:extension [#fhir/Extension{:url "foo"}]} :extension [#fhir/Extension{:url "bar"}])
             #fhir/markdown{:extension [#fhir/Extension{:url "bar"}]}))))

  (testing "value"
    (are [x] (= "markdown-123745" (:value x) (:value x) (:value x ::foo))
      #fhir/markdown "markdown-123745"
      #fhir/markdown{:id "foo" :value "markdown-123745"}))

  (testing "assoc value"
    (testing "non-extended"
      (is (= (assoc #fhir/markdown "markdown-165634" :value "markdown-165643")
             #fhir/markdown "markdown-165643")))

    (testing "already extended"
      (is (= (assoc #fhir/markdown{:id "foo"} :value "markdown-171046")
             #fhir/markdown{:id "foo" :value "markdown-171046"}))))

  (testing "lookup"
    (testing "other keys are not found"
      (is (= ::not-found (::other-key #fhir/markdown "foo" ::not-found)))))

  (testing "to-json"
    (is (= "markdown-123745" (gen-json-value #fhir/markdown "markdown-123745"))))

  (testing "to-xml"
    (is (= (sexp-value "markdown-123745")
           (type/to-xml #fhir/markdown "markdown-123745"))))

  (testing "equals"
    (is (let [markdown #fhir/markdown "175726"] (= markdown markdown)))
    (is (= #fhir/markdown "175726" #fhir/markdown "175726"))
    (is (not= #fhir/markdown "175726" #fhir/markdown "171153"))
    (is (not= #fhir/markdown "175726" "175726")))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/markdown "175726" "444928f7"
      #fhir/markdown{:value "175726"} "444928f7"
      #fhir/markdown{:id "foo"} "999ebb88"
      #fhir/markdown{:id "foo" :value "175726"} "c9b526e9"
      #fhir/markdown{:extension [#fhir/Extension{:url "foo"}]} "8d0712c5"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/markdown "175726" 64
      #fhir/markdown{:id "foo"} 80))

  (testing "references"
    (is (empty? (type/references #fhir/markdown "151424"))))

  (testing "print"
    (are [x s] (= (pr-str x) s)
      #fhir/markdown "175726" "#fhir/markdown \"175726\""
      #fhir/markdown{:id "foo"} "#fhir/markdown{:id \"foo\"}")))

(deftest unsignedInt-test
  (testing "unsignedInt?"
    (are [x] (type/unsignedInt? x)
      #fhir/unsignedInt 0
      (type/unsignedInt (dec (bit-shift-left 1 31)))
      #fhir/unsignedInt{:id "foo"}))

  (testing "type"
    (are [x] (= :fhir/unsignedInt (:fhir/type x))
      #fhir/unsignedInt 0
      (type/unsignedInt (dec (bit-shift-left 1 31)))
      #fhir/unsignedInt{:id "foo"}))

  (testing "invalid value"
    (doseq [x [-1 {:value -1}]]
      (given (type/unsignedInt x)
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid unsignedInt value `-1`."))

    (doseq [x [(inc Integer/MAX_VALUE) {:value (inc Integer/MAX_VALUE)}]]
      (given (type/unsignedInt x)
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid unsignedInt value `2147483648`.")))

  (testing "unsignedInt"
    (is (= #fhir/unsignedInt{:value 160845} #fhir/unsignedInt 160845)))

  (testing "interning"
    (is (not-interned? #fhir/unsignedInt 160845
                       #fhir/unsignedInt 160845)))

  (testing "assoc id"
    (testing "non-extended"
      (is (= (assoc #fhir/unsignedInt 165645 :id "id-111030")
             #fhir/unsignedInt{:id "id-111030" :value 165645})))

    (testing "already extended"
      (is (= (assoc #fhir/unsignedInt{:id "foo"} :id "bar")
             #fhir/unsignedInt{:id "bar"}))
      (is (= (assoc #fhir/unsignedInt{:extension [#fhir/Extension{:url "foo"}]} :id "id-111902")
             #fhir/unsignedInt{:id "id-111902" :extension [#fhir/Extension{:url "foo"}]}))))

  (testing "assoc extension"
    (testing "non-extended"
      (is (= (assoc #fhir/unsignedInt 165645 :extension [#fhir/Extension{:url "foo"}])
             #fhir/unsignedInt{:extension [#fhir/Extension{:url "foo"}] :value 165645})))

    (testing "already extended"
      (is (= (assoc #fhir/unsignedInt{:id "id-111953"} :extension [#fhir/Extension{:url "foo"}])
             #fhir/unsignedInt{:id "id-111953" :extension [#fhir/Extension{:url "foo"}]}))
      (is (= (assoc #fhir/unsignedInt{:extension [#fhir/Extension{:url "foo"}]} :extension [#fhir/Extension{:url "bar"}])
             #fhir/unsignedInt{:extension [#fhir/Extension{:url "bar"}]}))))

  (testing "value"
    (are [x] (= 160845 (:value x) (:value x) (:value x ::foo))
      #fhir/unsignedInt 160845
      #fhir/unsignedInt{:id "foo" :value 160845}))

  (testing "assoc value"
    (testing "non-extended"
      (is (= #fhir/unsignedInt 2 (assoc #fhir/unsignedInt 1 :value 2))))

    (testing "already extended"
      (is (= (assoc #fhir/unsignedInt{:id "foo"} :value 1)
             #fhir/unsignedInt{:id "foo" :value 1}))))

  (testing "lookup"
    (testing "other keys are not found"
      (is (= ::not-found (::other-key #fhir/unsignedInt 1 ::not-found)))))

  (testing "to-json"
    (is (= 160845 (gen-json-value #fhir/unsignedInt 160845))))

  (testing "to-xml"
    (is (= (sexp-value "160845")
           (type/to-xml #fhir/unsignedInt 160845))))

  (testing "equals"
    (is (= #fhir/unsignedInt 160845 #fhir/unsignedInt 160845))
    (is (not= #fhir/unsignedInt 160845 #fhir/unsignedInt 171218))
    (is (not= #fhir/unsignedInt 160845 160845)))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/unsignedInt 160845 "10a52aa2"
      #fhir/unsignedInt{:value 160845} "10a52aa2"
      #fhir/unsignedInt{:id "foo"} "7a1f86be"
      #fhir/unsignedInt{:id "foo" :value 160845} "aa5dbbe7"
      #fhir/unsignedInt{:extension [#fhir/Extension{:url "foo"}]} "8117a763"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/unsignedInt 160845 16
      #fhir/unsignedInt{:id "foo"} 80))

  (testing "references"
    (is (empty? (type/references #fhir/unsignedInt 151440))))

  (testing "print"
    (are [x s] (= (pr-str x) s)
      #fhir/unsignedInt 192629
      "#fhir/unsignedInt 192629"

      #fhir/unsignedInt{:id "id-192647"}
      "#fhir/unsignedInt{:id \"id-192647\"}"

      #fhir/unsignedInt{:id "id-192703" :value 192711}
      "#fhir/unsignedInt{:id \"id-192703\" :value 192711}"

      #fhir/unsignedInt{:extension [#fhir/Extension{:url "url-192724"}]}
      "#fhir/unsignedInt{:extension [#fhir/Extension{:url \"url-192724\"}]}")))

(deftest positiveInt-test
  (testing "positiveInt?"
    (are [x] (type/positiveInt? x)
      #fhir/positiveInt 1
      (type/positiveInt (dec (bit-shift-left 1 31)))
      #fhir/positiveInt{:id "foo"}))

  (testing "type"
    (are [x] (= :fhir/positiveInt (:fhir/type x))
      #fhir/positiveInt 1
      (type/positiveInt (dec (bit-shift-left 1 31)))
      #fhir/positiveInt{:id "foo"}))

  (testing "invalid value"
    (doseq [x [0 {:value 0}]]
      (given (type/positiveInt x)
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid positiveInt value `0`."))

    (doseq [x [-1 {:value -1}]]
      (given (type/positiveInt x)
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid positiveInt value `-1`."))

    (doseq [x [(inc Integer/MAX_VALUE) {:value (inc Integer/MAX_VALUE)}]]
      (given (type/positiveInt x)
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid positiveInt value `2147483648`.")))

  (testing "positiveInt"
    (is (= #fhir/positiveInt{:value 160845} #fhir/positiveInt 160845)))

  (testing "interning"
    (is (not-interned? #fhir/positiveInt 160845
                       #fhir/positiveInt 160845)))

  (testing "assoc id"
    (testing "non-extended"
      (is (= (assoc #fhir/positiveInt 165645 :id "id-111030")
             #fhir/positiveInt{:id "id-111030" :value 165645})))

    (testing "already extended"
      (is (= (assoc #fhir/positiveInt{:id "foo"} :id "bar")
             #fhir/positiveInt{:id "bar"}))
      (is (= (assoc #fhir/positiveInt{:extension [#fhir/Extension{:url "foo"}]} :id "id-111902")
             #fhir/positiveInt{:id "id-111902" :extension [#fhir/Extension{:url "foo"}]}))))

  (testing "assoc extension"
    (testing "non-extended"
      (is (= (assoc #fhir/positiveInt 165645 :extension [#fhir/Extension{:url "foo"}])
             #fhir/positiveInt{:extension [#fhir/Extension{:url "foo"}] :value 165645})))

    (testing "already extended"
      (is (= (assoc #fhir/positiveInt{:id "id-111953"} :extension [#fhir/Extension{:url "foo"}])
             #fhir/positiveInt{:id "id-111953" :extension [#fhir/Extension{:url "foo"}]}))
      (is (= (assoc #fhir/positiveInt{:extension [#fhir/Extension{:url "foo"}]} :extension [#fhir/Extension{:url "bar"}])
             #fhir/positiveInt{:extension [#fhir/Extension{:url "bar"}]}))))

  (testing "value"
    (are [x] (= 160845 (:value x) (:value x) (:value x ::foo))
      #fhir/positiveInt 160845
      #fhir/positiveInt{:id "foo" :value 160845}))

  (testing "assoc value"
    (testing "non-extended"
      (is (= #fhir/positiveInt 2 (assoc #fhir/positiveInt 1 :value 2))))

    (testing "already extended"
      (is (= (assoc #fhir/positiveInt{:id "foo"} :value 1)
             #fhir/positiveInt{:id "foo" :value 1}))))

  (testing "lookup"
    (testing "other keys are not found"
      (is (= ::not-found (::other-key #fhir/positiveInt 1 ::not-found)))))

  (testing "to-json"
    (is (= 160845 (gen-json-value #fhir/positiveInt 160845))))

  (testing "to-xml"
    (is (= (sexp-value "160845")
           (type/to-xml #fhir/positiveInt 160845))))

  (testing "equals"
    (is (= #fhir/positiveInt 160845 #fhir/positiveInt 160845))
    (is (not= #fhir/positiveInt 160845 #fhir/positiveInt 171237))
    (is (not= #fhir/positiveInt 160845 160845)))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/positiveInt 160845 "8c218d7d"
      #fhir/positiveInt{:value 160845} "8c218d7d"
      #fhir/positiveInt{:id "foo"} "3f7dbd4e"
      #fhir/positiveInt{:id "foo" :value 160845} "2f1e63f"
      #fhir/positiveInt{:extension [#fhir/Extension{:url "foo"}]} "7c036682"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/positiveInt 160845 16
      #fhir/positiveInt{:id "foo"} 80))

  (testing "references"
    (is (empty? (type/references #fhir/positiveInt 151500))))

  (testing "print"
    (are [x s] (= (pr-str x) s)
      #fhir/positiveInt 160845 "#fhir/positiveInt 160845"
      #fhir/positiveInt{:id "foo"} "#fhir/positiveInt{:id \"foo\"}")))

(deftest uuid-test
  (testing "uuid?"
    (are [x] (type/uuid? x)
      #fhir/uuid "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"
      #fhir/uuid{:id "foo"}))

  (testing "type"
    (are [x] (= :fhir/uuid (:fhir/type x))
      #fhir/uuid "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"
      #fhir/uuid{:id "foo"}))

  (testing "uuid"
    (is (= #fhir/uuid{:value "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"} #fhir/uuid "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3")))

  (testing "interning"
    (is (not-interned? #fhir/uuid "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"
                       #fhir/uuid "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"))

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

  (testing "assoc id"
    (testing "non-extended"
      (is (= (assoc #fhir/uuid "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3" :id "id-111030")
             #fhir/uuid{:id "id-111030" :value "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"})))

    (testing "already extended"
      (is (= (assoc #fhir/uuid{:id "foo"} :id "bar")
             #fhir/uuid{:id "bar"}))
      (is (= (assoc #fhir/uuid{:extension [#fhir/Extension{:url "foo"}]} :id "id-111902")
             #fhir/uuid{:id "id-111902" :extension [#fhir/Extension{:url "foo"}]}))))

  (testing "assoc extension"
    (testing "non-extended"
      (is (= (assoc #fhir/uuid "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3" :extension [#fhir/Extension{:url "foo"}])
             #fhir/uuid{:extension [#fhir/Extension{:url "foo"}] :value "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"})))

    (testing "already extended"
      (is (= (assoc #fhir/uuid{:id "id-111953"} :extension [#fhir/Extension{:url "foo"}])
             #fhir/uuid{:id "id-111953" :extension [#fhir/Extension{:url "foo"}]}))
      (is (= (assoc #fhir/uuid{:extension [#fhir/Extension{:url "foo"}]} :extension [#fhir/Extension{:url "bar"}])
             #fhir/uuid{:extension [#fhir/Extension{:url "bar"}]}))))

  (testing "value"
    (are [x] (= "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3" (:value x))
      #fhir/uuid "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"
      #fhir/uuid{:id "foo" :value "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"}))

  (testing "assoc value"
    (testing "non-extended"
      (is (= (assoc #fhir/uuid "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3" :value "urn:uuid:224c0729-05a7-4703-8ffd-acaa98d2d217")
             #fhir/uuid "urn:uuid:224c0729-05a7-4703-8ffd-acaa98d2d217")))

    (testing "already extended"
      (is (= (assoc #fhir/uuid{:id "foo"} :value "urn:uuid:224c0729-05a7-4703-8ffd-acaa98d2d217")
             #fhir/uuid{:id "foo" :value "urn:uuid:224c0729-05a7-4703-8ffd-acaa98d2d217"}))))

  (testing "to-json"
    (is (= "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"
           (gen-json-value #fhir/uuid "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"))))

  (testing "to-xml"
    (is (= (sexp-value "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3")
           (type/to-xml #fhir/uuid "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"))))

  (testing "equals"
    (is (= #fhir/uuid "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"
           #fhir/uuid "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"))
    (is (not= #fhir/uuid "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"
              #fhir/uuid "urn:uuid:ccd4a49d-a288-4387-b842-56dd0f896851"))
    (is (not= #fhir/uuid "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"
              "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3")))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/uuid "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3" "f894ff2b"
      #fhir/uuid{:value "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"} "f894ff2b"
      #fhir/uuid{:id "foo"} "3b18b5b7"
      #fhir/uuid{:id "foo" :value "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"} "64cb0e66"
      #fhir/uuid{:extension [#fhir/Extension{:url "foo"}]} "9160d648"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/uuid "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3" 40
      #fhir/uuid{:id "foo"} 80))

  (testing "references"
    (is (empty? (type/references #fhir/uuid "urn:uuid:89ddf6ab-8813-4c75-9500-dd07560fe817"))))

  (testing "print"
    (are [x s] (= (pr-str x) s)
      #fhir/uuid "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3" "#fhir/uuid \"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3\""
      #fhir/uuid{:id "foo"} "#fhir/uuid{:id \"foo\"}")))

(def xhtml-element
  (sexp
   [::xhtml/div {:xmlns "http://www.w3.org/1999/xhtml"}
    [::xhtml/p "FHIR is cool."]]))

(deftest xhtml-test
  (testing "xhtml?"
    (is (type/xhtml? #fhir/xhtml "xhtml-123745")))

  (testing "from XML"
    (is (= #fhir/xhtml "<div xmlns=\"http://www.w3.org/1999/xhtml\"><p>FHIR is cool.</p></div>"
           (type/xml->Xhtml xhtml-element))))

  (testing "type"
    (is (= :fhir/xhtml (:fhir/type #fhir/xhtml ""))))

  (testing "interning"
    (is (not-interned? #fhir/xhtml "xhtml-123745"
                       #fhir/xhtml "xhtml-123745")))

  (testing "value"
    (is (= "xhtml-123745" (:value #fhir/xhtml "xhtml-123745"))))

  (testing "assoc value"
    (is (= (assoc #fhir/xhtml "xhtml-165634" :value "xhtml-165643")
           #fhir/xhtml "xhtml-165643")))

  (testing "to-json"
    (is (= "xhtml-123745" (gen-json-value #fhir/xhtml "xhtml-123745"))))

  (testing "to-xml"
    (testing "plain text"
      (is (= (xml/emit-str (type/xhtml-to-xml #fhir/xhtml "xhtml-123745"))
             "<?xml version='1.0' encoding='UTF-8'?><div xmlns=\"http://www.w3.org/1999/xhtml\">xhtml-123745</div>")))

    (testing "not closed tag"
      (is (= (xml/emit-str (type/xhtml-to-xml #fhir/xhtml "<foo>"))
             "<?xml version='1.0' encoding='UTF-8'?><div xmlns=\"http://www.w3.org/1999/xhtml\">&lt;foo></div>")))

    (testing "invalid tag"
      (is (= (xml/emit-str (type/xhtml-to-xml #fhir/xhtml "<foo"))
             "<?xml version='1.0' encoding='UTF-8'?><div xmlns=\"http://www.w3.org/1999/xhtml\">&lt;foo</div>")))

    (testing "CDATA"
      (is (= (xml/emit-str (type/xhtml-to-xml #fhir/xhtml "<![CDATA[foo]]>"))
             "<?xml version='1.0' encoding='UTF-8'?><div xmlns=\"http://www.w3.org/1999/xhtml\">&lt;![CDATA[foo]]&gt;</div>")))

    (testing "CDATA end"
      (is (= (xml/emit-str (type/xhtml-to-xml #fhir/xhtml "]]>"))
             "<?xml version='1.0' encoding='UTF-8'?><div xmlns=\"http://www.w3.org/1999/xhtml\">]]&gt;</div>")))

    (is (= xhtml-element (type/xhtml-to-xml #fhir/xhtml "<div xmlns=\"http://www.w3.org/1999/xhtml\"><p>FHIR is cool.</p></div>"))))

  (testing "equals"
    (is (= #fhir/xhtml "175726" #fhir/xhtml "175726"))
    (is (not= #fhir/xhtml "175726" #fhir/xhtml "171511"))
    (is (not= #fhir/xhtml "175726" "175726")))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/xhtml "175726" "e90ddf05"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/xhtml "175726" 64
      #fhir/xhtml{:id "foo"} 80))

  (testing "references"
    (is (empty? (type/references #fhir/xhtml "151551"))))

  (testing "print"
    (are [x s] (= (pr-str x) s)
      #fhir/xhtml "175726" "#fhir/xhtml \"175726\""
      #fhir/xhtml{:id "foo"} "#fhir/xhtml{:id \"foo\"}"))

  (testing "toString"
    (is (= "Xhtml{id=null, extension=[], value='175718'}" (str #fhir/xhtml "175718")))))

(defn- recreate
  "Takes `x`, a complex type and recreates it from its components using
  `constructor`."
  [constructor x]
  (constructor (into {} x)))

(def ^:private markdown-extension-gen
  (fg/extension :value (fg/markdown :value fg/markdown-value)))

(deftest address-test
  (testing "type"
    (is (= :fhir/Address (:fhir/type #fhir/Address{}))))

  (testing "interning"
    (are [x y] (not-interned? x y)
      #fhir/Address{:id "foo"}
      #fhir/Address{:id "foo"}

      #fhir/Address{:extension [#fhir/Extension{:url "foo" :value #fhir/string "barbar"}]}
      #fhir/Address{:extension [#fhir/Extension{:url "foo" :value #fhir/string "barbar"}]}

      #fhir/Address{:text #fhir/string "foofoo"}
      #fhir/Address{:text #fhir/string "foofoo"}))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/Address{}
      "4a6b5e4f"

      #fhir/Address{:id "id-130739"}
      "bd6a5731"

      #fhir/Address{:extension [#fhir/Extension{}]}
      "2a3786e7"

      #fhir/Address{:use #fhir/code "use-155144"}
      "b6cf1d48"

      #fhir/Address{:type #fhir/code "type-084442"}
      "54c286c3"

      #fhir/Address{:text #fhir/string "text-212402"}
      "15baed84"

      #fhir/Address{:line [#fhir/string "line-212441"]}
      "eafac0f1"

      #fhir/Address{:line [#fhir/string "line-212448" #fhir/string "line-212454"]}
      "62f4cf8f"

      #fhir/Address{:city #fhir/string "city-084705"}
      "9765a1e9"

      #fhir/Address{:district #fhir/string "district-084717"}
      "9e6dc6b8"

      #fhir/Address{:state #fhir/string "state-084729"}
      "17a7640f"

      #fhir/Address{:postalCode #fhir/string "postalCode-084832"}
      "8880561c"

      #fhir/Address{:country #fhir/string "country-084845"}
      "57c51a7d"

      #fhir/Address{:period #fhir/Period{}}
      "fb17905a"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/Address{} 56
      #fhir/Address{:id "id-130739"} 128
      #fhir/Address{:extension [#fhir/Extension{}]} 56
      #fhir/Address{:use #fhir/code "use-155144"} 56
      #fhir/Address{:type #fhir/code "type-084442"} 56
      #fhir/Address{:text #fhir/string "text-212402"} 120
      #fhir/Address{:line [#fhir/string "line-212441"]} 176
      #fhir/Address{:line [#fhir/string "line-212448" #fhir/string "line-212454"]} 248
      #fhir/Address{:city #fhir/string "city-084705"} 120
      #fhir/Address{:district #fhir/string "district-084717"} 128
      #fhir/Address{:state #fhir/string "state-084729"} 120
      #fhir/Address{:postalCode #fhir/string "postalCode-084832"} 128
      #fhir/Address{:country #fhir/string "country-084845"} 128
      #fhir/Address{:period #fhir/Period{}} 56))

  (testing "references"
    (is (empty? (type/references #fhir/Address{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Address{} "#fhir/Address{}"
      #fhir/Address{:id "084856"} "#fhir/Address{:id \"084856\"}")))

(deftest age-test
  (testing "type"
    (is (= :fhir/Age (:fhir/type #fhir/Age{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Age{} "#fhir/Age{}"
      #fhir/Age{:id "212329"} "#fhir/Age{:id \"212329\"}")))

(deftest annotation-test
  (testing "type"
    (is (= :fhir/Annotation (:fhir/type #fhir/Annotation{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Annotation{} "#fhir/Annotation{}"
      #fhir/Annotation{:id "212329"} "#fhir/Annotation{:id \"212329\"}")))

(deftest attachment-test
  (testing "type"
    (is (= :fhir/Attachment (:fhir/type #fhir/Attachment{}))))

  (testing "interning"
    (are [x y] (not-interned? x y)
      #fhir/Attachment{:id "foo"}
      #fhir/Attachment{:id "foo"}))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/Attachment{}
      "982b1106"

      #fhir/Attachment{:id "id-204201"}
      "bf961f05"

      #fhir/Attachment{:extension [#fhir/Extension{}]}
      "f4c1b44b"

      #fhir/Attachment{:contentType #fhir/code "text/plain"}
      "3a7e400d"

      #fhir/Attachment{:language #fhir/code "de"}
      "e98243"

      #fhir/Attachment{:data #fhir/base64Binary "MTA1NjE0Cg=="}
      "c4a8a267"

      #fhir/Attachment{:url #fhir/url "url-210424"}
      "de0e50c7"

      #fhir/Attachment{:size #fhir/unsignedInt 1}
      "33c020e7"

      #fhir/Attachment{:hash #fhir/base64Binary "MTA1NjE0Cg=="}
      "9b0f6cc"

      #fhir/Attachment{:title #fhir/string "title-210622"}
      "48c71e7a"

      #fhir/Attachment{:creation #fhir/dateTime #system/date-time "2021"}
      "77c0ecf6"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/Attachment{:id "id-204201"} 120
      #fhir/Attachment{:extension [#fhir/Extension{}]} 48
      #fhir/Attachment{:contentType #fhir/code "text/plain"} 48
      #fhir/Attachment{:language #fhir/code "de"} 48
      #fhir/Attachment{:data #fhir/base64Binary "MTA1NjE0Cg=="} 112))

  (testing "references"
    (is (empty? (type/references #fhir/Attachment{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Attachment{} "#fhir/Attachment{}"
      #fhir/Attachment{:id "212329"} "#fhir/Attachment{:id \"212329\"}")))

(deftest bundle-entry-search-test
  (testing "type"
    (is (= :fhir.Bundle.entry/search (:fhir/type #fhir.Bundle.entry/search{}))))

  (testing "interning"
    (are [x y] (not-interned? x y)
      #fhir.Bundle.entry/search{:id "foo"}
      #fhir.Bundle.entry/search{:id "foo"}

      #fhir.Bundle.entry/search{:extension [#fhir/Extension{:url "foo" :value #fhir/string "barbar"}]}
      #fhir.Bundle.entry/search{:extension [#fhir/Extension{:url "foo" :value #fhir/string "barbar"}]}

      #fhir.Bundle.entry/search{:score #fhir/decimal 1M}
      #fhir.Bundle.entry/search{:score #fhir/decimal 1M}))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir.Bundle.entry/search{}
      "f945531f"

      #fhir.Bundle.entry/search{:id "id-130825"}
      "6b1b9201"

      #fhir.Bundle.entry/search{:extension [#fhir/Extension{}]}
      "f24daf4f"

      #fhir.Bundle.entry/search{:mode #fhir/code "match"}
      "5912b48c"

      #fhir.Bundle.entry/search{:score #fhir/decimal 1M}
      "2b2509dc"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir.Bundle.entry/search{} 24
      #fhir.Bundle.entry/search{:id "id-130825"} 96
      #fhir.Bundle.entry/search{:extension [#fhir/Extension{}]} 24
      #fhir.Bundle.entry/search{:mode #fhir/code "match"} 24
      #fhir.Bundle.entry/search{:score #fhir/decimal 1M} 72))

  (testing "references"
    (is (empty? (type/references #fhir.Bundle.entry/search{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir.Bundle.entry/search{} "#fhir.Bundle.entry/search{}"
      #fhir.Bundle.entry/search{:id "212329"} "#fhir.Bundle.entry/search{:id \"212329\"}")))

(deftest codeable-concept-test
  (testing "type"
    (is (= :fhir/CodeableConcept (:fhir/type #fhir/CodeableConcept{}))))

  (testing "interning"
    (testing "instances with id's are not interned"
      (satisfies-prop 100
        (prop/for-all [x (fg/codeable-concept :id fg/id-value)]
          (not-interned? x (recreate type/codeable-concept x)))))

    (testing "instances with not interned extensions are not interned"
      (satisfies-prop 100
        (prop/for-all [x (fg/codeable-concept :extension (gen/vector markdown-extension-gen 1))]
          (not-interned? x (recreate type/codeable-concept x))))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/CodeableConcept{}
      "7d180799"

      #fhir/CodeableConcept{:id "id-141755"}
      "b8c50bee"

      #fhir/CodeableConcept{:extension [#fhir/Extension{}]}
      "5ff0f810"

      #fhir/CodeableConcept{:coding [#fhir/Coding{}]}
      "e3a494d4"

      #fhir/CodeableConcept{:text #fhir/string "text-153829"}
      "7aca59d"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/CodeableConcept{} 0
      #fhir/CodeableConcept{:id "id-141755"} 96
      #fhir/CodeableConcept{:extension [#fhir/Extension{}]} 0
      #fhir/CodeableConcept{:coding [#fhir/Coding{}]} 0
      #fhir/CodeableConcept{:coding [#fhir/Coding{:id "foo"}]} 184
      #fhir/CodeableConcept{:text #fhir/string-interned "text-153829"} 0
      #fhir/CodeableConcept{:text #fhir/string "text-153829"} 88))

  (testing "references"
    (is (empty? (type/references #fhir/CodeableConcept{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/CodeableConcept{} "#fhir/CodeableConcept{}"
      #fhir/CodeableConcept{:id "212329"} "#fhir/CodeableConcept{:id \"212329\"}")))

(deftest coding-test
  (testing "type"
    (is (= :fhir/Coding (:fhir/type #fhir/Coding{}))))

  (testing "interning"
    (testing "instances with id's are not interned"
      (satisfies-prop 100
        (prop/for-all [x (fg/coding :id fg/id-value)]
          (not-interned? x (recreate type/coding x)))))

    (testing "instances with not interned extensions are not interned"
      (satisfies-prop 100
        (prop/for-all [x (fg/coding :extension (gen/vector markdown-extension-gen 1))]
          (not-interned? x (recreate type/coding x))))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/Coding{}
      "24e7e891"

      #fhir/Coding{:id "id-204201"}
      "c1c82c65"

      #fhir/Coding{:extension [#fhir/Extension{}]}
      "e1d440bb"

      #fhir/Coding{:system #fhir/uri "system-202808"}
      "da808d2d"

      #fhir/Coding{:version #fhir/string "version-154317"}
      "9df26acc"

      #fhir/Coding{:code #fhir/code "code-202828"}
      "74e3328d"

      #fhir/Coding{:display #fhir/string "display-154256"}
      "baac923d"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/Coding{} 0
      #fhir/Coding{:id "id-204201"} 112
      #fhir/Coding{:extension [#fhir/Extension{}]} 0
      #fhir/Coding{:system #fhir/uri-interned "system-202808"} 0
      #fhir/Coding{:version #fhir/string-interned "version-154317"} 0
      #fhir/Coding{:code #fhir/code "code-202828"} 0
      #fhir/Coding{:display #fhir/string-interned "display-154256"} 0
      #fhir/Coding{:display #fhir/string "display-154256"} 112))

  (testing "references"
    (is (empty? (type/references #fhir/Coding{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Coding{} "#fhir/Coding{}"
      #fhir/Coding{:id "212329"} "#fhir/Coding{:id \"212329\"}")))

(deftest contact-detail-test
  (testing "type"
    (is (= :fhir/ContactDetail (:fhir/type #fhir/ContactDetail{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/ContactDetail{} "#fhir/ContactDetail{}"
      #fhir/ContactDetail{:id "212329"} "#fhir/ContactDetail{:id \"212329\"}")))

(deftest contact-point-test
  (testing "type"
    (is (= :fhir/ContactPoint (:fhir/type #fhir/ContactPoint{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/ContactPoint{} "#fhir/ContactPoint{}"
      #fhir/ContactPoint{:id "212329"} "#fhir/ContactPoint{:id \"212329\"}")))

(deftest contributor-test
  (testing "type"
    (is (= :fhir/Contributor (:fhir/type #fhir/Contributor{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Contributor{} "#fhir/Contributor{}"
      #fhir/Contributor{:id "212329"} "#fhir/Contributor{:id \"212329\"}")))

(deftest count-test
  (testing "type"
    (is (= :fhir/Count (:fhir/type #fhir/Count{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Count{} "#fhir/Count{}"
      #fhir/Count{:id "212329"} "#fhir/Count{:id \"212329\"}")))

(deftest data-requirement-test
  (testing "type"
    (is (= :fhir/DataRequirement (:fhir/type #fhir/DataRequirement{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/DataRequirement{} "#fhir/DataRequirement{}"
      #fhir/DataRequirement{:id "212329"} "#fhir/DataRequirement{:id \"212329\"}")))

(deftest data-requirement-code-filter-test
  (testing "type"
    (is (= :fhir.DataRequirement/codeFilter (:fhir/type #fhir.DataRequirement/codeFilter{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir.DataRequirement/codeFilter{} "#fhir.DataRequirement/codeFilter{}"
      #fhir.DataRequirement/codeFilter{:id "212329"} "#fhir.DataRequirement/codeFilter{:id \"212329\"}")))

(deftest data-requirement-date-filter-test
  (testing "type"
    (is (= :fhir.DataRequirement/dateFilter (:fhir/type #fhir.DataRequirement/dateFilter{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir.DataRequirement/dateFilter{} "#fhir.DataRequirement/dateFilter{}"
      #fhir.DataRequirement/dateFilter{:id "212329"} "#fhir.DataRequirement/dateFilter{:id \"212329\"}")))

(deftest data-requirement-sort-test
  (testing "type"
    (is (= :fhir.DataRequirement/sort (:fhir/type #fhir.DataRequirement/sort{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir.DataRequirement/sort{} "#fhir.DataRequirement/sort{}"
      #fhir.DataRequirement/sort{:id "212329"} "#fhir.DataRequirement/sort{:id \"212329\"}")))

(deftest distance-test
  (testing "type"
    (is (= :fhir/Distance (:fhir/type #fhir/Distance{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Distance{} "#fhir/Distance{}"
      #fhir/Distance{:id "212329"} "#fhir/Distance{:id \"212329\"}")))

(deftest duration-test
  (testing "type"
    (is (= :fhir/Duration (:fhir/type #fhir/Duration{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Duration{} "#fhir/Duration{}"
      #fhir/Duration{:id "212329"} "#fhir/Duration{:id \"212329\"}")))

(deftest expression-test
  (testing "type"
    (is (= :fhir/Expression (:fhir/type #fhir/Expression{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Expression{} "#fhir/Expression{}"
      #fhir/Expression{:id "212329"} "#fhir/Expression{:id \"212329\"}")))

(deftest dosage-test
  (testing "type"
    (is (= :fhir/Dosage (:fhir/type #fhir/Dosage{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Dosage{} "#fhir/Dosage{}"
      #fhir/Dosage{:id "212329"} "#fhir/Dosage{:id \"212329\"}")))

(deftest dosage-dose-and-rate-test
  (testing "type"
    (is (= :fhir.Dosage/doseAndRate (:fhir/type #fhir.Dosage/doseAndRate{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir.Dosage/doseAndRate{} "#fhir.Dosage/doseAndRate{}"
      #fhir.Dosage/doseAndRate{:id "212329"} "#fhir.Dosage/doseAndRate{:id \"212329\"}")))

(deftest extension-test
  (testing "type"
    (is (= :fhir/Extension (:fhir/type #fhir/Extension{}))))

  (testing "interning"
    (testing "instances with code values are interned"
      (are [x y] (interned? x y)
        #fhir/Extension{:url "foo" :value #fhir/code "bar"}
        #fhir/Extension{:url "foo" :value #fhir/code "bar"}))

    (testing "instances with code values and interned extensions are interned"
      (are [x y] (interned? x y)
        #fhir/Extension
         {:extension [#fhir/Extension{:url "foo" :value #fhir/code "bar"}]
          :url "foo"
          :value #fhir/code "bar"}
        #fhir/Extension
         {:extension [#fhir/Extension{:url "foo" :value #fhir/code "bar"}]
          :url "foo"
          :value #fhir/code "bar"}))

    (testing "instances with code values but id's are not interned"
      (are [x y] (not-interned? x y)
        #fhir/Extension{:id "foo" :url "bar" :value #fhir/code "baz"}
        #fhir/Extension{:id "foo" :url "bar" :value #fhir/code "baz"}))

    (testing "instances with string values are not interned"
      (are [x y] (not-interned? x y)
        #fhir/Extension{:url "foo" :value #fhir/string "barbar"}
        #fhir/Extension{:url "foo" :value #fhir/string "barbar"})))

  (testing "equals"
    (is (= #fhir/Extension{:url ""} #fhir/Extension{:url ""})))

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

      #fhir/Extension{:value #fhir/code "value-130953"}
      "befce87a"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/Extension{} 0
      #fhir/Extension{:id "id-204201"} 96
      #fhir/Extension{:extension [#fhir/Extension{}]} 0
      #fhir/Extension{:extension [#fhir/Extension{} #fhir/Extension{}]} 0
      #fhir/Extension{:url "url-130945"} 0
      #fhir/Extension{:value #fhir/code "value-130953"} 0
      #fhir/Extension{:url "url-130945" :value #fhir/string "12345"} 88))

  (testing "references"
    (is (empty? (type/references #fhir/Extension{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Extension{} "#fhir/Extension{}"
      #fhir/Extension{:id "212329"} "#fhir/Extension{:id \"212329\"}")))

(deftest human-name-test
  (testing "type"
    (is (= :fhir/HumanName (:fhir/type #fhir/HumanName{}))))

  (testing "interning"
    (are [x y] (not-interned? x y)
      #fhir/HumanName{:id "foo"}
      #fhir/HumanName{:id "foo"}

      #fhir/HumanName{:extension [#fhir/Extension{:url "foo" :value #fhir/string "barbar"}]}
      #fhir/HumanName{:extension [#fhir/Extension{:url "foo" :value #fhir/string "barbar"}]}

      #fhir/HumanName{:text #fhir/string "foofoo"}
      #fhir/HumanName{:text #fhir/string "foofoo"}

      #fhir/HumanName{:family #fhir/string "foofoo"}
      #fhir/HumanName{:family #fhir/string "foofoo"}))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/HumanName{}
      "af56fc23"

      #fhir/HumanName{:id "id-130739"}
      "ebba60f8"

      #fhir/HumanName{:extension [#fhir/Extension{}]}
      "4947bc16"

      #fhir/HumanName{:use #fhir/code "use-155144"}
      "60b2b58c"

      #fhir/HumanName{:text #fhir/string "text-212402"}
      "b9ab5f61"

      #fhir/HumanName{:family #fhir/string "family-212422"}
      "915831d8"

      #fhir/HumanName{:given [#fhir/string "given-212441"]}
      "e26a58ee"

      #fhir/HumanName{:given [#fhir/string "given-212448" #fhir/string "given-212454"]}
      "b46d5198"

      #fhir/HumanName{:prefix [#fhir/string "prefix-212514"]}
      "1a411067"

      #fhir/HumanName{:prefix [#fhir/string "prefix-212523" #fhir/string "prefix-212525"]}
      "32529f07"

      #fhir/HumanName{:suffix [#fhir/string "suffix-212542"]}
      "3181f719"

      #fhir/HumanName{:suffix [#fhir/string "suffix-212547" #fhir/string "suffix-212554"]}
      "69ca06e0"

      #fhir/HumanName{:period #fhir/Period{}}
      "18b2a823"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/HumanName{} 40
      #fhir/HumanName{:id "id-130739"} 112
      #fhir/HumanName{:extension [#fhir/Extension{}]} 40
      #fhir/HumanName{:use #fhir/code "use-155144"} 40
      #fhir/HumanName{:text #fhir/string "text-212402"} 104
      #fhir/HumanName{:family #fhir/string "family-212422"} 112
      #fhir/HumanName{:given [#fhir/string "given-212441"]} 160
      #fhir/HumanName{:given [#fhir/string "given-212448" #fhir/string "given-212454"]} 232
      #fhir/HumanName{:prefix [#fhir/string "prefix-212514"]} 168
      #fhir/HumanName{:prefix [#fhir/string "prefix-212523" #fhir/string "prefix-212525"]} 248
      #fhir/HumanName{:suffix [#fhir/string "suffix-212542"]} 168
      #fhir/HumanName{:suffix [#fhir/string "suffix-212547" #fhir/string "suffix-212554"]} 248
      #fhir/HumanName{:period #fhir/Period{}} 40))

  (testing "references"
    (is (empty? (type/references #fhir/HumanName{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/HumanName{} "#fhir/HumanName{}"
      #fhir/HumanName{:id "212625"} "#fhir/HumanName{:id \"212625\"}")))

(deftest identifier-test
  (testing "type"
    (is (= :fhir/Identifier (:fhir/type #fhir/Identifier{}))))

  (testing "interning"
    (are [x y] (not-interned? x y)
      #fhir/Identifier{:id "foo"}
      #fhir/Identifier{:id "foo"}

      #fhir/Identifier{:extension [#fhir/Extension{:url "foo" :value #fhir/string "barbar"}]}
      #fhir/Identifier{:extension [#fhir/Extension{:url "foo" :value #fhir/string "barbar"}]}

      #fhir/Identifier{:value #fhir/string "foofoo"}
      #fhir/Identifier{:value #fhir/string "foofoo"}))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/Identifier{}
      "14336e1c"

      #fhir/Identifier{:id "id-130739"}
      "57c166f0"

      #fhir/Identifier{:extension [#fhir/Extension{}]}
      "810b77ff"

      #fhir/Identifier{:use #fhir/code "use-155144"}
      "4bf89602"

      #fhir/Identifier{:type #fhir/CodeableConcept{}}
      "3f7dea5e"

      #fhir/Identifier{:system #fhir/uri "system-145514"}
      "acbabb5d"

      #fhir/Identifier{:value #fhir/string "value-145509"}
      "de7e521f"

      #fhir/Identifier{:period #fhir/Period{}}
      "8a73bfa3"

      #fhir/Identifier{:assigner #fhir/Reference{}}
      "aa994e1e"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/Identifier{} 40
      #fhir/Identifier{:id "id-130739"} 112
      #fhir/Identifier{:extension [#fhir/Extension{}]} 40
      #fhir/Identifier{:use #fhir/code "use-155144"} 40
      #fhir/Identifier{:type #fhir/CodeableConcept{}} 40
      #fhir/Identifier{:system #fhir/uri-interned "system-145514"} 40
      #fhir/Identifier{:value #fhir/string "value-145509"} 104
      #fhir/Identifier{:period #fhir/Period{}} 40
      #fhir/Identifier{:assigner #fhir/Reference{}} 40))

  (testing "references"
    (is (empty? (type/references #fhir/Identifier{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Identifier{} "#fhir/Identifier{}"
      #fhir/Identifier{:id "212329"} "#fhir/Identifier{:id \"212329\"}")))

(deftest meta-test
  (testing "type"
    (is (= :fhir/Meta (:fhir/type #fhir/Meta{}))))

  (testing "interning"
    (are [x y] (not-interned? x y)
      #fhir/Meta{:id "foo"}
      #fhir/Meta{:id "foo"}

      #fhir/Meta{:extension [#fhir/Extension{:url "foo" :value #fhir/string "barbar"}]}
      #fhir/Meta{:extension [#fhir/Extension{:url "foo" :value #fhir/string "barbar"}]}

      #fhir/Meta{:versionId #fhir/id "foo"}
      #fhir/Meta{:versionId #fhir/id "foo"}

      #fhir/Meta{:lastUpdated #fhir/instant #system/date-time "2020-01-01T00:00:00Z"}
      #fhir/Meta{:lastUpdated #fhir/instant #system/date-time "2020-01-01T00:00:00Z"})

    (are [x y] (interned? x y)
      #fhir/Meta{:extension [#fhir/Extension{:url "foo" :value #fhir/code "bar"}]}
      #fhir/Meta{:extension [#fhir/Extension{:url "foo" :value #fhir/code "bar"}]}

      #fhir/Meta{:source #fhir/uri "foo"}
      #fhir/Meta{:source #fhir/uri "foo"}

      #fhir/Meta{:profile [#fhir/canonical "foo"]}
      #fhir/Meta{:profile [#fhir/canonical "foo"]}

      #fhir/Meta{:security [#fhir/Coding{:system #fhir/uri "foo" :code #fhir/code "bar"}]}
      #fhir/Meta{:security [#fhir/Coding{:system #fhir/uri "foo" :code #fhir/code "bar"}]}

      #fhir/Meta{:tag [#fhir/Coding{:system #fhir/uri "foo" :code #fhir/code "bar"}]}
      #fhir/Meta{:tag [#fhir/Coding{:system #fhir/uri "foo" :code #fhir/code "bar"}]}))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/Meta{}
      "cbae28fd"

      #fhir/Meta{:id "id-130825"}
      "c2c18a00"

      #fhir/Meta{:extension [#fhir/Extension{}]}
      "aaf41f94"

      #fhir/Meta{:versionId #fhir/id "versionId-161415"}
      "9edaa9b"

      #fhir/Meta{:lastUpdated #fhir/instant #system/date-time "2020-01-01T00:00:00Z"}
      "df91eaa0"

      #fhir/Meta{:source #fhir/uri "source-161629"}
      "bc99bc82"

      #fhir/Meta{:profile [#fhir/canonical "profile-uri-145024"]}
      "b13c3d52"

      #fhir/Meta{:security [#fhir/Coding{}]}
      "9b7633bc"

      #fhir/Meta{:tag [#fhir/Coding{}]}
      "96e4e336"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/Meta{} 0
      #fhir/Meta{:id "id-130825"} 112
      #fhir/Meta{:extension [#fhir/Extension{}]} 0
      #fhir/Meta{:versionId #fhir/id "versionId-161415"} 112
      #fhir/Meta{:lastUpdated #fhir/instant #system/date-time "2020-01-01T00:00:00Z"} 120
      #fhir/Meta{:source #fhir/uri "source-161629"} 112
      #fhir/Meta{:profile [#fhir/canonical "profile-uri-145024"]} 0
      #fhir/Meta{:security [#fhir/Coding{}]} 0
      #fhir/Meta{:tag [#fhir/Coding{}]} 0))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/Meta{}
      []

      #fhir/Meta{:extension [#fhir/Extension{}]}
      []

      #fhir/Meta
       {:extension
        [#fhir/Extension{:value #fhir/Reference{:reference #fhir/string "Patient/2"}}]}
      [["Patient" "2"]]))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Meta{} "#fhir/Meta{}"
      #fhir/Meta{:id "212329"} "#fhir/Meta{:id \"212329\"}")))

(deftest money-test
  (testing "type"
    (is (= :fhir/Money (:fhir/type #fhir/Money{}))))

  (testing "interning"
    (are [x y] (not-interned? x y)
      #fhir/Money{:id "foo"}
      #fhir/Money{:id "foo"}

      #fhir/Money{:extension [#fhir/Extension{:url "foo" :value #fhir/string "barbar"}]}
      #fhir/Money{:extension [#fhir/Extension{:url "foo" :value #fhir/string "barbar"}]}

      #fhir/Money{:value #fhir/decimal 1M}
      #fhir/Money{:value #fhir/decimal 1M}))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/Money{}
      "f64d19e"

      #fhir/Money{:id "id-130825"}
      "506137b"

      #fhir/Money{:extension [#fhir/Extension{}]}
      "cf162845"

      #fhir/Money{:value #fhir/decimal 1M}
      "1c2ff9f3"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/Money{} 24
      #fhir/Money{:id "id-130825"} 96
      #fhir/Money{:extension [#fhir/Extension{}]} 24
      #fhir/Money{:value #fhir/decimal 1M} 72))

  (testing "references"
    (is (empty? (type/references #fhir/Money{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Money{} "#fhir/Money{}"
      #fhir/Money{:id "212329"} "#fhir/Money{:id \"212329\"}")))

(deftest narrative-test
  (testing "type"
    (is (= :fhir/Narrative (:fhir/type #fhir/Narrative{}))))

  (testing "interning"
    (are [x y] (not-interned? x y)
      #fhir/Narrative{:id "foo"}
      #fhir/Narrative{:id "foo"}

      #fhir/Narrative{:extension [#fhir/Extension{:url "foo" :value #fhir/string "barbar"}]}
      #fhir/Narrative{:extension [#fhir/Extension{:url "foo" :value #fhir/string "barbar"}]}

      #fhir/Narrative{:status #fhir/code "generated"}
      #fhir/Narrative{:status #fhir/code "generated"}))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/Narrative{}
      "f783207"

      #fhir/Narrative{:id "id-130825"}
      "7f5e29d9"

      #fhir/Narrative{:extension [#fhir/Extension{}]}
      "9182f6bd"

      #fhir/Narrative{:status #fhir/code "generated"}
      "434e11b4"

      #fhir/Narrative{:div #fhir/xhtml "<div></div>"}
      "7f9f2215"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/Narrative{} 24
      #fhir/Narrative{:id "id-130825"} 96
      #fhir/Narrative{:extension [#fhir/Extension{}]} 24
      #fhir/Narrative{:status #fhir/code "generated"} 24
      #fhir/Narrative{:div #fhir/xhtml "<div></div>"} 88))

  (testing "references"
    (is (empty? (type/references #fhir/Narrative{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Narrative{} "#fhir/Narrative{}"
      #fhir/Narrative{:id "212329"} "#fhir/Narrative{:id \"212329\"}")))

(deftest parameter-definition-test
  (testing "type"
    (is (= :fhir/ParameterDefinition (:fhir/type #fhir/ParameterDefinition{}))))

  (testing "interning"
    (are [x y] (not-interned? x y)
      #fhir/ParameterDefinition{:id "foo"}
      #fhir/ParameterDefinition{:id "foo"}

      #fhir/ParameterDefinition{:extension [#fhir/Extension{:url "foo" :value #fhir/string "barbar"}]}
      #fhir/ParameterDefinition{:extension [#fhir/Extension{:url "foo" :value #fhir/string "barbar"}]}

      #fhir/ParameterDefinition{:type #fhir/code "Patient"}
      #fhir/ParameterDefinition{:type #fhir/code "Patient"}))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/ParameterDefinition{}
      "cca66a8a"

      #fhir/ParameterDefinition{:id "id-130825"}
      "71cc8c06"

      #fhir/ParameterDefinition{:extension [#fhir/Extension{}]}
      "b664c391"

      #fhir/ParameterDefinition{:type #fhir/code "Patient"}
      "be85c928"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/ParameterDefinition{} 40
      #fhir/ParameterDefinition{:id "id-130825"} 112
      #fhir/ParameterDefinition{:extension [#fhir/Extension{}]} 40
      #fhir/ParameterDefinition{:type #fhir/code "Patient"} 40))

  (testing "references"
    (is (empty? (type/references #fhir/ParameterDefinition{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/ParameterDefinition{} "#fhir/ParameterDefinition{}"
      #fhir/ParameterDefinition{:id "212329"} "#fhir/ParameterDefinition{:id \"212329\"}")))

(deftest period-test
  (testing "type"
    (is (= :fhir/Period (:fhir/type #fhir/Period{}))))

  (testing "interning"
    (are [x y] (not-interned? x y)
      #fhir/Period{:id "foo"}
      #fhir/Period{:id "foo"}

      #fhir/Period{:extension [#fhir/Extension{:url "foo" :value #fhir/string "barbar"}]}
      #fhir/Period{:extension [#fhir/Extension{:url "foo" :value #fhir/string "barbar"}]}

      #fhir/Period{:start #fhir/dateTime #system/date-time "2020"}
      #fhir/Period{:start #fhir/dateTime #system/date-time "2020"})

    (are [x y] (interned? x y)
      #fhir/Period{:extension [#fhir/Extension{:url "foo" :value #fhir/code "bar"}]}
      #fhir/Period{:extension [#fhir/Extension{:url "foo" :value #fhir/code "bar"}]}))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/Period{}
      "e5f76205"

      #fhir/Period{:id "id-130710"}
      "29c53420"

      #fhir/Period{:extension [#fhir/Extension{}]}
      "92e4ba37"

      #fhir/Period{:start #fhir/dateTime #system/date-time "2020"}
      "f1b7c952"

      #fhir/Period{:end #fhir/dateTime #system/date-time "2020"}
      "434787dd"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/Period{} 0
      #fhir/Period{:id "id-130710"} 96
      #fhir/Period{:extension [#fhir/Extension{}]} 0
      #fhir/Period{:start #fhir/dateTime #system/date-time "2020"} 56
      #fhir/Period{:end #fhir/dateTime #system/date-time "2020"} 56
      #fhir/Period{:start #fhir/dateTime #system/date-time "2020" :end #fhir/dateTime #system/date-time "2021"} 88))

  (testing "references"
    (is (empty? (type/references #fhir/Period{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Period{} "#fhir/Period{}"
      #fhir/Period{:id "212329"} "#fhir/Period{:id \"212329\"}")))

(deftest quantity-test
  (testing "type"
    (is (= :fhir/Quantity (:fhir/type #fhir/Quantity{}))))

  (testing "interning"
    (are [x y] (not-interned? x y)
      #fhir/Quantity{:id "foo"}
      #fhir/Quantity{:id "foo"}

      #fhir/Quantity{:extension [#fhir/Extension{:url "foo" :value #fhir/string "barbar"}]}
      #fhir/Quantity{:extension [#fhir/Extension{:url "foo" :value #fhir/string "barbar"}]}

      #fhir/Quantity{:value #fhir/decimal 1M}
      #fhir/Quantity{:value #fhir/decimal 1M})

    (are [x y] (interned? x y)
      #fhir/Quantity{:extension [#fhir/Extension{:url "foo" :value #fhir/code "bar"}]}
      #fhir/Quantity{:extension [#fhir/Extension{:url "foo" :value #fhir/code "bar"}]}

      #fhir/Quantity{:comparator #fhir/code "foo"}
      #fhir/Quantity{:comparator #fhir/code "foo"}

      #fhir/Quantity{:unit #fhir/string "foo"}
      #fhir/Quantity{:unit #fhir/string "foo"}

      #fhir/Quantity{:system #fhir/uri "foo"}
      #fhir/Quantity{:system #fhir/uri "foo"}

      #fhir/Quantity{:code #fhir/code "foo"}
      #fhir/Quantity{:code #fhir/code "foo"}))

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

      #fhir/Quantity{:comparator #fhir/code "comparator-153342"}
      "6339e3e8"

      #fhir/Quantity{:unit #fhir/string "unit-153351"}
      "d8f92891"

      #fhir/Quantity{:system #fhir/uri "system-153337"}
      "98f918ba"

      #fhir/Quantity{:code #fhir/code "code-153427"}
      "7ff49528"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/Quantity{} 0
      #fhir/Quantity{:id "id-141848"} 104
      #fhir/Quantity{:extension [#fhir/Extension{}]} 0
      #fhir/Quantity{:value #fhir/decimal 1M} 80
      #fhir/Quantity{:comparator #fhir/code "comparator-153342"} 0
      #fhir/Quantity{:unit #fhir/string "unit-153351"} 96
      #fhir/Quantity{:unit #fhir/string-interned "unit-153351"} 0
      #fhir/Quantity{:system #fhir/uri-interned "system-153337"} 0
      #fhir/Quantity{:code #fhir/code "code-153427"} 0))

  (testing "references"
    (is (empty? (type/references #fhir/Quantity{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Quantity{} "#fhir/Quantity{}"
      #fhir/Quantity{:id "212329"} "#fhir/Quantity{:id \"212329\"}")))

(deftest range-test
  (testing "type"
    (is (= :fhir/Range (:fhir/type #fhir/Range{}))))

  (testing "interning"
    (are [x y] (not-interned? x y)
      #fhir/Range{:id "foo"}
      #fhir/Range{:id "foo"}

      #fhir/Range{:extension [#fhir/Extension{:url "foo" :value #fhir/string "barbar"}]}
      #fhir/Range{:extension [#fhir/Extension{:url "foo" :value #fhir/string "barbar"}]}

      #fhir/Range{:low #fhir/Quantity{:value #fhir/decimal 1M}}
      #fhir/Range{:low #fhir/Quantity{:value #fhir/decimal 1M}}

      #fhir/Range{:high #fhir/Quantity{:value #fhir/decimal 1M}}
      #fhir/Range{:high #fhir/Quantity{:value #fhir/decimal 1M}})

    (are [x y] (interned? x y)
      #fhir/Range{:extension [#fhir/Extension{:url "foo" :value #fhir/code "bar"}]}
      #fhir/Range{:extension [#fhir/Extension{:url "foo" :value #fhir/code "bar"}]}))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/Range{}
      "129e217"

      #fhir/Range{:id "id-130710"}
      "3bf1204b"

      #fhir/Range{:extension [#fhir/Extension{}]}
      "458d6390"

      #fhir/Range{:low #fhir/Quantity{:value #fhir/decimal 1M}}
      "2e95d572"

      #fhir/Range{:high #fhir/Quantity{:value #fhir/decimal 1M}}
      "56047f86"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/Range{} 0
      #fhir/Range{:id "id-130710"} 96
      #fhir/Range{:extension [#fhir/Extension{}]} 0
      #fhir/Range{:low #fhir/Quantity{:value #fhir/decimal 1M}} 104
      #fhir/Range{:high #fhir/Quantity{:value #fhir/decimal 1M}} 104
      #fhir/Range{:low #fhir/Quantity{:value #fhir/decimal 1M} :high #fhir/Quantity{:value #fhir/decimal 2M}} 184))

  (testing "references"
    (is (empty? (type/references #fhir/Range{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Range{} "#fhir/Range{}"
      #fhir/Range{:id "212329"} "#fhir/Range{:id \"212329\"}")))

(deftest ratio-test
  (testing "type"
    (is (= :fhir/Ratio (:fhir/type #fhir/Ratio{}))))

  (testing "interning"
    (are [x y] (not-interned? x y)
      #fhir/Ratio{:id "foo"}
      #fhir/Ratio{:id "foo"}

      #fhir/Ratio{:extension [#fhir/Extension{:url "foo" :value #fhir/string "barbar"}]}
      #fhir/Ratio{:extension [#fhir/Extension{:url "foo" :value #fhir/string "barbar"}]}

      #fhir/Ratio{:numerator #fhir/Quantity{:value #fhir/decimal 1M}}
      #fhir/Ratio{:numerator #fhir/Quantity{:value #fhir/decimal 1M}}

      #fhir/Ratio{:denominator #fhir/Quantity{:value #fhir/decimal 1M}}
      #fhir/Ratio{:denominator #fhir/Quantity{:value #fhir/decimal 1M}})

    (are [x y] (interned? x y)
      #fhir/Ratio{:extension [#fhir/Extension{:url "foo" :value #fhir/code "bar"}]}
      #fhir/Ratio{:extension [#fhir/Extension{:url "foo" :value #fhir/code "bar"}]}))

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

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/Ratio{} 0
      #fhir/Ratio{:id "id-130710"} 96
      #fhir/Ratio{:extension [#fhir/Extension{}]} 0
      #fhir/Ratio{:numerator #fhir/Quantity{:value #fhir/decimal 1M}} 104
      #fhir/Ratio{:denominator #fhir/Quantity{:value #fhir/decimal 1M}} 104))

  (testing "references"
    (is (empty? (type/references #fhir/Ratio{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Ratio{} "#fhir/Ratio{}"
      #fhir/Ratio{:id "212329"} "#fhir/Ratio{:id \"212329\"}")))

(deftest reference-test
  (testing "type"
    (is (= :fhir/Reference (:fhir/type #fhir/Reference{}))))

  (testing "interning"
    (are [x y] (not-interned? x y)
      #fhir/Reference{:id "foo"}
      #fhir/Reference{:id "foo"}

      #fhir/Reference{:extension [#fhir/Extension{:url "foo" :value #fhir/string "barbar"}]}
      #fhir/Reference{:extension [#fhir/Extension{:url "foo" :value #fhir/string "barbar"}]}

      #fhir/Reference{:reference #fhir/string "foofoo"}
      #fhir/Reference{:reference #fhir/string "foofoo"}

      #fhir/Reference{:identifier #fhir/Identifier{:value #fhir/string "foofoo"}}
      #fhir/Reference{:identifier #fhir/Identifier{:value #fhir/string "foofoo"}}

      #fhir/Reference{:display #fhir/string "foofoo"}
      #fhir/Reference{:display #fhir/string "foofoo"}))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/Reference{}
      "6498613c"

      #fhir/Reference{:id "id-130802"}
      "a48cca5a"

      #fhir/Reference{:extension [#fhir/Extension{}]}
      "210e3eb7"

      #fhir/Reference{:reference #fhir/string "Patient/0"}
      "cd80b8ac"

      #fhir/Reference{:type #fhir/uri "type-161222"}
      "2fe271cd"

      #fhir/Reference{:identifier #fhir/Identifier{}}
      "eb066d27"

      #fhir/Reference{:display #fhir/string "display-161314"}
      "543cf75f"))

  (testing "mem-size"
    (are [s mem-size] (= mem-size (Base/memSize s))
      #fhir/Reference{} 32
      #fhir/Reference{:id "id-130802"} 104
      #fhir/Reference{:extension [#fhir/Extension{}]} 32
      #fhir/Reference{:reference #fhir/string "Patient/0"} 96
      #fhir/Reference{:type #fhir/uri-interned "type-161222"} 32
      #fhir/Reference{:identifier #fhir/Identifier{}} 72
      #fhir/Reference{:display #fhir/string "display-161314"} 104))

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
                   {:reference #fhir/string "Patient/1"}}]}
      [["Patient" "1"]]

      #fhir/Reference{:reference #fhir/string "Patient/0"}
      [["Patient" "0"]]

      #fhir/Reference{:reference #fhir/string "Patient"}
      []

      #fhir/Reference{:reference #fhir/string ""}
      []

      #fhir/Reference
       {:extension
        [#fhir/Extension
          {:value #fhir/Reference
                   {:reference #fhir/string "Patient/0"}}]
        :reference #fhir/string "Patient/1"}
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

(deftest related-artifact-test
  (testing "type"
    (is (= :fhir/RelatedArtifact (:fhir/type #fhir/RelatedArtifact{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/RelatedArtifact{} "#fhir/RelatedArtifact{}"
      #fhir/RelatedArtifact{:id "212329"} "#fhir/RelatedArtifact{:id \"212329\"}")))

(deftest sampled-data-test
  (testing "type"
    (is (= :fhir/SampledData (:fhir/type #fhir/SampledData{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/SampledData{} "#fhir/SampledData{}"
      #fhir/SampledData{:id "212329"} "#fhir/SampledData{:id \"212329\"}")))

(deftest signature-test
  (testing "type"
    (is (= :fhir/Signature (:fhir/type #fhir/Signature{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Signature{} "#fhir/Signature{}"
      #fhir/Signature{:id "212329"} "#fhir/Signature{:id \"212329\"}")))

(deftest timing-test
  (testing "type"
    (is (= :fhir/Timing (:fhir/type #fhir/Timing{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Timing{} "#fhir/Timing{}"
      #fhir/Timing{:id "212329"} "#fhir/Timing{:id \"212329\"}")))

(deftest timing-repeat-test
  (testing "type"
    (is (= :fhir.Timing/repeat (:fhir/type #fhir.Timing/repeat{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir.Timing/repeat{} "#fhir.Timing/repeat{}"
      #fhir.Timing/repeat{:id "212329"} "#fhir.Timing/repeat{:id \"212329\"}")))

(deftest trigger-definition-test
  (testing "type"
    (is (= :fhir/TriggerDefinition (:fhir/type #fhir/TriggerDefinition{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/TriggerDefinition{} "#fhir/TriggerDefinition{}"
      #fhir/TriggerDefinition{:id "212329"} "#fhir/TriggerDefinition{:id \"212329\"}")))

(deftest usage-context-test
  (testing "type"
    (is (= :fhir/UsageContext (:fhir/type #fhir/UsageContext{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/UsageContext{} "#fhir/UsageContext{}"
      #fhir/UsageContext{:id "212329"} "#fhir/UsageContext{:id \"212329\"}")))
