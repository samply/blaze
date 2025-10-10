(ns blaze.fhir.operation.value-set.expand-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.fhir.operation.value-set.expand]
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
    (given-failed-system {:blaze.fhir.operation.value-set/expand nil}
      :key := :blaze.fhir.operation.value-set/expand
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.fhir.operation.value-set/expand {}}
      :key := :blaze.fhir.operation.value-set/expand
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :terminology-service))))

  (testing "invalid structure definition repo"
    (given-failed-system {:blaze.fhir.operation.value-set/expand {:terminology-service ::invalid}}
      :key := :blaze.fhir.operation.value-set/expand
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/terminology-service]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(def config
  (assoc
   api-stub/mem-node-config
   :blaze.fhir.operation.value-set/expand
   {:terminology-service (ig/ref ::ts/local)}
   ::ts/local
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :graph-cache (ig/ref ::ts-local/graph-cache)}
   :blaze.test/fixed-rng-fn {}
   ::ts-local/graph-cache {}))

(defn- wrap-error [handler]
  (fn [request]
    (-> (handler request)
        (ac/exceptionally handler-util/error-response))))

(defmacro with-handler [[handler-binding] & more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{node# :blaze.db/node
                         handler# :blaze.fhir.operation.value-set/expand} config]
       ~txs
       (let [~handler-binding (-> handler# (wrap-db node# 100) wrap-error)]
         ~@body))))

(defn- parameter [name]
  (fn [{:keys [parameter]}]
    (some #(when (= name (type/value (:name %))) %) parameter)))

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
              @(handler {:query-params {"url" "153404"}})]

          (is (= 404 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "not-found"
            [:issue 0 :diagnostics] := #fhir/string "The value set `153404` was not found.")))))

  (testing "unsupported parameters"
    (with-handler [handler]
      (doseq [param ["context" "contextDirection" "filter" "date"
                     "designation" "useSupplement" "excludeNotForUI"
                     "excludePostCoordinated"
                     "exclude-system" "check-system-version"
                     "force-system-version"]]
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
                           :body (fu/parameters
                                  (type/string param) (type/string "foo"))})]

            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "error"
              [:issue 0 :code] := #fhir/code "not-supported"
              [:issue 0 :diagnostics] := (type/string (format "Unsupported parameter `%s`." param))))))))

  (testing "unsupported GET parameters"
    (with-handler [handler]
      (doseq [param ["valueSet" "tx-resource"]]
        (let [{:keys [status body]}
              @(handler {:query-params {param "foo"}})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "not-supported"
            [:issue 0 :diagnostics] := (type/string (format "Unsupported parameter `%s` in GET request. Please use POST." param)))))))

  (testing "invalid non-integer parameter count"
    (testing "GET"
      (with-handler [handler]
        (doseq [value ["" "a"]]
          (let [{:keys [status body]}
                @(handler {:query-params {"count" value}})]

            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "error"
              [:issue 0 :code] := #fhir/code "invalid"
              [:issue 0 :diagnostics] := #fhir/string "Invalid value for parameter `count`. Has to be an integer.")))))

    (testing "POST"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler {:request-method :post
                         :body (fu/parameters
                                #fhir/string "count" #fhir/integer -1)})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "invalid"
            [:issue 0 :diagnostics] := #fhir/string "Invalid value for parameter `count`. Has to be a non-negative integer.")))))

  (testing "invalid non-zero parameter offset"
    (testing "GET"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler {:query-params {"url" "153404" "offset" "1"}})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "invalid"
            [:issue 0 :diagnostics] := #fhir/string "Invalid non-zero value for parameter `offset`."))))

    (testing "POST"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler {:request-method :post
                         :body (fu/parameters
                                #fhir/string "offset" #fhir/integer 1)})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "invalid"
            [:issue 0 :diagnostics] := #fhir/string "Invalid non-zero value for parameter `offset`.")))))

  (testing "invalid boolean parameter includeDefinition"
    (with-handler [handler]
      (doseq [value ["" "a" "0" "1"]]
        (let [{:keys [status body]}
              @(handler {:query-params {"includeDefinition" value}})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "invalid"
            [:issue 0 :diagnostics] := #fhir/string "Invalid value for parameter `includeDefinition`. Has to be a boolean.")))))

  (testing "successful expansion by id"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-115910"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-115927"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "id-152952"
               :url #fhir/uri "value-set-164038"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-115910"}]}}]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "id-152952"}})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri "system-115910"
          [:expansion :contains 0 :code] := #fhir/code "code-115927"))

      (testing "ignores unknown parameter"
        (let [{:keys [status]}
              @(handler {:path-params {:id "id-152952"}
                         :query-params {"foo" "bar"}})]

          (is (= 200 status)))

        (let [{:keys [status]}
              @(handler {:request-method :post
                         :path-params {:id "id-152952"}
                         :body (fu/parameters
                                #fhir/string "foo" #fhir/string "bar")})]

          (is (= 200 status))))))

  (testing "successful expansion by url"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-115910"
               :version #fhir/string "version-170327"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-115927"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-154043"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-115910"}]}}]]]

      (doseq [include-designations [nil "true" "false"]
              lang [nil "en" "de"]
              property [nil "definition"]]
        (let [{:keys [status body]}
              @(handler {:query-params (cond-> {"url" "value-set-154043"}
                                         include-designations (assoc "includeDesignations" include-designations)
                                         lang (assoc "displayLanguage" lang)
                                         property (assoc "property" property))})]

          (is (= 200 status))

          (given body
            :fhir/type := :fhir/ValueSet
            :compose := nil
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri "system-115910"
            [:expansion :contains 0 :code] := #fhir/code "code-115927")))

      (testing "POST"
        (doseq [include-designations [nil true false]
                lang [nil "en" "de"]
                property [nil ["definition"] ["parent" "child"]]]
          (let [{:keys [status body]}
                @(handler
                  {:request-method :post
                   :body {:fhir/type :fhir/Parameters
                          :parameter
                          (cond->
                           [{:fhir/type :fhir.Parameters/parameter
                             :name #fhir/string "url"
                             :value #fhir/uri "value-set-154043"}]
                            include-designations
                            (conj
                             {:fhir/type :fhir.Parameters/parameter
                              :name #fhir/string "includeDesignations"
                              :value (type/boolean include-designations)})
                            lang
                            (conj
                             {:fhir/type :fhir.Parameters/parameter
                              :name #fhir/string "displayLanguage"
                              :value (type/code lang)})
                            property
                            (into
                             (map
                              (fn [property]
                                {:fhir/type :fhir.Parameters/parameter
                                 :name #fhir/string "property"
                                 :value (type/string property)}))
                             property))}})]

            (is (= 200 status))

            (given body
              :fhir/type := :fhir/ValueSet
              :compose := nil
              [:expansion :contains count] := 1
              [:expansion :contains 0 :system] := #fhir/uri "system-115910"
              [:expansion :contains 0 :code] := #fhir/code "code-115927"))))

      (testing "and system-version"
        (let [{:keys [status body]}
              @(handler {:query-params {"url" "value-set-154043"
                                        "system-version" "system-115910|version-170327"}})]

          (is (= 200 status))

          (given body
            :fhir/type := :fhir/ValueSet
            :compose := nil
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri "system-115910"
            [:expansion :contains 0 :code] := #fhir/code "code-115927"))))

    (testing "successful expansion by valueSet"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri "system-115910"
                 :version #fhir/string "version-170327"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-115927"}]}]]]

        (let [{:keys [status body]}
              @(handler {:request-method :post
                         :body (fu/parameters
                                "valueSet"
                                {:fhir/type :fhir/ValueSet
                                 :compose
                                 {:fhir/type :fhir.ValueSet/compose
                                  :include
                                  [{:fhir/type :fhir.ValueSet.compose/include
                                    :system #fhir/uri "system-115910"}]}})})]

          (is (= 200 status))

          (given body
            :fhir/type := :fhir/ValueSet
            :compose := nil
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri "system-115910"
            [:expansion :contains 0 :code] := #fhir/code "code-115927"))

        (testing "and count = 0"
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
                                  "count" #fhir/integer 0)})]

            (is (= 200 status))

            (given body
              :fhir/type := :fhir/ValueSet
              [:expansion (parameter "count") :value] := #fhir/integer 0
              [:expansion :total] := #fhir/integer 1
              [:expansion :contains count] := 0)))

        (testing "and offset = 0"
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
                                  "offset" #fhir/integer 0)})]

            (is (= 200 status))

            (given body
              :fhir/type := :fhir/ValueSet
              [:expansion :contains count] := 1
              [:expansion :contains 0 :system] := #fhir/uri "system-115910"
              [:expansion :contains 0 :code] := #fhir/code "code-115927")))

        (testing "including the definition"
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
                                  "includeDefinition" #fhir/boolean true)})]

            (is (= 200 status))

            (given body
              :fhir/type := :fhir/ValueSet
              [:compose :include count] := 1
              [:compose :include 0 :system] := #fhir/uri "system-115910"
              [:expansion :contains count] := 1
              [:expansion :contains 0 :system] := #fhir/uri "system-115910"
              [:expansion :contains 0 :code] := #fhir/code "code-115927")))

        (testing "and system-version"
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
                                  "system-version"
                                  #fhir/canonical "system-115910|version-170327")})]

            (is (= 200 status))

            (given body
              :fhir/type := :fhir/ValueSet
              :compose := nil
              [:expansion :contains count] := 1
              [:expansion :contains 0 :system] := #fhir/uri "system-115910"
              [:expansion :contains 0 :code] := #fhir/code "code-115927")))))

    (testing "including the definition"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri "system-115910"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-115927"}]}]
          [:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "value-set-154043"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "system-115910"}]}}]]]

        (let [{:keys [status body]}
              @(handler {:query-params {"url" "value-set-154043"
                                        "includeDefinition" "true"}})]

          (is (= 200 status))

          (given body
            :fhir/type := :fhir/ValueSet
            [:compose :include count] := 1
            [:compose :include 0 :system] := #fhir/uri "system-115910"
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri "system-115910"
            [:expansion :contains 0 :code] := #fhir/code "code-115927"))))

    (testing "and count = 0"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri "system-115910"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-115927"}]}]
          [:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "value-set-154043"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "system-115910"}]}}]]]

        (let [{:keys [status body]}
              @(handler {:query-params {"url" "value-set-154043" "count" "0"}})]

          (is (= 200 status))

          (given body
            :fhir/type := :fhir/ValueSet
            [:expansion (parameter "count") :value] := #fhir/integer 0
            [:expansion :total] := #fhir/integer 1
            [:expansion :contains count] := 0))))

    (testing "and count = 1"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri "system-115910"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-115927"}
                  {:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-155741"}]}]
          [:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "value-set-154043"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "system-115910"}]}}]]]

        (let [{:keys [status body]}
              @(handler {:query-params {"url" "value-set-154043" "count" "1"}})]

          (is (= 200 status))

          (given body
            :fhir/type := :fhir/ValueSet
            [:expansion (parameter "count") :value] := #fhir/integer 1
            [:expansion :total] := #fhir/integer 2
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri "system-115910"
            [:expansion :contains 0 :code] := #fhir/code "code-115927")))))

  (testing "successful expansion by url and valueSetVersion"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-115910"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-115927"}]}]
        [:put {:fhir/type :fhir/CodeSystem :id "1"
               :url #fhir/uri "system-135810"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-135827"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-154043"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-115910"}]}}]
        [:put {:fhir/type :fhir/ValueSet :id "1"
               :url #fhir/uri "value-set-154043"
               :version #fhir/string "version-135747"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-135810"}]}}]]]

      (let [{:keys [status body]}
            @(handler {:query-params {"url" "value-set-154043"
                                      "valueSetVersion" "version-135747"}})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri "system-135810"
          [:expansion :contains 0 :code] := #fhir/code "code-135827"))))

  (testing "successful expansion with inactive concept"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-170110"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-170118"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "inactive"
                   :value #fhir/boolean true}]}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-170056"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-170110"}]}}]]]

      (let [{:keys [status body]}
            @(handler {:query-params {"url" "value-set-170056"}})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri "system-170110"
          [:expansion :contains 0 :inactive] := #fhir/boolean true
          [:expansion :contains 0 :code] := #fhir/code "code-170118"))

      (testing "active only"
        (let [{:keys [status body]}
              @(handler {:query-params {"url" "value-set-170056"
                                        "activeOnly" "true"}})]

          (is (= 200 status))

          (given body
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 0)))))

  (testing "successful expansion with externally supplied value set and code system"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler {:request-method :post
                       :body (fu/parameters
                              "url" #fhir/uri "value-set-110445"
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
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri "system-115910"
          [:expansion :contains 0 :code] := #fhir/code "code-115927")))))
