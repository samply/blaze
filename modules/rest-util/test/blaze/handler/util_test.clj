(ns blaze.handler.util-test
  (:require
    [blaze.async-comp-spec]
    [blaze.handler.util :refer [preference]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is]]))


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest preference-test
  (is (= "representation"
         (preference {"prefer" "return=representation"} "return"))))
