(ns blaze.db.tx-log.kafka.config-test
  (:require
    [blaze.db.tx-log.kafka.config :as c]
    [blaze.fhir.hash-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [juxt.iota :refer [given]]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest producer-config-test
  (testing "bootstrap servers"
    (given (c/producer-config {:bootstrap-servers "server-153920"})
      "bootstrap.servers" := "server-153920"))

  (testing "max request size"
    (given (c/producer-config {:max-request-size 1024})
      "max.request.size" := "1024"))

  (testing "security protocol SSL"
    (given (c/producer-config {:security-protocol "SSL"})
      "security.protocol" := "SSL"))

  (testing "SSL truststore location"
    (given (c/producer-config {:ssl-truststore-location "file-195850"})
      "ssl.truststore.location" := "file-195850")))


(deftest consumer-config-test
  (testing "bootstrap servers"
    (given (c/consumer-config {:bootstrap-servers "server-195134"})
      "bootstrap.servers" := "server-195134"))

  (testing "security protocol SSL"
    (given (c/consumer-config {:security-protocol "SSL"})
      "security.protocol" := "SSL"))

  (testing "SSL truststore location"
    (given (c/consumer-config {:ssl-truststore-location "file-195850"})
      "ssl.truststore.location" := "file-195850")))
