(ns blaze.fhir.operation.disk-perf-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.fhir.operation.disk-perf]
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

(def ^:private base-url "base-url-113220")

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.fhir.operation/disk-perf nil}
      :key := :blaze.fhir.operation/disk-perf
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.fhir.operation/disk-perf {}}
      :key := :blaze.fhir.operation/disk-perf
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :clock))))

  (testing "invalid clock"
    (given-failed-system {:blaze.fhir.operation/disk-perf {:clock ::invalid}}
      :key := :blaze.fhir.operation/disk-perf
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/clock]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(def ^:private config
  (assoc
   api-stub/mem-node-config
   :blaze.fhir.operation/disk-perf
   {:clock (ig/ref :blaze.test/fixed-clock)
    :context-path "/fhir"}
   :blaze/job-scheduler
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}))

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
                         handler# :blaze.fhir.operation/disk-perf} config]
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

  (testing "invalid database parameter"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler {:body (fu/parameters "database" #fhir/integer 1)})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "invalid"
          [:issue 0 :diagnostics] := #fhir/string "Invalid value for parameter `database`. Has to be a code."))))

  (testing "invalid file-size parameter"
    (with-handler [handler]
      (doseq [value [#fhir/decimal 0.0001M #fhir/decimal 100M #fhir/integer 1]]
        (let [{:keys [status body]}
              @(handler {:body (fu/parameters "file-size" value)})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "invalid"
            [:issue 0 :diagnostics] := #fhir/string "Invalid value for parameter `file-size`. Has to be a decimal between 1 MiB and 64 GiB.")))))

  (testing "invalid phase-duration parameter"
    (with-handler [handler]
      (doseq [value [#fhir/decimal 0M #fhir/decimal 301M #fhir/integer 10]]
        (let [{:keys [status body]}
              @(handler {:body (fu/parameters "phase-duration" value)})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "invalid"
            [:issue 0 :diagnostics] := #fhir/string "Invalid value for parameter `phase-duration`. Has to be a decimal between 0 and 300 seconds.")))))

  (testing "invalid max-concurrency parameter"
    (with-handler [handler]
      (doseq [value [#fhir/integer 0 #fhir/positiveInt 1025 #fhir/decimal 4M]]
        (let [{:keys [status body]}
              @(handler {:body (fu/parameters "max-concurrency" value)})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "invalid"
            [:issue 0 :diagnostics] := #fhir/string "Invalid value for parameter `max-concurrency`. Has to be an integer between 1 and 1024.")))))

  (testing "success"
    (testing "without parameters"
      (doseq [body [nil {:fhir/type :fhir/Parameters}]]
        (with-handler [handler]
          (let [{:keys [status headers]}
                @(handler {:body body})]

            (is (= 202 status))

            (testing "the Content-Location header contains the status endpoint URL"
              (is (= (get headers "Content-Location")
                     (str base-url "/fhir/__async-status/AAAAAAAAAAAAAAAA"))))))))

    (testing "with boundary parameters"
      (doseq [[name value] [["file-size" #fhir/decimal 0.0009765625M]
                            ["file-size" #fhir/decimal 64M]
                            ["phase-duration" #fhir/decimal 0.2M]
                            ["phase-duration" #fhir/decimal 300M]
                            ["max-concurrency" #fhir/positiveInt 1]
                            ["max-concurrency" #fhir/positiveInt 1024]]]
        (with-handler [handler]
          (let [{:keys [status]}
                @(handler {:body (fu/parameters name value)})]

            (is (= 202 status))))))

    (testing "with all parameters"
      (with-handler [handler node]
        (let [{:keys [status headers]}
              @(handler {:body (fu/parameters
                                "database" #fhir/code "index"
                                "file-size" #fhir/decimal 1M
                                "phase-duration" #fhir/decimal 10M
                                "max-concurrency" #fhir/positiveInt 4)})]

          (is (= 202 status))

          (testing "the Content-Location header contains the status endpoint URL"
            (is (= (get headers "Content-Location")
                   (str base-url "/fhir/__async-status/AAAAAAAAAAAAAAAA"))))

          (testing "the job contains all parameters"
            (given @(d/pull node (d/resource-handle (d/db node) "Task" "AAAAAAAAAAAAAAAA"))
              [:input count] := 4
              [:input 0 :value] := #fhir/code "index"
              [:input 1 :value :value :value] := 1M
              [:input 1 :value :code] := #fhir/code "GiBy"
              [:input 2 :value :value :value] := 10M
              [:input 2 :value :code] := #fhir/code "s"
              [:input 3 :value] := #fhir/positiveInt 4)))))))
