(ns blaze.elm.compiler.external-data-test
  "11. External Data

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.anomaly :as ba]
    [blaze.db.api :as d]
    [blaze.db.api-stub :refer [mem-node-system with-system-data]]
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.external-data]
    [blaze.elm.compiler.test-util :as tu]
    [blaze.elm.literal :as elm]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec.type]
    [blaze.test-util :refer [with-system]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]])
  (:import
    [blaze.elm.compiler.external_data
     WithRelatedContextQueryRetrieveExpression]))


(st/instrument)
(tu/instrument-compile)


(defn- fixture [f]
  (st/instrument)
  (tu/instrument-compile)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


;; 11.1. Retrieve
;;
;; All access to external data within ELM is represented by Retrieve expressions.
;;
;; The Retrieve class defines the data type of the request, which determines the
;; type of elements to be returned. The result will always be a list of values
;; of the type specified in the request.
;;
;; The type of the elements to be returned is specified with the dataType
;; attribute of the Retrieve, and must refer to the name of a type within a
;; known data model specified in the dataModels element of the library
;; definition.
;;
;; In addition, the Retrieve introduces the ability to specify optional criteria
;; for the request. The available criteria are intentionally restricted to the
;; set of codes involved, and the date range involved. If these criteria are
;; omitted, the request is interpreted to mean all data of that type.
;;
;; Note that because every expression is being evaluated within a context (such
;; as Patient, Practitioner, or Unfiltered) as defined by the containing
;; ExpressionDef, the data returned by a retrieve depends on the context. For
;; example, for the Patient context, the data is returned for a single patient
;; only, as defined by the evaluation environment. Whereas for the Unfiltered
;; context, the data is returned for the entire source.
(deftest compile-retrieve-test
  (testing "Patient context"
    (testing "Patient"
      (with-system-data [{:blaze.db/keys [node]} mem-node-system]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

        (let [context
              {:node node
               :eval-context "Patient"
               :library {}}
              expr (c/compile context tu/patient-retrieve-elm)
              db (d/db node)
              patient (d/resource-handle db "Patient" "0")]

          (given (core/-eval expr {:db db} patient nil)
            [0 fhir-spec/fhir-type] := :fhir/Patient
            [0 :id] := "0"))))

    (testing "Observation"
      (with-system-data [{:blaze.db/keys [node]} mem-node-system]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Observation :id "1"
                 :subject
                 #fhir/Reference{:reference "Patient/0"}}]]]

        (let [context
              {:node node
               :eval-context "Patient"
               :library {}}
              expr (c/compile context (elm/retrieve {:type "Observation"}))
              db (d/db node)
              patient (d/resource-handle db "Patient" "0")]

          (given (core/-eval expr {:db db} patient nil)
            [0 fhir-spec/fhir-type] := :fhir/Observation
            [0 :id] := "1")))

      (testing "with one code"
        (with-system-data [{:blaze.db/keys [node]} mem-node-system]
          [[[:put {:fhir/type :fhir/Patient :id "0"}]
            [:put {:fhir/type :fhir/Observation :id "0"
                   :subject
                   #fhir/Reference{:reference "Patient/0"}}]
            [:put {:fhir/type :fhir/Observation :id "1"
                   :code
                   #fhir/CodeableConcept
                       {:coding
                        [#fhir/Coding
                            {:system #fhir/uri"system-192253"
                             :code #fhir/code"code-192300"}]}
                   :subject
                   #fhir/Reference{:reference "Patient/0"}}]]]

          (let [context
                {:node node
                 :eval-context "Patient"
                 :library
                 {:codeSystems
                  {:def
                   [{:name "sys-def-131750"
                     :id "system-192253"}]}}}
                elm (elm/retrieve
                      {:type "Observation"
                       :codes #elm/list[#elm/code["sys-def-131750"
                                                  "code-192300"]]})
                expr (c/compile context elm)
                db (d/db node)
                patient (d/resource-handle db "Patient" "0")]

            (given (core/-eval expr {:db db} patient nil)
              count := 1
              [0 fhir-spec/fhir-type] := :fhir/Observation
              [0 :id] := "1"))))

      (testing "with two codes"
        (with-system-data [{:blaze.db/keys [node]} mem-node-system]
          [[[:put {:fhir/type :fhir/Patient :id "0"}]
            [:put {:fhir/type :fhir/Observation :id "0"
                   :subject
                   #fhir/Reference{:reference "Patient/0"}}]
            [:put {:fhir/type :fhir/Observation :id "1"
                   :code
                   #fhir/CodeableConcept
                       {:coding
                        [#fhir/Coding
                            {:system #fhir/uri"system-192253"
                             :code #fhir/code"code-192300"}]}
                   :subject
                   #fhir/Reference{:reference "Patient/0"}}]
            [:put {:fhir/type :fhir/Observation :id "2"
                   :code
                   #fhir/CodeableConcept
                       {:coding
                        [#fhir/Coding
                            {:system #fhir/uri"system-192253"
                             :code #fhir/code"code-140541"}]}
                   :subject
                   #fhir/Reference{:reference "Patient/0"}}]]]

          (let [context
                {:node node
                 :eval-context "Patient"
                 :library
                 {:codeSystems
                  {:def
                   [{:name "sys-def-131750"
                     :id "system-192253"}]}}}
                elm (elm/retrieve
                      {:type "Observation"
                       :codes
                       #elm/list[#elm/code["sys-def-131750" "code-192300"]
                                 #elm/code["sys-def-131750" "code-140541"]]})
                expr (c/compile context elm)
                db (d/db node)
                patient (d/resource-handle db "Patient" "0")]

            (given (core/-eval expr {:db db} patient nil)
              count := 2
              [0 fhir-spec/fhir-type] := :fhir/Observation
              [0 :id] := "1"
              [1 fhir-spec/fhir-type] := :fhir/Observation
              [1 :id] := "2"))))))

  (testing "Specimen context"
    (testing "Patient"
      (with-system-data [{:blaze.db/keys [node]} mem-node-system]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Specimen :id "0"
                 :subject
                 #fhir/Reference{:reference "Patient/0"}}]]]

        (let [context
              {:node node
               :eval-context "Specimen"
               :library {}}
              expr (c/compile context tu/patient-retrieve-elm)
              db (d/db node)
              specimen (d/resource-handle db "Specimen" "0")]

          (given (core/-eval expr {:db db} specimen nil)
            [0 fhir-spec/fhir-type] := :fhir/Patient
            [0 :id] := "0")))))

  (testing "Unfiltered context"
    (testing "Medication"
      (with-system-data [{:blaze.db/keys [node]} mem-node-system]
        [[[:put {:fhir/type :fhir/Medication :id "0"
                 :code
                 #fhir/CodeableConcept
                     {:coding
                      [#fhir/Coding
                          {:system #fhir/uri"system-225806"
                           :code #fhir/code"code-225809"}]}}]]]

        (let [context
              {:node node
               :eval-context "Unfiltered"
               :library
               {:codeSystems
                {:def
                 [{:name "sys-def-225944"
                   :id "system-225806"}]}}}
              elm #elm/retrieve
                      {:type "Medication"
                       :codes #elm/list[#elm/code["sys-def-225944"
                                                  "code-225809"]]}
              expr (c/compile context elm)
              db (d/db node)]

          (given (core/-eval expr {:db db} nil nil)
            count := 1
            [0 fhir-spec/fhir-type] := :fhir/Medication
            [0 :id] := "0"))))

    (testing "unknown code property"
      (with-system [{:blaze.db/keys [node]} mem-node-system]
        (let [context
              {:node node
               :eval-context "Unfiltered"
               :library
               {:codeSystems
                {:def
                 [{:name "sys-def-225944"
                   :id "system-225806"}]}}}
              elm #elm/retrieve
                      {:type "Medication"
                       :codes #elm/list[#elm/code["sys-def-225944"
                                                  "code-225809"]]
                       :code-property "foo"}]

          (given (ba/try-anomaly (c/compile context elm))
            ::anom/category := ::anom/not-found
            ::anom/message := "The search-param with code `foo` and type `Medication` was not found.")))))

  (testing "with related context"
    (testing "with pre-compiled database query"
      (with-system [{:blaze.db/keys [node]} mem-node-system]
        (let [library {:codeSystems
                       {:def [{:name "sys-def-174848" :id "system-174915"}]}
                       :statements
                       {:def
                        [{:name "name-174207"
                          :resultTypeName "{http://hl7.org/fhir}Patient"}]}}
              elm #elm/retrieve
                      {:type "Observation"
                       :context #elm/expression-ref "name-174207"
                       :codes #elm/list[#elm/code["sys-def-174848"
                                                  "code-174911"]]}
              expr (c/compile {:node node :library library} elm)]
          (given expr
            type := WithRelatedContextQueryRetrieveExpression))))

    (testing "unknown code property"
      (with-system [{:blaze.db/keys [node]} mem-node-system]
        (let [library {:codeSystems
                       {:def [{:name "sys-def-174848" :id "system-174915"}]}
                       :statements
                       {:def
                        [{:name "name-174207"
                          :resultTypeName "{http://hl7.org/fhir}Patient"}]}}
              elm #elm/retrieve
                      {:type "Observation"
                       :context #elm/expression-ref "name-174207"
                       :codes #elm/list[#elm/code["sys-def-174848"
                                                  "code-174911"]]
                       :code-property "foo"}]
          (given (ba/try-anomaly (c/compile {:node node :library library} elm))
            ::anom/category := ::anom/not-found
            ::anom/message := "The search-param with code `foo` and type `Observation` was not found.")))))

  (testing "with unsupported type namespace"
    (let [elm {:type "Retrieve" :dataType "{foo}Bar"}]
      (given (ba/try-anomaly (c/compile {} elm))
        ::anom/category := ::anom/unsupported))))
