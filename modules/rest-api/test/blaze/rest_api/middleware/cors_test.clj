(ns blaze.rest-api.middleware.cors-test
  (:require
    [blaze.async.comp :as ac]
    [blaze.rest-api.middleware.cors :refer [wrap-cors]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest]]
    [juxt.iota :refer [given]]
    [ring.util.response :as ring]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest wrap-cors-test
  (given @((wrap-cors (fn [_] (ac/completed-future (ring/response nil)))) nil)
    [:headers "Access-Control-Allow-Origin"] := "*"))
