(ns blaze.operation.graph.compiler-test
  (:require
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.util-spec]
   [blaze.handler.fhir.util-spec]
   [blaze.interaction.search.util-spec]
   [blaze.middleware.fhir.decrypt-page-id-spec]
   [blaze.operation.graph.compiler :as c]
   [blaze.operation.graph.compiler-spec]
   [blaze.operation.graph.test-util :as g-tu]
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
               :extension
               [(g-tu/extension-start :value #fhir/id "patient")]
               :url #fhir/uri "144200"
               :name #fhir/string "fails"
               :status #fhir/code "active"
               :start (type/code {:extension [g-tu/data-absent-reason-unsupported]})
               :link
               [{:fhir/type :fhir.GraphDefinition/link
                 :extension
                 [(g-tu/extension-link-source-id :value #fhir/id "patient")
                  (g-tu/extension-link-target-id :value #fhir/id "observation")
                  (g-tu/extension-link-params :value #fhir/string "patient={ref}")]
                 :path #fhir/string "encounter"}]})
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid link with path and params."))

    (testing "link with neither params or path"
      (given (c/compile
              {:fhir/type :fhir/GraphDefinition :id "0"
               :extension
               [(g-tu/extension-start :value #fhir/id "patient")]
               :url #fhir/uri "144200"
               :name #fhir/string "fails"
               :status #fhir/code "active"
               :start (type/code {:extension [g-tu/data-absent-reason-unsupported]})
               :link
               [{:fhir/type :fhir.GraphDefinition/link
                 :extension
                 [(g-tu/extension-link-source-id :value #fhir/id "patient")
                  (g-tu/extension-link-target-id :value #fhir/id "observation")]}]})
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid link without path and params.")))

  (testing "patient-only"
    (is (= (c/compile
            {:fhir/type :fhir/GraphDefinition :id "0"
             :extension
             [(g-tu/extension-start :value #fhir/id "patient")
              (g-tu/extension-node
               :extension
               [#fhir/Extension{:url "nodeId" :value #fhir/id "patient"}
                #fhir/Extension{:url "type" :value #fhir/code "Patient"}])]
             :url #fhir/uri "151647"
             :name #fhir/string "patient-only"
             :status #fhir/code "active"
             :start (type/code {:extension [g-tu/data-absent-reason-unsupported]})})
           {:start-node-id "patient"
            :nodes {"patient" {:id "patient" :type "Patient"}}
            :links {}})))

  (testing "patient -> observation -> encounter"
    (given (c/compile
            {:fhir/type :fhir/GraphDefinition :id "0"
             :extension
             [(g-tu/extension-start :value #fhir/id "patient")
              (g-tu/extension-node
               :extension
               [#fhir/Extension{:url "nodeId" :value #fhir/id "patient"}
                #fhir/Extension{:url "type" :value #fhir/code "Patient"}])
              (g-tu/extension-node
               :extension
               [#fhir/Extension{:url "nodeId" :value #fhir/id "observation"}
                #fhir/Extension{:url "type" :value #fhir/code "Observation"}])
              (g-tu/extension-node
               :extension
               [#fhir/Extension{:url "nodeId" :value #fhir/id "encounter"}
                #fhir/Extension{:url "type" :value #fhir/code "Encounter"}])]
             :url #fhir/uri "144200"
             :name #fhir/string "patient-observation-encounter"
             :status #fhir/code "active"
             :start (type/code {:extension [g-tu/data-absent-reason-unsupported]})
             :link
             [{:fhir/type :fhir.GraphDefinition/link
               :extension
               [(g-tu/extension-link-source-id :value #fhir/id "patient")
                (g-tu/extension-link-target-id :value #fhir/id "observation")
                (g-tu/extension-link-params :value #fhir/string "patient={ref}")]}
              {:fhir/type :fhir.GraphDefinition/link
               :extension
               [(g-tu/extension-link-source-id :value #fhir/id "observation")
                (g-tu/extension-link-target-id :value #fhir/id "encounter")]
               :path #fhir/string "encounter"}]})
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
