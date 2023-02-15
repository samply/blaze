(ns blaze.rest-api.middleware.cors-test
  (:require
    [blaze.rest-api.middleware.cors :refer [wrap-cors]]
    [blaze.test-util :as tu]
    [blaze.test-util.ring :refer [call]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest]]
    [juxt.iota :refer [given]]))


(st/instrument)


(test/use-fixtures :each tu/fixture)


(deftest wrap-cors-test
  (given (call (wrap-cors (fn [_ respond _] (respond nil))) nil)
    [:headers "Access-Control-Allow-Origin"] := "*"))
