(ns blaze.db.search-param-registry-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.db.impl.search-param.core :as sc]
   [blaze.db.search-param-registry :as sr]
   [blaze.db.search-param-registry-spec]
   [blaze.fhir-path :as fhir-path]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.terminology-service :as-alias ts]
   [blaze.terminology-service.not-available]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def ^:private config
  {:blaze.db/search-param-registry
   {:structure-definition-repo structure-definition-repo
    :terminology-service (ig/ref ::ts/not-available)}
   ::ts/not-available {}})

(def config-extra
  (assoc-in
   config
   [:blaze.db/search-param-registry :extra-bundle-file]
   "../../.github/custom-search-parameters-test/custom-search-parameters.json"))

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.db/search-param-registry nil}
      :key := :blaze.db/search-param-registry
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.db/search-param-registry {}}
      :key := :blaze.db/search-param-registry
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :structure-definition-repo))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :terminology-service))))

  (testing "invalid structure-definition-repo"
    (given-failed-system (assoc-in config [:blaze.db/search-param-registry :structure-definition-repo] ::invalid)
      :key := :blaze.db/search-param-registry
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.fhir/structure-definition-repo]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid extra-bundle-file"
    (given-failed-system (assoc-in config [:blaze.db/search-param-registry :extra-bundle-file] ::invalid)
      :key := :blaze.db/search-param-registry
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::sr/extra-bundle-file]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "not-found extra-bundle-file"
    (given-failed-system (assoc-in config [:blaze.db/search-param-registry :extra-bundle-file] "foo")
      :key := :blaze.db/search-param-registry
      :reason := ::ig/build-threw-exception))

  (testing "invalid search param"
    (with-redefs [sc/search-param (fn [_ _] (ba/incorrect))]
      (given-failed-system config
        :key := :blaze.db/search-param-registry
        :reason := ::ig/build-threw-exception)))

  (testing "with nil extra bundle file"
    (is (->> (ig/init (assoc-in
                       config
                       [:blaze.db/search-param-registry :extra-bundle-file]
                       nil))
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
          :url := "http://hl7.org/fhir/SearchParameter/clinical-code"))

      (testing "not found"
        (is (nil? (sr/get search-param-registry "not-existing" "Observation"))))))

  (testing "with extra bundle file"
    (with-system [{:blaze.db/keys [search-param-registry]} config-extra]
      (testing "marital-status"
        (given (sr/get search-param-registry "marital-status" "Patient")
          :type := "token"
          :url := "https://samply.github.io/blaze/fhir/SearchParameter/Patient-marital-status")))))

(deftest get-by-url-test
  (testing "default system"
    (with-system [{:blaze.db/keys [search-param-registry]} config]
      (testing "clinical-code"
        (given (sr/get-by-url search-param-registry "http://hl7.org/fhir/SearchParameter/clinical-code")
          :type := "token"
          :url := "http://hl7.org/fhir/SearchParameter/clinical-code"))

      (testing "not found"
        (is (nil? (sr/get-by-url search-param-registry "not-existing"))))))

  (testing "with extra bundle file"
    (with-system [{:blaze.db/keys [search-param-registry]} config-extra]
      (testing "marital-status"
        (given (sr/get-by-url search-param-registry "https://samply.github.io/blaze/fhir/SearchParameter/Patient-marital-status")
          :type := "token"
          :url := "https://samply.github.io/blaze/fhir/SearchParameter/Patient-marital-status")))))

(deftest list-by-target-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (testing "Patient"
      (testing "every search param is of type reference"
        (is (every? (comp #{"reference"} :type)
                    (sr/list-by-target-type search-param-registry "Patient"))))

      (given (sr/list-by-target-type search-param-registry "Patient")
        count := 210
        [0 :base] := ["Account"]
        [0 :code] := "patient"
        [1 :base] := ["Account"]
        [1 :code] := "subject"
        [2 :base] := ["ActivityDefinition"]
        [2 :code] := "composed-of"))

    (testing "Encounter"
      (testing "every search param is of type reference"
        (is (every? (comp #{"reference"} :type)
                    (sr/list-by-target-type search-param-registry "Encounter"))))

      (given (sr/list-by-target-type search-param-registry "Encounter")
        count := 100
        [0 :base] := ["ActivityDefinition"]
        [0 :code] := "composed-of"))))

(deftest linked-compartments-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (testing "Condition subject"
      (given (sr/linked-compartments
              search-param-registry
              {:fhir/type :fhir/Condition :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/1"}})
        count := 1
        [0] := ["Patient" "1"]))

    (testing "Observation subject"
      (given (sr/linked-compartments
              search-param-registry
              {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/1"}})
        count := 1
        [0] := ["Patient" "1"])

      (testing "Group is no compartment"
        (is (empty? (sr/linked-compartments
                     search-param-registry
                     {:fhir/type :fhir/Observation :id "0"
                      :subject #fhir/Reference{:reference #fhir/string "Group/1"}})))))

    (testing "MedicationAdministration subject"
      (given (sr/linked-compartments
              search-param-registry
              {:fhir/type :fhir/MedicationAdministration :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/1"}})
        count := 1
        [0] := ["Patient" "1"]))

    (testing "MedicationAdministration subject and performer"
      (given (sr/linked-compartments
              search-param-registry
              {:fhir/type :fhir/MedicationAdministration :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/1"}
               :performer
               [{:fhir/type :fhir.MedicationAdministration/performer
                 :actor #fhir/Reference{:reference #fhir/string "Patient/2"}}]})
        count := 2
        [0] := ["Patient" "2"]
        [1] := ["Patient" "1"]))

    (testing "MedicationAdministration identical subject and performer"
      (given (sr/linked-compartments
              search-param-registry
              {:fhir/type :fhir/MedicationAdministration :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/1"}
               :performer
               [{:fhir/type :fhir.MedicationAdministration/performer
                 :actor #fhir/Reference{:reference #fhir/string "Patient/1"}}]})
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
        (given (sr/linked-compartments
                search-param-registry
                {:fhir/type :fhir/Condition :id "0"
                 :subject #fhir/Reference{:reference #fhir/string "Patient/1"}})
          ::anom/category := ::anom/fault
          ::anom/message := "msg-121005"
          ::x := ::y)))))

(deftest compartment-resources-test
  (testing "Patient"
    (with-system [{:blaze.db/keys [search-param-registry]} config]
      (testing "all resource types"
        (given (sr/compartment-resources search-param-registry "Patient")
          count := 66
          [0] := ["Account" ["subject"]]
          [1] := ["AdverseEvent" ["subject"]]
          [2] := ["AllergyIntolerance" ["patient" "recorder" "asserter"]]
          [3] := ["Appointment" ["actor"]]
          [65] := ["VisionPrescription" ["patient"]]))

      (testing "only Observation codes"
        (is (= (sr/compartment-resources search-param-registry "Patient" "Observation")
               ["subject" "performer"])))

      (testing "Device isn't in patient compartment"
        (is (empty? (sr/compartment-resources search-param-registry "Patient" "Device")))))))
