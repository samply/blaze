(ns blaze.elm.util-test
  (:require
   [blaze.elm.util :as elm-util]
   [blaze.elm.util-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest parse-qualified-name-test
  (testing "nil"
    (is (nil? (elm-util/parse-qualified-name nil))))

  (testing "empty string"
    (is (nil? (elm-util/parse-qualified-name ""))))

  (testing "invalid string"
    (are [s] (nil? (elm-util/parse-qualified-name s))
      "a"
      "aa"))

  (testing "valid string"
    (are [s ns name] (= [ns name] (elm-util/parse-qualified-name s))
      "{a}b" "a" "b")))

(deftest parse-type-test
  (testing "ELM type"
    (is (= "String" (elm-util/parse-type {:type "NamedTypeSpecifier" :name "{urn:hl7-org:elm-types:r1}String"}))))

  (testing "FHIR type"
    (is (= "Encounter" (elm-util/parse-type {:type "NamedTypeSpecifier" :name "{http://hl7.org/fhir}Encounter"}))))

  (testing "list type"
    (is (= ["Encounter"] (elm-util/parse-type {:type "ListTypeSpecifier" :elementType {:type "NamedTypeSpecifier" :name "{http://hl7.org/fhir}Encounter"}})))))
