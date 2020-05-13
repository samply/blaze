(ns blaze.elm.util-test
  (:require
    [blaze.elm.util :as elm-util]
    [blaze.elm.util-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer [are deftest is testing]]))


(st/instrument)


(deftest parse-qualified-name
  (testing "nil"
    (is (nil? (elm-util/parse-qualified-name nil))))

  (testing "empty string"
    (is (nil? (elm-util/parse-qualified-name ""))))

  (testing "invalid string"
    (are [s] (nil? (elm-util/parse-qualified-name s))
      "a"
      "aa"))

  (testing "valid string"
    (are [s ns name] (= [ns name] (elm-util/parse-qualified-name s))
      "{a}b" "a" "b")))
