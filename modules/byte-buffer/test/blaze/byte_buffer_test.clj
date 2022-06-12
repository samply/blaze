(ns blaze.byte-buffer-test
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.test-util :refer [satisfies-prop]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest]]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest limit-test
  (satisfies-prop 100
    (prop/for-all [capacity gen/nat]
      (= capacity (bb/limit (bb/allocate capacity))))))


(defn- transcode-5-byte-long [x]
  (let [buf (bb/allocate 5)]
    (bb/put-5-byte-long! buf x)
    (bb/flip! buf)
    (= x (bb/get-5-byte-long! buf))))


(deftest transcode-5-byte-long-test
  (are [x] (transcode-5-byte-long x)
    0
    0xFFFFFFFF
    0xFFFFFFFFFF)

  (satisfies-prop 100000
    (prop/for-all [x (gen/choose 0 0xFFFFFFFFFF)]
      (transcode-5-byte-long x))))
