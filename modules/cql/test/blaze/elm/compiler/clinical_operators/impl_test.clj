(ns blaze.elm.compiler.clinical-operators.impl-test
  (:require
   [blaze.elm.code :as code]
   [blaze.elm.compiler.clinical-operators.impl :as impl]
   [blaze.elm.value-set :as value-set]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest contains-any-test
  (testing "no code"
    (is (false? (impl/contains-any? #{} nil))))

  (testing "one code"
    (let [code (code/code "system-125341" "version-131455" "code-131458")]
      (are [value-set pred] (pred (impl/contains-any? value-set [code]))
        #{(value-set/->Code "system-125341" "code-131337")} false?
        #{(value-set/->Code "system-125341" "code-131458")} true?)))

  (testing "two codes"
    (let [code-1 (code/code "system-125341" "version-131455" "code-131458")
          code-2 (code/code "system-131726" "version-131455" "code-131653")]
      (are [value-set pred] (pred (impl/contains-any? value-set [code-1 code-2]))
        #{(value-set/->Code "system-125341" "code-131337")} false?
        #{(value-set/->Code "system-125341" "code-131458")} true?
        #{(value-set/->Code "system-131726" "code-131653")} true?))))
