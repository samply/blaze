(ns blaze.elm.code-test
  (:require
   [blaze.elm.code :as code]
   [blaze.elm.compiler :as c]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest to-code-test
  (testing "attach-cache"
    (let [code (code/to-code "foo" "bar" "baz")]
      (is (= [code] (st/with-instrument-disabled (c/attach-cache code ::cache)))))))
