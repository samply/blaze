(ns blaze.middleware.fhir.metrics-test
  (:require
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer [deftest is]]))


(st/instrument)


(deftest wrap-observe-request-duration-test
  (is (fn? (wrap-observe-request-duration identity))))
