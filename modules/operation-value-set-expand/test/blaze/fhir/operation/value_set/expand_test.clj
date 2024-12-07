(ns blaze.fhir.operation.value-set.expand-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.fhir.operation.value-set.expand]
   [blaze.handler.util :as handler-util]
   [blaze.terminology-service :as ts]
   [blaze.terminology-service.local]
   [blaze.terminology-service.spec :refer [terminology-service?]]
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
    (given-thrown (ig/init {:blaze.fhir.operation.value-set/expand nil})
      :key := :blaze.fhir.operation.value-set/expand
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze.fhir.operation.value-set/expand {}})
      :key := :blaze.fhir.operation.value-set/expand
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :terminology-service))))

  (testing "invalid structure definition repo"
    (given-thrown (ig/init {:blaze.fhir.operation.value-set/expand {:terminology-service ::invalid}})
      :key := :blaze.fhir.operation.value-set/expand
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `terminology-service?
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(def config
  (assoc
   api-stub/mem-node-config
   :blaze.fhir.operation.value-set/expand
   {:terminology-service (ig/ref ::ts/local)}
   ::ts/local
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)}))

(defn wrap-error [handler]
  (fn [request]
    (-> (handler request)
        (ac/exceptionally handler-util/error-response))))

(defmacro with-handler [[handler-binding] & more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{handler# :blaze.fhir.operation.value-set/expand} config]
       ~txs
       (let [~handler-binding (-> handler# wrap-error)]
         ~@body))))

(deftest handler-test
  (testing "ValueSet not found"
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
            [:issue 0 :diagnostics] := "The value set with URL `153404` was not found."))))

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

    (testing "unsupported parameters"
      (with-handler [handler]
        (doseq [param ["context" "contextDirection" "filter" "date" "offset"
                       "includeDesignations" "designation" "activeOnly"
                       "useSupplement" "excludeNested" "excludeNotForUI"
                       "excludePostCoordinated" "displayLanguage" "property"
                       "exclude-system" "system-version" "check-system-version"
                       "force-system-version"]]
          (let [{:keys [status body]}
                @(handler {:query-params {param "foo"}})]

            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"not-supported"
              [:issue 0 :diagnostics] := (format "Unsupported parameter `%s`." param))))))

    (testing "invalid non-integer parameter count"
      (with-handler [handler]
        (doseq [value ["", "a" "-1"]]
          (let [{:keys [status body]}
                @(handler {:query-params {"count" value}})]

            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"invalid"
              [:issue 0 :diagnostics] := "Invalid value for parameter `count`. Has to be a non-negative integer.")))))

    (testing "invalid boolean parameter includeDefinition"
      (with-handler [handler]
        (doseq [value ["", "a" "0" "1"]]
          (let [{:keys [status body]}
                @(handler {:query-params {"includeDefinition" value}})]

            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"invalid"
              [:issue 0 :diagnostics] := "Invalid value for parameter `includeDefinition`. Has to be a boolean."))))))

  (testing "successful expansion by id"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-115910"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-115927"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "152952"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-115910"}]}}]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "152952"}})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri"system-115910"
          [:expansion :contains 0 :code] := #fhir/code"code-115927"))))

  (testing "successful expansion by url"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-115910"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-115927"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-154043"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-115910"}]}}]]]

      (let [{:keys [status body]}
            @(handler {:query-params {"url" "value-set-154043"}})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/ValueSet
          :compose := nil
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri"system-115910"
          [:expansion :contains 0 :code] := #fhir/code"code-115927")))

    (testing "including the definition"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri"system-115910"
                 :content #fhir/code"complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code"code-115927"}]}]
          [:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri"value-set-154043"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"system-115910"}]}}]]]

        (let [{:keys [status body]}
              @(handler {:query-params {"url" "value-set-154043"
                                        "includeDefinition" "true"}})]

          (is (= 200 status))

          (given body
            :fhir/type := :fhir/ValueSet
            [:compose :include 0 :system] := #fhir/uri"system-115910"
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri"system-115910"
            [:expansion :contains 0 :code] := #fhir/code"code-115927"))))

    (testing "and count = 0"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri"system-115910"
                 :content #fhir/code"complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code"code-115927"}]}]
          [:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri"value-set-154043"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"system-115910"}]}}]]]

        (let [{:keys [status body]}
              @(handler {:query-params {"url" "value-set-154043" "count" "0"}})]

          (is (= 200 status))

          (given body
            :fhir/type := :fhir/ValueSet
            [:expansion :parameter 0 :name] := #fhir/string "count"
            [:expansion :parameter 0 :value] := #fhir/integer 0
            [:expansion :total] := #fhir/integer 1
            [:expansion :contains count] := 0))))

    (testing "and count = 1"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri"system-115910"
                 :content #fhir/code"complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code"code-115927"}
                  {:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code"code-155741"}]}]
          [:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri"value-set-154043"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"system-115910"}]}}]]]

        (let [{:keys [status body]}
              @(handler {:query-params {"url" "value-set-154043" "count" "1"}})]

          (is (= 200 status))

          (given body
            :fhir/type := :fhir/ValueSet
            [:expansion :parameter 0 :name] := #fhir/string "count"
            [:expansion :parameter 0 :value] := #fhir/integer 1
            [:expansion :total] := #fhir/integer 2
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri"system-115910"
            [:expansion :contains 0 :code] := #fhir/code"code-115927")))))

  (testing "successful expansion by url and valueSetVersion"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-115910"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-115927"}]}]
        [:put {:fhir/type :fhir/CodeSystem :id "1"
               :url #fhir/uri"system-135810"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-135827"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-154043"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-115910"}]}}]
        [:put {:fhir/type :fhir/ValueSet :id "1"
               :url #fhir/uri"value-set-154043"
               :version #fhir/string"version-135747"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-135810"}]}}]]]

      (let [{:keys [status body]}
            @(handler {:query-params {"url" "value-set-154043"
                                      "valueSetVersion" "version-135747"}})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri"system-135810"
          [:expansion :contains 0 :code] := #fhir/code"code-135827")))))
