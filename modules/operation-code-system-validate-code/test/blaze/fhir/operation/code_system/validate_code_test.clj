(ns blaze.fhir.operation.code-system.validate-code-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.fhir.operation.code-system.validate-code]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.util :as fu]
   [blaze.handler.util :as handler-util]
   [blaze.middleware.fhir.db :refer [wrap-db]]
   [blaze.middleware.fhir.db-spec]
   [blaze.module.test-util :refer [given-failed-system]]
   [blaze.terminology-service :as ts]
   [blaze.terminology-service.local :as ts-local]
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
    (given-failed-system {:blaze.fhir.operation.code-system/validate-code nil}
      :key := :blaze.fhir.operation.code-system/validate-code
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.fhir.operation.code-system/validate-code {}}
      :key := :blaze.fhir.operation.code-system/validate-code
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :terminology-service))))

  (testing "invalid structure definition repo"
    (given-failed-system {:blaze.fhir.operation.code-system/validate-code {:terminology-service ::invalid}}
      :key := :blaze.fhir.operation.code-system/validate-code
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/terminology-service]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(def config
  (assoc
   api-stub/mem-node-config
   :blaze.fhir.operation.code-system/validate-code
   {:terminology-service (ig/ref ::ts/local)}
   ::ts/local
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :graph-cache (ig/ref ::ts-local/graph-cache)}
   :blaze.test/fixed-rng-fn {}
   ::ts-local/graph-cache {}))

(defn wrap-error [handler]
  (fn [request]
    (-> (handler request)
        (ac/exceptionally handler-util/error-response))))

(defmacro with-handler [[handler-binding] & more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{node# :blaze.db/node
                         handler# :blaze.fhir.operation.code-system/validate-code} config]
       ~txs
       (let [~handler-binding (-> handler# (wrap-db node# 100) wrap-error)]
         ~@body))))

(deftest handler-test
  (testing "code system not found"
    (testing "by id"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler {:path-params {:id "170852"}})]

          (is (= 404 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "not-found"
            [:issue 0 :diagnostics] := #fhir/string "Resource `CodeSystem/170852` was not found."))))

    (testing "by url"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler {:query-params {"url" "153404"
                                        "code" "123033"}})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "not-found"
            [:issue 0 :diagnostics] := #fhir/string "The code system `153404` was not found.")))))

  (testing "unsupported parameters"
    (with-handler [handler]
      (doseq [param ["date" "abstract"]]
        (testing "GET"
          (let [{:keys [status body]}
                @(handler {:query-params {param "foo"}})]

            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "error"
              [:issue 0 :code] := #fhir/code "not-supported"
              [:issue 0 :diagnostics] := (type/string (format "Unsupported parameter `%s`." param)))))

        (testing "POST"
          (let [{:keys [status body]}
                @(handler {:request-method :post
                           :body (fu/parameters param #fhir/string "foo")})]

            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "error"
              [:issue 0 :code] := #fhir/code "not-supported"
              [:issue 0 :diagnostics] := (type/string (format "Unsupported parameter `%s`." param))))))))

  (testing "unsupported GET parameters"
    (with-handler [handler]
      (doseq [param ["codeSystem" "coding" "codeableConcept" "tx-resource"]]
        (let [{:keys [status body]}
              @(handler {:query-params {param "foo"}})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "not-supported"
            [:issue 0 :diagnostics] := (type/string (format "Unsupported parameter `%s` in GET request. Please use POST." param)))))))

  (testing "successful validation by id"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/CodeSystem :id "id-162245"
               :url #fhir/uri "system-115910"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-115927"}]}]]]

      (testing "and code"
        (doseq [[code result] [["code-115927" true] ["code-210428" false]]]
          (let [{:keys [status body]}
                @(handler {:path-params {:id "id-162245"}
                           :query-params {"code" code}})]

            (is (= 200 status))

            (given body
              :fhir/type := :fhir/Parameters
              [:parameter 0 :name] := #fhir/string "result"
              [:parameter 0 :value :value] := result)))

        (testing "ignores unknown parameter"
          (let [{:keys [status body]}
                @(handler {:path-params {:id "id-162245"}
                           :query-params {"code" "code-115927"
                                          "foo" "bar"}})]

            (is (= 200 status))

            (given body
              :fhir/type := :fhir/Parameters
              [:parameter 0 :name] := #fhir/string "result"
              [:parameter 0 :value] := #fhir/boolean true))))

      (testing "and coding"
        (let [{:keys [status body]}
              @(handler {:request-method :post
                         :path-params {:id "id-162245"}
                         :body (fu/parameters
                                "coding" #fhir/Coding{:system #fhir/uri "system-115910"
                                                      :code #fhir/code "code-115927"})})]

          (is (= 200 status))

          (given body
            :fhir/type := :fhir/Parameters
            [:parameter count] := 3
            [:parameter 0 :name] := #fhir/string "result"
            [:parameter 0 :value] := #fhir/boolean true
            [:parameter 1 :name] := #fhir/string "code"
            [:parameter 1 :value] := #fhir/code "code-115927"
            [:parameter 2 :name] := #fhir/string "system"
            [:parameter 2 :value] := #fhir/uri "system-115910"))

        (testing "ignores unknown parameter"
          (let [{:keys [status body]}
                @(handler {:request-method :post
                           :path-params {:id "id-162245"}
                           :body (fu/parameters
                                  "coding" #fhir/Coding{:system #fhir/uri "system-115910"
                                                        :code #fhir/code "code-115927"}
                                  "foo" "bar")})]

            (is (= 200 status))

            (given body
              :fhir/type := :fhir/Parameters
              [:parameter count] := 3
              [:parameter 0 :name] := #fhir/string "result"
              [:parameter 0 :value] := #fhir/boolean true
              [:parameter 1 :name] := #fhir/string "code"
              [:parameter 1 :value] := #fhir/code "code-115927"
              [:parameter 2 :name] := #fhir/string "system"
              [:parameter 2 :value] := #fhir/uri "system-115910"))))))

  (testing "successful validation by url"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-115910"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-115927"}]}]]]

      (testing "and code"
        (doseq [[code result] [["code-115927" true] ["code-210428" false]]]
          (let [{:keys [status body]}
                @(handler {:query-params {"url" "system-115910"
                                          "code" code}})]

            (is (= 200 status))

            (given body
              :fhir/type := :fhir/Parameters
              [:parameter 0 :name] := #fhir/string "result"
              [:parameter 0 :value :value] := result))))

      (testing "and coding"
        (let [{:keys [status body]}
              @(handler {:request-method :post
                         :body (fu/parameters
                                "url" #fhir/uri "system-115910"
                                "coding" #fhir/Coding{:system #fhir/uri "system-115910"
                                                      :code #fhir/code "code-115927"})})]

          (is (= 200 status))

          (given body
            :fhir/type := :fhir/Parameters
            [:parameter count] := 3
            [:parameter 0 :name] := #fhir/string "result"
            [:parameter 0 :value] := #fhir/boolean true
            [:parameter 1 :name] := #fhir/string "code"
            [:parameter 1 :value] := #fhir/code "code-115927"
            [:parameter 2 :name] := #fhir/string "system"
            [:parameter 2 :value] := #fhir/uri "system-115910"))))))
