(ns blaze.fhir.operation.graphql-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.db.resource-store :as rs]
   [blaze.db.spec]
   [blaze.executors :as ex]
   [blaze.fhir.operation.graphql :as graphql]
   [blaze.fhir.operation.graphql.spec]
   [blaze.fhir.operation.graphql.test-util :refer [wrap-error]]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.middleware.fhir.db :refer [wrap-db]]
   [blaze.middleware.fhir.db-spec]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def config
  (assoc
   api-stub/mem-node-config
   ::graphql/handler
   {:node (ig/ref :blaze.db/node)
    :writing-context (ig/ref :blaze.fhir/writing-context)
    :executor (ig/ref :blaze.test/executor)}
   :blaze.fhir/writing-context
   {:structure-definition-repo structure-definition-repo}
   :blaze.test/executor {}))

(deftest init-test
  (testing "nil config"
    (given-failed-system {::graphql/handler nil}
      :key := ::graphql/handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {::graphql/handler {}}
      :key := ::graphql/handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :writing-context))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :executor))))

  (testing "invalid node"
    (given-failed-system (assoc-in config [::graphql/handler :node] ::invalid)
      :key := ::graphql/handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/node]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid writing-context"
    (given-failed-system (assoc-in config [::graphql/handler :writing-context] ::invalid)
      :key := ::graphql/handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.fhir/writing-context]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid executor"
    (given-failed-system (assoc-in config [::graphql/handler :executor] ::invalid)
      :key := ::graphql/handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::graphql/executor]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(deftest executor-init-test
  (testing "nil config"
    (given-failed-system {::graphql/executor nil}
      :key := ::graphql/executor
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "invalid num-threads"
    (given-failed-system {::graphql/executor {:num-threads ::invalid}}
      :key := ::graphql/executor
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::graphql/num-threads]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "with default num-threads"
    (with-system [{::graphql/keys [executor]} {::graphql/executor {}}]
      (is (ex/executor? executor)))))

(defmacro with-handler [[handler-binding] & more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{node# :blaze.db/node
                         handler# ::graphql/handler} config]
       ~txs
       (let [~handler-binding (-> handler# (wrap-db node# 100) wrap-error)]
         ~@body))))

(deftest execute-query-test
  (testing "query param"
    (testing "invalid query"
      (testing "via query param"
        (with-handler [handler]
          (let [{:keys [status body]}
                @(handler
                  {:request-method :get
                   :params {"query" "{"}})]

            (is (= 200 status))

            (given body
              [:errors 0 :message] := "Failed to parse GraphQL query."))))

      (testing "via body"
        (with-handler [handler]
          (let [{:keys [status body]}
                @(handler
                  {:request-method :post
                   :body {:query "{"}})]

            (is (= 200 status))

            (given body
              [:errors 0 :message] := "Failed to parse GraphQL query.")))))

    (testing "success"
      (testing "Patient"
        (testing "empty result"
          (testing "via query param"
            (with-handler [handler]
              (let [{:keys [status body]}
                    @(handler
                      {:request-method :get
                       :params {"query" "{ PatientList { gender } }"}})]

                (is (= 200 status))

                (given body
                  [:data :PatientList] :? empty?
                  [:errors] :? empty?))))

          (testing "via body"
            (with-handler [handler]
              (let [{:keys [status body]}
                    @(handler
                      {:request-method :post
                       :body {:query "{ PatientList { gender } }"}})]

                (is (= 200 status))

                (given body
                  [:data :PatientList] :? empty?
                  [:errors] :? empty?)))))

        (testing "one Patient"
          (with-handler [handler]
            [[[:put {:fhir/type :fhir/Patient :id "0"
                     :gender #fhir/code "male"}]]]

            (let [{:keys [status body]}
                  @(handler
                    {:request-method :get
                     :params {"query" "{ PatientList { id gender } }"}})]

              (is (= 200 status))

              (given body
                [:data :PatientList 0 :id] := "0"
                [:data :PatientList 0 :gender] := "male"
                [:errors] :? empty?)))))

      (testing "Observation"
        (testing "empty result"
          (with-handler [handler]
            (let [{:keys [status body]}
                  @(handler
                    {:request-method :get
                     :params {"query" "{ ObservationList { subject { reference } } }"}})]

              (is (= 200 status))

              (given body
                [:data :ObservationList] :? empty?
                [:errors] :? empty?))))

        (testing "one Observation"
          (with-handler [handler]
            [[[:put {:fhir/type :fhir/Patient :id "0"}]
              [:put {:fhir/type :fhir/Observation :id "0"
                     :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

            (let [{:keys [status body]}
                  @(handler
                    {:request-method :get
                     :params {"query" "{ ObservationList { subject { reference } } }"}})]

              (is (= 200 status))

              (given body
                [:data :ObservationList 0 :subject :reference] := "Patient/0"
                [:errors] :? empty?))))

        (testing "one Observation with code"
          (with-handler [handler]
            [[[:put {:fhir/type :fhir/Patient :id "0"}]
              [:put {:fhir/type :fhir/Observation :id "0"
                     :code
                     #fhir/CodeableConcept
                      {:coding
                       [#fhir/Coding
                         {:system #fhir/uri "http://loinc.org"
                          :code #fhir/code "39156-5"}]}
                     :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
              [:put {:fhir/type :fhir/Observation :id "1"
                     :code
                     #fhir/CodeableConcept
                      {:coding
                       [#fhir/Coding
                         {:system #fhir/uri "http://loinc.org"
                          :code #fhir/code "29463-7"}]}
                     :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

            (let [{:keys [status body]}
                  @(handler
                    {:request-method :get
                     :params {"query" "{ ObservationList(code: \"39156-5\") { subject { reference } } }"}})]

              (is (= 200 status))

              (given body
                [:data :ObservationList count] := 1
                [:data :ObservationList 0 :subject :reference] := "Patient/0"
                [:errors] :? empty?)))))))

  (testing "missing resource contents"
    (with-redefs [rs/multi-get (fn [_ _] (ac/completed-future {}))]
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

        (let [{:keys [status body]}
              @(handler
                {:request-method :get
                 :params {"query" "{ PatientList { gender } }"}})]

          (is (= 200 status))

          (given body
            [:errors 0 :message] := "The resource content of `Patient/0` with hash `C9ADE22457D5AD750735B6B166E3CE8D6878D09B64C2C2868DCB6DE4C9EFBD4F` was not found."))))))
