(ns blaze.db.search-param-registry-test
  (:require
    [blaze.db.search-param-registry :as sr]
    [blaze.db.search-param-registry-spec]
    [blaze.fhir-path :as fhir-path]
    [blaze.fhir.spec.type]
    [blaze.fhir.structure-definition-repo.spec :refer [structure-definition-repo?]]
    [blaze.fhir.test-util :refer [structure-definition-repo]]
    [blaze.module.test-util :refer [with-system]]
    [blaze.test-util :as tu :refer [given-thrown]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(test/use-fixtures :each tu/fixture)


(def config
  {:blaze.db/search-param-registry
   {:structure-definition-repo structure-definition-repo}})


(def config-extra
  {:blaze.db/search-param-registry
   {:structure-definition-repo structure-definition-repo
    :extra-bundle-file "../../.github/custom-search-parameters-test/custom-search-parameters.json"}})


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
      [:explain ::s/problems 0 :val] := ::invalid))

  (testing "invalid extra-bundle-file"
    (given-thrown (ig/init {:blaze.db/search-param-registry
                            {:structure-definition-repo structure-definition-repo
                             :extra-bundle-file ::invalid}})
      :key := :blaze.db/search-param-registry
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `string?
      [:explain ::s/problems 0 :val] := ::invalid))

  (testing "not-found extra-bundle-file"
    (given-thrown (ig/init {:blaze.db/search-param-registry
                            {:structure-definition-repo structure-definition-repo
                             :extra-bundle-file "foo"}})
      :key := :blaze.db/search-param-registry
      :reason := ::ig/build-threw-exception))

  (testing "with nil extra bundle file"
    (is (->> (ig/init {:blaze.db/search-param-registry
                       {:structure-definition-repo structure-definition-repo
                        :extra-bundle-file nil}})
             :blaze.db/search-param-registry
             (s/valid? :blaze.db/search-param-registry))))

  (testing "without extra bundle file"
    (is (->> (:blaze.db/search-param-registry (ig/init config))
             (s/valid? :blaze.db/search-param-registry))))

  (testing "with extra bundle file"
    (is (->> (:blaze.db/search-param-registry (ig/init config-extra))
             (s/valid? :blaze.db/search-param-registry)))))


(deftest get-test
  (testing "default system"
    (with-system [{:blaze.db/keys [search-param-registry]} config]
      (testing "clinical-code"
        (given (sr/get search-param-registry "code" "Observation")
          :type := "token"
          :url := "http://hl7.org/fhir/SearchParameter/clinical-code"))))

  (testing "with extra bundle file"
    (with-system [{:blaze.db/keys [search-param-registry]} config-extra]
      (testing "marital-status"
        (given (sr/get search-param-registry "marital-status" "Patient")
          :type := "token"
          :url := "https://samply.github.io/blaze/fhir/SearchParameter/Patient-marital-status")))))


(deftest list-by-target-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
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
  (with-system [{:blaze.db/keys [search-param-registry]} config]
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

    (testing "a simple Patient has no compartments"
      (is (empty? (sr/linked-compartments
                    search-param-registry
                    {:fhir/type :fhir/Patient :id "0"}))))

    (testing "a simple Medication has no compartments"
      (is (empty? (sr/linked-compartments
                    search-param-registry
                    {:fhir/type :fhir/Medication :id "0"}))))

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


(deftest compartment-resources-test
  (testing "Patient"
    (with-system [{:blaze.db/keys [search-param-registry]} config]
      (given (sr/compartment-resources search-param-registry "Patient")
        count := 100
        [0] := ["Account" "subject"]
        [1] := ["AdverseEvent" "subject"]
        [2] := ["AllergyIntolerance" "patient"]
        [3] := ["AllergyIntolerance" "recorder"]
        [4] := ["AllergyIntolerance" "asserter"]
        [5] := ["Appointment" "actor"]
        [99] := ["VisionPrescription" "patient"]))))
