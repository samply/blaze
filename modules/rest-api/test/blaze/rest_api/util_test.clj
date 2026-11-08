(ns blaze.rest-api.util-test
  (:require
   [blaze.rest-api.util :as u]
   [blaze.rest-api.util-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn- operation [& resource-types]
  (cond->
   #:blaze.rest-api.operation
    {:code "code-133012"
     :def-uri "def-uri-133019"}
    (seq resource-types)
    (assoc :blaze.rest-api.operation/resource-types (vec resource-types))))

(deftest supports-resource-type-test
  (testing "operation without resource types"
    (is (false? (u/supports-resource-type? (operation) "Patient"))))

  (testing "operation with the resource type"
    (is (true? (u/supports-resource-type? (operation "Patient") "Patient"))))

  (testing "operation with another resource type"
    (is (false? (u/supports-resource-type? (operation "Measure") "Patient"))))

  (testing "operation with the resource type among others"
    (is (true? (u/supports-resource-type? (operation "Measure" "Patient") "Patient"))))

  (testing "operation with the abstract resource type Resource"
    (is (true? (u/supports-resource-type? (operation "Resource") "Patient")))
    (is (true? (u/supports-resource-type? (operation "Resource") "Bundle"))))

  (testing "the abstract resource type Resource itself"
    (is (true? (u/supports-resource-type? (operation "Resource") "Resource")))
    (is (false? (u/supports-resource-type? (operation "Patient") "Resource")))))

(deftest all-resource-types-test
  (testing "operation without resource types"
    (is (false? (u/all-resource-types? (operation)))))

  (testing "operation with concrete resource types"
    (is (false? (u/all-resource-types? (operation "Patient" "Measure")))))

  (testing "operation with the abstract resource type Resource"
    (is (true? (u/all-resource-types? (operation "Resource"))))
    (is (true? (u/all-resource-types? (operation "Patient" "Resource"))))))
