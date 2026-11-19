(ns blaze.admin-api.validator-test
  (:require
   [blaze.admin-api.validator :as validator]
   [blaze.admin-api.validator-spec]
   [blaze.fhir.canonical :as canonical]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu]
   [clojure.set :as set]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [jsonista.core :as j]
   [juxt.iota :refer [given]]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(def config
  {:blaze.admin-api/validator {}})

(defn- re-index-job [base]
  {:resourceType "Task"
   :meta {:profile [(str base "/StructureDefinition/ReIndexJob")]}
   :status "ready"
   :intent "order"
   :code {:coding [{:system (str base "/CodeSystem/JobType")
                    :code "re-index"
                    :display "(Re)Index a Search Parameter"}]}
   :authoredOn "2024-04-13T10:05:20.927Z"
   :input [{:type {:coding [{:system (str base "/CodeSystem/ReIndexJobParameter")
                             :code "search-param-url"}]}
            :valueCanonical "http://hl7.org/fhir/SearchParameter/Resource-profile"}]})

(defn- validate [validator resource]
  (validator/validate validator (j/write-value-as-string resource)))

(defn- error-issues [outcome]
  (filterv (comp #{"error"} :value :severity) (:issue outcome)))

(deftest validator-init-test
  (testing "the component initializes"
    (with-system [{validator :blaze.admin-api/validator} config]
      (is (some? validator)))))

(deftest validate-test
  (with-system [{validator :blaze.admin-api/validator} config]
    (doseq [base [canonical/base canonical/old-base]]
      (testing (str "valid re-index job (" base ")")
        (let [outcome (validate validator (re-index-job base))]
          (is (= :fhir/OperationOutcome (:fhir/type outcome)))
          (is (empty? (error-issues outcome)))))

      (testing (str "missing code (" base ")")
        (let [job (set/rename-keys (re-index-job base) {:code :_code})
              outcome (validate validator job)]
          (given (error-issues outcome)
            [0 :fhir/type] := :fhir.OperationOutcome/issue
            [0 :severity] := #fhir/code "error"
            [0 :code] := #fhir/code "structure"
            [0 :diagnostics :value] :# ".*Unrecognized property '_code'.*"
            [1 :diagnostics :value] := (str "Task.code: minimum required = 1, but only found 0 (from " base "/StructureDefinition/ReIndexJob)")
            [1 :expression 0 :value] := "Task")))

      (testing (str "invalid status code (" base ")")
        (let [job (assoc (re-index-job base) :status "foo")
              outcome (validate validator job)]
          (given (error-issues outcome)
            [0 :fhir/type] := :fhir.OperationOutcome/issue
            [0 :severity] := #fhir/code "error"
            [0 :code] := #fhir/code "not-found"
            [0 :diagnostics :value] := "The System URI could not be determined for the code 'foo' in the ValueSet 'http://hl7.org/fhir/ValueSet/task-status|4.0.1'"
            [0 :expression 0 :value] := "Task.status"
            [1 :severity] := #fhir/code "error"
            [1 :code] := #fhir/code "code-invalid"
            [1 :diagnostics :value] :# ".*The value provided \\('foo'\\) was not found in the value set 'TaskStatus'.*"
            [1 :expression 0 :value] := "Task.status"))))

    (testing "unparsable JSON yields an error issue"
      (given (validator/validate validator "{")
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code "fatal"))))

(deftest no-http-client-test
  (testing "neither OkHttp, Okio nor the Kotlin stdlib are on the classpath"
    (doseq [class-name ["okhttp3.OkHttpClient" "okio.Okio" "kotlin.Unit"]]
      (is (thrown? ClassNotFoundException (Class/forName class-name))))))

(deftest validate-unresolvable-test
  (with-system [{validator :blaze.admin-api/validator} config]
    (testing "unknown profile, extension and canonical don't lead to a remote fetch"
      (let [job (-> (re-index-job canonical/base)
                    (update-in [:meta :profile] conj "http://example.com/StructureDefinition/unknown")
                    (assoc :extension [{:url "http://example.com/StructureDefinition/unknown-extension"
                                        :valueString "foo"}])
                    (assoc :instantiatesCanonical "http://example.com/fhir/ActivityDefinition/unknown"))
            outcome (validate validator job)]
        (is (= :fhir/OperationOutcome (:fhir/type outcome)))

        (testing "the unknown extension is an error"
          (given (error-issues outcome)
            count := 1
            [0 :code] := #fhir/code "structure"
            [0 :diagnostics :value] := "The extension http://example.com/StructureDefinition/unknown-extension could not be found so is not allowed here"))

        (testing "the unknown profile is only a warning"
          (given (filterv (comp #{"warning"} :value :severity) (:issue outcome))
            [2 :diagnostics :value] := "Profile reference 'http://example.com/StructureDefinition/unknown' has not been checked because it could not be found"))))))
