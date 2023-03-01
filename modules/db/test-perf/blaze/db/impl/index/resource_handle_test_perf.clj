(ns blaze.db.impl.index.resource-handle-test-perf
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.db.impl.index.resource-handle :as rh]
    [blaze.test-util :as tu]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest testing]]
    [cuerdas.core :as c-str])
  (:import
    [org.openjdk.jol.info GraphLayout]))


(set! *warn-on-reflection* true)
(st/instrument)


(test/use-fixtures :each tu/fixture)


(defn- total-size [& xs]
  (.totalSize (GraphLayout/parseInstance (object-array xs))))


(defn- resource-handle [id-size]
  (rh/resource-handle 0 (c-str/repeat "0" id-size) 0 (bb/allocate 40)))


(deftest resource-handle-test
  (testing "instance size"
    (are [id-size size] (= size (total-size (resource-handle id-size)))
      1 248
      8 248
      9 256
      16 256
      17 264
      24 264
      25 272
      32 272
      33 280
      40 280
      41 288
      48 288
      49 296
      56 296
      57 304
      64 304)))
