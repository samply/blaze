(ns blaze.db.search-param-registry-test
  (:require
    [blaze.db.search-param-registry :as sr]
    [blaze.db.search-param-registry-spec]
    [blaze.fhir-path :as fhir-path]
    [blaze.fhir.spec.type]
    [blaze.fhir.structure-definition-repo]
    [blaze.fhir.structure-definition-repo.spec :refer [structure-definition-repo?]]
    [blaze.test-util :refer [given-thrown with-system]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [cognitect.anomalies :as anom]
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


(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze.db/search-param-registry nil})
      :key := :blaze.db/search-param-registry
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze.db/search-param-registry {}})
      :key := :blaze.db/search-param-registry
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :structure-definition-repo))))

  (testing "invalid structure-definition-repo"
    (given-thrown (ig/init {:blaze.db/search-param-registry {:structure-definition-repo ::invalid}})
      :key := :blaze.db/search-param-registry
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `structure-definition-repo?
      [:explain ::s/problems 0 :val] := ::invalid)))


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


(deftest list-by-target-test
  (with-system [{:blaze.db/keys [search-param-registry]} system]
    (testing "Patient"
      (given (sr/list-by-target search-param-registry "Patient")
        count := 210
        [0 :base] := ["Account"]
        [0 :code] := "patient"
        [1 :base] := ["Account"]
        [1 :code] := "subject"
        [2 :base] := ["ActivityDefinition"]
        [2 :code] := "composed-of"))))


(deftest linked-compartments-test
  (with-system [{:blaze.db/keys [search-param-registry]} system]
    (testing "Condition subject"
      (given (sr/linked-compartments
               search-param-registry
               {:fhir/type :fhir/Condition :id "0"
                :subject #fhir/Reference{:reference "Patient/1"}})
        count := 1
        [0] := ["Patient" "1"]))

    (testing "Observation subject"
      (given (sr/linked-compartments
               search-param-registry
               {:fhir/type :fhir/Observation :id "0"
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
        [0] := ["Patient" "1"]))

    (testing "with FHIRPath eval error"
      (with-redefs [fhir-path/eval
                    (fn [_ _ _]
                      {::anom/category ::anom/fault
                       ::anom/message "msg-121005"
                       ::x ::y})]
        (given
          (sr/linked-compartments
            search-param-registry
            {:fhir/type :fhir/Condition :id "0"
             :subject #fhir/Reference{:reference "Patient/1"}})
          ::anom/category := ::anom/fault
          ::anom/message := "msg-121005"
          ::x := ::y)))))
