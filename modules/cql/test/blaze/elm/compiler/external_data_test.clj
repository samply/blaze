(ns blaze.elm.compiler.external-data-test
  (:require
    [blaze.db.api :as d]
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.external-data]
    [blaze.elm.compiler.test-util :as tu]
    [blaze.elm.literal :as elm]
    [blaze.fhir.spec :as fhir-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]])
  (:import
    [blaze.elm.compiler.external_data
     WithRelatedContextQueryRetrieveExpression]))


(st/instrument)
(tu/instrument-compile)


(defn fixture [f]
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
  (testing "Patient content"
    (testing "Patient"
      (with-open [node (mem-node-with
                         [[[:put {:fhir/type :fhir/Patient :id "0"}]]])]
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
      (with-open [node (mem-node-with
                         [[[:put {:fhir/type :fhir/Patient :id "0"}]
                           [:put {:fhir/type :fhir/Observation :id "1"
                                  :subject
                                  {:fhir/type :fhir/Reference
                                   :reference "Patient/0"}}]]])]
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
        (with-open [node (mem-node-with
                           [[[:put {:fhir/type :fhir/Patient :id "0"}]
                             [:put {:fhir/type :fhir/Observation :id "0"
                                    :subject
                                    {:fhir/type :fhir/Reference
                                     :reference "Patient/0"}}]
                             [:put {:fhir/type :fhir/Observation :id "1"
                                    :code
                                    {:fhir/type :fhir/CodeableConcept
                                     :coding
                                     [{:fhir/type :fhir/Coding
                                       :system #fhir/uri"system-192253"
                                       :code #fhir/code"code-192300"}]}
                                    :subject
                                    {:fhir/type :fhir/Reference
                                     :reference "Patient/0"}}]]])]
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
        (with-open [node (mem-node-with
                           [[[:put {:fhir/type :fhir/Patient :id "0"}]
                             [:put {:fhir/type :fhir/Observation :id "0"
                                    :subject
                                    {:fhir/type :fhir/Reference
                                     :reference "Patient/0"}}]
                             [:put {:fhir/type :fhir/Observation :id "1"
                                    :code
                                    {:fhir/type :fhir/CodeableConcept
                                     :coding
                                     [{:fhir/type :fhir/Coding
                                       :system #fhir/uri"system-192253"
                                       :code #fhir/code"code-192300"}]}
                                    :subject
                                    {:fhir/type :fhir/Reference
                                     :reference "Patient/0"}}]
                             [:put {:fhir/type :fhir/Observation :id "2"
                                    :code
                                    {:fhir/type :fhir/CodeableConcept
                                     :coding
                                     [{:fhir/type :fhir/Coding
                                       :system #fhir/uri"system-192253"
                                       :code #fhir/code"code-140541"}]}
                                    :subject
                                    {:fhir/type :fhir/Reference
                                     :reference "Patient/0"}}]]])]
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
      (with-open [node (mem-node-with
                         [[[:put {:fhir/type :fhir/Patient :id "0"}]
                           [:put {:fhir/type :fhir/Specimen :id "0"
                                  :subject
                                  {:fhir/type :fhir/Reference
                                   :reference "Patient/0"}}]]])]
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

  (testing "Unspecified context")

  (testing "with related context"
    (testing "with pre-compiled database query"
      (with-open [node (mem-node-with [])]
        (let [library {:codeSystems
                       {:def [{:name "sys-def-174848" :id "system-174915"}]}
                       :statements
                       {:def
                        [{:name "name-174207"
                          :resultTypeName "{http://hl7.org/fhir}Patient"}]}}
              elm {:type "Retrieve"
                   :dataType "{http://hl7.org/fhir}Observation"
                   :context #elm/expression-ref "name-174207"
                   :codes #elm/list [#elm/code ["sys-def-174848" "code-174911"]]}
              expr (c/compile {:node node :library library} elm)]
          (given expr
            type := WithRelatedContextQueryRetrieveExpression)))))

  (testing "with unsupported type namespace"
    (let [elm {:type "Retrieve" :dataType "{foo}Bar"}]
      (is (thrown-anom? ::anom/unsupported (c/compile {} elm))))))

