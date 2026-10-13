(ns blaze.db.impl.cache-test
  (:require
   [blaze.db.impl.cache :as cache]
   [blaze.db.impl.cache-spec]
   [blaze.test-util :as tu :refer [with-global-log-capture]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [juxt.iota :refer [given]]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(def ^:private max-memory
  (.maxMemory (Runtime/getRuntime)))

(deftest max-size-in-bytes-test
  (testing "is the ratio of the max memory"
    (is (= (long (* max-memory 0.25)) (cache/max-size-in-bytes "foo" 0.25))))

  (testing "a ratio of zero is zero"
    (is (zero? (cache/max-size-in-bytes "foo" 0))))

  (testing "an integer ratio is the same as the equivalent double ratio"
    (is (= (cache/max-size-in-bytes "foo" 1.0) (cache/max-size-in-bytes "foo" 1))))

  (testing "logs the memory size of the cache"
    (let [max-size-in-bytes (long (* max-memory 0.25))
          msg (format "Create foo cache with a memory size of %d MiB (25%% of max memory size)"
                      (bit-shift-right max-size-in-bytes 20))]
      (with-global-log-capture [captured msg]
        (cache/max-size-in-bytes "foo cache" 0.25)

        (given (deref captured 1000 nil)
          :level := :info)))))
