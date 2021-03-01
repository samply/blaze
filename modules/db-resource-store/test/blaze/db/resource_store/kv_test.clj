(ns blaze.db.resource-store.kv-test
  (:require
    [blaze.byte-string :as bs]
    [blaze.db.kv :as kv]
    [blaze.db.kv.mem :refer [new-mem-kv-store]]
    [blaze.db.kv.mem-spec]
    [blaze.db.resource-store :as rs]
    [blaze.db.resource-store-spec]
    [blaze.db.resource-store.kv :refer [new-kv-resource-store]]
    [blaze.db.resource-store.kv-spec]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.hash-spec]
    [blaze.fhir.spec :as fhir-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cuerdas.core :as str]
    [taoensso.timbre :as log])
  (:refer-clojure :exclude [hash]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (log/with-level :trace (f))
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn hash [s]
  (assert (= 1 (count s)))
  (bs/from-hex (str/repeat s 64)))


(defn invalid-content
  "`0xA1` is the start of a map with one entry."
  []
  (byte-array [0xA1]))


(defn encode-resource [resource]
  (fhir-spec/unform-cbor resource))


(deftest get-test
  (testing "success"
    (let [content {:fhir/type :fhir/Patient :id "0"}
          hash (hash/generate content)
          kv-store (new-mem-kv-store)
          store (new-kv-resource-store kv-store)]
      (kv/put! kv-store (bs/to-byte-array hash) (fhir-spec/unform-cbor content))

      (is (= content @(rs/get store hash)))))

  (testing "parsing error"
    (let [hash (hash "0")
          kv-store (new-mem-kv-store)
          store (new-kv-resource-store kv-store)]
      (kv/put! kv-store (bs/to-byte-array hash) (invalid-content))

      (try
        @(rs/get store hash)
        (catch Exception e
          (is (str/starts-with? (ex-message (ex-cause e))
                                "Error while parsing resource content"))))))

  (testing "not-found"
    (let [hash (hash "0")
          kv-store (new-mem-kv-store)
          store (new-kv-resource-store kv-store)]

      (is (nil? @(rs/get store hash)))))

  (testing "error"
    (let [hash (hash "0")
          kv-store
          (reify kv/KvStore
            (-get [_ _]
              (throw (Exception. "msg-154312"))))
          store (new-kv-resource-store kv-store)]

      (try
        @(rs/get store hash)
        (catch Exception e
          (is (= "msg-154312" (ex-message (ex-cause e)))))))))


(deftest multi-get-test
  (testing "success with one hash"
    (let [content {:fhir/type :fhir/Patient :id "0"}
          hash (hash/generate content)
          kv-store (new-mem-kv-store)
          store (new-kv-resource-store kv-store)]
      (kv/put! kv-store (bs/to-byte-array hash) (fhir-spec/unform-cbor content))

      (is (= {hash content} @(rs/multi-get store [hash])))))

  (testing "success with two hashes"
    (let [content-0 {:fhir/type :fhir/Patient :id "0"}
          hash-0 (hash/generate content-0)
          content-1 {:fhir/type :fhir/Patient :id "1"}
          hash-1 (hash/generate content-1)
          kv-store (new-mem-kv-store)
          store (new-kv-resource-store kv-store)]
      (kv/put! kv-store (bs/to-byte-array hash-0) (fhir-spec/unform-cbor content-0))
      (kv/put! kv-store (bs/to-byte-array hash-1) (fhir-spec/unform-cbor content-1))

      (is (= {hash-0 content-0 hash-1 content-1}
             @(rs/multi-get store [hash-0 hash-1])))))

  (testing "parsing error"
    (let [hash (hash "0")
          kv-store (new-mem-kv-store)
          store (new-kv-resource-store kv-store)]
      (kv/put! kv-store (bs/to-byte-array hash) (invalid-content))

      (try
        @(rs/multi-get store [hash])
        (catch Exception e
          (is (str/starts-with? (ex-message (ex-cause e))
                                "Error while parsing resource content"))))))

  (testing "not-found"
    (let [hash (hash "0")
          kv-store (new-mem-kv-store)
          store (new-kv-resource-store kv-store)]

      (is (= {} @(rs/multi-get store [hash])))))

  (testing "error"
    (let [hash (hash "0")
          kv-store
          (reify kv/KvStore
            (-multi-get [_ _]
              (throw (Exception. "msg-154826"))))
          store (new-kv-resource-store kv-store)]

      (try
        @(rs/multi-get store [hash])
        (catch Exception e
          (is (= "msg-154826" (ex-message (ex-cause e)))))))))


(deftest put-test
  (let [content {:fhir/type :fhir/Patient :id "0"}
        hash (hash/generate content)
        kv-store (new-mem-kv-store)
        store (new-kv-resource-store kv-store)]
    (rs/put store {hash content})

    (is (= content @(rs/get store hash)))))
