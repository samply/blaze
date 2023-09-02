(ns blaze.handler.health-test
  (:require
    [blaze.handler.health]
    [blaze.module.test-util :refer [with-system]]
    [blaze.module.test-util.ring :refer [call]]
    [blaze.test-util :as tu]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest]]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(test/use-fixtures :each tu/fixture)


(def config
  {:blaze.handler/health {}})


(deftest handler-test
  (with-system [{handler :blaze.handler/health} config]
    (given (call handler {})
      :status := 200
      :body := "OK")))
