(ns blaze.elm.equiv-relationships-test
  (:require
    [blaze.elm.equiv-relationships :as equiv-relationships]
    [blaze.elm.equiv-relationships-spec]
    [blaze.elm.literal :as elm]
    [blaze.elm.literal-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest split-by-first-equal-expression-test
  (are [input equal-expr rest-expr]
    (let [res (equiv-relationships/split-by-first-equal-expression input)]
      (and (= equal-expr (:equal-expr res)) (= rest-expr (:rest-expr res))))

    {:type "Equal"}
    {:type "Equal"}
    nil

    {:type "And"
     :operand
     [{:type "Equal"}
      {:type "Foo"}]}
    {:type "Equal"}
    {:type "Foo"}

    {:type "And"
     :operand
     [{:type "Foo"}
      {:type "Equal"}]}
    {:type "Equal"}
    {:type "Foo"}

    {:type "And"
     :operand
     [{:type "Foo"}
      {:type "Bar"}]}
    nil
    {:type "And"
     :operand
     [{:type "Foo"}
      {:type "Bar"}]}

    {:type "And"
     :operand
     [{:type "And"
       :operand
       [{:type "Foo"}
        {:type "Equal"}]}
      {:type "Bar"}]}
    {:type "Equal"}
    {:type "And"
     :operand
     [{:type "Foo"}
      {:type "Bar"}]}

    {:type "And"
     :operand
     [{:type "Equal" :pos 1}
      {:type "Equal" :pos 2}]}
    {:type "Equal" :pos 1}
    {:type "Equal" :pos 2}))


(def query
  {:type "Query"
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
      :operand [#elm/integer "1" #elm/integer "1"]}}]})


(def equiv-query
  {:type "Query"
   :source
   [{:expression
     {:type "Retrieve"
      :dataType "{http://hl7.org/fhir}Condition"}
     :alias "C"}]
   :relationship
   [{:type "WithEquiv"
     :alias "P"
     :expression
     {:type "Retrieve" :dataType "{http://hl7.org/fhir}Procedure"}
     :equivOperand
     [#elm/integer "1" #elm/integer "1"]}]})


(deftest find-equiv-rels-test
  (testing "query"
    (is (= (equiv-relationships/find-equiv-rels query) equiv-query)))

  (testing "20.4. Distinct"
    (is (= (equiv-relationships/find-equiv-rels (elm/distinct query))
           (elm/distinct equiv-query))))

  (testing "20.8. Exists"
    (is (= (equiv-relationships/find-equiv-rels (elm/exists query))
           (elm/exists equiv-query))))

  (testing "20.10. First"
    (is (= (equiv-relationships/find-equiv-rels (elm/first query))
           (elm/first equiv-query))))

  (testing "20.11. Flatten"
    (is (= (equiv-relationships/find-equiv-rels (elm/flatten query))
           (elm/flatten equiv-query))))

  (testing "20.25. SingletonFrom"
    (is (= (equiv-relationships/find-equiv-rels (elm/singleton-from query))
           (elm/singleton-from equiv-query)))))
