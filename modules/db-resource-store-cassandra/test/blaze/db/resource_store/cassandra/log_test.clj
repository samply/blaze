(ns blaze.db.resource-store.cassandra.log-test
  (:require
    [blaze.db.resource-store.cassandra.log :as l]
    [clojure.test :as test :refer [deftest is testing]]))


(deftest init-msg-test
  (testing "minimal"
    (is (= "Open Cassandra resource store with the following settings: "
           (l/init-msg {}))))

  (testing "with contact points"
    (is (= "Open Cassandra resource store with the following settings: contact-points = localhost:9042"
           (l/init-msg {:contact-points "localhost:9042"}))))

  (testing "with password"
    (is (= "Open Cassandra resource store with the following settings: password = [hidden]"
           (l/init-msg {:password "foo"})))))
