(ns blaze.db.permissions-test
  (:require
   [blaze.db.permissions :as p]
   [blaze.db.permissions-spec]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest internal-test
  (is (s/valid? :blaze.db/permissions p/internal))

  (testing "internal code may write all AccessControl codes"
    (are [code] (p/may-write? p/internal code)
      "internal"
      "read-only")))

(deftest may-write-test
  (testing "absent permissions allow to write no AccessControl code"
    (are [code] (not (p/may-write? nil code))
      "internal"
      "read-only"))

  (testing "permissions without codes allow to write no AccessControl code"
    (are [permissions] (not (p/may-write? permissions "read-only"))
      {}
      {:write-access-control #{}}))

  (testing "permissions allow to write only the codes they contain"
    (let [permissions {:write-access-control #{"read-only"}}]
      (is (p/may-write? permissions "read-only"))
      (is (not (p/may-write? permissions "internal"))))))

(deftest decode-test
  (testing "turns the codes back into a set"
    (given (p/decode {:write-access-control ["internal" "read-only"]})
      count := 1
      :write-access-control := #{"internal" "read-only"}))

  (testing "permissions without codes stay unchanged"
    (given (p/decode {})
      count := 0))

  (testing "invalid data isn't coerced"
    (are [x] (= x (p/decode x))
      1
      "foo")

    (given (p/decode {:write-access-control "read-only"})
      :write-access-control := "read-only")))
