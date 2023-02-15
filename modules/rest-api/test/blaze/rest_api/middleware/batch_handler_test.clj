(ns blaze.rest-api.middleware.batch-handler-test
  (:require
    [blaze.rest-api.middleware.batch-handler :refer [wrap-batch-handler]]
    [blaze.test-util :as tu]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is]]))


(st/instrument)


(test/use-fixtures :each tu/fixture)


(deftest wrap-batch-handler-test
  (let [batch-handler-promise (promise)
        handler (wrap-batch-handler
                  (fn [{:keys [batch-handler]}]
                    (is (= ::batch-handler batch-handler)))
                  batch-handler-promise)]
    (deliver batch-handler-promise ::batch-handler)
    (handler {})))
