(ns blaze.db.tx-log.kafka.codec-test
  (:require
   [blaze.db.permissions.spec]
   [blaze.db.tx-log.kafka.codec :as codec]
   [blaze.db.tx-log.kafka.codec-spec]
   [blaze.db.tx-log.spec]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [clojure.test.check.properties :as prop]
   [juxt.iota :refer [given]]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn- serialize [tx-cmds]
  (.serialize codec/serializer "topic-093717" tx-cmds))

(defn- deserialize [data]
  (.deserialize codec/deserializer "topic-094321" data))

(deftest round-trip-test
  (satisfies-prop 100
    (prop/for-all [tx-cmds (s/gen :blaze.db/tx-cmds)]
      (= tx-cmds (deserialize (serialize tx-cmds))))))

(defn- invalid-cbor-content
  "`0xA1` is the start of a map with one entry."
  []
  (byte-array [0xA1]))

(deftest deserializer-test
  (testing "empty value"
    (is (nil? (deserialize (byte-array 0)))))

  (testing "invalid cbor value"
    (is (nil? (deserialize (invalid-cbor-content)))))

  (testing "invalid map value"
    (is (nil? (deserialize (serialize {:a 1}))))))

(deftest permissions-round-trip-test
  (satisfies-prop 100
    (prop/for-all [permissions (s/gen :blaze.db/permissions)]
      (= permissions (codec/decode-permissions
                      (codec/encode-permissions permissions))))))

(deftest decode-permissions-test
  (testing "the codes are a set"
    (given (codec/decode-permissions
            (codec/encode-permissions
             {:write-access-control #{"internal" "read-only"}}))
      count := 1
      :write-access-control := #{"internal" "read-only"}))

  (testing "invalid cbor value"
    (is (nil? (codec/decode-permissions (invalid-cbor-content))))))
