(ns blaze.db.impl.iterators-test
  (:require
    [blaze.byte-string :as bs]
    [blaze.db.bytes :as bytes]
    [blaze.db.impl.byte-buffer :as bb]
    [blaze.db.impl.iterators :as i]
    [blaze.db.kv :as kv]
    [blaze.db.kv.mem]
    [blaze.db.kv.mem-spec]
    [blaze.test-util :refer [with-system]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def system
  {::kv/mem {:column-families {}}})


(defn- ba [& bytes]
  (byte-array bytes))


(defn- decode-1
  "Decode function with a ByteBuffer of size one."
  ([]
   (bb/allocate-direct 1))
  ([kb]
   (let [bs (byte-array (.remaining kb))]
     (.get kb bs)
     (vec bs))))


(deftest keys-test
  (testing "normal read"
    (with-system [{kv-store ::kv/mem} system]
      (kv/put!
        kv-store
        [[(ba 0x00) bytes/empty]
         [(ba 0x01) bytes/empty]])

      (let [iter (kv/new-iterator (kv/new-snapshot kv-store))]
        (is (= [[0x00] [0x01]]
               (into [] (i/keys! iter decode-1 (bs/from-hex "00"))))))))

  (testing "too small ByteBuffer will be replaced with a larger one"
    (with-system [{kv-store ::kv/mem} system]
      (kv/put!
        kv-store
        [[(ba 0x00) bytes/empty]
         [(ba 0x00 0x01) bytes/empty]])

      (let [iter (kv/new-iterator (kv/new-snapshot kv-store))]
        (is (= [[0x00] [0x00 0x01]]
               (into [] (i/keys! iter decode-1 (bs/from-hex "00")))))))))
