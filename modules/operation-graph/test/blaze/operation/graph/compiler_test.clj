(ns blaze.operation.graph.compiler-test
  (:require
   [blaze.fhir.spec.type]
   [blaze.fhir.test-util]
   [blaze.fhir.util-spec]
   [blaze.handler.fhir.util-spec]
   [blaze.interaction.search.util-spec]
   [blaze.middleware.fhir.decrypt-page-id-spec]
   [blaze.operation.graph.compiler :as c]
   [blaze.operation.graph.compiler-spec]
   [blaze.page-id-cipher.spec]
   [blaze.test-util :as tu]
   [blaze.util.clauses-spec]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest compile-test
  (testing "fails on"
    (testing "link with both params and path"
      (given (c/compile
              {:fhir/type :fhir/GraphDefinition :id "0"
               :url #fhir/uri"144200"
               :name #fhir/string"fails"
               :status #fhir/code"active"
               :start #fhir/id"patient"
               :link
               [{:fhir/type :fhir.GraphDefinition/link
                 :sourceId #fhir/id"patient"
                 :path #fhir/string"encounter"
                 :targetId #fhir/id"observation"
                 :params #fhir/string"patient={ref}"}]})
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid link with path and params."))

    (testing "link with neither params or path"
      (given (c/compile
              {:fhir/type :fhir/GraphDefinition :id "0"
               :url #fhir/uri"144200"
               :name #fhir/string"fails"
               :status #fhir/code"active"
               :start #fhir/id"patient"
               :link
               [{:fhir/type :fhir.GraphDefinition/link
                 :sourceId #fhir/id"patient"
                 :targetId #fhir/id"observation"}]})
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid link without path and params.")))

  (testing "patient-only"
    (is (= (c/compile
            {:fhir/type :fhir/GraphDefinition :id "0"
             :url #fhir/uri"151647"
             :name #fhir/string"patient-only"
             :status #fhir/code"active"
             :start #fhir/id"patient"
             :node
             [{:fhir/type :fhir.GraphDefinition/node
               :nodeId #fhir/id"patient"
               :type #fhir/code"Patient"}]})
           {:start-node-id "patient"
            :nodes {"patient" {:id "patient" :type "Patient"}}
            :links {}})))

  (testing "patient -> observation -> encounter"
    (given (c/compile
            {:fhir/type :fhir/GraphDefinition :id "0"
             :url #fhir/uri"144200"
             :name #fhir/string"patient-observation-encounter"
             :status #fhir/code"active"
             :start #fhir/id"patient"
             :node
             [{:fhir/type :fhir.GraphDefinition/node
               :nodeId #fhir/id"patient"
               :type #fhir/code"Patient"}
              {:fhir/type :fhir.GraphDefinition/node
               :nodeId #fhir/id"observation"
               :type #fhir/code"Observation"}
              {:fhir/type :fhir.GraphDefinition/node
               :nodeId #fhir/id"encounter"
               :type #fhir/code"Encounter"}]
             :link
             [{:fhir/type :fhir.GraphDefinition/link
               :sourceId #fhir/id"patient"
               :targetId #fhir/id"observation"
               :params #fhir/string"patient={ref}"}
              {:fhir/type :fhir.GraphDefinition/link
               :sourceId #fhir/id"observation"
               :path "encounter"
               :targetId #fhir/id"encounter"}]})
      :start-node-id := "patient"
      [:nodes "patient"] := {:id "patient", :type "Patient"}
      [:nodes "observation"] := {:id "observation", :type "Observation"}
      [:nodes "encounter"] := {:id "encounter", :type "Encounter"}
      [:links "patient" count] := 1
      [:links "patient" 0 :source-id] := "patient"
      [:links "patient" 0 :target-id] := "observation"
      [:links "patient" 0 :resource-handles] :? fn?
      [:links "observation" count] := 1
      [:links "observation" 0 :source-id] := "observation"
      [:links "observation" 0 :target-id] := "encounter"
      [:links "observation" 0 :resource-handles] :? fn?)))
