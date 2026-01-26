(ns blaze.interaction.delete-test
  "Specifications relevant for the FHIR delete interaction:

  https://www.hl7.org/fhir/http.html#delete"
  (:require
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.db.spec]
   [blaze.interaction.delete]
   [blaze.module.test-util :refer [given-failed-system]]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [reitit.core :as reitit]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.interaction/delete nil}
      :key := :blaze.interaction/delete
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.interaction/delete {}}
      :key := :blaze.interaction/delete
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))))

  (testing "invalid node"
    (given-failed-system {:blaze.interaction/delete {:node ::invalid}}
      :key := :blaze.interaction/delete
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/node]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(def ^:private config
  (assoc
   api-stub/mem-node-config
   :blaze.interaction/delete
   {:node (ig/ref :blaze.db/node)}))

(defmacro with-handler [[handler-binding] & more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{handler# :blaze.interaction/delete} config]
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
