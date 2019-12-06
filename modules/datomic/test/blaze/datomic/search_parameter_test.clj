(ns blaze.datomic.search-parameter-test
  (:require
    [blaze.datomic.search-parameter :as sp]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer [are deftest]]))


(st/instrument)


(def string-parameter
  {:search-parameter/type :search-parameter.type/string})


(deftest normalize-test
  (are [v n] (= n (sp/normalize string-parameter v))
    "Foo.Bar" "foo bar"
    "Foo.,Bar" "foo bar"
    "a\tb" "a b"
    "123" "123"))
