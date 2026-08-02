(ns blaze.fhir.spec.impl.specs-test
  "Tests of the custom specs for primitive and complex types."
  (:require
   [blaze.fhir.spec]
   [blaze.fhir.spec.spec]
   [blaze.fhir.test-util]
   [blaze.test-util :as tu]
   [clojure.alpha.spec :as s2]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest record-conform-test
  (testing "conforming a complex type yields that very type again"
    (testing "without any property to conform"
      (let [x #fhir/Meta{:versionId #fhir/id "1"}]
        (is (identical? x (s2/conform :fhir/Meta x)))))

    (testing "with a property that would conform to a different shape"
      (testing "a repeating property"
        (let [x #fhir/Meta{:profile [#fhir/canonical "url-164445"]}]
          (is (identical? x (s2/conform :fhir/Meta x)))))

      (testing "a choice property, which conforms to a tagged pair"
        (let [x #fhir/Annotation{:text #fhir/markdown "text-134155"
                                 :author #fhir/string "author-134243"}]
          (is (identical? x (s2/conform :fhir/Annotation x)))))))

  (testing "a value of a different type is invalid"
    (is (s2/invalid? (s2/conform :fhir/Meta #fhir/Coding{}))))

  (testing "a property of the wrong type is still rejected"
    ;; `subject` is typed `Element` in Java, so a `Coding` passes `assoc`; only
    ;; the spec knows it has to be a `CodeableConcept` or a `Reference`
    (is (s2/invalid? (s2/conform :fhir/DataRequirement
                                 (assoc #fhir/DataRequirement{} :subject #fhir/Coding{}))))))
