(ns blaze.fhir.canonical-test
  (:require
   [blaze.fhir.canonical :as canonical]
   [blaze.fhir.canonical-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest base-test
  (is (= "https://blaze-server.org/fhir" canonical/base))
  (is (= "https://samply.github.io/blaze/fhir" canonical/old-base)))

(deftest url-test
  (is (= "https://blaze-server.org/fhir/CodeSystem/JobType"
         (canonical/url "CodeSystem/JobType"))))

(deftest old-url-test
  (is (= "https://samply.github.io/blaze/fhir/CodeSystem/JobType"
         (canonical/old-url "CodeSystem/JobType"))))

(deftest urls-test
  (testing "new first, old second"
    (is (= ["https://blaze-server.org/fhir/sid/JobNumber"
            "https://samply.github.io/blaze/fhir/sid/JobNumber"]
           (canonical/urls "sid/JobNumber")))))

(deftest extensions-test
  (testing "two extensions sharing the same value, new url first"
    (given (canonical/extensions "StructureDefinition/grand-total"
                                 #fhir/string "123")
      [0 :url] := "https://blaze-server.org/fhir/StructureDefinition/grand-total"
      [0 :value] := #fhir/string "123"
      [1 :url] := "https://samply.github.io/blaze/fhir/StructureDefinition/grand-total"
      [1 :value] := #fhir/string "123")))

(deftest codings-test
  (testing "two codings sharing the same code, new system first"
    (given (canonical/codings "CodeSystem/JobType" "compact")
      [0 :system] := #fhir/uri "https://blaze-server.org/fhir/CodeSystem/JobType"
      [0 :code] := #fhir/code "compact"
      [1 :system] := #fhir/uri "https://samply.github.io/blaze/fhir/CodeSystem/JobType"
      [1 :code] := #fhir/code "compact")))

(deftest legacy-url-test
  (testing "a current-base url maps to its legacy form"
    (is (= "https://samply.github.io/blaze/fhir/CodeSystem/JobType"
           (canonical/legacy-url "https://blaze-server.org/fhir/CodeSystem/JobType"))))
  (testing "a non-current-base url maps to nil"
    (is (nil? (canonical/legacy-url "https://samply.github.io/blaze/fhir/CodeSystem/JobType")))
    (is (nil? (canonical/legacy-url "http://example.com/foo")))))

(deftest both-urls-test
  (testing "a current-base url yields both forms, current first"
    (is (= ["https://blaze-server.org/fhir/StructureDefinition/ReIndexJob"
            "https://samply.github.io/blaze/fhir/StructureDefinition/ReIndexJob"]
           (canonical/both-urls "https://blaze-server.org/fhir/StructureDefinition/ReIndexJob"))))
  (testing "a legacy-base url yields both forms, current first"
    (is (= ["https://blaze-server.org/fhir/StructureDefinition/ReIndexJob"
            "https://samply.github.io/blaze/fhir/StructureDefinition/ReIndexJob"]
           (canonical/both-urls "https://samply.github.io/blaze/fhir/StructureDefinition/ReIndexJob"))))
  (testing "a non-Blaze url is returned unchanged"
    (is (= ["http://hl7.org/fhir/StructureDefinition/Task"]
           (canonical/both-urls "http://hl7.org/fhir/StructureDefinition/Task")))))

(deftest both-codings-test
  (testing "a Blaze coding is expanded to both systems, current first, preserving code/display"
    (given (canonical/both-codings
            #fhir/Coding{:system #fhir/uri "https://samply.github.io/blaze/fhir/CodeSystem/JobType"
                         :code #fhir/code "re-index"
                         :display #fhir/string "(Re)Index a Search Parameter"})
      [0 :system] := #fhir/uri "https://blaze-server.org/fhir/CodeSystem/JobType"
      [0 :code] := #fhir/code "re-index"
      [0 :display] := #fhir/string "(Re)Index a Search Parameter"
      [1 :system] := #fhir/uri "https://samply.github.io/blaze/fhir/CodeSystem/JobType"
      [1 :code] := #fhir/code "re-index"
      [1 :display] := #fhir/string "(Re)Index a Search Parameter"))
  (testing "a non-Blaze coding is returned unchanged"
    (given (canonical/both-codings
            #fhir/Coding{:system #fhir/uri "http://loinc.org" :code #fhir/code "8310-5"})
      count := 1
      [0 :system] := #fhir/uri "http://loinc.org"
      [0 :code] := #fhir/code "8310-5"))
  (testing "a coding without a system is returned unchanged"
    (is (= [#fhir/Coding{:code #fhir/code "x"}]
           (canonical/both-codings #fhir/Coding{:code #fhir/code "x"})))))

(deftest system-codings-test
  (testing "a current-base system yields both codings, current first"
    (given (canonical/system-codings
            "https://blaze-server.org/fhir/CodeSystem/JobOutput" "error")
      [0 :system] := #fhir/uri "https://blaze-server.org/fhir/CodeSystem/JobOutput"
      [0 :code] := #fhir/code "error"
      [1 :system] := #fhir/uri "https://samply.github.io/blaze/fhir/CodeSystem/JobOutput"
      [1 :code] := #fhir/code "error"))
  (testing "a non-Blaze system yields a single coding"
    (given (canonical/system-codings "http://example.com/foo" "bar")
      count := 1
      [0 :system] := #fhir/uri "http://example.com/foo"
      [0 :code] := #fhir/code "bar")))

(deftest matches?-test
  (testing "matches the current canonical"
    (is (true? (canonical/matches? (canonical/url "CodeSystem/JobType")
                                   "https://blaze-server.org/fhir/CodeSystem/JobType"))))
  (testing "matches the legacy canonical of the same path"
    (is (true? (canonical/matches? (canonical/url "CodeSystem/JobType")
                                   "https://samply.github.io/blaze/fhir/CodeSystem/JobType"))))
  (testing "does not match a different path on a Blaze base"
    (is (false? (canonical/matches? (canonical/url "CodeSystem/JobType")
                                    (canonical/url "CodeSystem/Foo"))))
    (is (false? (canonical/matches? (canonical/url "CodeSystem/JobType")
                                    (canonical/old-url "CodeSystem/Foo")))))
  (testing "does not match the same path on a different base"
    (is (false? (canonical/matches? (canonical/url "CodeSystem/JobType")
                                    "http://example.com/fhir/CodeSystem/JobType"))))
  (testing "does not match on nil"
    (is (false? (canonical/matches? (canonical/url "CodeSystem/JobType") nil)))))
