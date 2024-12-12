(ns blaze.fhir.operation.value-set.validate-code-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.fhir.operation.value-set.validate-code]
   [blaze.fhir.spec.type :as type]
   [blaze.handler.util :as handler-util]
   [blaze.terminology-service :as ts]
   [blaze.terminology-service.local]
   [blaze.test-util :as tu :refer [given-thrown]]
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
    (given-thrown (ig/init {:blaze.fhir.operation.value-set/validate-code nil})
      :key := :blaze.fhir.operation.value-set/validate-code
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze.fhir.operation.value-set/validate-code {}})
      :key := :blaze.fhir.operation.value-set/validate-code
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :terminology-service))))

  (testing "invalid structure definition repo"
    (given-thrown (ig/init {:blaze.fhir.operation.value-set/validate-code {:terminology-service ::invalid}})
      :key := :blaze.fhir.operation.value-set/validate-code
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/terminology-service]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(def config
  (assoc
   api-stub/mem-node-config
   :blaze.fhir.operation.value-set/validate-code
   {:terminology-service (ig/ref ::ts/local)}
   ::ts/local
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}
   :blaze.test/fixed-rng-fn {}))

(defn wrap-error [handler]
  (fn [request]
    (-> (handler request)
        (ac/exceptionally handler-util/error-response))))

(defmacro with-handler [[handler-binding] & more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{handler# :blaze.fhir.operation.value-set/validate-code} config]
       ~txs
       (let [~handler-binding (-> handler# wrap-error)]
         ~@body))))

(deftest handler-test
  (testing "value set not found"
    (testing "by id"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler {:path-params {:id "170852"}})]

          (is (= 404 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code"error"
            [:issue 0 :code] := #fhir/code"not-found"
            [:issue 0 :diagnostics] := "The value set with id `170852` was not found."))))

    (testing "by url"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler {:query-params {"url" "153404"}})]

          (is (= 404 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code"error"
            [:issue 0 :code] := #fhir/code"not-found"
            [:issue 0 :diagnostics] := "The value set `153404` was not found."))))

    (testing "without url or id"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler {})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code"error"
            [:issue 0 :code] := #fhir/code"invalid"
            [:issue 0 :diagnostics] := "Missing required parameter `url`."))))

    (testing "with code but without system"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler {:query-params {"code" "foo"}})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code"error"
            [:issue 0 :code] := #fhir/code"invalid"
            [:issue 0 :diagnostics] := "Missing required parameter `system`."))))

    (testing "unsupported parameters"
      (with-handler [handler]
        (doseq [param ["context" "valueSetVersion" "systemVersion" "display"
                       "codeableConcept" "date" "abstract" "useSupplement"]]
          (testing "GET"
            (let [{:keys [status body]}
                  @(handler {:query-params {param "foo"}})]

              (is (= 400 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code"error"
                [:issue 0 :code] := #fhir/code"not-supported"
                [:issue 0 :diagnostics] := (format "Unsupported parameter `%s`." param))))

          (testing "POST"
            (let [{:keys [status body]}
                  @(handler {:request-method :post
                             :body {:fhir/type :fhir/Parameters
                                    :parameter
                                    [{:fhir/type :fhir.Parameters/parameter
                                      :name (type/string param)
                                      :value (type/string "foo")}]}})]

              (is (= 400 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code"error"
                [:issue 0 :code] := #fhir/code"not-supported"
                [:issue 0 :diagnostics] := (format "Unsupported parameter `%s`." param)))))))

    (testing "unsupported GET parameters"
      (with-handler [handler]
        (doseq [param ["valueSet" "coding"]]
          (let [{:keys [status body]}
                @(handler {:query-params {param "foo"}})]

            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"not-supported"
              [:issue 0 :diagnostics] := (format "Unsupported parameter `%s` in GET request. Please use POST." param)))))))

  (testing "successful validation by id"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-115910"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-115927"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "id-152952"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-115910"}]}}]]]

      (testing "and system and code"
        (doseq [[code result] [["code-115927" true] ["code-210428" false]]]
          (let [{:keys [status body]}
                @(handler {:path-params {:id "id-152952"}
                           :query-params {"system" "system-115910"
                                          "code" code}})]

            (is (= 200 status))

            (given body
              :fhir/type := :fhir/Parameters
              [:parameter 0 :name] := #fhir/string"result"
              [:parameter 0 :value type/value] := result))))

      (testing "and coding"
        (let [{:keys [status body]}
              @(handler {:request-method :post
                         :path-params {:id "id-152952"}
                         :body {:fhir/type :fhir/Parameters
                                :parameter
                                [{:fhir/type :fhir.Parameters/parameter
                                  :name #fhir/string"coding"
                                  :value #fhir/Coding{:system #fhir/uri"system-115910"
                                                      :code #fhir/code"code-115927"}}]}})]

          (is (= 200 status))

          (given body
            :fhir/type := :fhir/Parameters
            [:parameter count] := 3
            [:parameter 0 :name] := #fhir/string"result"
            [:parameter 0 :value] := #fhir/boolean true
            [:parameter 1 :name] := #fhir/string"code"
            [:parameter 1 :value] := #fhir/code"code-115927"
            [:parameter 2 :name] := #fhir/string"system"
            [:parameter 2 :value] := #fhir/uri"system-115910")))))

  (testing "successful validation by url"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-115910"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-115927"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "152952"
               :url #fhir/uri"value-set-163309"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-115910"}]}}]]]

      (testing "and system and code"
        (doseq [[code result] [["code-115927" true] ["code-210428" false]]]
          (let [{:keys [status body]}
                @(handler {:query-params {"url" "value-set-163309"
                                          "system" "system-115910"
                                          "code" code}})]

            (is (= 200 status))

            (given body
              :fhir/type := :fhir/Parameters
              [:parameter 0 :name] := #fhir/string"result"
              [:parameter 0 :value type/value] := result)))

        (testing "with displayLanguage"
          (doseq [lang ["en" "de"]]
            (let [{:keys [status body]}
                  @(handler {:query-params {"url" "value-set-163309"
                                            "system" "system-115910"
                                            "code" "code-115927"
                                            "displayLanguage" lang}})]

              (is (= 200 status))

              (given body
                :fhir/type := :fhir/Parameters
                [:parameter 0 :name] := #fhir/string"result"
                [:parameter 0 :value] := #fhir/boolean true)))))

      (testing "and coding"
        (let [{:keys [status body]}
              @(handler {:request-method :post
                         :body {:fhir/type :fhir/Parameters
                                :parameter
                                [{:fhir/type :fhir.Parameters/parameter
                                  :name #fhir/string"url"
                                  :value #fhir/uri"value-set-163309"}
                                 {:fhir/type :fhir.Parameters/parameter
                                  :name #fhir/string"coding"
                                  :value #fhir/Coding{:system #fhir/uri"system-115910"
                                                      :code #fhir/code"code-115927"}}]}})]

          (is (= 200 status))

          (given body
            :fhir/type := :fhir/Parameters
            [:parameter count] := 3
            [:parameter 0 :name] := #fhir/string"result"
            [:parameter 0 :value] := #fhir/boolean true
            [:parameter 1 :name] := #fhir/string"code"
            [:parameter 1 :value] := #fhir/code"code-115927"
            [:parameter 2 :name] := #fhir/string"system"
            [:parameter 2 :value] := #fhir/uri"system-115910"))

        (testing "with displayLanguage"
          (doseq [lang ["en" "de"]]
            (let [{:keys [status body]}
                  @(handler {:request-method :post
                             :body {:fhir/type :fhir/Parameters
                                    :parameter
                                    [{:fhir/type :fhir.Parameters/parameter
                                      :name #fhir/string"url"
                                      :value #fhir/uri"value-set-163309"}
                                     {:fhir/type :fhir.Parameters/parameter
                                      :name #fhir/string"coding"
                                      :value #fhir/Coding{:system #fhir/uri"system-115910"
                                                          :code #fhir/code"code-115927"}}
                                     {:fhir/type :fhir.Parameters/parameter
                                      :name #fhir/string"displayLanguage"
                                      :value (type/code lang)}]}})]

              (is (= 200 status))

              (given body
                :fhir/type := :fhir/Parameters
                [:parameter count] := 3
                [:parameter 0 :name] := #fhir/string"result"
                [:parameter 0 :value] := #fhir/boolean true
                [:parameter 1 :name] := #fhir/string"code"
                [:parameter 1 :value] := #fhir/code"code-115927"
                [:parameter 2 :name] := #fhir/string"system"
                [:parameter 2 :value] := #fhir/uri"system-115910")))))))

  (testing "successful validation by valueSet"
    (testing "code only"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri"system-115910"
                 :content #fhir/code"complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code"code-115927"}]}]]]

        (doseq [[code result] [["code-115927" true] ["code-210428" false]]]
          (let [{:keys [status body]}
                @(handler {:request-method :post
                           :body {:fhir/type :fhir/Parameters
                                  :parameter
                                  [{:fhir/type :fhir.Parameters/parameter
                                    :name #fhir/string"valueSet"
                                    :resource
                                    {:fhir/type :fhir/ValueSet
                                     :compose
                                     {:fhir/type :fhir.ValueSet/compose
                                      :include
                                      [{:fhir/type :fhir.ValueSet.compose/include
                                        :system #fhir/uri"system-115910"}]}}}
                                   {:fhir/type :fhir.Parameters/parameter
                                    :name #fhir/string"code"
                                    :value (type/code code)}
                                   {:fhir/type :fhir.Parameters/parameter
                                    :name #fhir/string"inferSystem"
                                    :value #fhir/boolean true}]}})]

            (is (= 200 status))

            (given body
              :fhir/type := :fhir/Parameters
              [:parameter 0 :name] := #fhir/string"result"
              [:parameter 0 :value type/value] := result)))))

    (testing "code and system"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri"system-115910"
                 :content #fhir/code"complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code"code-115927"}]}]]]

        (doseq [[code result] [["code-115927" true] ["code-210428" false]]]
          (let [{:keys [status body]}
                @(handler {:request-method :post
                           :body {:fhir/type :fhir/Parameters
                                  :parameter
                                  [{:fhir/type :fhir.Parameters/parameter
                                    :name #fhir/string"valueSet"
                                    :resource
                                    {:fhir/type :fhir/ValueSet
                                     :compose
                                     {:fhir/type :fhir.ValueSet/compose
                                      :include
                                      [{:fhir/type :fhir.ValueSet.compose/include
                                        :system #fhir/uri"system-115910"}]}}}
                                   {:fhir/type :fhir.Parameters/parameter
                                    :name #fhir/string"system"
                                    :value #fhir/uri"system-115910"}
                                   {:fhir/type :fhir.Parameters/parameter
                                    :name #fhir/string"code"
                                    :value (type/code code)}]}})]

            (is (= 200 status))

            (given body
              :fhir/type := :fhir/Parameters
              [:parameter 0 :name] := #fhir/string"result"
              [:parameter 0 :value type/value] := result)))))))
