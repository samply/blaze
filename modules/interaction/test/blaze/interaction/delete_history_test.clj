(ns blaze.interaction.delete-history-test
  "Specifications relevant for the FHIR delete-history interaction:

  https://build.fhir.org/http.html#delete-history"
  (:require
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.db.spec]
   [blaze.interaction.delete-history]
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
    (given-failed-system {:blaze.interaction/delete-history nil}
      :key := :blaze.interaction/delete-history
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.interaction/delete-history {}}
      :key := :blaze.interaction/delete-history
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))))

  (testing "invalid node"
    (given-failed-system {:blaze.interaction/delete-history {:node ::invalid}}
      :key := :blaze.interaction/delete-history
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/node]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(def config
  (assoc api-stub/mem-node-config
         :blaze.interaction/delete-history
         {:node (ig/ref :blaze.db/node)}))

(defmacro with-handler [[handler-binding] & more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{handler# :blaze.interaction/delete-history} config]
       ~txs
       (let [~handler-binding handler#]
         ~@body))))

(deftest handler-test
  (testing "Returns No Content on non-existing resource"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler
              {:path-params {:id "0"}
               ::reitit/match {:data {:fhir.resource/type "Patient"}}})]

        (is (= 204 status))

        (is (nil? body)))))

  (testing "Returns No Content on successful history deletion"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean false}]]
       [[:put {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean true}]]]

      (let [{:keys [status body]}
            @(handler
              {:path-params {:id "0"}
               ::reitit/match {:data {:fhir.resource/type "Patient"}}})]

        (is (= 204 status))

        (is (nil? body))))))
