(ns blaze.db.node.validation-test
  (:require
    [blaze.db.node.validation :as validation]
    [blaze.db.node.validation-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is]]))


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest validate-ops
  (is (nil? (validation/validate-ops []))))
