(ns blaze.elm.spec-test
  (:require
    [blaze.elm.literal]
    [blaze.elm.literal-spec]
    [blaze.elm.spec]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest testing]]
    [juxt.iota :refer [given]]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest literal-test
  (testing "valid"
    (are [x] (s/valid? :elm/expression x)
      #elm/boolean "true"))

  (testing "invalid"
    (given (s/explain-data :elm/expression {:type "Literal"})
      [::s/problems 0 :path 0] := :elm.spec.type/literal)))


(deftest literal-integer-test
  (testing "valid"
    (are [x] (s/valid? :elm/integer x)
      #elm/integer "0"))

  (testing "invalid"
    (given (s/explain-data :elm/integer nil)
      [::s/problems 0 :pred] := `map?)

    (given (s/explain-data :elm/integer {:type "Literal"})
      [::s/problems 0 :pred] := `(fn [~'%] (contains? ~'% :valueType)))

    (given (s/explain-data :elm/integer #elm/boolean "true")
      [::s/problems 0 :path 0] := :valueType)))


(deftest literal-decimal-test
  (testing "valid"
    (are [x] (s/valid? :elm/decimal x)
      #elm/decimal "0"))

  (testing "invalid"
    (given (s/explain-data :elm/decimal nil)
      [::s/problems 0 :pred] := `map?)

    (given (s/explain-data :elm/decimal {:type "Literal"})
      [::s/problems 0 :pred] := `(fn [~'%] (contains? ~'% :valueType)))

    (given (s/explain-data :elm/decimal #elm/boolean "true")
      [::s/problems 0 :path 0] := :valueType)))


(deftest tuple-test
  (testing "valid"
    (are [x] (s/valid? :elm/expression x)
      #elm/tuple{}
      #elm/tuple{"id" #elm/integer "0"}))

  (testing "invalid"
    (given (s/explain-data :elm/expression {:type "Tuple" :element "foo"})
      [::s/problems 0 :pred] := `coll?)

    (given (s/explain-data :elm/expression {:type "Tuple" :element ["foo"]})
      [::s/problems 0 :pred] := `map?)

    (given (s/explain-data :elm/expression {:type "Tuple" :element [{}]})
      [::s/problems 0 :pred] := `(fn [~'%] (contains? ~'% :name)))

    (given (s/explain-data :elm/expression {:type "Tuple" :element [{:name "foo"}]})
      [::s/problems 0 :pred] := `(fn [~'%] (contains? ~'% :value)))))


(deftest instance-test
  (testing "valid"
    (are [x] (s/valid? :elm/expression x)
      #elm/instance ["{urn:hl7-org:elm-types:r1}Code"
                    {"system" #elm/string "foo" "code" #elm/string "bar"}]))

  (testing "invalid"
    (given (s/explain-data :elm/expression {:type "Instance"})
      [::s/problems 0 :pred] := `(fn [~'%] (contains? ~'% :classType)))

    (given (s/explain-data :elm/expression {:type "Instance" :classType "foo"
                                            :element "foo"})
      [::s/problems 0 :pred] := `coll?)

    (given (s/explain-data :elm/expression {:type "Instance" :classType "foo"
                                            :element ["foo"]})
      [::s/problems 0 :pred] := `map?)

    (given (s/explain-data :elm/expression {:type "Instance" :classType "foo"
                                            :element [{}]})
      [::s/problems 0 :pred] := `(fn [~'%] (contains? ~'% :name)))

    (given (s/explain-data :elm/expression {:type "Instance" :classType "foo"
                                            :element [{:name "foo"}]})
      [::s/problems 0 :pred] := `(fn [~'%] (contains? ~'% :value)))))


(deftest query-test
  (testing "valid"
    (are [x] (s/valid? :elm/expression x)
      {:type "Query"
       :source [{:alias "foo" :expression #elm/integer "0"}]}))

  (testing "invalid"
    (given (s/explain-data :elm/expression {:type "Query"})
      [::s/problems 0 :pred] := `(fn [~'%] (contains? ~'% :source)))

    (given (s/explain-data :elm/expression {:type "Query" :source "foo"})
      [::s/problems 0 :pred] := `coll?)

    (given (s/explain-data :elm/expression {:type "Query" :source ["foo"]})
      [::s/problems 0 :pred] := `map?)

    (given (s/explain-data :elm/expression {:type "Query" :source [{}]})
      [::s/problems 0 :pred] := `(fn [~'%] (contains? ~'% :expression)))

    (given (s/explain-data :elm/expression {:type "Query"
                                            :source
                                            [{:expression #elm/integer "0"}]})
      [::s/problems 0 :pred] := `(fn [~'%] (contains? ~'% :alias)))

    (given (s/explain-data :elm/expression {:type "Query"
                                            :source
                                            [{:expression #elm/integer "0"
                                              :alias "foo"}]
                                            :sort "foo"})
      [::s/problems 0 :pred] := `map?)

    (given (s/explain-data :elm/expression {:type "Query"
                                            :source
                                            [{:expression #elm/integer "0"
                                              :alias "foo"}]
                                            :sort {}})
      [::s/problems 0 :pred] := `(fn [~'%] (contains? ~'% :by)))

    (given (s/explain-data :elm/expression {:type "Query"
                                            :source
                                            [{:expression #elm/integer "0"
                                              :alias "foo"}]
                                            :sort {:by "foo"}})
      [::s/problems 0 :pred] := `coll?)

    (given (s/explain-data :elm/expression {:type "Query"
                                            :source
                                            [{:expression #elm/integer "0"
                                              :alias "foo"}]
                                            :sort {:by ["foo"]}})
      [::s/problems 0 :pred] := `blaze.elm.spec/sort-by-item)

    (given (s/explain-data :elm/expression {:type "Query"
                                            :source
                                            [{:expression #elm/integer "0"
                                              :alias "foo"}]
                                            :sort {:by [{:type "ByDirection"}]}})
      [::s/problems 0 :pred] := `(fn [~'%] (contains? ~'% :direction)))

    (given (s/explain-data :elm/expression {:type "Query"
                                            :source
                                            [{:expression #elm/integer "0"
                                              :alias "foo"}]
                                            :sort {:by [{:type "ByColumn"}]}})
      [::s/problems 0 :pred] := `(fn [~'%] (contains? ~'% :direction)))

    (given (s/explain-data :elm/expression {:type "Query"
                                            :source
                                            [{:expression #elm/integer "0"
                                              :alias "foo"}]
                                            :sort
                                            {:by
                                             [{:type "ByColumn"
                                               :direction "foo"}]}})
      [::s/problems 0 :pred] := `(fn [~'%] (contains? ~'% :path))
      [::s/problems 1 :pred] := `#{"descending" "ascending" "desc" "asc"})

    (given (s/explain-data :elm/expression {:type "Query"
                                            :source
                                            [{:expression #elm/integer "0"
                                              :alias "foo"}]
                                            :sort
                                            {:by
                                             [{:type "ByExpression"
                                               :direction "foo"}]}})
      [::s/problems 0 :pred] := `(fn [~'%] (contains? ~'% :expression))
      [::s/problems 1 :pred] := `#{"descending" "ascending" "desc" "asc"})))
