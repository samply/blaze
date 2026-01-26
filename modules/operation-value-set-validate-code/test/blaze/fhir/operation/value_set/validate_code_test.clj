(ns blaze.fhir.operation.value-set.validate-code-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.fhir.operation.value-set.validate-code]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util :refer [parameter]]
   [blaze.fhir.util :as fu]
   [blaze.handler.util :as handler-util]
   [blaze.middleware.fhir.db :refer [wrap-db]]
   [blaze.middleware.fhir.db-spec]
   [blaze.module.test-util :refer [given-failed-system]]
   [blaze.terminology-service :as ts]
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
    (given-failed-system {:blaze.fhir.operation.value-set/validate-code nil}
      :key := :blaze.fhir.operation.value-set/validate-code
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.fhir.operation.value-set/validate-code {}}
      :key := :blaze.fhir.operation.value-set/validate-code
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :terminology-service))))

  (testing "invalid structure definition repo"
    (given-failed-system {:blaze.fhir.operation.value-set/validate-code {:terminology-service ::invalid}}
      :key := :blaze.fhir.operation.value-set/validate-code
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/terminology-service]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(def ^:private config
  (assoc
   api-stub/mem-node-config
   :blaze.fhir.operation.value-set/validate-code
   {:terminology-service (ig/ref ::ts/local)}))

(defn- wrap-error [handler]
  (fn [request]
    (-> (handler request)
        (ac/exceptionally handler-util/error-response))))

(defmacro with-handler [[handler-binding] & more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{node# :blaze.db/node
                         handler# :blaze.fhir.operation.value-set/validate-code} config]
       ~txs
       (let [~handler-binding (-> handler# (wrap-db node# 100) wrap-error)]
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
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "not-found"
            [:issue 0 :diagnostics] := #fhir/string "Resource `ValueSet/170852` was not found."))))

    (testing "by url"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler {:query-params {"url" "value-set-153404"
                                        "code" "foo"
                                        "system" "foo"}})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "not-found"
            [:issue 0 :diagnostics] := #fhir/string "The value set `value-set-153404` was not found.")))))

  (testing "unsupported parameters"
    (with-handler [handler]
      (doseq [param ["context" "date" "abstract" "useSupplement"]]
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
      (doseq [param ["valueSet" "coding" "codeableConcept" "tx-resource"]]
        (let [{:keys [status body]}
              @(handler {:query-params {param "foo"}})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "not-supported"
            [:issue 0 :diagnostics] := (type/string (format "Unsupported parameter `%s` in GET request. Please use POST." param)))))))

  (testing "invalid boolean parameter inferSystem"
    (with-handler [handler]
      (doseq [value ["" "a" "0" "1"]]
        (let [{:keys [status body]}
              @(handler {:query-params {"inferSystem" value}})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "invalid"
            [:issue 0 :diagnostics] := #fhir/string "Invalid value for parameter `inferSystem`. Has to be a boolean.")))))

  (testing "successful validation by id"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-115910"
               :version #fhir/string "version-181230"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-115927"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "id-152952"
               :url #fhir/uri "value-set-171446"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-115910"}]}}]]]

      (testing "and system and code"
        (doseq [[code result] [["code-115927" true] ["code-210428" false]]]
          (let [{:keys [status body]}
                @(handler {:path-params {:id "id-152952"}
                           :query-params {"system" "system-115910"
                                          "code" code}})]

            (is (= 200 status))

            (given body
              :fhir/type := :fhir/Parameters
              [:parameter 0 :name] := #fhir/string "result"
              [:parameter 0 :value :value] := result)))

        (testing "ignores unknown parameter"
          (let [{:keys [status body]}
                @(handler {:path-params {:id "id-152952"}
                           :query-params {"system" "system-115910"
                                          "code" "code-115927"
                                          "foo" "bar"}})]

            (is (= 200 status))

            (given body
              :fhir/type := :fhir/Parameters
              [:parameter 0 :name] := #fhir/string "result"
              [:parameter 0 :value] := #fhir/boolean true))))

      (testing "and system, systemVersion and code"
        (doseq [[code result] [["code-115927" true] ["code-210428" false]]]
          (let [{:keys [status body]}
                @(handler {:path-params {:id "id-152952"}
                           :query-params {"system" "system-115910"
                                          "systemVersion" "version-181230"
                                          "code" code}})]

            (is (= 200 status))

            (given body
              :fhir/type := :fhir/Parameters
              [:parameter 0 :name] := #fhir/string "result"
              [:parameter 0 :value :value] := result))))

      (testing "and coding"
        (let [{:keys [status body]}
              @(handler
                {:request-method :post
                 :path-params {:id "id-152952"}
                 :body (fu/parameters
                        "coding" #fhir/Coding{:system #fhir/uri "system-115910"
                                              :code #fhir/code "code-115927"})})]

          (is (= 200 status))

          (given body
            :fhir/type := :fhir/Parameters
            [:parameter count] := 4
            [:parameter 0 :name] := #fhir/string "result"
            [:parameter 0 :value] := #fhir/boolean true
            [:parameter 1 :name] := #fhir/string "code"
            [:parameter 1 :value] := #fhir/code "code-115927"
            [:parameter 2 :name] := #fhir/string "system"
            [:parameter 2 :value] := #fhir/uri "system-115910"
            [:parameter 3 :name] := #fhir/string "version"
            [:parameter 3 :value] := #fhir/string "version-181230"))

        (testing "ignores unknown parameter"
          (let [{:keys [status body]}
                @(handler
                  {:request-method :post
                   :path-params {:id "id-152952"}
                   :body (fu/parameters
                          "coding" #fhir/Coding{:system #fhir/uri "system-115910"
                                                :code #fhir/code "code-115927"}
                          "foo" #fhir/string "bar")})]

            (is (= 200 status))

            (given body
              :fhir/type := :fhir/Parameters
              [:parameter 0 :name] := #fhir/string "result"
              [:parameter 0 :value] := #fhir/boolean true))))))

  (testing "successful validation by url"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-115910"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-115927"
                 :designation
                 [{:fhir/type :fhir.CodeSystem.concept/designation
                   :language #fhir/code "de"
                   :value #fhir/string "display-125412"}]}]}]
        [:put {:fhir/type :fhir/ValueSet :id "152952"
               :url #fhir/uri "value-set-163309"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-115910"}]}}]]]

      (testing "and system and code"
        (doseq [[code result] [["code-115927" true] ["code-210428" false]]]
          (let [{:keys [status body]}
                @(handler {:query-params {"url" "value-set-163309"
                                          "system" "system-115910"
                                          "code" code}})]

            (is (= 200 status))

            (given body
              :fhir/type := :fhir/Parameters
              [:parameter 0 :name] := #fhir/string "result"
              [:parameter 0 :value :value] := result)))

        (testing "with display"
          (doseq [lang [nil "de" "en,de"]]
            (let [{:keys [status body]}
                  @(handler {:query-params (cond->
                                            {"url" "value-set-163309"
                                             "system" "system-115910"
                                             "code" "code-115927"
                                             "display" "display-125412"}
                                             lang (assoc "displayLanguage" lang))})]

              (is (= 200 status))

              (given body
                :fhir/type := :fhir/Parameters
                [:parameter 0 :name] := #fhir/string "result"
                [:parameter 0 :value] := #fhir/boolean true
                [(parameter "display") 0 :value] := #fhir/string "display-125412")))

          (testing "an en display isn't available"
            (testing "displayLanguage from query params"
              (let [{:keys [status body]}
                    @(handler {:query-params {"url" "value-set-163309"
                                              "system" "system-115910"
                                              "code" "code-115927"
                                              "display" "display-125412"
                                              "displayLanguage" "en"}})]

                (is (= 200 status))

                (given body
                  :fhir/type := :fhir/Parameters
                  [:parameter 0 :name] := #fhir/string "result"
                  [:parameter 0 :value] := #fhir/boolean false)))

            (testing "displayLanguage from Parameters resource"
              (let [{:keys [status body]}
                    @(handler {:request-method :post
                               :body (fu/parameters
                                      "url" #fhir/uri "value-set-163309"
                                      "system" #fhir/uri "system-115910"
                                      "code" #fhir/code "code-115927"
                                      "display" #fhir/string "display-125412"
                                      "displayLanguage" #fhir/code "en")})]

                (is (= 200 status))

                (given body
                  :fhir/type := :fhir/Parameters
                  [:parameter 0 :name] := #fhir/string "result"
                  [:parameter 0 :value] := #fhir/boolean false)))

            (testing "displayLanguage from Accept-Language header"
              (testing "GET"
                (let [{:keys [status body]}
                      @(handler {:headers {"accept-language" "en"}
                                 :query-params {"url" "value-set-163309"
                                                "system" "system-115910"
                                                "code" "code-115927"
                                                "display" "display-125412"}})]

                  (is (= 200 status))

                  (given body
                    :fhir/type := :fhir/Parameters
                    [:parameter 0 :name] := #fhir/string "result"
                    [:parameter 0 :value] := #fhir/boolean false)))

              (testing "POST"
                (let [{:keys [status body]}
                      @(handler {:request-method :post
                                 :headers {"accept-language" "en"}
                                 :body (fu/parameters
                                        "url" #fhir/uri "value-set-163309"
                                        "system" #fhir/uri "system-115910"
                                        "code" #fhir/code "code-115927"
                                        "display" #fhir/string "display-125412")})]

                  (is (= 200 status))

                  (given body
                    :fhir/type := :fhir/Parameters
                    [:parameter 0 :name] := #fhir/string "result"
                    [:parameter 0 :value] := #fhir/boolean false)))))))

      (testing "inferSystem"
        (let [{:keys [status body]}
              @(handler {:query-params {"url" "value-set-163309"
                                        "code" "code-115927"
                                        "inferSystem" "true"}})]

          (is (= 200 status))

          (given body
            :fhir/type := :fhir/Parameters
            [:parameter 0 :name] := #fhir/string "result"
            [:parameter 0 :value] := #fhir/boolean true)))

      (testing "and coding"
        (let [{:keys [status body]}
              @(handler {:request-method :post
                         :body (fu/parameters
                                "url" #fhir/uri "value-set-163309"
                                "coding" #fhir/Coding{:system #fhir/uri "system-115910"
                                                      :code #fhir/code "code-115927"})})]

          (is (= 200 status))

          (given body
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean true
            [:parameter 1 :name] := #fhir/string "code"
            [:parameter 1 :value] := #fhir/code "code-115927"
            [:parameter 2 :name] := #fhir/string "system"
            [:parameter 2 :value] := #fhir/uri "system-115910"
            [(parameter "display") 0 :value] := #fhir/string "display-125412"))

        (testing "with displayLanguage"
          (doseq [lang ["en" "de"]]
            (let [{:keys [status body]}
                  @(handler
                    {:request-method :post
                     :body (fu/parameters
                            "url" #fhir/uri "value-set-163309"
                            "coding" #fhir/Coding{:system #fhir/uri "system-115910"
                                                  :code #fhir/code "code-115927"}
                            "displayLanguage" (type/code lang))})]

              (is (= 200 status))

              (given body
                :fhir/type := :fhir/Parameters
                [(parameter "result") 0 :value] := #fhir/boolean true
                [(parameter "display") 0 :value] := #fhir/string "display-125412"))))))

    (with-handler [handler]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-115910"
               :version #fhir/string "1.0"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-115927"}]}]
        [:put {:fhir/type :fhir/CodeSystem :id "1"
               :url #fhir/uri "system-115910"
               :version #fhir/string "1.2"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-155335"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "152952"
               :url #fhir/uri "value-set-163309"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-115910"}]}}]]]

      (let [{:keys [status body]}
            @(handler {:query-params {"url" "value-set-163309"
                                      "system" "system-115910"
                                      "code" "code-155335"}})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/Parameters
          [:parameter 0 :name] := #fhir/string "result"
          [:parameter 0 :value :value] := true))

      (testing "with system-version"
        (let [{:keys [status body]}
              @(handler {:query-params {"url" "value-set-163309"
                                        "system" "system-115910"
                                        "code" "code-115927"
                                        "system-version" "system-115910|1.0"}})]

          (is (= 200 status))

          (given body
            :fhir/type := :fhir/Parameters
            [:parameter 0 :name] := #fhir/string "result"
            [:parameter 0 :value :value] := true)))))

  (testing "successful validation by valueSet"
    (testing "code only"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri "system-115910"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-115927"}]}]]]

        (doseq [[code result] [["code-115927" true] ["code-210428" false]]]
          (let [{:keys [status body]}
                @(handler {:request-method :post
                           :body (fu/parameters
                                  "valueSet"
                                  {:fhir/type :fhir/ValueSet
                                   :compose
                                   {:fhir/type :fhir.ValueSet/compose
                                    :include
                                    [{:fhir/type :fhir.ValueSet.compose/include
                                      :system #fhir/uri "system-115910"}]}}
                                  "code" (type/code code)
                                  "inferSystem" #fhir/boolean true)})]

            (is (= 200 status))

            (given body
              :fhir/type := :fhir/Parameters
              [:parameter 0 :name] := #fhir/string "result"
              [:parameter 0 :value :value] := result)))))

    (testing "code and system"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri "system-115910"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-115927"}]}]]]

        (doseq [[code result] [["code-115927" true] ["code-210428" false]]]
          (let [{:keys [status body]}
                @(handler {:request-method :post
                           :body (fu/parameters
                                  "valueSet"
                                  {:fhir/type :fhir/ValueSet
                                   :compose
                                   {:fhir/type :fhir.ValueSet/compose
                                    :include
                                    [{:fhir/type :fhir.ValueSet.compose/include
                                      :system #fhir/uri "system-115910"}]}}
                                  "system" #fhir/uri "system-115910"
                                  "code" (type/code code))})]

            (is (= 200 status))

            (given body
              :fhir/type := :fhir/Parameters
              [:parameter 0 :name] := #fhir/string "result"
              [:parameter 0 :value :value] := result))))))

  (testing "successful validation with externally supplied value set and code system"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler {:request-method :post
                       :body (fu/parameters
                              "url" #fhir/uri "value-set-110445"
                              "system" #fhir/uri "system-115910"
                              "code" #fhir/uri "code-115927"
                              "tx-resource"
                              {:fhir/type :fhir/CodeSystem
                               :url #fhir/uri "system-115910"
                               :content #fhir/code "complete"
                               :concept
                               [{:fhir/type :fhir.CodeSystem/concept
                                 :code #fhir/code "code-115927"}]}
                              "tx-resource"
                              {:fhir/type :fhir/ValueSet
                               :url #fhir/uri "value-set-110445"
                               :compose
                               {:fhir/type :fhir.ValueSet/compose
                                :include
                                [{:fhir/type :fhir.ValueSet.compose/include
                                  :system #fhir/uri "system-115910"}]}})})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/Parameters
          [:parameter 0 :name] := #fhir/string "result"
          [:parameter 0 :value] := #fhir/boolean true)))))
