(ns blaze.middleware.fhir.metrics-test
  (:require
    [blaze.async.comp :as ac]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def interaction-name "interaction-name-112524")


(deftest wrap-observe-request-duration-test
  (testing "without interaction name"
    (is (= ::request
           @((wrap-observe-request-duration ac/completed-future) ::request))))

  (testing "with interaction name"
    (is (= {:fhir/interaction-name interaction-name}
           @((wrap-observe-request-duration
               #(ac/completed-future
                  (assoc % :fhir/interaction-name interaction-name)))
             {})))

    (testing "and request-method"
      (is (= {:fhir/interaction-name interaction-name}
             @((wrap-observe-request-duration
                 (fn [_]
                   (ac/completed-future
                     {:fhir/interaction-name interaction-name})))
               {:request-method :get})))

      (testing "and status"
        (is (= {:fhir/interaction-name interaction-name
                :status 200}
               @((wrap-observe-request-duration
                   (fn [_]
                     (ac/completed-future
                       {:fhir/interaction-name interaction-name
                        :status 200})))
                 {:request-method :get})))

        (testing "and request arrived"
          (is (= {:fhir/interaction-name interaction-name
                  :status 200}
                 @((wrap-observe-request-duration
                     (fn [_]
                       (ac/completed-future
                         {:fhir/interaction-name interaction-name
                          :status 200})))
                   {:request-method :get
                    :aleph/request-arrived (System/nanoTime)})))))))

  (testing "with interaction name as parameter"
    (is (= {:status 200}
           @((wrap-observe-request-duration
               (fn [_]
                 (ac/completed-future
                   {:status 200}))
               interaction-name)
             {:request-method :get
              :aleph/request-arrived (System/nanoTime)})))))
