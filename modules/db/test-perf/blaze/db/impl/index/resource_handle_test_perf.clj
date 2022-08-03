(ns blaze.db.impl.index.resource-handle-test-perf
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.db.impl.index.resource-handle :as rh]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest testing]]
    [cuerdas.core :as c-str])
  (:import
    [org.openjdk.jol.info GraphLayout]))


(set! *warn-on-reflection* true)
(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn- total-size [& xs]
  (.totalSize (GraphLayout/parseInstance (object-array xs))))


(defn- resource-handle [id-size]
  (rh/resource-handle 0 (c-str/repeat "0" id-size) 0 (bb/allocate 40)))


(deftest resource-handle-test
  (testing "instance size"
    (are [id-size size] (= size (total-size (resource-handle id-size)))
      1 272
      8 272
      9 280
      16 280
      17 288
      24 288
      25 296
      32 296
      33 304
      40 304
      41 312
      48 312
      49 320
      56 320
      57 328
      64 328)))
