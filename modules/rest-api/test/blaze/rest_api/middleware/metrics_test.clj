(ns blaze.rest-api.middleware.metrics-test
  (:require
   [blaze.rest-api.middleware.metrics :as metrics]
   [blaze.test-util :as tu]
   [blaze.util :as u]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [juxt.iota :refer [given]]
   [prometheus.alpha :as prom]
   [ring.util.response :as ring]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(def interaction-name "interaction-name-112524")

(defn mock-inc! [result]
  (fn [_collector status interaction request-method]
    (swap! result update :requests-total
           #(assoc %
                   :status status
                   :interaction interaction
                   :request-method request-method))))

(defn mock-observe! [result]
  (fn [_collector interaction request-method duration]
    (swap! result update :request-duration-seconds
           #(assoc %
                   :interaction interaction
                   :request-method request-method
                   :duration duration))))

(deftest wrap-observe-request-duration-test
  (testing "without request arrived"
    (let [result (atom nil)]
      (with-redefs [prom/inc! (mock-inc! result)]
        (((metrics/wrap-observe-request-duration-fn "interaction-180413")
          (fn [_request respond _raise]
            (respond (ring/response nil))))
         {:request-method :get}
         (partial swap! result assoc :response)
         (constantly nil)))

      (given @result
        [:response :status] := 200
        [:requests-total :status] := "200"
        [:requests-total :interaction] := "interaction-180413"
        [:requests-total :request-method] := "GET")))

  (testing "with request arrived"
    (let [result (atom nil)]
      (with-redefs [prom/inc! (mock-inc! result)
                    prom/observe! (mock-observe! result)
                    u/duration-s (fn [_start] 181116)]
        (((metrics/wrap-observe-request-duration-fn "interaction-180413")
          (fn [_request respond _raise]
            (respond (ring/response nil))))
         {:request-method :get :blaze/request-arrived 0}
         (partial swap! result assoc :response)
         (constantly nil)))

      (given @result
        [:response :status] := 200
        [:requests-total :status] := "200"
        [:requests-total :interaction] := "interaction-180413"
        [:requests-total :request-method] := "GET"
        [:request-duration-seconds :interaction] := "interaction-180413"
        [:request-duration-seconds :request-method] := "GET"
        [:request-duration-seconds :duration] := 181116))))
