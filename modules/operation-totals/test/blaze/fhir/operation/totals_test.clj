(ns blaze.fhir.operation.totals-test
  (:require
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.fhir.operation.totals]
   [blaze.fhir.structure-definition-repo-spec]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.middleware.fhir.db :as db]
   [blaze.middleware.fhir.db-spec]
   [blaze.module.test-util :refer [given-failed-system]]
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

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.fhir.operation/totals nil}
      :key := :blaze.fhir.operation/totals
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.fhir.operation/totals {}}
      :key := :blaze.fhir.operation/totals
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :structure-definition-repo))))

  (testing "invalid structure definition repo"
    (given-failed-system {:blaze.fhir.operation/totals {:structure-definition-repo ::invalid}}
      :key := :blaze.fhir.operation/totals
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.fhir/structure-definition-repo]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(def config
  (assoc
   api-stub/mem-node-config
   :blaze.fhir.operation/totals
   {:structure-definition-repo structure-definition-repo}))

(defmacro with-handler [[handler-binding & [node-binding]] & more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{node# :blaze.db/node
                         handler# :blaze.fhir.operation/totals} config]
       ~txs
       (let [~handler-binding (db/wrap-db handler# node# 100)
             ~(or node-binding '_) node#]
         ~@body))))

(deftest handler-test
  (testing "on empty database"
    (with-handler [handler]
      (let [{:keys [status body]} @(handler {})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/Parameters
          :parameter := []))))

  (testing "with one patient"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (let [{:keys [status body]} @(handler {})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/Parameters
          [:parameter count] := 1
          [:parameter 0 :name] := #fhir/string "Patient"
          [:parameter 0 :value] := #fhir/unsignedInt 1))))

  (testing "with two patients"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "1"}]]]

      (let [{:keys [status body]} @(handler {})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/Parameters
          [:parameter count] := 1
          [:parameter 0 :name] := #fhir/string "Patient"
          [:parameter 0 :value] := #fhir/unsignedInt 2))))

  (testing "with one patient and one observation"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"}]]]

      (let [{:keys [status body]} @(handler {})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/Parameters
          [:parameter count] := 2
          [:parameter 0 :name] := #fhir/string "Observation"
          [:parameter 0 :value] := #fhir/unsignedInt 1
          [:parameter 1 :name] := #fhir/string "Patient"
          [:parameter 1 :value] := #fhir/unsignedInt 1)))))
