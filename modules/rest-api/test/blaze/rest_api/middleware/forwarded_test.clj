(ns blaze.rest-api.middleware.forwarded-test
  (:require
    [blaze.fhir.test-util.ring :refer [call]]
    [blaze.rest-api.middleware.forwarded :refer [wrap-forwarded]]
    [blaze.test-util :as tu]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(test/use-fixtures :each tu/fixture)


(defn- handler [request respond _]
  (respond request))


(deftest wrap-forwarded-test
  (testing "no header"
    (given (call (wrap-forwarded handler "http://localhost:8080") {})
      :blaze/base-url := "http://localhost:8080"))

  (testing "X-Forwarded-Host header"
    (given (call (wrap-forwarded handler "http://localhost:8080")
                 {:headers {"x-forwarded-host" "blaze.de"}})
      :blaze/base-url := "http://blaze.de"))

  (testing "X-Forwarded-Host header"
    (given (call (wrap-forwarded handler "http://localhost:8080")
                 {:headers {"x-forwarded-host" "blaze.de"}})
      :blaze/base-url := "http://blaze.de"))

  (testing "X-Forwarded-Host header with port"
    (given (call (wrap-forwarded handler "http://localhost:8080")
                 {:headers {"x-forwarded-host" "localhost:8081"}})
      :blaze/base-url := "http://localhost:8081"))

  (testing "X-Forwarded-Host and X-Forwarded-Proto header"
    (given (call (wrap-forwarded handler "http://localhost:8080")
                 {:headers
                  {"x-forwarded-host" "blaze.de"
                   "x-forwarded-proto" "https"}})
      :blaze/base-url := "https://blaze.de"))

  (testing "Forwarded header"
    (testing "with host"
      (given (call (wrap-forwarded handler "http://localhost:8080")
                   {:headers {"forwarded" "host=blaze.de"}})
        :blaze/base-url := "http://blaze.de"))

    (testing "with host and port"
      (given (call (wrap-forwarded handler "http://localhost:8080")
                   {:headers {"forwarded" "host=localhost:8081"}})
        :blaze/base-url := "http://localhost:8081"))

    (testing "with host and proto"
      (testing "host first"
        (given (call (wrap-forwarded handler "http://localhost:8080")
                     {:headers {"forwarded" "host=blaze.de;proto=https"}})
          :blaze/base-url := "https://blaze.de"))

      (testing "proto first"
        (given (call (wrap-forwarded handler "http://localhost:8080")
                     {:headers {"forwarded" "proto=https;host=blaze.de"}})
          :blaze/base-url := "https://blaze.de"))

      (testing "extra for"
        (given (call (wrap-forwarded handler "http://localhost:8080")
                     {:headers {"forwarded" "for=127.0.0.1;host=blaze.de;proto=https"}})
          :blaze/base-url := "https://blaze.de")))))
