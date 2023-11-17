(ns blaze.rest-api.middleware.cors-test
  (:require
   [blaze.module.test-util.ring :refer [call]]
   [blaze.rest-api.middleware.cors :refer [wrap-cors]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest]]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest wrap-cors-test
  (given (call (wrap-cors (fn [_ respond _] (respond nil))) nil)
    [:headers "Access-Control-Allow-Origin"] := "*"))
