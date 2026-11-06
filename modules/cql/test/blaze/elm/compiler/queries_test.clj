(ns blaze.elm.compiler.queries-test
  "10. Queries

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.cql.translator :as t]
   [blaze.db.api :as d]
   [blaze.db.api-stub :as api-stub :refer [with-db with-system-data]]
   [blaze.elm.code :as code]
   [blaze.elm.code-spec]
   [blaze.elm.compiler :as c]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.core-spec]
   [blaze.elm.compiler.queries :as queries]
   [blaze.elm.compiler.test-util :as ctu :refer [has-form]]
   [blaze.elm.expression :as-alias expr]
   [blaze.elm.expression.cache :as ec]
   [blaze.elm.literal :as elm]
   [blaze.elm.literal-spec]
   [blaze.elm.protocols :as p]
   [blaze.elm.util-spec]
   [blaze.fhir.spec.generators :as fg]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.spec.type.system.spec]
   [blaze.fhir.test-util :refer [run-all!]]
   [blaze.terminology-service :as-alias ts]
   [blaze.test-util :refer [satisfies-prop]]
   [blaze.util-spec]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [integrant.core :as ig]
   [java-time.api :as time]
   [juxt.iota :refer [given]])
  (:import
   [java.time OffsetDateTime]))

(set! *warn-on-reflection* true)
(st/instrument)
(ctu/instrument-compile)

(defn- fixture [f]
  (st/instrument)
  (ctu/instrument-compile)
  (f)
  (st/unstrument))

(test/use-fixtures :each fixture)

(defn- dateTime-value []
  (gen/fmap #(p/to-date-time % (OffsetDateTime/now)) (fg/dateTime-value)))

(deftest sort-by-test
  (testing "date"
    (testing "asc"
      (satisfies-prop 1000
        (prop/for-all [dates (gen/vector (fg/rare-nil fg/date-value))]
          (= (sort (map time/year dates))
             (map time/year (queries/sort-by identity "asc" dates))))))

    (testing "desc"
      (satisfies-prop 1000
        (prop/for-all [dates (gen/vector (fg/rare-nil fg/date-value))]
          (= (reverse (sort (map time/year dates)))
             (map time/year (queries/sort-by identity "desc" dates)))))))

  (testing "date-time"
    (testing "asc"
      (satisfies-prop 1000
        (prop/for-all [date-times (gen/vector (fg/rare-nil (dateTime-value)))]
          (= (sort (map time/year date-times))
             (map time/year (queries/sort-by identity "asc" date-times))))))

    (testing "desc"
      (satisfies-prop 1000
        (prop/for-all [date-times (gen/vector (fg/rare-nil (dateTime-value)))]
          (= (reverse (sort (map time/year date-times)))
             (map time/year (queries/sort-by identity "desc" date-times))))))))

;; 10.1. Query
;;
;; The Query operator represents a clause-based query. The result of the query
;; is determined by the type of sources included as well as the clauses used
;; in the query.
(deftest compile-query-test
  (testing "Non-retrieve queries"
    (testing "Sort"
      (testing "ByDirection"
        (let [elm {:type "Query"
                   :source
                   [#elm/aliased-query-source [#elm/list [#elm/integer "2"
                                                          #elm/integer "1"
                                                          #elm/integer "1"]
                                               "S"]]
                   :sort {:by [{:type "ByDirection" :direction "asc"}]}}
              expr (c/compile {} elm)]

          (testing "eval"
            (is (= [1 2] (core/-eval expr {} nil nil))))

          (testing "form"
            (has-form expr '(sorted-vector-query distinct [2 1 1] asc)))))

      (testing "ByExpression"
        (testing "with IdentifierRef"
          (are [query res] (= res (core/-eval (c/compile {} query) {} nil nil))
            {:type "Query"
             :source [#elm/aliased-query-source [#elm/list [#elm/instance ["{urn:hl7-org:elm-types:r1}Code"
                                                                           {"system" #elm/string "foo"
                                                                            "code" #elm/string "c"}]
                                                            #elm/instance ["{urn:hl7-org:elm-types:r1}Code"
                                                                           {"system" #elm/string "bar"
                                                                            "code" #elm/string "c"}]]
                                                 "S"]]
             :sort
             {:by
              [{:type "ByExpression"
                :direction "asc"
                :expression
                {:type "IdentifierRef"
                 :name "system"}}]}}
            [(code/code "bar" nil "c")
             (code/code "foo" nil "c")]))))

    (testing "Return non-distinct"
      (let [elm {:type "Query"
                 :source [#elm/aliased-query-source [#elm/list [#elm/integer "1" #elm/integer "1"] "S"]]
                 :return {:distinct false :expression {:type "AliasRef" :name "S"}}}
            expr (c/compile {} elm)]

        (testing "eval"
          (is (= [1 1] (core/-eval expr {} nil nil))))

        (testing "form"
          (has-form expr '(vector-query (map (fn [S] (alias-ref S))) [1 1])))))

    (testing "with query hint optimize first"
      (let [elm {:type "Query"
                 :source [#elm/aliased-query-source [#elm/list [#elm/integer "1"
                                                                #elm/integer "1"]
                                                     "S"]]}
            expr (c/compile {:optimizations #{:first}} elm)]

        (testing "eval"
          (is (= [1] (into [] (core/-eval expr {} nil nil)))))

        (testing "form"
          (has-form expr '(eduction-query distinct [1 1])))))

    (testing "with query hint optimize non-distinct"
      (let [elm {:type "Query"
                 :source [#elm/aliased-query-source [#elm/list [#elm/integer "1"
                                                                #elm/integer "1"]
                                                     "S"]]}
            expr (c/compile {:optimizations #{:non-distinct}} elm)]

        (testing "eval"
          (is (= [1 1] (into [] (core/-eval expr {} nil nil)))))

        (testing "form"
          (has-form expr [1 1])))))

  (testing "Retrieve queries"
    (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :gender #fhir/code "female"}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :gender #fhir/code "male"}]]]

      (with-db [db node]
        (let [retrieve #elm/retrieve{:type "Patient"}
              where #elm/equal[{:type "FunctionRef"
                                :name "ToString"
                                :operand [#elm/scope-property ["P" "gender"]]}
                               #elm/string "female"]
              return #elm/scope-property ["P" "gender"]]

          (testing "source only"
            (let [elm {:type "Query"
                       :source
                       [{:expression retrieve
                         :alias "P"}]}
                  expr (c/compile {:node node :eval-context "Unfiltered"} elm)]

              (testing "eval"
                (given (core/-eval expr {:db db} nil nil)
                  count := 2
                  [0 :fhir/type] := :fhir/Patient
                  [0 :id] := "0"
                  [1 :fhir/type] := :fhir/Patient
                  [1 :id] := "1"))

              (testing "form"
                (has-form expr '(vector-query distinct (retrieve "Patient"))))))

          (testing "with where clause"
            (let [elm {:type "Query"
                       :source
                       [{:expression retrieve
                         :alias "P"}]
                       :where where}
                  expr (c/compile {:node node :eval-context "Unfiltered"} elm)]

              (testing "eval"
                (given (core/-eval expr {:db db} nil nil)
                  count := 1
                  [0 :fhir/type] := :fhir/Patient
                  [0 :id] := "0"))

              (testing "form"
                (has-form expr
                  '(vector-query
                    (comp
                     (filter
                      (fn [P]
                        (equal (call "ToString" (:gender P)) "female")))
                     distinct)
                    (retrieve "Patient")))))

            (testing "with query hint optimize non-distinct"
              (let [elm {:type "Query"
                         :source
                         [{:expression retrieve
                           :alias "P"}]
                         :where where}
                    ctx {:node node :eval-context "Unfiltered"
                         :optimizations #{:non-distinct}}
                    expr (c/compile ctx elm)]

                (testing "eval"
                  (given (core/-eval expr {:db db} nil nil)
                    count := 1
                    [0 :fhir/type] := :fhir/Patient
                    [0 :id] := "0"))

                (testing "form"
                  (has-form expr
                    '(vector-query
                      (filter
                       (fn [P]
                         (equal (call "ToString" (:gender P)) "female")))
                      (retrieve "Patient"))))))

            (testing "with exists expression around"
              (let [elm {:type "Query"
                         :source
                         [{:expression retrieve
                           :alias "P"}]
                         :where where}
                    ctx {:node node :eval-context "Unfiltered"}
                    expr (c/compile ctx (elm/exists elm))]

                (testing "eval"
                  (is (true? (core/-eval expr {:db db} nil nil))))

                (testing "form"
                  (has-form expr
                    '(exists
                      (eduction-query
                       (filter
                        (fn [P]
                          (equal (call "ToString" (:gender P)) "female")))
                       (retrieve "Patient"))))))))

          (testing "with return clause"
            (let [elm {:type "Query"
                       :source
                       [{:expression retrieve
                         :alias "P"}]
                       :return {:expression return}}
                  expr (c/compile {:node node :eval-context "Unfiltered"} elm)]

              (testing "eval"
                (given (core/-eval expr {:db db} nil nil)
                  count := 2
                  [0] := #fhir/code "female"
                  [1] := #fhir/code "male"))

              (testing "form"
                (has-form expr
                  '(vector-query
                    (comp
                     (map (fn [P] (:gender P)))
                     distinct)
                    (retrieve "Patient")))))

            (testing "with query hint optimize non-distinct"
              (let [elm {:type "Query"
                         :source
                         [{:expression retrieve
                           :alias "P"}]
                         :return {:expression return}}
                    ctx {:node node :eval-context "Unfiltered"
                         :optimizations #{:non-distinct}}
                    expr (c/compile ctx elm)]

                (testing "eval"
                  (given (core/-eval expr {:db db} nil nil)
                    count := 2
                    [0] := #fhir/code "female"
                    [1] := #fhir/code "male"))

                (testing "form"
                  (has-form expr
                    '(vector-query
                      (map (fn [P] (:gender P)))
                      (retrieve "Patient")))))))

          (testing "with where and return clauses"
            (let [elm {:type "Query"
                       :source
                       [{:expression retrieve
                         :alias "P"}]
                       :where where
                       :return {:expression return}}
                  expr (c/compile {:node node :eval-context "Unfiltered"} elm)]

              (testing "eval"
                (given (core/-eval expr {:db db} nil nil)
                  count := 1
                  [0] := #fhir/code "female"))

              (testing "form"
                (has-form expr
                  '(vector-query
                    (comp
                     (filter
                      (fn [P]
                        (equal (call "ToString" (:gender P)) "female")))
                     (comp
                      (map
                       (fn [P] (:gender P)))
                      distinct))
                    (retrieve "Patient")))))

            (testing "with query hint optimize non-distinct"
              (let [elm {:type "Query"
                         :source
                         [{:expression retrieve
                           :alias "P"}]
                         :where where
                         :return {:expression return}}
                    ctx {:node node :eval-context "Unfiltered"
                         :optimizations #{:non-distinct}}
                    expr (c/compile ctx elm)]

                (testing "eval"
                  (given (core/-eval expr {:db db} nil nil)
                    count := 1
                    [0] := #fhir/code "female"))

                (testing "form"
                  (has-form expr
                    '(vector-query
                      (comp
                       (filter
                        (fn [P]
                          (equal (call "ToString" (:gender P)) "female")))
                       (map
                        (fn [P] (:gender P))))
                      (retrieve "Patient")))))))))))

  (testing "With clause"
    (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Encounter :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
        [:put {:fhir/type :fhir/Specimen :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
               :encounter #fhir/Reference{:reference #fhir/string "Encounter/0"}
               :specimen #fhir/Reference{:reference #fhir/string "Specimen/0"}}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

      (with-db [db node]
        (let [patient (ctu/resource db "Patient" "0")]

          (let [elm {:type "Query"
                     :source [#elm/aliased-query-source [#elm/retrieve{:type "Observation"} "O"]]
                     :relationship
                     [{:type "With"
                       :expression #elm/retrieve{:type "Encounter"}
                       :alias "E"
                       :suchThat #elm/equal [#elm/function-ref ["ToString" #elm/source-property [#elm/scope-property ["O" "encounter"] "reference"]]
                                             #elm/concatenate [#elm/string "Encounter/" #elm/scope-property ["E" "id"]]]}]}]

            (let [expr (c/compile {:node node :eval-context "Patient"} elm)]
              (testing "eval"
                (given (core/-eval expr {:db db} patient nil)
                  count := 1
                  [0 :fhir/type] := :fhir/Observation
                  [0 :id] := "0"))

              (testing "form"
                (has-form expr
                  '(vector-query
                    (comp
                     (filter
                      (fn [O]
                        (exists
                         (fn [E]
                           (equal
                            (call "ToString" (:reference (:encounter O)))
                            (concatenate "Encounter/" (:id E))))
                         (retrieve "Encounter"))))
                     distinct)
                    (retrieve "Observation")))))

            (testing "with query hint optimize non-distinct"
              (let [ctx {:node node :eval-context "Patient"
                         :optimizations #{:non-distinct}}
                    expr (c/compile ctx elm)]
                (testing "eval"
                  (given (core/-eval expr {:db db} patient nil)
                    count := 1
                    [0 :fhir/type] := :fhir/Observation
                    [0 :id] := "0"))

                (testing "form"
                  (has-form expr
                    '(vector-query
                      (filter
                       (fn [O]
                         (exists
                          (fn [E]
                            (equal
                             (call "ToString" (:reference (:encounter O)))
                             (concatenate "Encounter/" (:id E))))
                          (retrieve "Encounter"))))
                      (retrieve "Observation")))))))

          (testing "including return clause"
            (let [elm {:type "Query"
                       :source [#elm/aliased-query-source [#elm/retrieve{:type "Observation"} "O"]]
                       :relationship
                       [{:type "With"
                         :expression #elm/retrieve{:type "Encounter"}
                         :alias "E"
                         :suchThat #elm/equal [#elm/function-ref ["ToString" #elm/source-property [#elm/scope-property ["O" "encounter"] "reference"]]
                                               #elm/concatenate [#elm/string "Encounter/" #elm/scope-property ["E" "id"]]]}]
                       :return {:expression #elm/scope-property ["O" "id"]}}]

              (let [expr (c/compile {:node node :eval-context "Patient"} elm)]
                (testing "eval"
                  (given (core/-eval expr {:db db} patient nil)
                    count := 1
                    [0] := "0"))

                (testing "form"
                  (has-form expr
                    '(vector-query
                      (comp
                       (filter
                        (fn [O]
                          (exists
                           (fn [E]
                             (equal
                              (call "ToString" (:reference (:encounter O)))
                              (concatenate "Encounter/" (:id E))))
                           (retrieve "Encounter"))))
                       (comp
                        (map
                         (fn [O] (:id O)))
                        distinct))
                      (retrieve "Observation")))))

              (testing "with query hint optimize non-distinct"
                (let [ctx {:node node :eval-context "Patient"
                           :optimizations #{:non-distinct}}
                      expr (c/compile ctx elm)]
                  (testing "eval"
                    (given (core/-eval expr {:db db} patient nil)
                      count := 1
                      [0] := "0"))

                  (testing "form"
                    (has-form expr
                      '(vector-query
                        (comp
                         (filter
                          (fn [O]
                            (exists
                             (fn [E]
                               (equal
                                (call "ToString" (:reference (:encounter O)))
                                (concatenate "Encounter/" (:id E))))
                             (retrieve "Encounter"))))
                         (map
                          (fn [O] (:id O))))
                        (retrieve "Observation"))))))))

          (testing "including non-distinct return clause"
            (let [elm {:type "Query"
                       :source [#elm/aliased-query-source [#elm/retrieve{:type "Observation"} "O"]]
                       :relationship
                       [{:type "With"
                         :expression #elm/retrieve{:type "Encounter"}
                         :alias "E"
                         :suchThat #elm/equal [#elm/function-ref ["ToString" #elm/source-property [#elm/scope-property ["O" "encounter"] "reference"]]
                                               #elm/concatenate [#elm/string "Encounter/" #elm/scope-property ["E" "id"]]]}]
                       :return {:distinct false :expression #elm/scope-property ["O" "id"]}}
                  expr (c/compile {:node node :eval-context "Patient"} elm)]

              (testing "eval"
                (given (core/-eval expr {:db db} patient nil)
                  count := 1
                  [0] := "0"))

              (testing "form"
                (has-form expr
                  '(vector-query
                    (comp
                     (filter
                      (fn [O]
                        (exists
                         (fn [E]
                           (equal
                            (call "ToString" (:reference (:encounter O)))
                            (concatenate "Encounter/" (:id E))))
                         (retrieve "Encounter"))))
                     (map
                      (fn [O] (:id O))))
                    (retrieve "Observation"))))))

          (testing "including where clause"
            (let [elm {:type "Query"
                       :source [#elm/aliased-query-source [#elm/retrieve{:type "Observation"} "O"]]
                       :relationship
                       [{:type "With"
                         :expression #elm/retrieve{:type "Encounter"}
                         :alias "E"
                         :suchThat #elm/equal [#elm/function-ref ["ToString" #elm/source-property [#elm/scope-property ["O" "encounter"] "reference"]]
                                               #elm/concatenate [#elm/string "Encounter/" #elm/scope-property ["E" "id"]]]}]
                       :where #elm/equal [#elm/string "1" #elm/scope-property ["O" "id"]]}]

              (let [expr (c/compile {:node node :eval-context "Patient"} elm)]
                (testing "eval"
                  (is (empty? (core/-eval expr {:db db} patient nil))))

                (testing "form"
                  (has-form expr
                    '(vector-query
                      (comp
                       (filter
                        (fn [O] (equal "1" (:id O))))
                       (filter
                        (fn [O]
                          (exists
                           (fn [E]
                             (equal
                              (call "ToString" (:reference (:encounter O)))
                              (concatenate "Encounter/" (:id E))))
                           (retrieve "Encounter"))))
                       distinct)
                      (retrieve "Observation")))))

              (testing "with query hint optimize non-distinct"
                (let [ctx {:node node :eval-context "Patient"
                           :optimizations #{:non-distinct}}
                      expr (c/compile ctx elm)]
                  (testing "eval"
                    (is (empty? (core/-eval expr {:db db} patient nil))))

                  (testing "form"
                    (has-form expr
                      '(vector-query
                        (comp
                         (filter
                          (fn [O] (equal "1" (:id O))))
                         (filter
                          (fn [O]
                            (exists
                             (fn [E]
                               (equal
                                (call "ToString" (:reference (:encounter O)))
                                (concatenate "Encounter/" (:id E))))
                             (retrieve "Encounter")))))
                        (retrieve "Observation"))))))))

          (testing "two with clauses"
            (let [elm {:type "Query"
                       :source [#elm/aliased-query-source [#elm/retrieve{:type "Observation"} "O"]]
                       :relationship
                       [{:type "With"
                         :expression #elm/retrieve{:type "Encounter"}
                         :alias "E"
                         :suchThat #elm/equal [#elm/function-ref ["ToString" #elm/source-property [#elm/scope-property ["O" "encounter"] "reference"]]
                                               #elm/concatenate [#elm/string "Encounter/" #elm/scope-property ["E" "id"]]]}
                        {:type "With"
                         :expression #elm/retrieve{:type "Specimen"}
                         :alias "S"
                         :suchThat #elm/equal [#elm/function-ref ["ToString" #elm/source-property [#elm/scope-property ["O" "specimen"] "reference"]]
                                               #elm/concatenate [#elm/string "Specimen/" #elm/scope-property ["S" "id"]]]}]}]

              (let [expr (c/compile {:node node :eval-context "Patient"} elm)]
                (testing "eval"
                  (given (core/-eval expr {:db db} patient nil)
                    count := 1
                    [0 :fhir/type] := :fhir/Observation
                    [0 :id] := "0"))

                (testing "form"
                  (has-form expr
                    '(vector-query
                      (comp
                       (filter
                        (fn [O]
                          (exists
                           (fn [E]
                             (equal
                              (call "ToString" (:reference (:encounter O)))
                              (concatenate "Encounter/" (:id E))))
                           (retrieve "Encounter"))))
                       (filter
                        (fn [O]
                          (exists
                           (fn [S]
                             (equal
                              (call "ToString" (:reference (:specimen O)))
                              (concatenate "Specimen/" (:id S))))
                           (retrieve "Specimen"))))
                       distinct)
                      (retrieve "Observation")))))

              (testing "with query hint optimize non-distinct"
                (let [ctx {:node node :eval-context "Patient"
                           :optimizations #{:non-distinct}}
                      expr (c/compile ctx elm)]
                  (testing "eval"
                    (given (core/-eval expr {:db db} patient nil)
                      count := 1
                      [0 :fhir/type] := :fhir/Observation
                      [0 :id] := "0"))

                  (testing "form"
                    (has-form expr
                      '(vector-query
                        (comp
                         (filter
                          (fn [O]
                            (exists
                             (fn [E]
                               (equal
                                (call "ToString" (:reference (:encounter O)))
                                (concatenate "Encounter/" (:id E))))
                             (retrieve "Encounter"))))
                         (filter
                          (fn [O]
                            (exists
                             (fn [S]
                               (equal
                                (call "ToString" (:reference (:specimen O)))
                                (concatenate "Specimen/" (:id S))))
                             (retrieve "Specimen")))))
                        (retrieve "Observation"))))))))))))

  (testing "Without clause"
    (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Encounter :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
               :encounter #fhir/Reference{:reference #fhir/string "Encounter/0"}}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

      (let [elm {:type "Query"
                 :source [#elm/aliased-query-source [#elm/retrieve{:type "Observation"} "O"]]
                 :relationship
                 [{:type "Without"
                   :expression #elm/retrieve{:type "Encounter"}
                   :alias "E"
                   :suchThat #elm/equal [#elm/function-ref ["ToString" #elm/source-property [#elm/scope-property ["O" "encounter"] "reference"]]
                                         #elm/concatenate [#elm/string "Encounter/" #elm/scope-property ["E" "id"]]]}]}
            expr (c/compile {:node node :eval-context "Patient"} elm)]
        (with-db [db node]
          (let [patient (ctu/resource db "Patient" "0")]

            (testing "eval"
              (given (core/-eval expr {:db db} patient nil)
                count := 1
                [0 :fhir/type] := :fhir/Observation
                [0 :id] := "1"))

            (testing "form"
              (has-form expr
                '(vector-query
                  (comp
                   (filter
                    (fn [O]
                      (not-exists
                       (fn [E]
                         (equal
                          (call "ToString" (:reference (:encounter O)))
                          (concatenate "Encounter/" (:id E))))
                       (retrieve "Encounter"))))
                   distinct)
                  (retrieve "Observation")))))))))

  (testing "Sort"
    (testing "ByExpression"
      (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Encounter :id "0"
                 :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
                 :period #fhir/Period{:start #fhir/dateTime #system/date-time "2025-05-15"}}]
          [:put {:fhir/type :fhir/Encounter :id "1"
                 :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
                 :period #fhir/Period{:start #fhir/dateTime #system/date-time "2025-05-16"}}]]]

        (let [elm {:type "Query"
                   :source
                   [#elm/aliased-query-source [#elm/retrieve{:type "Encounter"} "E"]]
                   :sort
                   {:type "SortClause",
                    :by
                    [{:type "ByExpression"
                      :expression
                      {:type "Property"
                       :path "start.value"
                       :source
                       {:type "IdentifierRef"
                        :name "period"
                        :resultTypeName "{http://hl7.org/fhir}Period"}
                       :resultTypeName "{http://hl7.org/fhir}dateTime"}
                      :resultTypeName "{urn:hl7-org:elm-types:r1}DateTime"
                      :direction "asc"}]}}
              expr (c/compile {:node node :eval-context "Patient"} elm)]
          (with-db [db node]
            (let [patient (ctu/resource db "Patient" "0")]

              (testing "eval"
                (is (= ["0" "1"] (map :id (core/-eval expr {:db db} patient nil))))))))))))

(deftest compile-query-medication-reference-test
  (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
    [[[:put {:fhir/type :fhir/Patient :id "0"}]
      [:put {:fhir/type :fhir/MedicationAdministration :id "0"
             :medication #fhir/Reference{:reference #fhir/string "Medication/0"}
             :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
      [:put {:fhir/type :fhir/MedicationAdministration :id "1"
             :medication #fhir/Reference{:reference #fhir/string "Medication/1"}
             :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
      [:put {:fhir/type :fhir/MedicationStatement :id "0"
             :medication #fhir/Reference{:reference #fhir/string "Medication/0"}
             :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
      [:put {:fhir/type :fhir/MedicationStatement :id "1"
             :medication #fhir/Reference{:reference #fhir/string "Medication/1"}
             :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
      [:put {:fhir/type :fhir/Medication :id "0"}]
      [:put {:fhir/type :fhir/Medication :id "1"}]]]

    (doseq [medication-type ["MedicationAdministration"
                             "MedicationStatement"]]
      (let [elm {:type "Query"
                 :source [(elm/aliased-query-source [(elm/retrieve {:type medication-type}) "M"])]
                 :where #elm/contains[#elm/list [#elm/string "Medication/0"] #elm/function-ref["ToString" #elm/source-property [#elm/scope-property ["M" "medication"] "reference"]]]}

            expr (c/compile {:node node :eval-context "Patient"
                             :optimizations #{:first :non-distinct}} elm)]
        (with-db [db node]
          (let [expr (c/optimize expr db)
                patient (ctu/resource db "Patient" "0")]

            (testing "eval"
              (given (into [] (core/-eval expr {:db db} patient nil))
                count := 1
                [0 :fhir/type] := (keyword "fhir" medication-type)
                [0 :id] := "0"))

            (testing "form"
              (has-form expr
                (list 'eduction-query
                      '(matcher [["medication" "Medication/0"]])
                      (list 'retrieve medication-type))))))))

    (testing "empty list in contains optimizes to an overall empty list"
      (let [elm {:type "Query"
                 :source [(elm/aliased-query-source [(elm/retrieve {:type "MedicationAdministration"}) "M"])]
                 :where #elm/contains[#elm/list [] #elm/function-ref["ToString" #elm/source-property [#elm/scope-property ["M" "medication"] "reference"]]]}

            expr (c/compile {:node node :eval-context "Patient"
                             :optimizations #{:first :non-distinct}} elm)]
        (with-db [db node]
          (let [expr (c/optimize expr db)
                patient (ctu/resource db "Patient" "0")]

            (testing "eval"
              (is (empty? (core/-eval expr {:db db} patient nil))))

            (testing "form"
              (has-form expr []))))))))

(def ^:private atc
  "http://fhir.de/CodeSystem/dimdi/atc")

(defn- atc-medication [id code]
  [:put {:fhir/type :fhir/Medication :id id
         :code
         (type/codeable-concept
          {:coding
           [(type/coding
             {:system (type/uri atc)
              :code (type/code code)})]})}])

(defn- medication-use [type id medication-ref]
  [:put {:fhir/type (keyword "fhir" type) :id id
         :medication (type/reference {:reference (type/string medication-ref)})
         :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}])

(def ^:private medication-reference-such-that
  "M.medication.reference = 'Medication/' + Med.id")

(defn- medication-with-query
  "Compiles the query of the define `Q` of a library with `source-type` and a
  `with` clause over the Medication retrieve `rhs` with `such-that`.

  The library defines the code system `atc`, the value set `vs` and the concept
  `two-codes` of the codes A10BF01 and B01AC06."
  ([system source-type code]
   (medication-with-query system source-type
                          (str "[Medication: Code '" code "' from atc]")
                          medication-reference-such-that ""))
  ([{:blaze.db/keys [node] ::ts/keys [local]} source-type rhs such-that more]
   (let [library (t/translate
                  (str "library Test
                        using FHIR version '4.0.0'
                        include FHIRHelpers version '4.0.0'
                        codesystem atc: '" atc "'
                        valueset vs: 'value-set-162344'
                        code c1: 'A10BF01' from atc
                        code c2: 'B01AC06' from atc
                        concept \"two-codes\": {c1, c2}
                        context Patient
                        define Q:
                          from [" source-type "] M
                            with " rhs " Med
                              such that " such-that "
                            " more))]
     (c/compile {:node node :eval-context "Patient" :library library
                 :terminology-service local
                 :optimizations #{:first :non-distinct}}
                (-> library :statements :def last :expression)))))

(defn- eval-ids [expr db]
  (mapv :id (core/-eval expr {:db db} (ctu/resource db "Patient" "0") nil)))

(defn- query-form
  "Returns the form of `expr` as map of :xform and :source, if `expr` is an
  eduction query."
  [expr]
  (let [[sym xform source] (c/form expr)]
    (when (= 'eduction-query sym)
      {:xform xform :source source})))

(deftest compile-query-medication-with-test
  (with-system-data [system api-stub/mem-node-config]
    [[[:put {:fhir/type :fhir/Patient :id "0"}]
      (atc-medication "0" "A10BF01")
      (atc-medication "1" "B01AC06")
      (medication-use "MedicationAdministration" "0" "Medication/0")
      (medication-use "MedicationAdministration" "1" "Medication/1")
      (medication-use "MedicationStatement" "0" "Medication/0")
      (medication-use "MedicationStatement" "1" "Medication/1")]]

    (with-db [db (:blaze.db/node system)]
      (doseq [source-type ["MedicationAdministration" "MedicationStatement"]
              such-that [medication-reference-such-that
                         "(M.medication as Reference).reference = 'Medication/' + Med.id"
                         "'Medication/' + Med.id = M.medication.reference"]]
        (let [expr (c/optimize (medication-with-query
                                system source-type "[Medication: Code 'A10BF01' from atc]"
                                such-that "")
                               db)]

          (testing "eval"
            (is (= ["0"] (eval-ids expr db))))

          (testing "form"
            (let [{:keys [xform source]} (query-form expr)]
              (is (= (list 'matcher [["medication:Medication.code" (str atc "|A10BF01")]]) xform))
              (is (= (list 'retrieve source-type) source))))))

      (testing "no Medication has the code"
        (is (= [] (c/optimize (medication-with-query system "MedicationAdministration" "C01AA05")
                              db)))))))

(deftest compile-query-medication-with-concept-test
  (testing "a concept of two codes is optimized into one matcher clause with both codes"
    (with-system-data [system api-stub/mem-node-config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        (atc-medication "0" "A10BF01")
        (atc-medication "1" "B01AC06")
        (atc-medication "2" "C01AA05")
        (medication-use "MedicationAdministration" "0" "Medication/0")
        (medication-use "MedicationAdministration" "1" "Medication/1")
        (medication-use "MedicationAdministration" "2" "Medication/2")]]

      (with-db [db (:blaze.db/node system)]
        (let [expr (medication-with-query
                    system "MedicationAdministration" "[Medication: \"two-codes\"]"
                    medication-reference-such-that "")
              optimized-expr (c/optimize expr db)]

          (testing "eval"
            (is (= ["0" "1"] (eval-ids optimized-expr db)))

            (testing "returns the same as the original query"
              (is (= (eval-ids expr db) (eval-ids optimized-expr db)))))

          (testing "form"
            (is (= (list 'matcher [["medication:Medication.code"
                                    (str atc "|A10BF01") (str atc "|B01AC06")]])
                   (:xform (query-form optimized-expr))))))))))

(deftest compile-query-medication-with-value-set-test
  (with-system-data [system api-stub/mem-node-config]
    [[[:put {:fhir/type :fhir/CodeSystem :id "0"
             :url (type/uri atc)
             :content #fhir/code "complete"
             :concept
             [{:fhir/type :fhir.CodeSystem/concept
               :code #fhir/code "A10BF01"}
              {:fhir/type :fhir.CodeSystem/concept
               :code #fhir/code "B01AC06"}]}]
      [:put {:fhir/type :fhir/ValueSet :id "0"
             :url #fhir/uri "value-set-162344"
             :compose
             {:fhir/type :fhir.ValueSet/compose
              :include
              [{:fhir/type :fhir.ValueSet.compose/include
                :system (type/uri atc)
                :concept
                [{:fhir/type :fhir.ValueSet.compose.include/concept
                  :code #fhir/code "A10BF01"}]}]}}]]
     [[:put {:fhir/type :fhir/Patient :id "0"}]
      (atc-medication "0" "A10BF01")
      (atc-medication "1" "B01AC06")
      (medication-use "MedicationAdministration" "0" "Medication/0")
      (medication-use "MedicationAdministration" "1" "Medication/1")]]

    (with-db [db (:blaze.db/node system)]
      (let [expr (medication-with-query
                  system "MedicationAdministration" "[Medication: vs]"
                  medication-reference-such-that "")]

        (testing "isn't optimized"
          (is (= (c/form expr) (c/form (c/optimize expr db)))))

        (testing "eval"
          (is (= ["0"] (eval-ids (c/optimize expr db) db))))))))

(deftest compile-query-medication-with-reference-test
  (testing "the optimized query returns the same as the original query"
    (doseq [reference ["Medication/0" "Medication/1" "Medication/2"
                       "http://localhost/fhir/Medication/0" "#0"]]
      (with-system-data [system (assoc-in api-stub/mem-node-config
                                          [:blaze.db/node :enforce-referential-integrity]
                                          false)]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          (atc-medication "0" "A10BF01")
          (atc-medication "1" "B01AC06")
          (medication-use "MedicationAdministration" "0" reference)]]

        (with-db [db (:blaze.db/node system)]
          (let [expr (medication-with-query system "MedicationAdministration" "A10BF01")]
            (is (= (eval-ids expr db) (eval-ids (c/optimize expr db) db))
                reference))))))

  (testing "a deleted Medication doesn't match"
    (with-system-data [system (assoc-in api-stub/mem-node-config
                                        [:blaze.db/node :enforce-referential-integrity]
                                        false)]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        (atc-medication "0" "A10BF01")
        (atc-medication "1" "A10BF01")
        (medication-use "MedicationAdministration" "0" "Medication/0")]
       [[:delete "Medication" "0"]]]

      (with-db [db (:blaze.db/node system)]
        (let [expr (medication-with-query system "MedicationAdministration" "A10BF01")]
          (is (= [] (eval-ids (c/optimize expr db) db))))))))

(def ^:private cache-config
  (assoc
   api-stub/mem-node-config
   ::expr/cache
   {:node (ig/ref :blaze.db/node)
    :executor (ig/ref :blaze.test/manual-executor)}
   :blaze.test/manual-executor {}))

(deftest compile-query-medication-with-bloom-filter-test
  (testing "the optimized query gets no Bloom filter, because it reads Medications"
    (with-system-data [{::expr/keys [cache] executor :blaze.test/manual-executor
                        :as system} cache-config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        (atc-medication "0" "A10BF01")
        (medication-use "MedicationAdministration" "0" "Medication/0")]]

      (with-db [db (:blaze.db/node system)]
        (let [expr (c/optimize (medication-with-query system "MedicationAdministration" "A10BF01")
                               db)]
          (ec/get cache expr)
          (run-all! executor)

          (is (nil? (ec/get cache expr))))))))

(deftest compile-query-medication-with-not-optimized-test
  (with-system-data [system api-stub/mem-node-config]
    [[[:put {:fhir/type :fhir/Patient :id "0"}]
      (atc-medication "0" "A10BF01")
      (medication-use "MedicationAdministration" "0" "Medication/0")]]

    (with-db [db (:blaze.db/node system)]
      (testing "with an additional where clause"
        (let [expr (medication-with-query
                    system "MedicationAdministration"
                    "[Medication: Code 'A10BF01' from atc]"
                    medication-reference-such-that
                    "where M.status = 'completed'")]
          (is (= (c/form expr) (c/form (c/optimize expr db))))))

      (testing "with another relationship"
        (let [expr (medication-with-query
                    system "MedicationAdministration"
                    "[Medication: Code 'A10BF01' from atc]"
                    "M.id = Med.id" "")]
          (is (= (c/form expr) (c/form (c/optimize expr db)))))))))

(deftest compile-query-medication-with-anomaly-test
  (with-system-data [system api-stub/mem-node-config]
    [[[:put {:fhir/type :fhir/Patient :id "0"}]
      (atc-medication "0" "A10BF01")]]

    (with-db [db (:blaze.db/node system)]
      (let [expr (medication-with-query system "MedicationAdministration" "A10BF01")]
        (testing "keeps the query if the matcher can't be compiled"
          (with-redefs [d/compile-type-matcher
                        (fn [_ _ _] (ac/completed-future (ba/fault "msg-163521")))]
            (is (= (c/form expr) (c/form (c/optimize expr db))))))))))

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
