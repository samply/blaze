(ns blaze.fhir.operation.code-system.lookup-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.fhir.operation.code-system.lookup]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util :refer [parameter]]
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

(defn- body-parameter [name value]
  {:fhir/type :fhir.Parameters/parameter
   :name (type/string name)
   :value value})

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.fhir.operation.code-system/lookup nil}
      :key := :blaze.fhir.operation.code-system/lookup
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.fhir.operation.code-system/lookup {}}
      :key := :blaze.fhir.operation.code-system/lookup
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :terminology-service))))

  (testing "invalid structure definition repo"
    (given-failed-system {:blaze.fhir.operation.code-system/lookup {:terminology-service ::invalid}}
      :key := :blaze.fhir.operation.code-system/lookup
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/terminology-service]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(def config
  (assoc
   api-stub/mem-node-config
   :blaze.fhir.operation.code-system/lookup
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
                         handler# :blaze.fhir.operation.code-system/lookup} config]
       ~txs
       (let [~handler-binding (-> handler# (wrap-db node# 100) wrap-error)]
         ~@body))))

(deftest handler-test
  (log/set-min-level! :warn)

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
              @(handler {:query-params {"system" "153404"
                                        "code" "123033"}})]

          (is (= 400 status))
          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "not-found"
            [:issue 0 :diagnostics] := #fhir/string "The code system `153404` was not found.")))))

  (testing "unsupported parameters"
    (with-handler [handler]
      (doseq [param ["date" "displayLanguage" "property" "useSupplement"]]
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
                           :body {:parameter [(body-parameter param "foo")]}})]

            (is (= 400 status))
            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "error"
              [:issue 0 :code] := #fhir/code "not-supported"
              [:issue 0 :diagnostics] := (type/string (format "Unsupported parameter `%s`." param))))))))

  (testing "successful lookup by system and code"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/CodeSystem :id "id-162245"
               :url #fhir/uri "system-115910"
               :version #fhir/string "version-152300"
               :name #fhir/string "name-152300"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-115927"
                 :display #fhir/string "display-152300"
                 :definition #fhir/string "definition-152300"
                 :designation
                 [{:fhir/type :fhir.CodeSystem.concept/designation
                   :value #fhir/string "designation-en-value-152300"
                   :language #fhir/code "en"}
                  {:fhir/type :fhir.CodeSystem.concept/designation
                   :value #fhir/string "designation-de-value-152300"
                   :language #fhir/code "de"}]
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "prop-code-114800"
                   :subproperty
                   [{:fhir/type :fhir.CodeSystem.concept.property/subproperty
                     :code #fhir/code "subprop-code-114800"
                     :value #fhir/string "subprop-value-114800"}]}]}]}]]]

      (testing "is found"
        (doseq [[code name] [["code-115927" "name-152300"]]]
          (let [{:keys [status body]}
                @(handler {:query-params {"code" code
                                          "system" "system-115910"
                                          "version" "version-152300"}})]

            (is (= 200 status))
            (given body
              :fhir/type := :fhir/Parameters
              [(parameter "name") 0 :value :value] := name))))

      (testing "is not found"
        (doseq [[code] [["non-existent-code"]]]
          (let [{:keys [status body]}
                @(handler {:query-params {"code" code "system" "non-existent-system"}})]

            (is (= 400 status))
            (given body
              :fhir/type := :fhir/OperationOutcome))))

      (testing "ignores unknown parameter"
        (let [{:keys [status body]}
              @(handler {:query-params {"code" "code-115927"
                                        "system" "system-115910"
                                        "foo" "bar"}})]

          (is (= 200 status))
          (given body
            :fhir/type := :fhir/Parameters
            [(parameter "name") 0 :value :value] := "name-152300")))

      (testing "and coding"
        (let [{:keys [status body]}
              @(handler {:request-method :post
                         :body (fu/parameters
                                "coding" #fhir/Coding {:system #fhir/uri "system-115910"
                                                       :version #fhir/string "version-152300"
                                                       :code #fhir/code "code-115927"})})]

          (is (= 200 status))
          (given body
            :fhir/type := :fhir/Parameters
            [(parameter "name") 0 :value :value] := "name-152300"))

        (testing "and coding and ignores unknown parameter"
          (let [{:keys [status body]}
                @(handler {:request-method :post
                           :path-params {:id "id-162245"}
                           :body (fu/parameters
                                  "coding" #fhir/Coding{:system #fhir/uri "system-115910"
                                                        :version #fhir/string "version-152300"
                                                        :code #fhir/code "code-115927"}
                                  "foo" "bar")})]

            (is (= 200 status))
            (given body
              :fhir/type := :fhir/Parameters
              [:parameter count] := 7
              [(parameter "name") 0 :value :value] := "name-152300"))))))

  (testing "successful lookup by id"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/CodeSystem :id "id-162245"
               :url #fhir/uri "system-115910"
               :version #fhir/string "version-152300"
               :name #fhir/string "name-152300"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-115927"}]}]]]

      (testing "and code"
        (let [{:keys [status body]}
              @(handler {:path-params {:id "id-162245"}
                         :query-params {"code" "code-115927"}})]

          (is (= 200 status))
          (given body
            :fhir/type := :fhir/Parameters
            [(parameter "name") 0 :value :value] := "name-152300")))

      (testing "ignores unknown parameter"
        (let [{:keys [status body]}
              @(handler {:path-params {:id "id-162245"}
                         :query-params {"code" "code-115927"
                                        "foo" "bar"}})]

          (is (= 200 status))
          (given body
            :fhir/type := :fhir/Parameters
            [(parameter "name") 0 :value :value] := "name-152300")))

      (testing "and coding"
        (let [{:keys [status body]}
              @(handler {:request-method :post
                         :path-params {:id "id-162245"}
                         :body (fu/parameters
                                "coding" #fhir/Coding {:system #fhir/uri "system-115910"
                                                       :code #fhir/code "code-115927"})})]

          (is (= 200 status))
          (given body
            :fhir/type := :fhir/Parameters
            [:parameter count] := 2
            [(parameter "name") 0 :value :value] := "name-152300")))

      (testing "ignores unknown parameter"
        (let [{:keys [status body]}
              @(handler {:request-method :post
                         :path-params {:id "id-162245"}
                         :body (fu/parameters
                                "coding" #fhir/Coding {:system #fhir/uri "system-115910"
                                                       :code #fhir/code "code-115927"}
                                "foo" "bar")})]

          (is (= 200 status))
          (given body
            :fhir/type := :fhir/Parameters
            [:parameter count] := 2
            [(parameter "name") 0 :value :value] := "name-152300")))))

  (testing "lookup by id on CodeSystem without version"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/CodeSystem :id "id-162245"
               :url #fhir/uri "system-115910"
               :name #fhir/string "name-152300"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-115927"}]}]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "id-162245"}
                       :query-params {"code" "code-115927"}})]

        (is (= 200 status))
        (given body
          :fhir/type := :fhir/Parameters
          [(parameter "name") 0 :value :value] := "name-152300")))))
