(ns blaze.operation.graph.compiler-test
  (:require
   [blaze.fhir.search-clauses-spec]
   [blaze.fhir.test-util.graph-definition :as g-tu]
   [blaze.fhir.test-util.graph-definition-spec]
   [blaze.fhir.util-spec]
   [blaze.graph-definition-spec]
   [blaze.handler.fhir.util-spec]
   [blaze.interaction.search.util-spec]
   [blaze.middleware.fhir.page-id-spec]
   [blaze.operation.graph.compiler :as c]
   [blaze.operation.graph.compiler-spec]
   [blaze.page-id-cipher.spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest compile-test
  (testing "fails on invalid graph definition"
    (given (c/compile
            (g-tu/graph-def
             :link
             [(g-tu/link "patient" "observation")]))
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid start node id `patient` not referring to a node."))

  (testing "patient-only"
    (is (= (c/compile
            (g-tu/graph-def
             :nodes
             [(g-tu/node "patient" "Patient")]))
           {:start-node-id "patient"
            :nodes {"patient" {:id "patient" :type "Patient"}}
            :links {}})))

  (testing "patient -> observation -> encounter"
    (given (c/compile
            (g-tu/graph-def
             :nodes
             [(g-tu/node "patient" "Patient")
              (g-tu/node "observation" "Observation")
              (g-tu/node "encounter" "Encounter")]
             :link
             [(g-tu/link "patient" "observation" :params "patient={ref}")
              (g-tu/link "observation" "encounter" :path "encounter")]))
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

(deftest node-type-matches-test
  (testing "a node of a concrete type matches only that type"
    (is (true? (c/node-type-matches? "Patient" "Patient")))
    (is (false? (c/node-type-matches? "Patient" "Observation"))))

  (testing "a node of type Resource matches all types"
    (doseq [type ["Patient" "Observation" "Binary" "Bundle" "Parameters"]]
      (is (true? (c/node-type-matches? "Resource" type)) type)))

  (testing "a node of type DomainResource matches all types except Binary, Bundle and Parameters"
    (doseq [type ["Patient" "Observation"]]
      (is (true? (c/node-type-matches? "DomainResource" type)) type))
    (doseq [type ["Binary" "Bundle" "Parameters"]]
      (is (false? (c/node-type-matches? "DomainResource" type)) type))))
