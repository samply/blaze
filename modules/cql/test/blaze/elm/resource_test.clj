(ns blaze.elm.resource-test
  (:require
   [blaze.db.api :as d]
   [blaze.db.api-stub :as api-stub :refer [with-open-db with-system-data]]
   [blaze.elm.compiler.test-util :as ctu :refer [has-form]]
   [blaze.elm.expression-spec]
   [blaze.elm.resource :as cr]
   [blaze.elm.resource-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn- resource [db type id]
  (cr/mk-resource db (d/resource-handle db type id)))

(defn- fhir-type
  "Keyword lookup site for testing lookup on other types still works.

  Should be used with a resource and another value."
  [x]
  (:fhir/type x))

(defn- id
  "Keyword lookup site for testing lookup on other types still works.

  Should be used with a resource and another value."
  [x]
  (:id x))

(deftest resource-test
  (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
    [[[:put {:fhir/type :fhir/Patient :id "0" :gender #fhir/code "female"}]]]

    (let [resource (resource (d/db node) "Patient" "0")]

      (testing "type"
        (is (= :fhir/Patient (fhir-type resource)))
        (is (= :foo (fhir-type {:fhir/type :foo}))))

      (testing "id"
        (is (= "0" (id resource)))
        (is (= :foo (id {:id :foo}))))

      (testing "gender"
        (is (= #fhir/code "female" (:gender resource))))

      (ctu/testing-constant resource)

      (testing "form"
        (has-form resource '(resource "Patient" "0" 1)))

      (testing "toString"
        (is (= "Patient[id = 0, t = 1, last-change-t = 1]" (str resource)))))))

(deftest mk-resource-test
  (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
    [[[:put {:fhir/type :fhir/Patient :id "0"}]
      [:put {:fhir/type :fhir/Observation :id "0"
             :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]
     [[:put {:fhir/type :fhir/Patient :id "1"}]]]

    (with-open-db [db node]
      (testing "the last change of a patient comes from its compartment"
        (is (= "Patient[id = 0, t = 1, last-change-t = 1]"
               (str (resource db "Patient" "0")))))

      (testing "the last change of a patient without resources is its own t"
        (is (= "Patient[id = 1, t = 2, last-change-t = 2]"
               (str (resource db "Patient" "1")))))

      (testing "the last change of other resources is newer than the database, so that Bloom filters are never used for them"
        (is (= "Observation[id = 0, t = 1, last-change-t = 3]"
               (str (resource db "Observation" "0"))))))))

(defn- map-resources [mapper db type]
  (into #{} (comp (mapper db) (map str)) (d/type-list db type)))

(deftest resource-mapper-test
  (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
    [[[:put {:fhir/type :fhir/Observation :id "0"}]]
     [[:put {:fhir/type :fhir/Observation :id "1"}]]]

    (testing "the last change of resources is newer than the database, so that Bloom filters are never used for them"
      (with-open-db [db node]
        (is (= #{"Observation[id = 0, t = 1, last-change-t = 3]"
                 "Observation[id = 1, t = 2, last-change-t = 3]"}
               (map-resources cr/resource-mapper db "Observation")))))))

(deftest patient-mapper-test
  (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
    [[[:put {:fhir/type :fhir/Patient :id "0"}]
      [:put {:fhir/type :fhir/Patient :id "1"}]]
     [[:put {:fhir/type :fhir/Observation :id "0"
             :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]
     [[:put {:fhir/type :fhir/Patient :id "1" :gender #fhir/code "female"}]]]

    (testing "patient 0 has its last change from the observation and patient 1 from itself"
      (with-open-db [db node]
        (is (= #{"Patient[id = 0, t = 1, last-change-t = 2]"
                 "Patient[id = 1, t = 3, last-change-t = 3]"}
               (map-resources cr/patient-mapper db "Patient")))))))
