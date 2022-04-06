(ns blaze.db.impl.index.resource-handle-test-perf
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.db.impl.index.resource-handle :as rh]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest testing]]
    [cuerdas.core :as str])
  (:import
    [org.openjdk.jol.info GraphLayout]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn- total-size [& xs]
  (.totalSize (GraphLayout/parseInstance (object-array xs))))


(defn- resource-handle [id-size]
  (rh/resource-handle 0 (str/repeat "0" id-size) 0 (bb/allocate 40)))


(deftest resource-handle-test
  (testing "instance size"
    (are [id-size size] (= size (total-size (resource-handle id-size)))
      1 288
      8 288
      9 296
      16 296
      17 304
      24 304
      25 312
      32 312
      33 320
      40 320
      41 328
      48 328
      49 336
      56 336
      57 344
      64 344)))
