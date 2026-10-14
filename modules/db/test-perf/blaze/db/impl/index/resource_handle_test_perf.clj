(ns blaze.db.impl.index.resource-handle-test-perf
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [are deftest testing]])
  (:import
   [org.openjdk.jol.info GraphLayout]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn- total-size [& xs]
  (.totalSize (GraphLayout/parseInstance (object-array xs))))

(defn- resource-handle [id-size]
  (rh/resource-handle! 0 (str/join (repeat id-size "0")) 0 0 (bb/allocate 40)))

(deftest resource-handle-test
  (testing "instance size"
    (are [id-size size] (= size (total-size (resource-handle id-size)))
      1 120
      8 120
      9 128
      16 128
      17 136
      24 136
      25 144
      32 144
      33 152
      40 152
      41 160
      48 160
      49 168
      56 168
      57 176
      64 176)))
