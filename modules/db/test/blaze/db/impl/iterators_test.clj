(ns blaze.db.impl.iterators-test
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.db.impl.bytes :as bytes]
   [blaze.db.impl.iterators :as i]
   [blaze.db.kv :as kv]
   [blaze.db.kv.mem]
   [blaze.db.kv.mem-spec]
   [blaze.db.test-util :as dtu]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu :refer [ba]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]])
  (:import
   [java.lang AutoCloseable]
   [java.util Iterator]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(def config
  {::kv/mem {:column-families {}}})

(defn- bytes-of
  "Returns a byte array of `n` bytes all having the value `b`."
  [n b]
  (byte-array (repeat n (unchecked-byte b))))

(def ^:private large-size
  "A size which doesn't fit into the initial byte buffers."
  (inc i/buffer-size))

(def ^:private larger-size
  "A size which doesn't even fit into the first enlarged byte buffers."
  (* 3 i/buffer-size))

(def ^:private mixed-size-keys
  "Keys in index order where small keys follow keys which need enlarged byte
  buffers."
  [(bytes-of 1 0x00)
   (bytes-of large-size 0x01)
   (bytes-of 1 0x02)
   (bytes-of larger-size 0x03)
   (bytes-of 1 0x04)])

(deftest seek-key-filter-test
  (testing "keys and targets which need enlarged byte buffers"
    (with-system [{kv-store ::kv/mem} config]
      (kv/put! kv-store (mapv #(vector :default % bytes/empty) mixed-size-keys))

      (with-open [snapshot (kv/new-snapshot kv-store)]
        (let [encode (fn [target-buf input _]
                       (let [bytes (bs/to-byte-array input)
                             buf (if (< (bb/capacity target-buf) (alength bytes))
                                   (bb/allocate (alength bytes))
                                   target-buf)]
                         (bb/put-byte-array! buf bytes)))
              matches? (i/target-length-matcher (fn [_ _ _] true))
              inputs (mapv bs/from-byte-array
                           (conj mixed-size-keys (bytes-of larger-size 0x05)))]
          (is (= (mapv bs/from-byte-array mixed-size-keys)
                 (into [] (i/seek-key-filter snapshot :default
                                             (fn [_] kv/seek-buffer!)
                                             matches? encode [:value])
                       inputs)))))))

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
               (vec (i/keys snapshot :default bs/from-byte-buffer! #blaze/byte-string"00")))))))

  (testing "small keys following keys which need enlarged byte buffers"
    (with-system [{kv-store ::kv/mem} config]
      (kv/put! kv-store (mapv #(vector :default % bytes/empty) mixed-size-keys))

      (with-open [snapshot (kv/new-snapshot kv-store)]
        (let [keys (i/keys snapshot :default bs/from-byte-buffer! #blaze/byte-string"00")]
          (is (= (mapv bs/from-byte-array mixed-size-keys)
                 (vec keys)
                 (vec-from-iter keys))))))))

(deftest entries-test
  (testing "small keys and values following ones which need enlarged byte buffers"
    (with-system [{kv-store ::kv/mem} config]
      (let [entries (mapv vector mixed-size-keys (reverse mixed-size-keys))]
        (kv/put! kv-store (mapv #(into [:default] %) entries))

        (with-open [snapshot (kv/new-snapshot kv-store)]
          (is (= (mapv #(mapv bs/from-byte-array %) entries)
                 (vec (i/entries snapshot :default
                                 (map #(mapv bs/from-byte-buffer! %))
                                 #blaze/byte-string"00")))))))))

(defn- counting-completions
  "Returns a transducer that counts its completions in the volatile
  `completions`."
  [completions]
  (fn [rf]
    (fn
      ([result]
       (vswap! completions inc)
       (rf result))
      ([result input]
       (rf result input)))))

(defn- throwing-completion []
  (fn [rf]
    (fn
      ([_]
       (throw (Exception. "completion failed")))
      ([result input]
       (rf result input)))))

(deftest prefix-keys-xf-test
  (with-system [{kv-store ::kv/mem} config]
    (kv/put!
     kv-store
     [[:default (ba 0x00 0x00) bytes/empty]
      [:default (ba 0x01 0x00) bytes/empty]
      [:default (ba 0x01 0x01) bytes/empty]
      [:default (ba 0x02 0x00) bytes/empty]])

    (with-open [snapshot (kv/new-snapshot kv-store)]
      (testing "the transducer receives the keys with matching prefix"
        (let [keys (i/prefix-keys-xf snapshot :default (map bs/from-byte-buffer!)
                                     1 #blaze/byte-string"01")]
          (is (= [#blaze/byte-string"0100" #blaze/byte-string"0101"]
                 (vec keys)
                 (vec-from-iter keys)))))

      (testing "the transducer is completed"
        (is (= [[#blaze/byte-string"0100" #blaze/byte-string"0101"]]
               (vec (i/prefix-keys-xf snapshot :default
                                      (comp (map bs/from-byte-buffer!)
                                            (partition-all 3))
                                      1 #blaze/byte-string"01")))))

      (testing "closing an iterator twice"
        (let [completions (volatile! 0)
              iter (.iterator ^Iterable (i/prefix-keys-xf
                                         snapshot :default
                                         (comp (map bs/from-byte-buffer!)
                                               (counting-completions completions))
                                         1 #blaze/byte-string"01"))]
          (is (= #blaze/byte-string"0100" (.next ^Iterator iter)))
          (.close ^AutoCloseable iter)
          (.close ^AutoCloseable iter)

          (testing "completes the transducer only once"
            (is (= 1 @completions)))

          (testing "returns the kv iterator only once"
            (with-open [iter-1 (kv/new-iterator snapshot :default)
                        iter-2 (kv/new-iterator snapshot :default)]
              (kv/seek-to-first! iter-1)
              (kv/seek-to-last! iter-2)
              (is (= #blaze/byte-string"0000"
                     (bs/from-byte-array (kv/key iter-1))))))))

      (testing "closing an iterator closes the kv iterator even if the completion throws"
        (let [iter (.iterator ^Iterable (i/prefix-keys-xf
                                         snapshot :default
                                         (comp (map bs/from-byte-buffer!)
                                               (throwing-completion))
                                         1 #blaze/byte-string"01"))]
          (is (= #blaze/byte-string"0100" (.next ^Iterator iter)))
          (is (thrown? Exception (.close ^AutoCloseable iter)))
          (is (zero? (dtu/num-borrowed-iterators {:snapshot snapshot}))))))))

(deftest prefix-keys-prev-xf-test
  (with-system [{kv-store ::kv/mem} config]
    (kv/put!
     kv-store
     [[:default (ba 0x00 0x00) bytes/empty]
      [:default (ba 0x01 0x00) bytes/empty]
      [:default (ba 0x01 0x01) bytes/empty]
      [:default (ba 0x02 0x00) bytes/empty]])

    (with-open [snapshot (kv/new-snapshot kv-store)]
      (testing "the transducer receives the keys with matching prefix"
        (is (= [#blaze/byte-string"0101" #blaze/byte-string"0100"]
               (into [] (i/prefix-keys-prev-xf
                         snapshot :default (map bs/from-byte-buffer!)
                         1 #blaze/byte-string"01FF")))))

      (testing "the transducer is completed"
        (is (= [[#blaze/byte-string"0101" #blaze/byte-string"0100"]]
               (into [] (i/prefix-keys-prev-xf
                         snapshot :default
                         (comp (map bs/from-byte-buffer!) (partition-all 3))
                         1 #blaze/byte-string"01FF"))))))))
