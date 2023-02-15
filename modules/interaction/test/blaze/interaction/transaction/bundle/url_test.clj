(ns blaze.interaction.transaction.bundle.url-test
  (:require
    [blaze.interaction.transaction.bundle.url :as url]
    [blaze.interaction.transaction.bundle.url-spec]
    [blaze.test-util :as tu]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]))


(st/instrument)


(test/use-fixtures :each tu/fixture)


(deftest match-url-test
  (testing "type-level"
    (is (nil? (url/match-url ""))))

  (testing "type-level"
    (is (= ["Patient"] (url/match-url "Patient"))))

  (testing "instance-level"
    (is (= ["Patient" "135825"] (url/match-url "Patient/135825")))))
