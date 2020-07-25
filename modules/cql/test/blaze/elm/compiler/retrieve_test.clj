(ns blaze.elm.compiler.retrieve-test
  (:require
    [blaze.db.api :as d]
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.elm.code :refer [to-code]]
    [blaze.elm.compiler.protocols :refer [-eval]]
    [blaze.elm.compiler.retrieve :refer [expr]]
    [blaze.elm.compiler.retrieve-spec]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [juxt.iota :refer [given]]))


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


#_(deftest related-context-expr-test-1
    (testing "without codes"
      (datomic-test-util/stub-entity-type
        ::related-resource ::related-resource-type)
      (stub-context-expr
        ::db ::related-resource-type ::result-type
        (reify Expression
          (-eval [_ _ resource _]
            (is (= ::related-resource resource))
            ::result)))

      (let [related-context-expr
            (reify Expression
              (-eval [_ _ resource _]
                (is (= ::initial-resource resource))
                ::related-resource))
            expr (with-related-context-expr
                   related-context-expr ::result-type nil nil)]
        (is (= ::result (-eval expr {:db ::db} ::initial-resource nil))))))


#_(deftest related-context-expr-test-2
    (st/unstrument `with-related-context-expr)

    (testing "with one code"
      (datomic-test-util/stub-entity-type
        ::related-resource ::related-resource-type)
      (stub-single-code-expr
        ::db ::related-resource-type ::result-type ::code-property ::code
        (reify Expression
          (-eval [_ _ resource _]
            (is (= ::related-resource resource))
            ::result)))

      (let [related-context-expr
            (reify Expression
              (-eval [_ _ resource _]
                (is (= ::initial-resource resource))
                ::related-resource))
            expr (with-related-context-expr
                   related-context-expr ::result-type ::code-property [::code])]
        (is (= ::result (-eval expr {:db ::db} ::initial-resource nil))))))


#_(deftest related-context-expr-test-3
    (st/unstrument `with-related-context-expr)

    (testing "with two codes"
      (datomic-test-util/stub-entity-type
        ::related-resource ::related-resource-type)
      (stub-multiple-codes-expr
        ::db ::related-resource-type ::result-type ::code-property
        [::code-1 :code-2]
        (reify Expression
          (-eval [_ _ resource _]
            (is (= ::related-resource resource))
            ::result)))

      (let [related-context-expr
            (reify Expression
              (-eval [_ _ resource _]
                (is (= ::initial-resource resource))
                ::related-resource))
            expr (with-related-context-expr
                   related-context-expr ::result-type ::code-property
                   [::code-1 :code-2])]
        (is (= ::result (-eval expr {:db ::db} ::initial-resource nil))))))


#_(deftest related-context-expr-test-4
    (st/unstrument `with-related-context-expr)

    (testing "with one nil code"
      (let [expr (with-related-context-expr
                   ::related-context-expr ::result-type ::code-property [nil])]
        (is (= [] (-eval expr {:db ::db} ::initial-resource nil))))))


(deftest expr-test
  (testing "in non-Unspecified eval context"
    (testing "without codes"
      (testing "Patient in Patient context"
        (with-open [node (mem-node-with
                           [[[:put {:resourceType "Patient" :id "0"}]]])]
          (given
            (-eval
              (expr node "Patient" "Patient" "foo" [])
              {:db (d/db node)}
              {:resourceType "Patient" :id "0"}
              nil)
            [0 :resourceType] := "Patient"
            [0 :id] := "0")))

      (testing "Observation in Patient context"
        (with-open [node (mem-node-with
                           [[[:put {:resourceType "Patient" :id "0"}]
                             [:put {:resourceType "Observation" :id "1"
                                    :subject {:reference "Patient/0"}}]]])]
          (given
            (-eval
              (expr node "Patient" "Observation" "foo" [])
              {:db (d/db node)}
              {:resourceType "Patient" :id "0"}
              nil)
            [0 :resourceType] := "Observation"
            [0 :id] := "1"))))

    (testing "with one code"
      (testing "Observation in Patient context"
        (with-open [node (mem-node-with
                           [[[:put {:resourceType "Patient" :id "0"}]
                             [:put {:resourceType "Observation" :id "1"
                                    :code {:coding [{:system "s1" :code "c1"}]}
                                    :subject {:reference "Patient/0"}}]]])]
          (given
            (-eval
              (expr node "Patient" "Observation" "code" [(to-code "s1" "v1" "c1")])
              {:db (d/db node)}
              {:resourceType "Patient" :id "0"}
              nil)
            [0 :resourceType] := "Observation"
            [0 :id] := "1"))))

    (testing "with two codes"
      (testing "Observation in Patient context"
        (with-open [node (mem-node-with
                           [[[:put {:resourceType "Patient" :id "0"}]
                             [:put {:resourceType "Observation" :id "1"
                                    :code {:coding [{:system "s1" :code "c1"}]}
                                    :subject {:reference "Patient/0"}}]]])]
          (given
            (-eval
              (expr node "Patient" "Observation" "code" [(to-code "s1" "v1" "c1")
                                                         (to-code "s2" "v2" "c2")])
              {:db (d/db node)}
              {:resourceType "Patient" :id "0"}
              nil)
            [0 :resourceType] := "Observation"
            [0 :id] := "1"))))))


(defn stub-expr
  [node eval-context data-type code-property-name codes-spec res]
  (st/instrument
    [`expr]
    {:spec
     {`expr
      (s/fspec
        :args (s/cat :node #{node}
                     :eval-context #{eval-context}
                     :data-type #{data-type}
                     :code-property-name #{code-property-name}
                     :codes codes-spec)
        :ret #{res})}
     :stub
     #{`expr}}))
