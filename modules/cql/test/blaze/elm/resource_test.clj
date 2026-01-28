(ns blaze.elm.resource-test
  (:require
   [blaze.db.api :as d]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
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
