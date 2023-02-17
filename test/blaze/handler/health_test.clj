(ns blaze.handler.health-test
  (:require
    [blaze.handler.health]
    [blaze.test-util :as tu :refer [with-system]]
    [blaze.test-util.ring :refer [call]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest]]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(test/use-fixtures :each tu/fixture)


(def system
  {:blaze.handler/health {}})


(deftest handler-test
  (with-system [{handler :blaze.handler/health} system]
    (given (call handler {})
      :status := 200
      :body := "OK")))
