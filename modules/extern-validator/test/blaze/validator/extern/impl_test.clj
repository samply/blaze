(ns blaze.validator.extern.impl-test
  (:require
   [blaze.fhir.hash :as hash]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util]
   [blaze.test-util :as tu]
   [blaze.validator.extern.impl :as impl]
   [blaze.validator.extern.impl-spec]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn- operation-outcome [& severities]
  {:fhir/type :fhir/OperationOutcome
   :issue
   (mapv
    (fn [severity]
      {:fhir/type :fhir.OperationOutcome/issue
       :severity (type/code severity)
       :code #fhir/code "processing"
       :diagnostics (type/string (str severity " issue"))})
    severities)})

(deftest invalid?-test
  (testing "an error issue is invalid"
    (is (impl/invalid? (operation-outcome "error"))))

  (testing "a fatal issue is invalid"
    (is (impl/invalid? (operation-outcome "fatal"))))

  (testing "warning and information issues are valid"
    (is (not (impl/invalid? (operation-outcome "warning" "information"))))))

(deftest tag-invalid-test
  (testing "tag-only adds the invalid tag without contained outcome"
    (given (impl/tag-invalid
            {:fhir/type :fhir/Patient :id "0"}
            (operation-outcome "error") false)
      [:meta :tag count] := 1
      [:meta :tag 0 :system] := #fhir/uri "https://blaze-server.org/fhir/CodeSystem/ValidationStatus"
      [:meta :tag 0 :code] := #fhir/code "invalid"
      [:meta :extension] :? empty?
      :contained := nil))

  (testing "existing tags are preserved"
    (given (impl/tag-invalid
            {:fhir/type :fhir/Patient :id "0"
             :meta (type/meta {:tag [(type/coding {:code #fhir/code "foo"})]})}
            (operation-outcome "error") false)
      [:meta :tag count] := 2
      [:meta :tag 0 :code] := #fhir/code "foo"
      [:meta :tag 1 :code] := #fhir/code "invalid"))

  (testing "all other meta fields are preserved"
    (given (impl/tag-invalid
            {:fhir/type :fhir/Patient :id "0"
             :meta (type/meta {:id "m-1"
                               :source #fhir/uri "source-161513"
                               :profile [#fhir/canonical "profile-161513"]
                               :security [(type/coding {:code #fhir/code "sec"})]
                               :extension [#fhir/Extension{:url "ext-161513"}]})}
            (operation-outcome "error") false)
      [:meta :id] := "m-1"
      [:meta :source] := #fhir/uri "source-161513"
      [:meta :profile 0] := #fhir/canonical "profile-161513"
      [:meta :security 0 :code] := #fhir/code "sec"
      [:meta :extension 0 :url] := "ext-161513"
      [:meta :tag 0 :code] := #fhir/code "invalid"))

  (testing "tag-outcome adds the tag, the contained outcome and the meta extension"
    (let [outcome (operation-outcome "error")
          id (subs (str (hash/generate outcome)) 0 32)]
      (given (impl/tag-invalid {:fhir/type :fhir/Patient :id "0"} outcome true)
        [:meta :tag 0 :code] := #fhir/code "invalid"
        [:meta :extension 0 :url] := "https://blaze-server.org/fhir/StructureDefinition/validation-outcome"
        ;; the extension references the contained outcome by its derived id
        [:meta :extension 0 :value :reference] := (type/string (str "#" id))
        [:contained count] := 1
        [:contained 0 :fhir/type] := :fhir/OperationOutcome
        ;; the contained id is derived from the outcome content, not a fixed
        ;; string, so clients must follow the extension to find the outcome
        [:contained 0 :id] := id)))

  (testing "existing contained resources are preserved"
    (let [outcome (operation-outcome "error")
          id (subs (str (hash/generate outcome)) 0 32)]
      (given (impl/tag-invalid {:fhir/type :fhir/Patient :id "0"
                                :contained [{:fhir/type :fhir/Patient :id "1"}]}
                               outcome true)
        [:meta :tag 0 :code] := #fhir/code "invalid"
        [:meta :extension 0 :url] := "https://blaze-server.org/fhir/StructureDefinition/validation-outcome"
        ;; the extension references the contained outcome by its derived id
        [:meta :extension 0 :value :reference] := (type/string (str "#" id))
        [:contained count] := 2
        [:contained (partial some #(when (= "1" (:id %)) %)) :fhir/type] := :fhir/Patient
        [:contained (partial some #(when (= id (:id %)) %)) :fhir/type] := :fhir/OperationOutcome)))

  (testing "different outcomes get different contained ids"
    (let [id-fn #(-> (impl/tag-invalid {:fhir/type :fhir/Patient :id "0"} % true)
                     (get-in [:contained 0 :id]))]
      (is (not= (id-fn (operation-outcome "error"))
                (id-fn (operation-outcome "error" "error"))))))

  (testing "equal outcomes get equal contained ids (idempotency)"
    (let [id-fn #(-> (impl/tag-invalid {:fhir/type :fhir/Patient :id "0"} % true)
                     (get-in [:contained 0 :id]))]
      (is (= (id-fn (operation-outcome "error"))
             (id-fn (operation-outcome "error")))))))

(deftest reject-anomaly-test
  (testing "carries severity, code and diagnostics"
    (given (impl/reject-anomaly (operation-outcome "error"))
      ::anom/category := ::anom/incorrect
      :fhir/issue := "invalid"
      [:fhir/issues count] := 1
      [:fhir/issues 0 :fhir.issues/severity] := "error"
      [:fhir/issues 0 :fhir.issues/code] := "processing"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "error issue"))

  (testing "carries the expression"
    (given (impl/reject-anomaly
            {:fhir/type :fhir/OperationOutcome
             :issue
             [{:fhir/type :fhir.OperationOutcome/issue
               :severity #fhir/code "error"
               :code #fhir/code "processing"
               :expression [#fhir/string "Patient.gender"]}]})
      [:fhir/issues 0 :fhir.issues/expression 0] := "Patient.gender"))

  (testing "handles an issue without any fields"
    (given (impl/reject-anomaly
            {:fhir/type :fhir/OperationOutcome
             :issue [{:fhir/type :fhir.OperationOutcome/issue}]})
      ::anom/category := ::anom/incorrect
      [:fhir/issues 0] := {})))
