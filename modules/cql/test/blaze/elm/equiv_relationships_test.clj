(ns blaze.elm.equiv-relationships-test
  (:require
    [blaze.elm.equiv-relationships
     :refer [split-by-first-equal-expression]]
    [blaze.elm.literals]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer [are deftest]]))


(st/instrument)


(deftest split-by-first-equal-expression-test
  (are [input equal-expr rest-expr]
    (let [res (split-by-first-equal-expression input)]
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
