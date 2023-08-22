(ns blaze.elm.compiler.queries-test
  "10. Queries

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.anomaly :as ba]
    [blaze.db.api :as d]
    [blaze.db.api-stub :refer [mem-node-config with-system-data]]
    [blaze.elm.code :as code]
    [blaze.elm.code-spec]
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.core-spec]
    [blaze.elm.compiler.queries :as queries]
    [blaze.elm.compiler.test-util :as tu :refer [has-form]]
    [blaze.elm.literal]
    [blaze.elm.literal-spec]
    [blaze.elm.quantity :as quantity]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec.type]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]))


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
;; is determined by the type of sources included as well as the clauses used
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
                     [#elm/quantity [2 "m"]
                      #elm/quantity [1 "m"]
                      #elm/quantity [1 "m"]]}]
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
                       [#elm/instance ["{urn:hl7-org:elm-types:r1}Code"
                                       {"system" #elm/string "foo"
                                        "code" #elm/string "c"}]
                        #elm/instance ["{urn:hl7-org:elm-types:r1}Code"
                                       {"system" #elm/string "bar"
                                        "code" #elm/string "c"}]]}]
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
      (let [elm {:type "Query"
                 :source
                 [{:alias "S"
                   :expression #elm/list [#elm/integer "1" #elm/integer "1"]}]
                 :return {:distinct false :expression {:type "AliasRef" :name "S"}}}
            expr (c/compile {} elm)]

        (testing "eval"
          (is (= [1 1] (core/-eval expr {} nil nil))))

        (testing "form"
          (has-form expr '(vector-query (return (fn [S] (alias-ref S))) [1 1])))))

    (testing "with query hint optimize first"
      (let [elm {:type "Query"
                 :source
                 [{:alias "S"
                   :expression #elm/list [#elm/integer "1" #elm/integer "1"]}]}
            expr (c/compile {:optimizations #{:first}} elm)]

        (testing "eval"
          (is (= [1] (into [] (core/-eval expr {} nil nil)))))

        (testing "form"
          (has-form expr '(eduction-query distinct [1 1]))))))

  (testing "Retrieve queries"
    (with-system-data [{:blaze.db/keys [node]} mem-node-config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :gender #fhir/code"female"}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :gender #fhir/code"male"}]]]

      (let [db (d/db node)
            retrieve {:type "Retrieve" :dataType "{http://hl7.org/fhir}Patient"}
            where {:type "Equal"
                   :operand
                   [{:type "FunctionRef"
                     :name "ToString"
                     :operand
                     [{:path "gender"
                       :scope "P"
                       :type "Property"
                       :resultTypeName "{http://hl7.org/fhir}string"}]}
                    #elm/string "female"]}
            return {:path "gender"
                    :scope "P"
                    :type "Property"
                    :resultTypeName "{http://hl7.org/fhir}string"}]

        (testing "source only"
          (let [elm {:type "Query"
                     :source
                     [{:alias "P"
                       :expression retrieve}]}
                expr (c/compile {:node node :eval-context "Unfiltered"} elm)]

            (testing "eval"
              (given (core/-eval expr {:db db} nil nil)
                count := 2
                [0 fhir-spec/fhir-type] := :fhir/Patient
                [0 :id] := "0"
                [1 fhir-spec/fhir-type] := :fhir/Patient
                [1 :id] := "1"))

            (testing "form"
              (has-form expr '(vector-query distinct (retrieve "Patient"))))))

        (testing "with where clause"
          (let [elm {:type "Query"
                     :source
                     [{:alias "P"
                       :expression retrieve}]
                     :where where}
                expr (c/compile {:node node :eval-context "Unfiltered"} elm)]

            (testing "eval"
              (given (core/-eval expr {:db db} nil nil)
                count := 1
                [0 fhir-spec/fhir-type] := :fhir/Patient
                [0 :id] := "0"))

            (testing "form"
              (has-form expr
                '(vector-query
                   (comp
                     (where
                       (fn [P]
                         (equal (call "ToString" (:gender P)) "female")))
                     distinct)
                   (retrieve "Patient"))))))

        (testing "with return clause"
          (let [elm {:type "Query"
                     :source
                     [{:alias "P"
                       :expression retrieve}]
                     :return {:expression return}}
                expr (c/compile {:node node :eval-context "Unfiltered"} elm)]

            (testing "eval"
              (given (core/-eval expr {:db db} nil nil)
                count := 2
                [0] := #fhir/code"female"
                [1] := #fhir/code"male"))

            (testing "form"
              (has-form expr
                '(vector-query
                   (distinct (return (fn [P] (:gender P))))
                   (retrieve "Patient"))))))

        (testing "with where and return clauses"
          (let [elm {:type "Query"
                     :source
                     [{:alias "P"
                       :expression retrieve}]
                     :where where
                     :return {:expression return}}
                expr (c/compile {:node node :eval-context "Unfiltered"} elm)]

            (testing "eval"
              (given (core/-eval expr {:db db} nil nil)
                count := 1
                [0] := #fhir/code"female"))

            (testing "form"
              (has-form expr
                '(vector-query
                   (comp
                     (where
                       (fn [P]
                         (equal (call "ToString" (:gender P)) "female")))
                     (distinct (return (fn [P] (:gender P)))))
                   (retrieve "Patient")))))))))

  (testing "Unsupported With clause"
    (let [elm {:type "Query"
               :source
               [{:expression
                 {:type "Retrieve"
                  :dataType "{http://hl7.org/fhir}Condition"}
                 :alias "C"}]
               :relationship
               [{:type "With"
                 :alias "P"
                 :expression
                 {:type "Retrieve" :dataType "{http://hl7.org/fhir}Procedure"}
                 :suchThat
                 {:type "Equal"
                  :operand [#elm/integer "1" #elm/integer "1"]}}]}]
      (given (ba/try-anomaly (c/compile {} elm))
        ::anom/category := ::anom/unsupported
        ::anom/message := "Unsupported With clause in query expression.")))

  (testing "Unsupported Without clause"
    (let [elm {:type "Query"
               :source
               [{:expression
                 {:type "Retrieve"
                  :dataType "{http://hl7.org/fhir}Condition"}
                 :alias "C"}]
               :relationship
               [{:type "Without"
                 :alias "P"
                 :expression
                 {:type "Retrieve" :dataType "{http://hl7.org/fhir}Procedure"}
                 :suchThat
                 {:type "Equal"
                  :operand [#elm/integer "1" #elm/integer "1"]}}]}]
      (given (ba/try-anomaly (c/compile {} elm))
        ::anom/category := ::anom/unsupported
        ::anom/message := "Unsupported Without clause in query expression."))))


;; 10.3. AliasRef
;;
;; The AliasRef expression allows for the reference of a specific source within
;; the scope of a query.
(deftest compile-alias-ref-test
  (let [expr (c/compile {} {:type "AliasRef" :name "foo"})]
    (testing "eval"
      (is (= ::result (core/-eval expr {} nil {"foo" ::result}))))

    (testing "form"
      (has-form expr '(alias-ref foo)))))


;; 10.7. IdentifierRef
;;
;; The IdentifierRef type defines an expression that references an identifier
;; that is either unresolved, or has been resolved to an attribute in an
;; unambiguous iteration scope such as a sort. Implementations should attempt to
;; resolve the identifier, only throwing an error at compile-time (or run-time
;; for an interpretive system) if the identifier reference cannot be resolved.
(deftest compile-identifier-ref-test
  (let [expr (c/compile {} {:type "IdentifierRef" :name "foo"})]

    (testing "form"
      (has-form expr '(:foo default)))))


;; TODO 10.9. QueryLetRef
;;
;; The QueryLetRef expression allows for the reference of a specific let
;; definition within the scope of a query.


;; 10.14. With
;;
;; The With clause restricts the elements of a given source to only those
;; elements that have elements in the related source that satisfy the suchThat
;; condition. This operation is known as a semi-join in database languages.
(deftest compile-with-clause-test
  (testing "Equiv With with two Observations comparing there subjects."
    (with-system-data [{:blaze.db/keys [node]} mem-node-config]
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
                   :life/scopes #{"O0"}}
                  {:path "subject"
                   :scope "O1"
                   :type "Property"
                   :resultTypeName "{http://hl7.org/fhir}Reference"
                   :life/scopes #{"O1"}}]}
            compile-context
            {:node node :eval-context "Unfiltered"}
            xform-factory (queries/compile-with-equiv-clause compile-context "O0" elm)
            eval-context {:db (d/db node)}
            xform (queries/-create xform-factory eval-context nil nil)
            lhs-entity {:fhir/type :fhir/Observation
                        :subject #fhir/Reference{:reference "Patient/0"}}]

        (testing "filtering"
          (is (= [lhs-entity] (into [] xform [lhs-entity]))))

        (testing "form"
          (is (= '(with (retrieve "Observation"))
                 (queries/-form xform-factory)))))))

  (testing "Equiv With with one Patient and one Observation comparing the patient with the operation subject."
    (with-system-data [{:blaze.db/keys [node]} mem-node-config]
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
                   :life/scopes #{"O"}}]}
            compile-context
            {:node node :eval-context "Unfiltered"}
            xform-factory (queries/compile-with-equiv-clause compile-context "P" elm)
            eval-context {:db (d/db node)}
            xform (queries/-create xform-factory eval-context nil nil)
            lhs-entity #fhir/Reference{:reference "Patient/0"}]
        (is (= [lhs-entity] (into [] xform [lhs-entity])))))))
