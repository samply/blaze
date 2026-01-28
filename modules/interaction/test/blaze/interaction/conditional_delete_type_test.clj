(ns blaze.interaction.conditional-delete-type-test
  "Specifications relevant for the FHIR conditional delete interaction:

  https://www.hl7.org/fhir/http.html#cdelete"
  (:require
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.db.spec]
   [blaze.interaction.conditional-delete-type]
   [blaze.interaction.test-util :refer [wrap-error]]
   [blaze.module.test-util :refer [given-failed-system]]
   [blaze.test-util :as tu]
   [blaze.util-spec]
   [blaze.util.clauses-spec]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [reitit.core :as reitit]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)
(tu/set-default-locale-english!)                            ; important for the thousands separator in 10,000

(test/use-fixtures :each tu/fixture)

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.interaction/conditional-delete-type nil}
      :key := :blaze.interaction/conditional-delete-type
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.interaction/conditional-delete-type {}}
      :key := :blaze.interaction/conditional-delete-type
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))))

  (testing "invalid executor"
    (given-failed-system {:blaze.interaction/conditional-delete-type {:node ::invalid}}
      :key := :blaze.interaction/conditional-delete-type
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/node]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(def ^:private config
  (assoc
   api-stub/mem-node-config
   :blaze.interaction/conditional-delete-type
   {:node (ig/ref :blaze.db/node)
    :executor (ig/ref :blaze.test/executor)}
   :blaze.test/executor {}))

(defmacro with-handler [[handler-binding] & more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{handler# :blaze.interaction/conditional-delete-type} config]
       ~txs
       (let [~handler-binding (-> handler# wrap-error)]
         ~@body))))

(defmacro with-handler-allow-multiple [[handler-binding] & more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{handler# :blaze.interaction/conditional-delete-type} (assoc-in config [:blaze.db/node :allow-multiple-delete] true)]
       ~txs
       (let [~handler-binding (-> handler# wrap-error)]
         ~@body))))

(deftest handler-test
  (testing "returns error on unknown search parameter"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler
              {::reitit/match {:data {:fhir.resource/type "Patient"}}
               :query-params {"foo" "bar"}})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "invalid"
          [:issue 0 :diagnostics] := #fhir/string "Conditional delete of Patients with query `foo=bar` failed. Cause: The search-param with code `foo` and type `Patient` was not found."))))

  (testing "returns error on multiple delete"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "1"}]]]

      (let [{:keys [status body]}
            @(handler
              {::reitit/match {:data {:fhir.resource/type "Patient"}}})]

        (is (= 412 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "conflict"
          [:issue 0 :diagnostics] := #fhir/string "Conditional delete of one single Patient without a query failed because at least the two matches `Patient/0/_history/1` and `Patient/1/_history/1` were found."))))

  (testing "deleting more than 10,000 Patients fails"
    (with-handler-allow-multiple [handler]
      [(vec (for [id (range 10001)]
              [:put {:fhir/type :fhir/Patient :id (str id)}]))]

      (let [{:keys [status body]}
            @(handler
              {::reitit/match {:data {:fhir.resource/type "Patient"}}})]

        (is (= 409 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "too-costly"
          [:issue 0 :diagnostics] := #fhir/string "Conditional delete of all Patients failed because more than 10,000 matches were found."))))

  (testing "returns No Content on non-existing resource"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler
              {::reitit/match {:data {:fhir.resource/type "Patient"}}})]

        (is (= 204 status))

        (is (nil? body)))))

  (testing "returns No Content on successful deletion"
    (testing "without search params"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

        (let [{:keys [status body]}
              @(handler
                {::reitit/match {:data {:fhir.resource/type "Patient"}}})]

          (is (= 204 status))

          (is (nil? body)))))

    (testing "with search params"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Patient :id "0"
                 :identifier [#fhir/Identifier{:value #fhir/string "181205"}]}]
          [:put {:fhir/type :fhir/Patient :id "1"}]]]

        (let [{:keys [status body]}
              @(handler
                {::reitit/match {:data {:fhir.resource/type "Patient"}}
                 :query-params {"identifier" "181205"}})]

          (is (= 204 status))

          (is (nil? body))))))

  (testing "returns No Content on already deleted resource"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:delete "Patient" "0"]]]

      (let [{:keys [status body]}
            @(handler
              {::reitit/match {:data {:fhir.resource/type "Patient"}}})]

        (is (= 204 status))

        (is (nil? body)))))

  (testing "with return=OperationOutcome Prefer header"
    (testing "deleting no Patients"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler
                {::reitit/match {:data {:fhir.resource/type "Patient"}}
                 :headers {"prefer" "return=OperationOutcome"}})]

          (is (= 200 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "success"
            [:issue 0 :code] := #fhir/code "success"
            [:issue 0 :diagnostics] := #fhir/string "Success. No Patients exist. Nothing to delete.")))

      (testing "with query"
        (with-handler [handler]
          (let [{:keys [status body]}
                @(handler
                  {::reitit/match {:data {:fhir.resource/type "Patient"}}
                   :headers {"prefer" "return=OperationOutcome"}
                   :query-params {"identifier" "181205"}})]

            (is (= 200 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "success"
              [:issue 0 :code] := #fhir/code "success"
              [:issue 0 :diagnostics] := #fhir/string "Success. No Patients were matched by query `identifier=181205`. Nothing to delete.")))))

    (testing "deleting one Patient"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Patient :id "0"
                 :identifier [#fhir/Identifier{:value #fhir/string "181205"}]}]
          [:put {:fhir/type :fhir/Patient :id "1"}]]]

        (let [{:keys [status body]}
              @(handler
                {::reitit/match {:data {:fhir.resource/type "Patient"}}
                 :headers {"prefer" "return=OperationOutcome"}
                 :query-params {"identifier" "181205"}})]

          (is (= 200 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "success"
            [:issue 0 :code] := #fhir/code "success"
            [:issue 0 :diagnostics] := #fhir/string "Successfully deleted 1 Patient."))))

    (testing "deleting two Patients"
      (with-handler-allow-multiple [handler]
        [[[:put {:fhir/type :fhir/Patient :id "0"
                 :identifier [#fhir/Identifier{:value #fhir/string "181205"}]}]
          [:put {:fhir/type :fhir/Patient :id "1"
                 :identifier [#fhir/Identifier{:value #fhir/string "181205"}]}]]]

        (let [{:keys [status body]}
              @(handler
                {::reitit/match {:data {:fhir.resource/type "Patient"}}
                 :headers {"prefer" "return=OperationOutcome"}
                 :query-params {"identifier" "181205"}})]

          (is (= 200 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "success"
            [:issue 0 :code] := #fhir/code "success"
            [:issue 0 :diagnostics] := #fhir/string "Successfully deleted 2 Patients."))))))
