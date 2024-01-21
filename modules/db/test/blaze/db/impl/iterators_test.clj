(ns blaze.db.impl.iterators-test
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.db.impl.bytes :as bytes]
   [blaze.db.impl.iterators :as i]
   [blaze.db.kv :as kv]
   [blaze.db.kv.mem]
   [blaze.db.kv.mem-spec]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def config
  {::kv/mem {:column-families {}}})

(defn- ba [& bytes]
  (byte-array bytes))

(defn- decode-1
  "Decode function with a ByteBuffer of size one."
  [kb]
  (let [len (bb/remaining kb)
        bs (byte-array len)]
    (bb/copy-into-byte-array! kb bs 0 len)
    (vec bs)))

(deftest keys-test
  (testing "normal read"
    (with-system [{kv-store ::kv/mem} config]
      (kv/put!
       kv-store
       [[:default (ba 0x00) bytes/empty]
        [:default (ba 0x01) bytes/empty]])

      (with-open [snapshot (kv/new-snapshot kv-store)
                  iter (kv/new-iterator snapshot :default)]
        (is (= [[0x00] [0x01]]
               (vec (i/keys! iter decode-1 (bs/from-hex "00"))))))))

  (testing "too small ByteBuffer will be replaced with a larger one"
    (with-system [{kv-store ::kv/mem} config]
      (kv/put!
       kv-store
       [[:default (ba 0x00) bytes/empty]
        [:default (ba 0x00 0x01) bytes/empty]])

      (with-open [snapshot (kv/new-snapshot kv-store)
                  iter (kv/new-iterator snapshot :default)]
        (is (= [[0x00] [0x00 0x01]]
               (vec (i/keys! iter decode-1 (bs/from-hex "00")))))))

    (testing "new ByteBuffer is bigger than a two times increase"
      (with-system [{kv-store ::kv/mem} config]
        (kv/put!
         kv-store
         [[:default (ba 0x00) bytes/empty]
          [:default (ba 0x00 0x01 0x02) bytes/empty]])

        (with-open [snapshot (kv/new-snapshot kv-store)
                    iter (kv/new-iterator snapshot :default)]
          (is (= [[0x00] [0x00 0x01 0x02]]
                 (vec (i/keys! iter decode-1 (bs/from-hex "00"))))))))))
