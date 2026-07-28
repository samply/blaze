(ns blaze.page-id-cipher.impl-test
  (:require
   [blaze.db.kv.mem]
   [blaze.db.search-param-registry]
   [blaze.db.search-param-registry-spec]
   [blaze.db.tx-cache]
   [blaze.db.tx-log.local]
   [blaze.page-id-cipher.impl :as impl]
   [blaze.page-id-cipher.impl-spec]
   [blaze.spec]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.datafy :as datafy]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [com.google.crypto.tink Aead]
   [java.util Base64]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest gen-new-key-set-handle-test
  (given (datafy/datafy (impl/gen-new-key-set-handle))
    count := 1
    [0 :primary] := true
    [0 :status] := :key.status/enabled))

(defn- rotate-keys-n [n]
  (nth (iterate impl/rotate-keys (impl/gen-new-key-set-handle)) n))

(defn- primary-key? [id]
  (fn [entry]
    (and (true? (:primary entry)) (= id (:id entry)))))

(defn- new-key? [id]
  (fn [entry]
    (and (false? (:primary entry)) (= id (:id entry)))))

(defn- old-key? [id]
  (fn [entry]
    (and (false? (:primary entry)) (= id (:id entry)))))

(deftest rotate-keys-test
  (testing "[primary-key] -> [primary-key new-key]"
    (given (datafy/datafy (rotate-keys-n 1))
      count := 2
      0 :? (primary-key? 0)
      1 :? (new-key? 1)))

  (testing "[primary-key new-key] -> [old-key primary-key]"
    (given (datafy/datafy (rotate-keys-n 2))
      count := 2
      0 :? (old-key? 0)
      1 :? (primary-key? 1)))

  (testing "[old-key primary-key] -> [old-key primary-key new-key]"
    (given (datafy/datafy (rotate-keys-n 3))
      count := 3
      0 :? (old-key? 0)
      1 :? (primary-key? 1)
      2 :? (new-key? 2)))

  (testing "[old-key primary-key new-key] -> [old-key old-key primary-key]"
    (given (datafy/datafy (rotate-keys-n 4))
      count := 3
      0 :? (old-key? 0)
      1 :? (old-key? 1)
      2 :? (primary-key? 2)))

  (testing "[old-key old-key primary-key] -> [old-key primary-key new-key]"
    (given (datafy/datafy (rotate-keys-n 5))
      count := 3
      0 :? (old-key? 1)
      1 :? (primary-key? 2)
      2 :? (new-key? 3)))

  (testing "[old-key primary-key new-key] -> [old-key old-key primary-key]"
    (given (datafy/datafy (rotate-keys-n 6))
      count := 3
      0 :? (old-key? 1)
      1 :? (old-key? 2)
      2 :? (primary-key? 3)))

  (testing "size after 3 rotations is always 3"
    (satisfies-prop 10
      (prop/for-all [n (gen/choose 3 10000)]
        (= 3 (impl/size (rotate-keys-n n)))))))

(defn- b64-encode [bytes]
  (.encodeToString (Base64/getEncoder) bytes))

(defn- b64-decode [s]
  (.decode (Base64/getDecoder) ^String s))

(def ^:private legacy-key-set
  "A key set of two keys, serialized by a Blaze version that still called
  `TinkProtoKeysetFormat/serializeKeyset` without a `Configuration`."
  (str "CAESTgpICjB0eXBlLmdvb2dsZWFwaXMuY29tL2dvb2dsZS5jcnlwdG8udGluay5BZXNHY21L"
       "ZXkSEhoQPDwKJ789lRMozURjmRshxhgBEAEgARJQCkgKMHR5cGUuZ29vZ2xlYXBpcy5jb20v"
       "Z29vZ2xlLmNyeXB0by50aW5rLkFlc0djbUtleRISGhAjQIKZLGPuEfwmBzuygniuGAEQARgB"
       "IAE="))

(deftest parse-key-set-test
  (testing "a key set written by an older Blaze version still parses"
    (given (datafy/datafy (impl/parse-key-set (b64-decode legacy-key-set)))
      count := 2
      0 :? (old-key? 0)
      1 :? (primary-key? 1))))

(defn- encrypt [handle plaintext associated-data]
  (.encrypt ^Aead (impl/get-aead handle) (.getBytes ^String plaintext)
            (.getBytes ^String associated-data)))

(defn- decrypt [handle ciphertext associated-data]
  (String. (.decrypt ^Aead (impl/get-aead handle) ciphertext
                     (.getBytes ^String associated-data))))

(deftest serialize-key-set-test
  (testing "round-trip"
    (let [handle (rotate-keys-n 3)
          round-tripped (impl/parse-key-set (impl/serialize-key-set handle))]

      (testing "preserves the key set entries"
        (given (datafy/datafy round-tripped)
          count := 3
          0 :? (old-key? 0)
          1 :? (primary-key? 1)
          2 :? (new-key? 2)))

      (testing "preserves the key material"
        (is (= "foo" (decrypt round-tripped (encrypt handle "foo" "bar") "bar"))))))

  (testing "the serialized form is unchanged compared to an older Blaze version"
    (is (= legacy-key-set
           (b64-encode (impl/serialize-key-set (impl/parse-key-set (b64-decode legacy-key-set))))))))
