(ns blaze.rest-api.middleware.cors-test
  (:require
    [blaze.rest-api.middleware.cors :refer [wrap-cors]]
    [blaze.test-util.ring :refer [call]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest]]
    [juxt.iota :refer [given]]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest wrap-cors-test
  (given (call (wrap-cors (fn [_ respond _] (respond nil))) nil)
    [:headers "Access-Control-Allow-Origin"] := "*"))
