(ns blaze.fhir.spec.type-test-mem
  (:require
   [blaze.fhir.spec.memory :as mem]
   [blaze.fhir.spec.type :as type]
   [blaze.test-util]
   [clojure.alpha.spec :as s2]
   [clojure.string :as str]
   [clojure.test :refer [are deftest is testing]])
  (:import
   [java.time Instant ZoneOffset]))

(deftest mem-test
  (are [x size] (= (mem/total-size x) size)
    #fhir/integer 1 16

    #fhir/integer64 1 24

    #fhir/string"" 40
    #fhir/string"a" 48
    #fhir/string{:value "a"} 48
    (type/string (str/join (repeat 8 "a"))) 48
    (type/string (str/join (repeat 9 "a"))) 56
    #fhir/string{:id "0" :value "foo"} 136

    #fhir/decimal 1.1M 40

    #fhir/uri"" 96
    #fhir/uri"a" 120

    #fhir/url"" 56
    #fhir/url"a" 64

    #fhir/canonical"" 96
    #fhir/canonical"a" 120

    #fhir/base64Binary"" 56
    #fhir/base64Binary"YQo=" 64
    #fhir/base64Binary"MTA1NjE0Cg==" 72

    #fhir/date"2020" 16
    #fhir/date"2020-01" 24
    #fhir/date"2020-01-01" 24

    #fhir/dateTime"2020" 16
    #fhir/dateTime"2020-01" 24
    #fhir/dateTime"2020-01-01" 24

    #fhir/dateTime"2020-01-01T00:00:00" 72
    #fhir/dateTime"2020-01-01T00:00:00.000" 72

    #fhir/time"13:53:21" 24

    #fhir/code"" 96
    #fhir/code"175718" 120

    #fhir/oid"" 56
    #fhir/oid"175718" 64

    #fhir/id"" 56
    #fhir/id"175718" 64

    #fhir/markdown"" 56
    #fhir/markdown"175718" 64

    #fhir/unsignedInt 0 16
    #fhir/unsignedInt 175718 16

    #fhir/positiveInt 0 16
    #fhir/positiveInt 175718 16

    #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3" 32

    #fhir/xhtml"" 56
    #fhir/xhtml"175718" 64

    #fhir/Attachment{} 72

    #fhir/Extension{} 48

    #fhir/Coding{} 56

    #fhir/CodeableConcept{} 48

    #fhir/Quantity{} 56

    #fhir/Ratio{} 48

    #fhir/Period{} 48

    #fhir/Identifier{} 64

    #fhir/HumanName{} 64

    #fhir/Address{} 80
    #fhir/Address{:extension [#fhir/Extension{:url "url-120620" :value #fhir/code"code-120656"}]} 392
    #fhir/Address{:text "text-212402"} 136
    #fhir/Address{:line ["line-212441"]} 200

    #fhir/Reference{} 56

    #fhir/Meta{} 64
    #fhir/Meta{:profile [#fhir/canonical"foo"]} 248

    #fhir/BundleEntrySearch{} 48)

  (testing "interning"
    (are [x y] (= (mem/total-size x) (mem/total-size x y))
      #fhir/Address{:extension [#fhir/Extension{:url "foo" :value #fhir/code"bar"}]}
      #fhir/Address{:extension [#fhir/Extension{:url "foo" :value #fhir/code"bar"}]}

      #fhir/Meta{:profile [#fhir/canonical"foo"]}
      #fhir/Meta{:profile [#fhir/canonical"foo"]}

      #fhir/Coding{:system #fhir/uri"foo" :code #fhir/code"bar"}
      #fhir/Coding{:system #fhir/uri"foo" :code #fhir/code"bar"}))

  (testing "instant"
    (testing "backed by OffsetDateTime, taking into account shared offsets"
      (is (= 112 (- (mem/total-size #fhir/instant"2020-01-01T00:00:00+02:00")
                    (mem/total-size ZoneOffset/UTC)))))
    (testing "backed by java.time.Instant"
      (is (= 24 (mem/total-size Instant/EPOCH)))))

  (testing "dateTime"
    (testing "instance size taking into account shared offsets"
      (is (= 96 (- (mem/total-size #fhir/dateTime"2020-01-01T00:00:00Z")
                   (mem/total-size ZoneOffset/UTC))))))

  (testing "Meta"
    (testing "two interned instances take the same memory as one"
      (is (= 248 (mem/total-size #fhir/Meta{:profile [#fhir/canonical"foo"]}
                                 #fhir/Meta{:profile [#fhir/canonical"foo"]}))))))

(deftest extension-url-test
  (testing "conformed instance size"
    (testing "JSON"
      (testing "two interned instances take the same memory as one"
        (is (= (mem/total-size "foo")
               (mem/total-size
                (s2/conform :fhir.json.Extension/url (String. "foo"))
                (s2/conform :fhir.json.Extension/url (String. "foo")))))))

    (testing "CBOR"
      (testing "two interned instances take the same memory as one"
        (is (= (mem/total-size "foo")
               (mem/total-size
                (s2/conform :fhir.cbor.Extension/url (String. "foo"))
                (s2/conform :fhir.cbor.Extension/url (String. "foo")))))))))

(deftest extension-test
  (testing "conformed instance size"
    (testing "JSON"
      (are [json size] (= size (mem/total-size (s2/conform :fhir.json/Extension json)))
        {} 48
        {:url "foo" :valueCode "bar"} 216)

      (testing "two instances have only the 48 byte instance overhead"
        (is (= (+ (mem/total-size
                   (s2/conform :fhir.json/Extension
                               {:url "foo" :valueString "bar"}))
                  48)
               (mem/total-size
                (s2/conform :fhir.json/Extension
                            {:url (String. "foo") :valueString "bar"})
                (s2/conform :fhir.json/Extension
                            {:url (String. "foo") :valueString "bar"})))))

      (testing "two instances with code values take the same amount of memory as one"
        (is (= (mem/total-size
                (s2/conform :fhir.json/Extension
                            {:url "foo" :valueCode "bar"}))
               (mem/total-size
                (s2/conform :fhir.json/Extension
                            {:url (String. "foo") :valueCode "bar"})
                (s2/conform :fhir.json/Extension
                            {:url (String. "foo") :valueCode "bar"}))))))))

(deftest coding-test
  (testing "conformed instance size"
    (testing "JSON"
      (are [json size] (= size (mem/total-size (s2/conform :fhir.json/Coding json)))
        {} 56
        {:system "foo" :code "bar"} 296)

      (testing "two interned instances take the same memory as one"
        (is (= 296 (mem/total-size (s2/conform :fhir.json/Coding {:system "foo" :code "bar"})
                                   (s2/conform :fhir.json/Coding {:system "foo" :code "bar"}))))))))

(deftest quantity-unit-test
  (testing "conformed instance size"
    (testing "JSON"
      (testing "two interned instances take the same memory as one"
        (is (= (mem/total-size "foo")
               (mem/total-size
                (s2/conform :fhir.json.Quantity/unit (String. "foo"))
                (s2/conform :fhir.json.Quantity/unit (String. "foo")))))))

    (testing "CBOR"
      (testing "two interned instances take the same memory as one"
        (is (= (mem/total-size "foo")
               (mem/total-size
                (s2/conform :fhir.cbor.Quantity/unit (String. "foo"))
                (s2/conform :fhir.cbor.Quantity/unit (String. "foo")))))))))

(deftest human-name-test
  (testing "conformed instance size"
    (testing "JSON"
      (are [json size] (= size (mem/total-size (s2/conform :fhir.json/HumanName json)))
        {} 64
        {:use "usual"} 184
        {:given ["given-212441"]} 184))

    (testing "CBOR"
      (are [cbor size] (= size (mem/total-size (s2/conform :fhir.cbor/HumanName cbor)))
        {} 64
        {:use "usual"} 184
        {:given ["given-212441"]} 184))))

(deftest address-test
  (testing "conformed instance size"
    (are [json size] (= size (mem/total-size (s2/conform :fhir.json/Address json)))
      {} 80
      {:extension [{:url "foo1foo1" :valueCode "bar"}]} 360
      {:extension [{:url (String. "foo") :valueCode (String. "bar")}
                   {:url (String. "foo") :valueCode (String. "bar")}]} 360
      {:text "text-212402"} 136
      {:line ["line-212441"]} 200)))

(deftest meta-test
  (testing "conformed instance size"
    (are [json size] (= size (mem/total-size (s2/conform :fhir.json/Meta json)))
      {} 64
      {:versionId "1"} 128
      {:profile ["foo"]} 248)

    (testing "two interned instances take the same memory as one"
      (is (= 248 (mem/total-size (s2/conform :fhir.json/Meta {:profile ["foo"]})
                                 (s2/conform :fhir.json/Meta {:profile ["foo"]})))))))
