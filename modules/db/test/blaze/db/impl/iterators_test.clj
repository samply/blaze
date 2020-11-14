(ns blaze.db.impl.iterators-test
  (:require
    [blaze.db.bytes :as bytes]
    [blaze.db.impl.iterators :as i]
    [blaze.db.kv :as kv]
    [blaze.db.kv.mem :refer [new-mem-kv-store]]
    [blaze.db.kv.mem-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]])
  (:import
    [java.nio ByteBuffer]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn- new-mem-kv-store-with
  [entries]
  (let [kv-store (new-mem-kv-store)]
    (kv/put! kv-store entries)
    kv-store))


(defn- ba [& bytes]
  (byte-array bytes))


(defn- decode-1
  "Decode function with a ByteBuffer of size one."
  ([]
   (ByteBuffer/allocateDirect 1))
  ([kb]
   (let [bs (byte-array (.remaining kb))]
     (.get kb bs)
     (vec bs))))


(deftest keys-test
  (testing "normal read"
    (let [kv-store (new-mem-kv-store-with
                     [[(ba 0x00) bytes/empty]
                      [(ba 0x01) bytes/empty]])
          iter (kv/new-iterator (kv/new-snapshot kv-store))]
      (is (= [[0x00] [0x01]] (into [] (i/keys iter decode-1 (ba 0x00)))))))

  (testing "too small ByteBuffer will be replaced with a larger one"
    (let [kv-store (new-mem-kv-store-with
                     [[(ba 0x00) bytes/empty]
                      [(ba 0x00 0x01) bytes/empty]])
          iter (kv/new-iterator (kv/new-snapshot kv-store))]
      (is (= [[0x00] [0x00 0x01]] (into [] (i/keys iter decode-1 (ba 0x00))))))))
