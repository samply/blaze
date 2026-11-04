(ns blaze.interaction.delete-history-version-test
  "Specifications relevant for the FHIR delete-history-version interaction:

  https://build.fhir.org/http.html#delete-history-version"
  (:require
   [blaze.db.api :as d]
   [blaze.db.api-stub :as api-stub :refer [with-db with-system-data]]
   [blaze.db.spec]
   [blaze.interaction.delete-history-version]
   [blaze.module.test-util :refer [given-failed-system]]
   [blaze.test-util :as tu :refer [given-failed-future]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [reitit.core :as reitit]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.interaction/delete-history-version nil}
      :key := :blaze.interaction/delete-history-version
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.interaction/delete-history-version {}}
      :key := :blaze.interaction/delete-history-version
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))))

  (testing "invalid node"
    (given-failed-system {:blaze.interaction/delete-history-version {:node ::invalid}}
      :key := :blaze.interaction/delete-history-version
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/node]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(def ^:private config
  (assoc api-stub/mem-node-config
         :blaze.interaction/delete-history-version
         {:node (ig/ref :blaze.db/node)}))

(defmacro with-handler [[handler-binding node-binding] & more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{node# :blaze.db/node
                         handler# :blaze.interaction/delete-history-version} config]
       ~txs
       (let [~handler-binding handler#
             ~(or node-binding '_) node#]
         ~@body))))

(defn- request [id vid]
  {:path-params {:id id :vid vid}
   ::reitit/match {:data {:fhir.resource/type "Patient"}}})

(deftest handler-test
  (testing "Returns No Content on non-existing resource"
    (with-handler [handler]
      (let [{:keys [status body]} @(handler (request "0" "1"))]

        (is (= 204 status))

        (is (nil? body)))))

  (testing "Returns No Content on invalid version"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (doseq [vid ["a" "-1" "1.1" "72057594037927936" "9223372036854775808"]]
        (let [{:keys [status body]} @(handler (request "0" vid))]

          (is (= 204 status))

          (is (nil? body))))))

  (testing "Returns No Content on non-existing version"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (let [{:keys [status body]} @(handler (request "0" "2"))]

        (is (= 204 status))

        (is (nil? body)))))

  (testing "Returns No Content on successful version deletion"
    (with-handler [handler node]
      [[[:put {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean false}]]
       [[:put {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean true}]]]

      (let [{:keys [status body]} @(handler (request "0" "1"))]

        (is (= 204 status))

        (is (nil? body)))

      (testing "the version is removed from the history"
        (with-db [db node]
          (given (vec (d/instance-history db "Patient" "0"))
            count := 1
            [0 :t] := 2)))))

  (testing "Returns Conflict on deleting the current version"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean false}]]
       [[:put {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean true}]]]

      (given-failed-future (handler (request "0" "2"))
        ::anom/category := ::anom/conflict
        ::anom/message := "Can't delete the current version `2` of the resource `Patient/0`."))))
