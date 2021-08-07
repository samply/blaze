(ns blaze.elm.compiler.queries-test
  (:require
    [blaze.db.api :as d]
    [blaze.db.api-stub :refer [mem-node-system with-system-data]]
    [blaze.elm.code :as code]
    [blaze.elm.code-spec]
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.queries :as queries]
    [blaze.elm.compiler.test-util :as tu]
    [blaze.elm.literal]
    [blaze.elm.literal-spec]
    [blaze.elm.quantity :as quantity]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec.type]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [juxt.iota :refer [given]])
  (:import
    [clojure.lang IPersistentCollection]))


(st/instrument)
(tu/instrument-compile)


(defn- fixture [f]
  (st/instrument)
  (tu/instrument-compile)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


;; 10.1. Query
;;
;; The Query operator represents a clause-based query. The result of the query
;; is determined by the type of sources included, as well as the clauses used
;; in the query.
(deftest compile-query-test
  (testing "Non-retrieve queries"
    (testing "Sort"
      (testing "ByDirection"
        (are [query res] (= res (core/-eval (c/compile {} query) {} nil nil))
          {:type "Query"
           :source
           [{:alias "S"
             :expression #elm/list [#elm/integer "2" #elm/integer "1" #elm/integer "1"]}]
           :sort {:by [{:type "ByDirection" :direction "asc"}]}}
          [1 2]))

      (testing "ByExpression"
        (are [query res] (= res (core/-eval (c/compile {} query) {} nil nil))
          {:type "Query"
           :source
           [{:alias "S"
             :expression
             #elm/list
                 [#elm/quantity[2 "m"]
                  #elm/quantity[1 "m"]
                  #elm/quantity[1 "m"]]}]
           :sort
           {:by
            [{:type "ByExpression"
              :direction "asc"
              :expression
              {:type "Property"
               :path "value"
               :scope "S"
               :resultTypeName "{urn:hl7-org:elm-types:r1}decimal"}}]}}
          [(quantity/quantity 1 "m") (quantity/quantity 2 "m")])

        (testing "with IdentifierRef"
          (are [query res] (= res (core/-eval (c/compile {} query) {} nil nil))
            {:type "Query"
             :source
             [{:alias "S"
               :expression
               #elm/list
                   [#elm/instance["{urn:hl7-org:elm-types:r1}Code"
                                  {"system" #elm/string"foo"
                                   "code" #elm/string"c"}]
                    #elm/instance["{urn:hl7-org:elm-types:r1}Code"
                                  {"system" #elm/string"bar"
                                   "code" #elm/string"c"}]]}]
             :sort
             {:by
              [{:type "ByExpression"
                :direction "asc"
                :expression
                {:type "IdentifierRef"
                 :name "system"}}]}}
            [(code/to-code "bar" nil "c")
             (code/to-code "foo" nil "c")]))))

    (testing "Return non-distinct"
      (are [query res] (= res (core/-eval (c/compile {} query) {} nil nil))
        {:type "Query"
         :source
         [{:alias "S"
           :expression #elm/list [#elm/integer "1" #elm/integer "1"]}]
         :return {:distinct false :expression {:type "AliasRef" :name "S"}}}
        [1 1]))

    (testing "returns only the first item on optimize first"
      (let [query {:type "Query"
                   :source
                   [{:alias "S"
                     :expression #elm/list [#elm/integer "1" #elm/integer "1"]}]}
            res (core/-eval (c/compile {:optimizations #{:first}} query) {} nil nil)]
        (is (not (instance? IPersistentCollection res))))))

  (testing "Retrieve queries"
    (with-system-data [{:blaze.db/keys [node]} mem-node-system]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (let [db (d/db node)
            retrieve {:type "Retrieve" :dataType "{http://hl7.org/fhir}Patient"}
            where {:type "Equal"
                   :operand
                   [{:path "gender"
                     :scope "P"
                     :type "Property"
                     :resultTypeName "{http://hl7.org/fhir}string"
                     :life/source-type "{http://hl7.org/fhir}Patient"}
                    #elm/integer "2"]}
            return {:path "gender"
                    :scope "P"
                    :type "Property"
                    :resultTypeName "{http://hl7.org/fhir}string"
                    :life/source-type "{http://hl7.org/fhir}Patient"}]

        (let [query {:type "Query"
                     :source
                     [{:alias "P"
                       :expression retrieve}]}]
          (given (core/-eval (c/compile {:node node :eval-context "Unfiltered"} query) {:db db} nil nil)
            [0 fhir-spec/fhir-type] := :fhir/Patient
            [0 :id] := "0"))

        (let [query {:type "Query"
                     :source
                     [{:alias "P"
                       :expression retrieve}]
                     :where where}]
          (is (empty? (core/-eval (c/compile {:node node :eval-context "Unfiltered"} query) {:db db} nil nil))))

        (let [query {:type "Query"
                     :source
                     [{:alias "P"
                       :expression retrieve}]
                     :return {:expression return}}]
          (is (nil? (first (core/-eval (c/compile {:node node :eval-context "Unfiltered"} query) {:db db} nil nil)))))

        (let [query {:type "Query"
                     :source
                     [{:alias "P"
                       :expression retrieve}]
                     :where where
                     :return {:expression return}}]
          (is (empty? (core/-eval (c/compile {:node node :eval-context "Unfiltered"} query) {:db db} nil nil))))))))


;; 10.3. AliasRef
(deftest compile-alias-ref-test
  (are [elm res] (= res (core/-eval (c/compile {} elm) {} nil {"foo" ::result}))
    {:type "AliasRef" :name "foo"}
    ::result))


;; 10.12. With
(deftest compile-with-clause-test
  (testing "Equiv With with two Observations comparing there subjects."
    (with-system-data [{:blaze.db/keys [node]} mem-node-system]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject
               #fhir/Reference{:reference "Patient/0"}}]]]

      (let [elm {:alias "O1"
               :type "WithEquiv"
               :expression
               {:type "Retrieve"
                :dataType "{http://hl7.org/fhir}Observation"}
               :equivOperand
               [{:path "subject"
                 :scope "O0"
                 :type "Property"
                 :resultTypeName "{http://hl7.org/fhir}Reference"
                 :life/scopes #{"O0"}
                 :life/source-type "{http://hl7.org/fhir}Observation"}
                {:path "subject"
                 :scope "O1"
                 :type "Property"
                 :resultTypeName "{http://hl7.org/fhir}Reference"
                 :life/scopes #{"O1"}
                 :life/source-type "{http://hl7.org/fhir}Observation"}]}
          compile-context
          {:node node :life/single-query-scope "O0" :eval-context "Unfiltered"}
          xform-factory (queries/compile-with-equiv-clause compile-context elm)
          eval-context {:db (d/db node)}
          xform (queries/-create xform-factory eval-context nil)
          lhs-entity {:fhir/type :fhir/Observation
                      :subject #fhir/Reference{:reference "Patient/0"}}]
      (is (= [lhs-entity] (into [] xform [lhs-entity]))))))

  (testing "Equiv With with one Patient and one Observation comparing the patient with the operation subject."
    (with-system-data [{:blaze.db/keys [node]} mem-node-system]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject
               #fhir/Reference{:reference "Patient/0"}}]]]

      (let [elm {:alias "O"
               :type "WithEquiv"
               :expression
               {:type "Retrieve"
                :dataType "{http://hl7.org/fhir}Observation"}
               :equivOperand
               [{:name "P" :type "AliasRef" :life/scopes #{"P"}}
                {:path "subject"
                 :scope "O"
                 :type "Property"
                 :resultTypeName "{http://hl7.org/fhir}Reference"
                 :life/scopes #{"O"}
                 :life/source-type "{http://hl7.org/fhir}Observation"}]}
          compile-context
          {:node node :life/single-query-scope "P" :eval-context "Unfiltered"}
          xform-factory (queries/compile-with-equiv-clause compile-context elm)
          eval-context {:db (d/db node)}
          xform (queries/-create xform-factory eval-context nil)
          lhs-entity #fhir/Reference{:reference "Patient/0"}]
      (is (= [lhs-entity] (into [] xform [lhs-entity])))))))
