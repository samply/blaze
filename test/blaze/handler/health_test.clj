(ns blaze.handler.health-test
  (:require
    [blaze.handler.health]
    [blaze.test-util :refer [with-system]]
    [blaze.test-util.ring :refer [call]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest]]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def system
  {:blaze.handler/health {}})


(deftest handler-test
  (with-system [{handler :blaze.handler/health} system]
    (given (call handler {})
      :status := 200
      :body := "OK")))
