(ns blaze.rest-api.middleware.batch-handler-test
  (:require
    [blaze.rest-api.middleware.batch-handler :refer [wrap-batch-handler]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is]]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest wrap-batch-handler-test
  (let [batch-handler-promise (promise)
        handler (wrap-batch-handler
                  (fn [{:keys [batch-handler]}]
                    (is (= ::batch-handler batch-handler)))
                  batch-handler-promise)]
    (deliver batch-handler-promise ::batch-handler)
    (handler {})))
