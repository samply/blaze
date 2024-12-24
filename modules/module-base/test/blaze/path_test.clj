(ns blaze.path-test
  (:require
   [blaze.path :refer [dir? path path?]]
   [blaze.path-spec]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest path-test
  (testing "existing directory"
    (is (path? (path "src")))
    (is (dir? (path "src"))))

  (testing "non-existing path"
    (is (not (dir? (path "foo"))))))

(deftest dir-spec-test
  (is (s/valid? :blaze/dir (path "src")))

  (are [x] (not (s/valid? :blaze/dir x))
    (path "foo")
    "foo"
    nil))
