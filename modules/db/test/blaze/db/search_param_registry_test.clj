(ns blaze.db.search-param-registry-test
  (:require
    [blaze.db.search-param-registry :as sr]
    [blaze.db.search-param-registry-spec]
    [blaze.fhir.spec.type]
    [blaze.fhir.structure-definition-repo]
    [blaze.test-util :refer [with-system]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [integrant.core :as ig]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def system
  {:blaze.fhir/structure-definition-repo {}
   :blaze.db/search-param-registry
   {:structure-definition-repo (ig/ref :blaze.fhir/structure-definition-repo)}})


(deftest get-test
  (with-system [{:blaze.db/keys [search-param-registry]} system]
    (testing "_id"
      (given (sr/get search-param-registry "_id")
        :type := "token"
        :url := "http://hl7.org/fhir/SearchParameter/Resource-id"))))


(deftest linked-compartments-test
  (with-system [{:blaze.db/keys [search-param-registry]} system]
    (testing "Condition subject"
      (given (sr/linked-compartments
               search-param-registry
               {:fhir/type :fhir/Condition :id "0"
                :subject #fhir/Reference{:reference "Patient/1"}})
        count := 1
        [0] := ["Patient" "1"]))

    (testing "MedicationAdministration subject"
      (given (sr/linked-compartments
               search-param-registry
               {:fhir/type :fhir/MedicationAdministration :id "0"
                :subject #fhir/Reference{:reference "Patient/1"}})
        count := 1
        [0] := ["Patient" "1"]))

    (testing "MedicationAdministration subject and performer"
      (given (sr/linked-compartments
               search-param-registry
               {:fhir/type :fhir/MedicationAdministration :id "0"
                :subject #fhir/Reference{:reference "Patient/1"}
                :performer
                [{:fhir/type :fhir.MedicationAdministration/performer
                  :actor #fhir/Reference{:reference "Patient/2"}}]})
        count := 2
        [0] := ["Patient" "2"]
        [1] := ["Patient" "1"]))

    (testing "MedicationAdministration identical subject and performer"
      (given (sr/linked-compartments
               search-param-registry
               {:fhir/type :fhir/MedicationAdministration :id "0"
                :subject #fhir/Reference{:reference "Patient/1"}
                :performer
                [{:fhir/type :fhir.MedicationAdministration/performer
                  :actor #fhir/Reference{:reference "Patient/1"}}]})
        count := 1
        [0] := ["Patient" "1"]))))
