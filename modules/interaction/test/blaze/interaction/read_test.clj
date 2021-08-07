(ns blaze.interaction.read-test
  "Specifications relevant for the FHIR read interaction:

  https://www.hl7.org/fhir/http.html#read
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.db.api-stub :refer [mem-node-system with-system-data]]
    [blaze.db.spec]
    [blaze.interaction.read]
    [blaze.test-util :refer [given-thrown with-system]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [integrant.core :as ig]
    [juxt.iota :refer [given]]
    [reitit.core :as reitit]
    [taoensso.timbre :as log])
  (:import
    [java.time Instant]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze.interaction/read nil})
      :key := :blaze.interaction/read
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze.interaction/read {}})
      :key := :blaze.interaction/read
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))))

  (testing "invalid node"
    (given-thrown (ig/init {:blaze.interaction/read {:node ::node}})
      :key := :blaze.interaction/read
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `blaze.db.spec/node?
      [:explain ::s/problems 0 :val] := ::node)))


(def system
  (assoc mem-node-system
    :blaze.interaction/read
    {:node (ig/ref :blaze.db/node)}))


(def match
  {:data {:fhir.resource/type "Patient"}})


(defn wrap-defaults [handler]
  (fn [request]
    @(handler (assoc request ::reitit/match match))))


(defmacro with-handler [[handler-binding] & body]
  `(with-system [{handler# :blaze.interaction/read} system]
     (let [~handler-binding (wrap-defaults handler#)]
       ~@body)))


(defmacro with-handler-data [[handler-binding] txs & body]
  `(with-system-data [{handler# :blaze.interaction/read} system]
     ~txs
     (let [~handler-binding (wrap-defaults handler#)]
       ~@body)))


(deftest handler-test
  (testing "Returns Not Found on Non-Existing Resource"
    (with-handler [handler]
      (let [{:keys [status body]}
            (handler
              {:path-params {:id "0"}})]

        (is (= 404 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code"error"
          [:issue 0 :code] := #fhir/code"not-found"
          [:issue 0 :diagnostics] := "Resource `/Patient/0` not found"))))


  (testing "Returns Not Found on Invalid Version ID"
    (with-handler [handler]
      (let [{:keys [status body]}
            (handler
              {:path-params {:id "0" :vid "a"}})]

        (is (= 404 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code"error"
          [:issue 0 :code] := #fhir/code"not-found"
          [:issue 0 :diagnostics] := "Resource `/Patient/0` with versionId `a` was not found."))))


  (testing "Returns Gone on Deleted Resource"
    (with-handler-data [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:delete "Patient" "0"]]]

      (let [{:keys [status body headers]}
            (handler
              {:path-params {:id "0"}})]

        (is (= 410 status))

        (testing "Transaction time in Last-Modified header"
          (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code"error"
          [:issue 0 :code] := #fhir/code"deleted"))))


  (testing "Returns Existing Resource"
    (with-handler-data [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (let [{:keys [status headers body]}
            (handler
              {:path-params {:id "0"}})]

        (is (= 200 status))

        (testing "Transaction time in Last-Modified header"
          (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

        (testing "Version in ETag header"
          ;; 1 is the T of the transaction of the resource update
          (is (= "W/\"1\"" (get headers "ETag"))))

        (given body
          :fhir/type := :fhir/Patient
          :id := "0"
          [:meta :versionId] := #fhir/id"1"
          [:meta :lastUpdated] := Instant/EPOCH))))


  (testing "Returns Existing Resource on versioned read"
    (with-handler-data [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (let [{:keys [status headers body]}
            (handler
              {:path-params {:id "0" :vid "1"}})]

        (is (= 200 status))

        (testing "Transaction time in Last-Modified header"
          (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

        (testing "Version in ETag header"
          ;; 1 is the T of the transaction of the resource update
          (is (= "W/\"1\"" (get headers "ETag"))))

        (given body
          :fhir/type := :fhir/Patient
          :id := "0"
          [:meta :versionId] := #fhir/id"1"
          [:meta :lastUpdated] := Instant/EPOCH)))))
