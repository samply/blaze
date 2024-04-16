(ns blaze.db.tx-log.kafka.log-test
  (:require
   [blaze.db.tx-log :as tx-log]
   [blaze.db.tx-log.kafka.log :as l]
   [clojure.test :refer [deftest is testing]]))

(deftest init-msg-test
  (testing "minimal"
    (is (= "Open Kafka transaction log with the following settings: bootstrap-servers = localhost:9092"
           (l/init-msg nil {:bootstrap-servers "localhost:9092"}))))

  (testing "with security protocol"
    (is (= "Open Kafka transaction log with the following settings: bootstrap-servers = localhost:9092, security-protocol = SSL"
           (l/init-msg nil {:bootstrap-servers "localhost:9092" :security-protocol "SSL"}))))

  (testing "with password"
    (is (= "Open Kafka transaction log with the following settings: bootstrap-servers = localhost:9092, key-password = [hidden]"
           (l/init-msg nil {:bootstrap-servers "localhost:9092" :key-password "foo"}))))

  (testing "non default key"
    (is (= "Open admin Kafka transaction log with the following settings: bootstrap-servers = localhost:9092"
           (l/init-msg [::tx-log/kafka :blaze.db.admin/tx-log] {:bootstrap-servers "localhost:9092"})))))
