(ns blaze.db.impl.index.doorkeeper-test
  (:require
   [blaze.db.impl.index.resource-handle-test-util :refer [resource-handle]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]])
  (:import
   [blaze.db.impl.index Doorkeeper]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn- seen? [^Doorkeeper doorkeeper id]
  (.seen doorkeeper (resource-handle "Patient" id 1)))

(deftest seen-test
  (testing "a key isn't seen on its first miss but on its second"
    (let [doorkeeper (Doorkeeper. 10)]
      (is (false? (seen? doorkeeper "0")))
      (is (true? (seen? doorkeeper "0")))))

  (testing "versions of the same resource are the same key"
    (let [doorkeeper (Doorkeeper. 10)]
      (.seen doorkeeper (resource-handle "Patient" "0" 1))
      (is (true? (.seen doorkeeper (resource-handle "Patient" "0" 2))))))

  (testing "the same id of another type is another key"
    (let [doorkeeper (Doorkeeper. 10)]
      (.seen doorkeeper (resource-handle "Patient" "0" 1))
      (is (false? (.seen doorkeeper (resource-handle "Observation" "0" 1))))))

  (testing "with a window checked by a sample of the new keys"
    (let [window 131072
          doorkeeper (Doorkeeper. window)
          ids (mapv str (range 1000))]
      (run! (partial seen? doorkeeper) ids)

      (testing "all keys are seen on their second miss"
        (is (every? (partial seen? doorkeeper) ids)))

      (testing "keys are forgotten after many windows of distinct keys"
        (run! (partial seen? doorkeeper) (map #(str "x" %) (range (* 4 window))))

        ;; only false positives of the Bloom filters remain, at most 1 %
        (is (< (count (filter (partial seen? doorkeeper) ids)) 30))))))
