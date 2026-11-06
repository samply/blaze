(ns blaze.graph-definition-test
  (:require
   [blaze.fhir-path :as fhir-path]
   [blaze.fhir.test-util]
   [blaze.fhir.test-util.graph-definition :as g-tu]
   [blaze.fhir.test-util.graph-definition-spec]
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

(def ^:private patient-observation-encounter-nodes
  [(g-tu/node "patient" "Patient")
   (g-tu/node "observation" "Observation")
   (g-tu/node "encounter" "Encounter")])

(defn- graph-def-with-links [& links]
  (g-tu/graph-def :nodes patient-observation-encounter-nodes :link (vec links)))

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
      (given (gd/compile (g-tu/graph-def :start nil :nodes [(g-tu/node "patient" "Patient")]))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid GraphDefinition without start node."))

    (testing "start node id not referring to a node"
      (given (gd/compile (g-tu/graph-def :start "foo" :nodes [(g-tu/node "patient" "Patient")]))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid start node id `foo` not referring to a node."))

    (testing "node without nodeId"
      (given (gd/compile (g-tu/graph-def :nodes [(g-tu/node nil "Patient")]))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid node without nodeId."))

    (testing "node without type"
      (given (gd/compile (g-tu/graph-def :nodes [(g-tu/node "patient" nil)]))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid node `patient` without type."))

    (testing "node with unknown resource type"
      (given (gd/compile (g-tu/graph-def :nodes [(g-tu/node "patient" "Foo")]))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid node `patient` with unknown resource type `Foo`."))

    (testing "node with data type instead of resource type"
      (given (gd/compile (g-tu/graph-def :nodes [(g-tu/node "patient" "Coding")]))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid node `patient` with unknown resource type `Coding`."))

    (testing "node with XML spec name instead of resource type"
      (given (gd/compile (g-tu/graph-def :nodes [(g-tu/node "patient" "xml.Observation")]))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid node `patient` with unknown resource type `xml.Observation`."))

    (testing "node with profile"
      (given (gd/compile
              (g-tu/graph-def
               :nodes
               [(g-tu/node "patient" "Patient"
                           :profile "http://hl7.org/fhir/StructureDefinition/Patient")]))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid node `patient` with unsupported profile."))

    (testing "duplicate nodeId"
      (given (gd/compile
              (g-tu/graph-def
               :nodes
               [(g-tu/node "patient" "Patient")
                (g-tu/node "patient" "Observation")]))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid node `patient` with duplicate nodeId."))

    (testing "link without sourceId"
      (given (gd/compile (graph-def-with-links (g-tu/link nil "observation" :params "patient={ref}")))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid link without sourceId."))

    (testing "link with sourceId not referring to a node"
      (given (gd/compile (graph-def-with-links (g-tu/link "foo" "observation" :params "patient={ref}")))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid link with sourceId `foo` not referring to a node."))

    (testing "link without targetId"
      (given (gd/compile (graph-def-with-links (g-tu/link "patient" nil :params "patient={ref}")))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid link without targetId."))

    (testing "link with targetId not referring to a node"
      (given (gd/compile (graph-def-with-links (g-tu/link "patient" "foo" :params "patient={ref}")))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid link with targetId `foo` not referring to a node."))

    (testing "link with compartment"
      (given (gd/compile
              (graph-def-with-links
               (g-tu/link "patient" "observation" :params "patient={ref}" :compartment true)))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid link with unsupported compartment."))

    (testing "link with both params and path"
      (given (gd/compile
              (graph-def-with-links
               (g-tu/link "patient" "observation" :params "patient={ref}"
                          :path "encounter")))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid link with path and params."))

    (testing "link with neither params or path"
      (given (gd/compile (graph-def-with-links (g-tu/link "patient" "observation")))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid link without path and params."))

    (testing "link with params without {ref}"
      (doseq [params ["status=final" "patient" "" "patient=" "_foo={ref}"
                      "%5Ffoo={ref}" "=%7Bref%7D"]]
        (given (gd/compile (graph-def-with-links (g-tu/link "patient" "observation" :params params)))
          ::anom/category := ::anom/incorrect
          ::anom/message := (format "Invalid link with params `%s` without a parameter with the value `{ref}`." params))))

    (testing "link with params with {ref} combined with other values"
      (doseq [params ["patient={ref},foo" "patient=foo,{ref}"
                      "status=final&patient={ref},Patient/0"]]
        (given (gd/compile (graph-def-with-links (g-tu/link "patient" "observation" :params params)))
          ::anom/category := ::anom/incorrect
          ::anom/message := (format "Invalid link with params `%s` with the value `{ref}` combined with other values." params))))

    (testing "link with params with more than one parameter with {ref}"
      (doseq [params ["patient={ref}&subject={ref}"
                      "patient={ref}&subject={ref},Group/1"]]
        (given (gd/compile (graph-def-with-links (g-tu/link "patient" "observation" :params params)))
          ::anom/category := ::anom/incorrect
          ::anom/message := (format "Invalid link with params `%s` with more than one parameter with the value `{ref}`." params))))

    (testing "link with params to node of abstract type"
      (doseq [type ["Resource" "DomainResource"]]
        (given (gd/compile
                (g-tu/graph-def
                 :nodes [(g-tu/node "patient" "Patient") (g-tu/node "resource" type)]
                 :link [(g-tu/link "patient" "resource" :params "patient={ref}")]))
          ::anom/category := ::anom/incorrect
          ::anom/message := (format "Invalid link with params to node `resource` of abstract type `%s`." type))))

    (testing "link with invalid path"
      (given (gd/compile (graph-def-with-links (g-tu/link "observation" "encounter"
                                                          :path "value.ofType(Foo)")))
        ::anom/category := ::anom/incorrect
        ::anom/message := "unknown FHIR type `Foo` in expression `value.ofType(Foo)`")))

  (testing "patient-only"
    (given (gd/compile (g-tu/graph-def :nodes [(g-tu/node "patient" "Patient")]))
      :start-node-id := "patient"
      :nodes := {"patient" {:id "patient" :type "Patient"}}
      :links := {}))

  (testing "node with abstract type"
    (doseq [type ["Resource" "DomainResource"]]
      (given (gd/compile
              (g-tu/graph-def
               :nodes [(g-tu/node "patient" "Patient") (g-tu/node "practitioner" type)]
               :link [(g-tu/link "patient" "practitioner" :path "generalPractitioner")]))
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
      (given (gd/compile (graph-def-with-links (g-tu/link "patient" "observation" :params params)))
        [:links "patient"] := [{:source-id "patient"
                                :target-id "observation"
                                :clauses clauses}])))

  (testing "link min and max are ignored"
    (given (gd/compile
            (graph-def-with-links
             (assoc (g-tu/link "patient" "observation" :params "patient={ref}")
                    :min #fhir/integer 1 :max #fhir/string "1")))
      [:links "patient"] := [{:source-id "patient"
                              :target-id "observation"
                              :clauses [["patient" "{ref}"]]}]))

  (testing "patient -> observation -> encounter"
    (let [graph (gd/compile
                 (graph-def-with-links
                  (g-tu/link "patient" "observation" :params "patient={ref}")
                  (g-tu/link "observation" "encounter" :path "encounter")))]
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
