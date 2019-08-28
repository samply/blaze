(ns blaze.deferred-test
  (:require
    [blaze.deferred :as bd]
    [clojure.test :refer :all]
    [manifold.deferred :as md]))


(deftest map-test
  (is (= [1 2] @(transduce (bd/map inc) conj [(md/success-deferred 0) (md/success-deferred 1)]))))
