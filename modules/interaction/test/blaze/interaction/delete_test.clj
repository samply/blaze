(ns blaze.interaction.delete-test
  "Specifications relevant for the FHIR update interaction:

  https://www.hl7.org/fhir/http.html#delete"
  (:require
    [blaze.db.api-stub :refer [mem-node-system with-system-data]]
    [blaze.executors :as ex]
    [blaze.interaction.delete]
    [blaze.test-util :as tu :refer [given-thrown]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [integrant.core :as ig]
    [reitit.core :as reitit]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(test/use-fixtures :each tu/fixture)


(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze.interaction/delete nil})
      :key := :blaze.interaction/delete
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze.interaction/delete {}})
      :key := :blaze.interaction/delete
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :executor))))

  (testing "invalid executor"
    (given-thrown (ig/init {:blaze.interaction/delete {:executor ::invalid}})
      :key := :blaze.interaction/delete
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:explain ::s/problems 1 :pred] := `ex/executor?
      [:explain ::s/problems 1 :val] := ::invalid)))


(def system
  (assoc mem-node-system
    :blaze.interaction/delete
    {:node (ig/ref :blaze.db/node)
     :executor (ig/ref :blaze.test/executor)}
    :blaze.test/executor {}))


(defmacro with-handler [[handler-binding] & more]
  (let [[txs body] (tu/extract-txs-body more)]
    `(with-system-data [{handler# :blaze.interaction/delete} system]
       ~txs
       (let [~handler-binding handler#]
         ~@body))))


(deftest handler-test
  (testing "Returns No Content on non-existing resource"
    (with-handler [handler]
      (let [{:keys [status headers body]}
            @(handler
               {:path-params {:id "0"}
                ::reitit/match {:data {:fhir.resource/type "Patient"}}})]

        (is (= 204 status))

        (testing "Transaction time in Last-Modified header"
          (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

        (testing "Version in ETag header"
          ;; 1 is the T of the transaction of the resource update
          (is (= "W/\"1\"" (get headers "ETag"))))

        (is (nil? body)))))


  (testing "Returns No Content on successful deletion"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (let [{:keys [status headers body]}
            @(handler
               {:path-params {:id "0"}
                ::reitit/match {:data {:fhir.resource/type "Patient"}}})]

        (is (= 204 status))

        (testing "Transaction time in Last-Modified header"
          (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

        (testing "Version in ETag header"
          ;; 2 is the T of the transaction of the resource update
          (is (= "W/\"2\"" (get headers "ETag"))))

        (is (nil? body)))))


  (testing "Returns No Content on already deleted resource"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:delete "Patient" "0"]]]

      (let [{:keys [status headers body]}
            @(handler
               {:path-params {:id "0"}
                ::reitit/match {:data {:fhir.resource/type "Patient"}}})]

        (is (= 204 status))

        (testing "Transaction time in Last-Modified header"
          (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

        (testing "Version in ETag header"
          ;; 3 is the T of the transaction of the resource update
          (is (= "W/\"3\"" (get headers "ETag"))))

        (is (nil? body))))))
