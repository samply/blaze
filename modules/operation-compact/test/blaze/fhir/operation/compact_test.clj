(ns blaze.fhir.operation.compact-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.fhir.operation.compact]
   [blaze.fhir.util :as fu]
   [blaze.handler.util :as handler-util]
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

(def ^:private base-url "base-url-130959")

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.fhir.operation/compact nil}
      :key := :blaze.fhir.operation/compact
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.fhir.operation/compact {}}
      :key := :blaze.fhir.operation/compact
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :clock))))

  (testing "invalid clock"
    (given-failed-system {:blaze.fhir.operation/compact {:clock ::invalid}}
      :key := :blaze.fhir.operation/compact
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/clock]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(def ^:private config
  (assoc
   api-stub/mem-node-config
   :blaze.fhir.operation/compact
   {:clock (ig/ref :blaze.test/fixed-clock)
    :context-path "/fhir"}
   :blaze/job-scheduler
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}
   :blaze.test/fixed-rng-fn {}))

(defn- wrap-defaults [handler]
  (fn [request]
    (handler
     (assoc request
            :blaze/base-url base-url))))

(defn- wrap-job-scheduler [handler job-scheduler]
  (fn [request]
    (handler (assoc request :blaze/job-scheduler job-scheduler))))

(defn- wrap-error [handler]
  (fn [request]
    (-> (handler request)
        (ac/exceptionally handler-util/error-response))))

(defmacro with-handler [[handler-binding & [node-binding]] & more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{node# :blaze.db/node
                         job-scheduler# :blaze/job-scheduler
                         handler# :blaze.fhir.operation/compact} config]
       ~txs
       (let [~handler-binding (-> handler#
                                  wrap-defaults
                                  (wrap-job-scheduler job-scheduler#)
                                  wrap-error)
             ~(or node-binding '_) node#]
         ~@body))))

(deftest handler-test
  (testing "wrong resource type"
    (with-handler [handler]
      (let [{:keys [status body]} @(handler {:body {:fhir/type :fhir/Patient}})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "invalid"
          [:issue 0 :diagnostics] := #fhir/string "Expected Parameters resource but was `Patient` resource."))))

  (testing "Missing database parameter"
    (with-handler [handler]
      (let [{:keys [status body]} @(handler {:body {:fhir/type :fhir/Parameters}})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "invalid"
          [:issue 0 :diagnostics] := #fhir/string "Missing `database` parameter."))))

  (testing "Missing column-family parameter"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler {:body (fu/parameters "database" #fhir/code "index")})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "invalid"
          [:issue 0 :diagnostics] := #fhir/string "Missing `column-family` parameter."))))

  (testing "success"
    (with-handler [handler]
      (let [{:keys [status headers]}
            @(handler {:body (fu/parameters
                              "database" #fhir/code "index"
                              "column-family" #fhir/code "resource-as-of-index")})]

        (is (= 202 status))

        (testing "the Content-Location header contains the status endpoint URL"
          (is (= (get headers "Content-Location")
                 (str base-url "/fhir/__async-status/AAAAAAAAAAAAAAAA"))))))))
