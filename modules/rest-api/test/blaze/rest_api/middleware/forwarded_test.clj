(ns blaze.rest-api.middleware.forwarded-test
  (:require
    [blaze.rest-api.middleware.forwarded :refer [wrap-forwarded]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest wrap-forwarded-test
  (testing "no header"
    (given ((wrap-forwarded identity "http://localhost:8080") {})
      :blaze/base-url := "http://localhost:8080"))

  (testing "X-Forwarded-Host header"
    (given ((wrap-forwarded identity "http://localhost:8080")
            {:headers {"x-forwarded-host" "blaze.de"}})
      :blaze/base-url := "http://blaze.de"))

  (testing "X-Forwarded-Host header"
    (given ((wrap-forwarded identity "http://localhost:8080")
            {:headers {"x-forwarded-host" "blaze.de"}})
      :blaze/base-url := "http://blaze.de"))

  (testing "X-Forwarded-Host header with port"
    (given ((wrap-forwarded identity "http://localhost:8080")
            {:headers {"x-forwarded-host" "localhost:8081"}})
      :blaze/base-url := "http://localhost:8081"))

  (testing "X-Forwarded-Host and X-Forwarded-Proto header"
    (given ((wrap-forwarded identity "http://localhost:8080")
            {:headers
             {"x-forwarded-host" "blaze.de"
              "x-forwarded-proto" "https"}})
      :blaze/base-url := "https://blaze.de"))

  (testing "Forwarded header"
    (testing "with host"
      (given ((wrap-forwarded identity "http://localhost:8080")
              {:headers {"forwarded" "host=blaze.de"}})
        :blaze/base-url := "http://blaze.de"))

    (testing "with host and port"
      (given ((wrap-forwarded identity "http://localhost:8080")
              {:headers {"forwarded" "host=localhost:8081"}})
        :blaze/base-url := "http://localhost:8081"))

    (testing "with host and proto"
      (testing "host first"
        (given ((wrap-forwarded identity "http://localhost:8080")
                {:headers {"forwarded" "host=blaze.de;proto=https"}})
          :blaze/base-url := "https://blaze.de"))

      (testing "proto first"
        (given ((wrap-forwarded identity "http://localhost:8080")
                {:headers {"forwarded" "proto=https;host=blaze.de"}})
          :blaze/base-url := "https://blaze.de"))

      (testing "extra for"
        (given ((wrap-forwarded identity "http://localhost:8080")
                {:headers {"forwarded" "for=127.0.0.1;host=blaze.de;proto=https"}})
          :blaze/base-url := "https://blaze.de")))))
