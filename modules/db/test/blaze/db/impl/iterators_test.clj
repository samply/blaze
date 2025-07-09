(ns blaze.db.impl.iterators-test
  (:require
   [blaze.byte-string :as bs]
   [blaze.db.impl.bytes :as bytes]
   [blaze.db.impl.iterators :as i]
   [blaze.db.kv :as kv]
   [blaze.db.kv.mem]
   [blaze.db.kv.mem-spec]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu :refer [ba]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]])
  (:import
   [java.lang AutoCloseable]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(def config
  {::kv/mem {:column-families {}}})

(deftest seek-key-filter-test
  (testing "minimal filter matching every input"
    (with-system [{kv-store ::kv/mem} config]
      (kv/put!
       kv-store
       [[:default (ba 0x00) bytes/empty]])

      (with-open [snapshot (kv/new-snapshot kv-store)]
        (is (= 3 (transduce
                  (i/seek-key-filter snapshot :default (fn [_] kv/seek-buffer!)
                                     (fn [_ _ _ _] true) (fn [tb _ _] tb)
                                     [#blaze/byte-string"00"])
                  +
                  [1 2])))))))

(defn vec-from-iter [coll]
  (let [iter (.iterator ^Iterable coll)]
    (loop [res []]
      (if (.hasNext iter)
        (recur (conj res (.next iter)))
        (do
          (.close ^AutoCloseable iter)
          res)))))

(deftest keys-test
  (testing "normal read"
    (with-system [{kv-store ::kv/mem} config]
      (kv/put!
       kv-store
       [[:default (ba 0x00) bytes/empty]
        [:default (ba 0x01) bytes/empty]])

      (with-open [snapshot (kv/new-snapshot kv-store)]
        (let [keys (i/keys snapshot :default bs/from-byte-buffer! #blaze/byte-string"00")]

          (is (= [#blaze/byte-string"00" #blaze/byte-string"01"]
                 (vec keys)
                 (vec-from-iter keys)))))))

  (testing "too small ByteBuffer will be replaced with a larger one"
    (with-system [{kv-store ::kv/mem} config]
      (kv/put!
       kv-store
       [[:default (byte-array (repeat (inc i/buffer-size) 0x00)) bytes/empty]])

      (with-open [snapshot (kv/new-snapshot kv-store)]
        (is (= [(bs/from-hex (apply str (repeat (inc i/buffer-size) "00")))]
               (vec (i/keys snapshot :default bs/from-byte-buffer! #blaze/byte-string"00"))))))))
