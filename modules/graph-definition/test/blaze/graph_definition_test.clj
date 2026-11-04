(ns blaze.graph-definition-test
  (:require
   [blaze.fhir-path :as fhir-path]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util]
   [blaze.fhir.test-util.graph-definition :as g-tu]
   [blaze.graph-definition :as gd]
   [blaze.graph-definition-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def ^:private noop-resolver
  (reify fhir-path/Resolver (-resolve [_ _])))

(defn- graph-def [& {:keys [extension link]}]
  (cond->
   {:fhir/type :fhir/GraphDefinition :id "0"
    :extension
    (into [(g-tu/extension-start :value #fhir/id "patient")] extension)
    :url #fhir/uri "144200"
    :name #fhir/string "test"
    :status #fhir/code "active"
    :start (type/code {:extension [g-tu/data-absent-reason-unsupported]})}
    link (assoc :link link)))

(defn- node [id type]
  (g-tu/extension-node
   :extension
   [(type/extension {:url "nodeId" :value (type/id id)})
    (type/extension {:url "type" :value (type/code type)})]))

(defn- link [source-id target-id & {:keys [params path]}]
  (cond->
   {:fhir/type :fhir.GraphDefinition/link
    :extension
    (cond->
     [(g-tu/extension-link-source-id :value (type/id source-id))
      (g-tu/extension-link-target-id :value (type/id target-id))]
      params (conj (g-tu/extension-link-params :value (type/string params))))}
    path (assoc :path (type/string path))))

(deftest compile-test
  (testing "fails on"
    (testing "link with both params and path"
      (given (gd/compile
              (graph-def
               :link [(link "patient" "observation" :params "patient={ref}"
                            :path "encounter")]))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid link with path and params."))

    (testing "link with neither params or path"
      (given (gd/compile (graph-def :link [(link "patient" "observation")]))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid link without path and params."))

    (testing "link with invalid path"
      (given (gd/compile (graph-def :link [(link "observation" "encounter"
                                                 :path "value.ofType(Foo)")]))
        ::anom/category := ::anom/incorrect
        ::anom/message := "unknown FHIR type `Foo` in expression `value.ofType(Foo)`")))

  (testing "patient-only"
    (given (gd/compile (graph-def :extension [(node "patient" "Patient")]))
      :start-node-id := "patient"
      :nodes := {"patient" {:id "patient" :type "Patient"}}
      :links := {}))

  (testing "patient -> observation -> encounter"
    (let [graph (gd/compile
                 (graph-def
                  :extension
                  [(node "patient" "Patient")
                   (node "observation" "Observation")
                   (node "encounter" "Encounter")]
                  :link
                  [(link "patient" "observation" :params "patient={ref}")
                   (link "observation" "encounter" :path "encounter")]))]
      (given graph
        :start-node-id := "patient"
        [:nodes "patient"] := {:id "patient" :type "Patient"}
        [:nodes "observation"] := {:id "observation" :type "Observation"}
        [:nodes "encounter"] := {:id "encounter" :type "Encounter"}
        [:links "patient"] := [{:source-id "patient"
                                :target-id "observation"
                                :params "patient={ref}"}]
        [:links "observation" count] := 1
        [:links "observation" 0 :source-id] := "observation"
        [:links "observation" 0 :target-id] := "encounter")

      (testing "the path is compiled"
        (is (= [#fhir/Reference{:reference #fhir/string "Encounter/0"}]
               (fhir-path/eval
                noop-resolver
                (get-in graph [:links "observation" 0 :path])
                {:fhir/type :fhir/Observation :id "0"
                 :encounter #fhir/Reference{:reference #fhir/string "Encounter/0"}})))))))
