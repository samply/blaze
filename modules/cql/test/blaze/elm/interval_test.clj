(ns blaze.elm.interval-test
  (:require
   [blaze.elm.compiler.core :as core]
   [blaze.elm.interval :as interval]
   [blaze.elm.interval-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest expression-test
  (let [expr (interval/interval 1 2)]
    (is (true? (core/-static expr)))
    (is (nil? (core/-patient-count expr)))
    (is (= expr (core/-resolve-refs expr {})))
    (is (= expr (core/-resolve-params expr {})))
    (is (= expr (core/-optimize expr ::db)))
    (is (= expr (core/-eval expr {} nil nil)))
    (is (= '(interval 1 2) (core/-form expr)))))
