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
   [clojure.test :as test :refer [are deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def ^:private noop-resolver
  (reify fhir-path/Resolver (-resolve [_ _])))

(defn- graph-def
  "Creates a GraphDefinition with the start node `patient` unless `:start` is
  given. A `:start` of nil omits the start node."
  [& {:keys [extension link] :as opts}]
  (let [start (get opts :start "patient")]
    (cond->
     {:fhir/type :fhir/GraphDefinition :id "0"
      :extension
      (cond-> []
        start (conj (g-tu/extension-start :value (type/id start)))
        true (into extension))
      :url #fhir/uri "144200"
      :name #fhir/string "test"
      :status #fhir/code "active"
      :start (type/code {:extension [g-tu/data-absent-reason-unsupported]})}
      link (assoc :link link))))

(defn- node [id type & {:keys [profile]}]
  (g-tu/extension-node
   :extension
   (cond-> []
     id (conj (type/extension {:url "nodeId" :value (type/id id)}))
     type (conj (type/extension {:url "type" :value (type/code type)}))
     profile (conj (type/extension {:url "profile" :value (type/canonical profile)})))))

(defn- link [source-id target-id & {:keys [params path compartment]}]
  (cond->
   {:fhir/type :fhir.GraphDefinition/link
    :extension
    (cond-> []
      source-id (conj (g-tu/extension-link-source-id :value (type/id source-id)))
      target-id (conj (g-tu/extension-link-target-id :value (type/id target-id)))
      params (conj (g-tu/extension-link-params :value (type/string params)))
      compartment (conj (g-tu/extension-link-compartment
                         :extension
                         [(type/extension {:url "use" :value #fhir/code "where"})
                          (type/extension {:url "rule" :value #fhir/code "identical"})
                          (type/extension {:url "code" :value #fhir/code "Patient"})])))}
    path (assoc :path (type/string path))))

(def ^:private patient-observation-encounter-nodes
  [(node "patient" "Patient")
   (node "observation" "Observation")
   (node "encounter" "Encounter")])

(defn- graph-def-with-links [& links]
  (graph-def :extension patient-observation-encounter-nodes :link (vec links)))

(deftest ref-clause-test
  (testing "true"
    (are [clause] (true? (gd/ref-clause? clause))
      ["patient" "{ref}"]
      ["subject:Patient" "{ref}"]))

  (testing "false"
    (are [clause] (false? (gd/ref-clause? clause))
      ["status" "final"]
      ["patient" "{ref}" "foo"]
      ["patient" "foo" "{ref}"])))

(deftest compile-test
  (testing "fails on"
    (testing "missing start node"
      (given (gd/compile (graph-def :start nil :extension [(node "patient" "Patient")]))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid GraphDefinition without start node."))

    (testing "start node id not referring to a node"
      (given (gd/compile (graph-def :start "foo" :extension [(node "patient" "Patient")]))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid start node id `foo` not referring to a node."))

    (testing "node without nodeId"
      (given (gd/compile (graph-def :extension [(node nil "Patient")]))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid node without nodeId."))

    (testing "node without type"
      (given (gd/compile (graph-def :extension [(node "patient" nil)]))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid node `patient` without type."))

    (testing "node with unknown resource type"
      (given (gd/compile (graph-def :extension [(node "patient" "Foo")]))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid node `patient` with unknown resource type `Foo`."))

    (testing "node with data type instead of resource type"
      (given (gd/compile (graph-def :extension [(node "patient" "Coding")]))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid node `patient` with unknown resource type `Coding`."))

    (testing "node with XML spec name instead of resource type"
      (given (gd/compile (graph-def :extension [(node "patient" "xml.Observation")]))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid node `patient` with unknown resource type `xml.Observation`."))

    (testing "node with profile"
      (given (gd/compile
              (graph-def
               :extension
               [(node "patient" "Patient"
                      :profile "http://hl7.org/fhir/StructureDefinition/Patient")]))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid node `patient` with unsupported profile."))

    (testing "duplicate nodeId"
      (given (gd/compile
              (graph-def
               :extension
               [(node "patient" "Patient")
                (node "patient" "Observation")]))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid node `patient` with duplicate nodeId."))

    (testing "link without sourceId"
      (given (gd/compile (graph-def-with-links (link nil "observation" :params "patient={ref}")))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid link without sourceId."))

    (testing "link with sourceId not referring to a node"
      (given (gd/compile (graph-def-with-links (link "foo" "observation" :params "patient={ref}")))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid link with sourceId `foo` not referring to a node."))

    (testing "link without targetId"
      (given (gd/compile (graph-def-with-links (link "patient" nil :params "patient={ref}")))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid link without targetId."))

    (testing "link with targetId not referring to a node"
      (given (gd/compile (graph-def-with-links (link "patient" "foo" :params "patient={ref}")))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid link with targetId `foo` not referring to a node."))

    (testing "link with compartment"
      (given (gd/compile
              (graph-def-with-links
               (link "patient" "observation" :params "patient={ref}" :compartment true)))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid link with unsupported compartment."))

    (testing "link with both params and path"
      (given (gd/compile
              (graph-def-with-links
               (link "patient" "observation" :params "patient={ref}"
                     :path "encounter")))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid link with path and params."))

    (testing "link with neither params or path"
      (given (gd/compile (graph-def-with-links (link "patient" "observation")))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid link without path and params."))

    (testing "link with params without {ref}"
      (doseq [params ["status=final" "patient" "" "patient=" "_foo={ref}"
                      "%5Ffoo={ref}" "=%7Bref%7D"]]
        (given (gd/compile (graph-def-with-links (link "patient" "observation" :params params)))
          ::anom/category := ::anom/incorrect
          ::anom/message := (format "Invalid link with params `%s` without a parameter with the value `{ref}`." params))))

    (testing "link with params with {ref} combined with other values"
      (doseq [params ["patient={ref},foo" "patient=foo,{ref}"
                      "status=final&patient={ref},Patient/0"]]
        (given (gd/compile (graph-def-with-links (link "patient" "observation" :params params)))
          ::anom/category := ::anom/incorrect
          ::anom/message := (format "Invalid link with params `%s` with the value `{ref}` combined with other values." params))))

    (testing "link with params with more than one parameter with {ref}"
      (doseq [params ["patient={ref}&subject={ref}"
                      "patient={ref}&subject={ref},Group/1"]]
        (given (gd/compile (graph-def-with-links (link "patient" "observation" :params params)))
          ::anom/category := ::anom/incorrect
          ::anom/message := (format "Invalid link with params `%s` with more than one parameter with the value `{ref}`." params))))

    (testing "link with params to node of abstract type"
      (doseq [type ["Resource" "DomainResource"]]
        (given (gd/compile
                (graph-def
                 :extension [(node "patient" "Patient") (node "resource" type)]
                 :link [(link "patient" "resource" :params "patient={ref}")]))
          ::anom/category := ::anom/incorrect
          ::anom/message := (format "Invalid link with params to node `resource` of abstract type `%s`." type))))

    (testing "link with invalid path"
      (given (gd/compile (graph-def-with-links (link "observation" "encounter"
                                                     :path "value.ofType(Foo)")))
        ::anom/category := ::anom/incorrect
        ::anom/message := "unknown FHIR type `Foo` in expression `value.ofType(Foo)`")))

  (testing "patient-only"
    (given (gd/compile (graph-def :extension [(node "patient" "Patient")]))
      :start-node-id := "patient"
      :nodes := {"patient" {:id "patient" :type "Patient"}}
      :links := {}))

  (testing "node with abstract type"
    (doseq [type ["Resource" "DomainResource"]]
      (given (gd/compile
              (graph-def
               :extension [(node "patient" "Patient") (node "practitioner" type)]
               :link [(link "patient" "practitioner" :path "generalPractitioner")]))
        [:nodes "practitioner"] := {:id "practitioner" :type type}
        [:links "patient" 0 :target-id] := "practitioner")))

  (testing "link with params"
    (doseq [[params clauses]
            [["patient={ref}" [["patient" "{ref}"]]]
             ["status=final&patient={ref}" [["status" "final"] ["patient" "{ref}"]]]
             ["patient=%7Bref%7D" [["patient" "{ref}"]]]
             ["patient=+{ref}+" [["patient" "{ref}"]]]
             ["patient={ref},{ref}" [["patient" "{ref}"]]]
             ["subject:Patient={ref}" [["subject:Patient" "{ref}"]]]
             ["patient={ref}&patient=foo" [["patient" "{ref}"] ["patient" "foo"]]]
             ["patient={ref}&_count=10" [["patient" "{ref}"]]]]]
      (given (gd/compile (graph-def-with-links (link "patient" "observation" :params params)))
        [:links "patient"] := [{:source-id "patient"
                                :target-id "observation"
                                :clauses clauses}])))

  (testing "link min and max are ignored"
    (given (gd/compile
            (graph-def-with-links
             (assoc (link "patient" "observation" :params "patient={ref}")
                    :min #fhir/integer 1 :max #fhir/string "1")))
      [:links "patient"] := [{:source-id "patient"
                              :target-id "observation"
                              :clauses [["patient" "{ref}"]]}]))

  (testing "patient -> observation -> encounter"
    (let [graph (gd/compile
                 (graph-def-with-links
                  (link "patient" "observation" :params "patient={ref}")
                  (link "observation" "encounter" :path "encounter")))]
      (given graph
        :start-node-id := "patient"
        [:nodes "patient"] := {:id "patient" :type "Patient"}
        [:nodes "observation"] := {:id "observation" :type "Observation"}
        [:nodes "encounter"] := {:id "encounter" :type "Encounter"}
        [:links "patient"] := [{:source-id "patient"
                                :target-id "observation"
                                :clauses [["patient" "{ref}"]]}]
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
