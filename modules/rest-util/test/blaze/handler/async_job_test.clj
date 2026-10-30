(ns blaze.handler.async-job-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.util :as fu]
   [blaze.handler.async-job :as async-job]
   [blaze.handler.async-job-spec]
   [blaze.handler.util :as handler-util]
   [blaze.job-scheduler]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def ^:private base-url "base-url-111523")

(def ^:private param-specs
  {"name"
   {:action :copy
    :required true
    :coerce (fn [{:keys [value]}]
              (if (= "invalid" value)
                (ba/incorrect "Is invalid.")
                value))}})

(defn- job
  "Returns a job resource with the authored-on time and the name param as
  description, so that tests can check both were passed."
  [authored-on {:keys [name]}]
  {:fhir/type :fhir/Task
   :status #fhir/code "ready"
   :intent #fhir/code "order"
   :authoredOn (type/dateTime authored-on)
   :description (type/string name)})

(def ^:private config
  (assoc
   api-stub/mem-node-config
   :blaze/job-scheduler
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}))

(defn- wrap-defaults [handler job-scheduler]
  (fn [request]
    (handler
     (assoc request
            :blaze/base-url base-url
            :blaze/job-scheduler job-scheduler))))

(defn- wrap-error [handler]
  (fn [request]
    (-> (handler request)
        (ac/exceptionally handler-util/error-response))))

(defmacro with-handler [[handler-binding node-binding] & body]
  `(with-system-data [{node# :blaze.db/node
                       job-scheduler# :blaze/job-scheduler
                       clock# :blaze.test/fixed-clock} config]
     []
     (let [~handler-binding (-> (async-job/handler
                                 {:clock clock# :context-path "/fhir"}
                                 param-specs job)
                                (wrap-defaults job-scheduler#)
                                wrap-error)
           ~node-binding node#]
       ~@body)))

(deftest handler-test
  (testing "wrong resource type"
    (with-handler [handler _]
      (let [{:keys [status body]} @(handler {:body {:fhir/type :fhir/Patient}})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :code] := #fhir/code "invalid"
          [:issue 0 :diagnostics] := #fhir/string "Expected Parameters resource but was `Patient` resource."))))

  (testing "missing body"
    (with-handler [handler _]
      (let [{:keys [status body]} @(handler {})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :code] := #fhir/code "invalid"
          [:issue 0 :diagnostics] := #fhir/string "Missing required parameter `name`."))))

  (testing "invalid parameter"
    (with-handler [handler _]
      (let [{:keys [status body]}
            @(handler {:body (fu/parameters "name" #fhir/string "invalid")})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :code] := #fhir/code "invalid"
          [:issue 0 :diagnostics] := #fhir/string "Invalid value for parameter `name`. Is invalid."))))

  (testing "success"
    (with-handler [handler node]
      (let [{:keys [status headers]}
            @(handler {:body (fu/parameters "name" #fhir/string "name-112134")})]

        (is (= 202 status))

        (testing "the Content-Location header contains the status endpoint URL"
          (is (= (str base-url "/fhir/__async-status/AAAAAAAAAAAAAAAA")
                 (get headers "Content-Location"))))

        (testing "the job is created from the authored-on time and the params"
          (given @(d/pull node (d/resource-handle (d/db node) "Task" "AAAAAAAAAAAAAAAA"))
            :fhir/type := :fhir/Task
            :authoredOn := #fhir/dateTime #system/date-time "1970-01-01T00:00Z"
            :description := #fhir/string "name-112134"))))))
