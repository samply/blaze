(ns blaze.fhir.operation.cql-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.elm.expression :as expr]
   [blaze.elm.expression.cache-spec]
   [blaze.fhir.operation.cql :as cql]
   [blaze.fhir.spec.type]
   [blaze.fhir.test-util :refer [parameter parameter-part]]
   [blaze.fhir.util :as fu]
   [blaze.fhir.util-spec]
   [blaze.handler.util :as handler-util]
   [blaze.metrics.spec]
   [blaze.middleware.fhir.db :refer [wrap-db]]
   [blaze.middleware.fhir.db-spec]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.terminology-service :as-alias ts]
   [blaze.terminology-service-spec]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [reitit.core :as reitit]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def ^:private config
  (assoc
   api-stub/mem-node-config
   :blaze.fhir.operation/cql
   {:node (ig/ref :blaze.db/node)
    ::expr/cache (ig/ref ::expr/cache)
    :terminology-service (ig/ref ::ts/local)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}
   :blaze/job-scheduler
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}
   ::expr/cache
   {:node (ig/ref :blaze.db/node)
    :executor (ig/ref :blaze.test/executor)}
   :blaze.test/executor {}))

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.fhir.operation/cql nil}
      :key := :blaze.fhir.operation/cql
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.fhir.operation/cql {}}
      :key := :blaze.fhir.operation/cql
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :terminology-service))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))))

  (testing "invalid node"
    (given-failed-system (assoc-in config [:blaze.fhir.operation/cql :node] ::invalid)
      :key := :blaze.fhir.operation/cql
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/node]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid terminology-service"
    (given-failed-system (assoc-in config [:blaze.fhir.operation/cql :terminology-service] ::invalid)
      :key := :blaze.fhir.operation/cql
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/terminology-service]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid clock"
    (given-failed-system (assoc-in config [:blaze.fhir.operation/cql :clock] ::invalid)
      :key := :blaze.fhir.operation/cql
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/clock]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid rng-fn"
    (given-failed-system (assoc-in config [:blaze.fhir.operation/cql :rng-fn] ::invalid)
      :key := :blaze.fhir.operation/cql
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/rng-fn]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "init"
    (with-system [{handler :blaze.fhir.operation/cql} config]
      (is (fn? handler)))))

(deftest compile-duration-seconds-collector-init-test
  (with-system [{collector ::cql/compile-duration-seconds}
                {::cql/compile-duration-seconds nil}]
    (is (s/valid? :blaze.metrics/collector collector))))

(deftest evaluate-duration-seconds-collector-init-test
  (with-system [{collector ::cql/evaluate-duration-seconds}
                {::cql/evaluate-duration-seconds nil}]
    (is (s/valid? :blaze.metrics/collector collector))))

(def ^:private base-url "base-url-144638")

(def ^:private router
  (reitit/router
   [["/MeasureReport" {:name :MeasureReport/type}]]
   {:syntax :bracket}))

(defn- wrap-defaults [handler]
  (fn [request]
    (handler
     (assoc request
            :blaze/base-url base-url
            ::reitit/router router))))

(defn- wrap-job-scheduler [handler job-scheduler]
  (fn [request]
    (handler (assoc request :blaze/job-scheduler job-scheduler))))

(defn- wrap-error [handler]
  (fn [request]
    (-> (handler request)
        (ac/exceptionally handler-util/error-response))))

(defmacro with-handler [[handler-binding] & more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{node# :blaze.db/node
                         job-scheduler# :blaze/job-scheduler
                         handler# :blaze.fhir.operation/cql} config]
       ~txs
       (let [~handler-binding (-> handler# wrap-defaults (wrap-db node# 100)
                                  (wrap-job-scheduler job-scheduler#)
                                  wrap-error)]
         ~@body))))

(deftest handler-unsupported-param-test
  (testing "data"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :body
               (fu/parameters
                "data" {:fhir/type :fhir/Bundle})})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "not-supported"
          [:issue 0 :diagnostics] := #fhir/string "Unsupported parameter `data`."))))

  (testing "dataEndpoint"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :body
               (fu/parameters
                "dataEndpoint" {:fhir/type :fhir/Endpoint})})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "not-supported"
          [:issue 0 :diagnostics] := #fhir/string "Unsupported parameter `dataEndpoint`."))))

  (testing "contentEndpoint"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :body
               (fu/parameters
                "contentEndpoint" {:fhir/type :fhir/Endpoint})})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "not-supported"
          [:issue 0 :diagnostics] := #fhir/string "Unsupported parameter `contentEndpoint`."))))

  (testing "terminologyEndpoint"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :body
               (fu/parameters
                "terminologyEndpoint" {:fhir/type :fhir/Endpoint})})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "not-supported"
          [:issue 0 :diagnostics] := #fhir/string "Unsupported parameter `terminologyEndpoint`.")))))

(defn- failing-eval
  ([msg]
   (fn [_ _ _] (throw (Exception. ^String msg))))
  ([msg ex-data]
   (fn [_ _ _] (throw (ex-info msg ex-data)))))

(deftest handler-test
  (testing "errors on invalid expression"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :body
               (fu/parameters
                "expression" #fhir/string "invalid")})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "value"
          [:issue 0 :diagnostics] := #fhir/string "Could not resolve identifier invalid in the current library.")))

    (testing "during compilation"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler
                {:request-method :post
                 :body
                 (fu/parameters
                  "expression" #fhir/string "singleton from {1, 2}")})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "value"
            [:issue 0 :diagnostics] := #fhir/string "More than one element in `SingletonFrom` expression.")))))

  (testing "errors on expression eval error"
    (doseq [f [(failing-eval "msg-131124") (failing-eval "msg-131124" {})]]
      (with-redefs [expr/eval f]
        (with-handler [handler]
          (let [{:keys [status body]}
                @(handler
                  {:request-method :post
                   :body
                   (fu/parameters
                    "expression" #fhir/string "1")})]

            (is (= 500 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "error"
              [:issue 0 :code] := #fhir/code "exception"
              [:issue 0 :diagnostics] := #fhir/string "Error while evaluating the expression: msg-131124"))))))

  (testing "errors on unsupported FHIR type mapping"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :body
               (fu/parameters
                "expression" #fhir/string "X"
                "parameters" (fu/parameters "X" #fhir/Timing{}))})]

        (is (= 422 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "not-supported"
          [:issue 0 :diagnostics] := #fhir/string "Unsupported CQL type mapping from FHIR type `Timing`."))))

  (testing "errors on unsupported multiple params"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :body
               (fu/parameters
                "expression" #fhir/string "X"
                "parameters" (fu/parameters "X" #fhir/string "foo" "X" #fhir/string "bar"))})]

        (is (= 422 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "not-supported"
          [:issue 0 :diagnostics] := #fhir/string "Unsupported multiple param `X`."))))

  (testing "returns a date"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :body
               (fu/parameters
                "expression" #fhir/string "@2026")})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/Parameters
          [(parameter "return") count] := 1
          [(parameter "return") 0 :value] := #fhir/date #system/date "2026"))))

  (testing "calculates 1 + 1"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :body
               (fu/parameters
                "expression" #fhir/string "1 + 1")})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/Parameters
          [(parameter "return") count] := 1
          [(parameter "return") 0 :value] := #fhir/integer 2)))

    (testing "with subject without value (does nothing)"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler
                {:request-method :post
                 :body
                 (fu/parameters
                  "subject" #fhir/string{:id "foo"}
                  "expression" #fhir/string "1 + 1")})]

          (is (= 200 status))

          (given body
            :fhir/type := :fhir/Parameters
            [(parameter "return") count] := 1
            [(parameter "return") 0 :value] := #fhir/integer 2))))

    (testing "async"
      (with-handler [handler]
        (let [{:keys [status headers]}
              @(handler
                {:request-method :post
                 :uri "/"
                 :headers {"prefer" "respond-async"}
                 :body
                 (fu/parameters
                  "expression" #fhir/string "1 + 1")})]

          (is (= 202 status))

          (testing "the Content-Location header contains the status endpoint URL"
            (is (= (get headers "Content-Location")
                   (str base-url "/__async-status/AAAAAAAAAAAAAAAA"))))))))

  (testing "calculates 1 + X"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :body
               (fu/parameters
                "expression" #fhir/string "1 + X"
                "parameters" (fu/parameters "X" #fhir/integer 2))})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/Parameters
          [(parameter "return") count] := 1
          [(parameter "return") 0 :value] := #fhir/integer 3))))

  (testing "calculates 'foo' + X"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :body
               (fu/parameters
                "expression" #fhir/string "'foo' + X"
                "parameters" (fu/parameters "X" #fhir/string "bar"))})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/Parameters
          [(parameter "return") count] := 1
          [(parameter "return") 0 :value] := #fhir/string "foobar"))))

  (testing "nested lists"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :body
               (fu/parameters
                "expression" #fhir/string "{ { 1, 2 }, { 3, 4 } }")})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/Parameters
          [(parameter "return") count] := 2
          [(parameter "return") 0 (parameter-part "element") 0 :value] := #fhir/integer 1
          [(parameter "return") 0 (parameter-part "element") 1 :value] := #fhir/integer 2
          [(parameter "return") 1 (parameter-part "element") 0 :value] := #fhir/integer 3
          [(parameter "return") 1 (parameter-part "element") 1 :value] := #fhir/integer 4))))

  (testing "returns all patients"
    (with-handler [handler]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]
        [:create {:fhir/type :fhir/Patient :id "1"}]]]

      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :body
               (fu/parameters
                "expression" #fhir/string "[Patient]")})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/Parameters
          [(parameter "return") count] := 2
          [(parameter "return") 0 :resource :id] := "0"
          [(parameter "return") 1 :resource :id] := "1"))))

  (testing "returns the id and bith date of all patients"
    (with-handler [handler]
      [[[:create {:fhir/type :fhir/Patient :id "0"
                  :birthDate #fhir/date #system/date "2020-02-08"}]
        [:create {:fhir/type :fhir/Patient :id "1"
                  :birthDate #fhir/date #system/date "2022-04-01"}]
        [:create {:fhir/type :fhir/Patient :id "2"}]]]

      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :body
               (fu/parameters
                "expression" #fhir/string "[Patient] P return { id: P.id, birthDate: P.birthDate }")})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/Parameters
          [(parameter "return") count] := 3
          [(parameter "return") 0 (parameter-part "id") 0 :value] := #fhir/string "0"
          [(parameter "return") 0 (parameter-part "birthDate") 0 :value] := #fhir/date #system/date "2020-02-08"
          [(parameter "return") 1 (parameter-part "id") 0 :value] := #fhir/string "1"
          [(parameter "return") 1 (parameter-part "birthDate") 0 :value] := #fhir/date #system/date "2022-04-01"
          [(parameter "return") 2 (parameter-part "id") 0 :value] := #fhir/string "2"
          [(parameter "return") 2 (parameter-part "birthDate") count] := 0))))

  (testing "returns the id, first code and subject.reference of all observations"
    (with-handler [handler]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]
        [:create {:fhir/type :fhir/Patient :id "1"}]
        [:create {:fhir/type :fhir/Observation :id "0"
                  :code
                  #fhir/CodeableConcept
                   {:coding
                    [#fhir/Coding
                      {:system #fhir/uri "system-121531"
                       :code #fhir/code "code-121534"}]}
                  :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
        [:create {:fhir/type :fhir/Observation :id "1"
                  :code
                  #fhir/CodeableConcept
                   {:coding
                    [#fhir/Coding
                      {:system #fhir/uri "system-121531"
                       :code #fhir/code "code-122509"}]}
                  :subject #fhir/Reference{:reference #fhir/string "Patient/1"}}]]]

      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :body
               (fu/parameters
                "expression" #fhir/string "[Observation] O
                  return { id: O.id, code: O.code.coding.code.first(), subject: O.subject.reference.first() }")})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/Parameters
          [(parameter "return") count] := 2
          [(parameter "return") 0 (parameter-part "id") 0 :value] := #fhir/string "0"
          [(parameter "return") 0 (parameter-part "code") 0 :value] := #fhir/code "code-121534"
          [(parameter "return") 0 (parameter-part "subject") 0 :value] := #fhir/string "Patient/0"
          [(parameter "return") 1 (parameter-part "id") 0 :value] := #fhir/string "1"
          [(parameter "return") 1 (parameter-part "code") 0 :value] := #fhir/code "code-122509"
          [(parameter "return") 1 (parameter-part "subject") 0 :value] := #fhir/string "Patient/1"))))

  (testing "returns the id and patient identifier of all observations"
    (with-handler [handler]
      [[[:create {:fhir/type :fhir/Patient :id "0"
                  :identifier [#fhir/Identifier{:value #fhir/string "identifier-151139"}]}]
        [:create {:fhir/type :fhir/Patient :id "1"
                  :identifier [#fhir/Identifier{:value #fhir/string "identifier-151151"}]}]
        [:create {:fhir/type :fhir/Observation :id "0"
                  :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
        [:create {:fhir/type :fhir/Observation :id "1"
                  :subject #fhir/Reference{:reference #fhir/string "Patient/1"}}]]]

      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :body
               (fu/parameters
                "expression" #fhir/string "[Observation] O
                  return { id: O.id, patientIdentifier: First([Patient] P where P.id = Last(Split(O.subject.reference, '/')) return P.identifier.value.first()) }")})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/Parameters
          [(parameter "return") count] := 2
          [(parameter "return") 0 (parameter-part "id") 0 :value] := #fhir/string "0"
          [(parameter "return") 0 (parameter-part "patientIdentifier") 0 :value] := #fhir/string "identifier-151139"
          [(parameter "return") 1 (parameter-part "id") 0 :value] := #fhir/string "1"
          [(parameter "return") 1 (parameter-part "patientIdentifier") 0 :value] := #fhir/string "identifier-151151"))))

  (testing "returns the id of observations of male patients"
    (with-handler [handler]
      [[[:create {:fhir/type :fhir/Patient :id "0" :gender #fhir/code "male"}]
        [:create {:fhir/type :fhir/Patient :id "1" :gender #fhir/code "female"}]
        [:create {:fhir/type :fhir/Observation :id "0"
                  :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
        [:create {:fhir/type :fhir/Observation :id "1"
                  :subject #fhir/Reference{:reference #fhir/string "Patient/1"}}]]]

      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :body
               (fu/parameters
                "expression" #fhir/string "[Observation] O
                  with [Patient] P
                    such that O.subject.reference = 'Patient/' + P.id and P.gender = 'male'
                  return { id: O.id }")})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/Parameters
          [(parameter "return") count] := 1
          [(parameter "return") 0 (parameter-part "id") 0 :value] := #fhir/string "0"))))

  (testing "returns observations with a particular code"
    (with-handler [handler]
      [[[:create {:fhir/type :fhir/Observation :id "0"
                  :code
                  #fhir/CodeableConcept
                   {:coding
                    [#fhir/Coding
                      {:system #fhir/uri "system-121531"
                       :code #fhir/code "code-121534"}]}}]
        [:create {:fhir/type :fhir/Observation :id "1"
                  :code
                  #fhir/CodeableConcept
                   {:coding
                    [#fhir/Coding
                      {:system #fhir/uri "system-121531"
                       :code #fhir/code "code-125112"}]}}]]]

      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :body
               (fu/parameters
                "expression" #fhir/string "[Observation: Code {system: 'system-121531', code: 'code-125112'}]")})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/Parameters
          [(parameter "return") count] := 1
          [(parameter "return") 0 :resource :id] := "1")))))

(deftest handler-subject-test
  (testing "errors on invalid subject type"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :body
               (fu/parameters
                "subject" #fhir/date #system/date "2026")})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "invalid"
          [:issue 0 :diagnostics] := #fhir/string "Invalid value for parameter `subject`. Expect FHIR string."))))

  (testing "errors on invalid subject content"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :body
               (fu/parameters
                "subject" #fhir/string "invalid")})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "invalid"
          [:issue 0 :diagnostics] := #fhir/string "Invalid value for parameter `subject`. Invalid subject `invalid` expect `<type>/<id>`."))))

  (testing "errors on non-patient subject"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :body
               (fu/parameters
                "subject" #fhir/string "Practitioner/0")})]

        (is (= 422 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "not-supported"
          [:issue 0 :diagnostics] := #fhir/string "Invalid value for parameter `subject`. Unsupported subject type `Practitioner`."))))

  (testing "errors on missing subject"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :body
               (fu/parameters
                "subject" #fhir/string "Patient/0")})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "invalid"
          [:issue 0 :diagnostics] := #fhir/string "Subject with type `Patient` and id `0` was not found."))))

  (testing "errors on deleted subject"
    (with-handler [handler]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]]
       [[:delete "Patient" "0"]]]

      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :body
               (fu/parameters
                "subject" #fhir/string "Patient/0")})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "invalid"
          [:issue 0 :diagnostics] := #fhir/string "Subject with type `Patient` and id `0` was not found."))))

  (testing "calculates the patients age"
    (with-handler [handler]
      [[[:create {:fhir/type :fhir/Patient :id "0"
                  :birthDate #fhir/date #system/date "1960"}]]]

      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :body
               (fu/parameters
                "subject" #fhir/string "Patient/0"
                "expression" #fhir/string "AgeInYears()")})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/Parameters
          [(parameter "return") count] := 1
          [(parameter "return") 0 :value] := #fhir/integer 10))))

  (testing "calculates the patients age at a certain year"
    (with-handler [handler]
      [[[:create {:fhir/type :fhir/Patient :id "0"
                  :birthDate #fhir/date #system/date "1960"}]]]

      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :body
               (fu/parameters
                "subject" #fhir/string "Patient/0"
                "expression" #fhir/string "AgeInYearsAt(@2020)")})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/Parameters
          [(parameter "return") count] := 1
          [(parameter "return") 0 :value] := #fhir/integer 60)))))
