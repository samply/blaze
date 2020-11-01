(ns blaze.db.impl.iterators
  (:require
    [blaze.db.kv :as kv])
  (:import
    [clojure.lang IReduceInit]
    [java.nio ByteBuffer BufferOverflowException])
  (:refer-clojure :exclude [keys]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn- read-key! [iter ^ByteBuffer bb]
  (let [size (kv/key! iter (.clear bb))]
    (if (< (.capacity bb) ^long size)
      (let [bb (ByteBuffer/allocateDirect size)]
        (kv/key! iter bb)
        bb)
      bb)))


(defn- reduce-keys! [iter decode dir rf init]
  (loop [kb (decode)
         ret init]
    (if (kv/valid? iter)
      (let [kb (read-key! iter kb)
            ret (rf ret (decode kb))]
        (if (reduced? ret)
          @ret
          (do (dir iter) (recur kb ret))))
      ret)))


(defn keys
  "Returns a reducible collection of keys of `iter` starting with `start-key`
  and decoded with the `decode` function.

  The decode function has to return a ByteBuffer when called with no argument.
  That same ByteBuffer will be used for each key read and will be passed to the
  decode function for decoding into a value which will end up in the collection."
  [iter decode start-key]
  (reify IReduceInit
    (reduce [_ rf init]
      (kv/seek! iter start-key)
      (reduce-keys! iter decode kv/next! rf init))))


(defn keys-prev [iter decode start-key]
  (reify IReduceInit
    (reduce [_ rf init]
      (kv/seek-for-prev! iter start-key)
      (reduce-keys! iter decode kv/prev! rf init))))


(defn- read-value! [iter ^ByteBuffer bb]
  (when (< (.capacity bb) ^long (kv/value! iter (.clear bb)))
    (throw (BufferOverflowException.))))


(defn- reduce-kvs! [iter decode dir rf init]
  (let [[kb vb] (decode)]
    (loop [ret init]
      (if (kv/valid? iter)
        (do
          (read-key! iter kb)
          (read-value! iter vb)
          (let [ret (rf ret (decode kb vb))]
            (if (reduced? ret)
              @ret
              (do (dir iter) (recur ret)))))
        ret))))


(defn kvs
  "Returns a reducible collection of decoded values of `iter`.

  The function `decode` will be called with the key buffer and the value buffer
  and its result are the decoded values.

  Doesn't close the iterator."
  [iter decode start-key]
  (reify IReduceInit
    (reduce [_ rf init]
      (kv/seek! iter start-key)
      (reduce-kvs! iter decode kv/next! rf init))))


(defn- reduce-iter! [iter rf init]
  (loop [ret init]
    (if (kv/valid? iter)
      (let [ret (rf ret iter)]
        (if (reduced? ret)
          @ret
          (do (kv/next! iter) (recur ret))))
      ret)))


(defn iter
  "Returns a reducible collection of `iter` itself.

  Doesn't close the iterator."
  ([iter]
   (reify IReduceInit
     (reduce [_ rf init]
       (kv/seek-to-first! iter)
       (reduce-iter! iter rf init))))
  ([iter start-key]
   (reify IReduceInit
     (reduce [_ rf init]
       (kv/seek! iter start-key)
       (reduce-iter! iter rf init)))))
