(ns blaze.operation.patient.purge-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.db.spec]
   [blaze.handler.fhir.util-spec]
   [blaze.handler.util :as handler-util]
   [blaze.middleware.fhir.decrypt-page-id-spec]
   [blaze.module.test-util :refer [given-failed-system]]
   [blaze.operation.patient.purge]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)
(tu/set-default-locale-english!)                            ; important for the thousands separator in 10,000

(test/use-fixtures :each tu/fixture)

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.operation.patient/purge nil}
      :key := :blaze.operation.patient/purge
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.operation.patient/purge {}}
      :key := :blaze.operation.patient/purge
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))))

  (testing "invalid node"
    (given-failed-system {:blaze.operation.patient/purge {:node ::invalid}}
      :key := :blaze.operation.patient/purge
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/node]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(def base-url "base-url-113047")
(def context-path "/context-path-173858")

(def config
  (assoc
   api-stub/mem-node-config
   :blaze.operation.patient/purge
   {:node (ig/ref :blaze.db/node)}))

(defn wrap-error [handler]
  (fn [request]
    (-> (handler request)
        (ac/exceptionally handler-util/error-response))))

(defmacro with-handler [[handler-binding & [node-binding]] & more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{node# :blaze.db/node
                         handler# :blaze.operation.patient/purge} config]
       ~txs
       (let [~handler-binding (-> handler# wrap-error)
             ~(or node-binding '_) node#]
         ~@body))))

(deftest handler-test
  (testing "Success on non-existing patient"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler
              {:path-params {:id "0"}})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "success"
          [:issue 0 :code] := #fhir/code "success"
          [:issue 0 :diagnostics] := #fhir/string "The patient with id `0` was purged successfully."))))

  (testing "Success on existing patient"
    (with-handler [handler node]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]]]

      (let [{:keys [status body]}
            @(handler
              {:path-params {:id "0"}})]

        (testing "the patient is gone"
          (is (nil? (d/resource-handle (d/db node) "Patient" "0"))))

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "success"
          [:issue 0 :code] := #fhir/code "success"
          [:issue 0 :diagnostics] := #fhir/string "The patient with id `0` was purged successfully."))))

  (testing "Fails on one observation referenced by another observation outside the patients compartment"
    (with-handler [handler]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]
        [:create {:fhir/type :fhir/Observation :id "0"
                  :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
        [:create {:fhir/type :fhir/Observation :id "1"
                  :hasMember [#fhir/Reference{:reference #fhir/string "Observation/0"}]}]]]

      (let [{:keys [status body]}
            @(handler
              {:path-params {:id "0"}})]

        (is (= 409 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "conflict"
          [:issue 0 :diagnostics] := #fhir/string "Referential integrity violated. Resource `Observation/0` should be deleted but is referenced from `Observation/1`.")))))
