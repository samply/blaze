(ns blaze.db.impl.codec.resource-types-test
  (:require
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.codec-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]])
  (:import
   [blaze.db.impl.codec ResourceTypes]
   [com.sun.management ThreadMXBean]
   [java.lang.management ManagementFactory]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn- allocated-bytes ^long []
  (.getCurrentThreadAllocatedBytes ^ThreadMXBean (ManagementFactory/getThreadMXBean)))

(deftest tid-test
  (testing "is the murmur3 32-bit hash of the type name"
    (are [type tid] (= tid (ResourceTypes/tid type))
      "ClinicalImpression" -2146857976
      "Patient" -1508652135
      "Observation" 1543762679
      "TestReport" 2081142363))

  (testing "works with a type name that isn't the same instance"
    (is (= -1508652135 (ResourceTypes/tid (String. (.toCharArray "Patient"))))))

  (testing "is the murmur3 32-bit hash of an unknown type name"
    (is (= 1319662467 (ResourceTypes/tid "Foo"))))

  (testing "doesn't allocate for known types"
    (let [start (allocated-bytes)]
      (dotimes [_ 1000]
        (ResourceTypes/tid "Patient"))
      (is (< (- (allocated-bytes) start) 1000)))))

(deftest keyword-test
  (testing "returns the interned keyword of every type"
    (is (every? #(identical? (keyword "fhir" %) (ResourceTypes/keyword (codec/tid %)))
                codec/all-types)))

  (testing "returns nil on unknown tid"
    (is (nil? (ResourceTypes/keyword 0)))))

(deftest name-test
  (testing "returns the name of every type"
    (is (every? #(= % (ResourceTypes/name (codec/tid %))) codec/all-types)))

  (testing "returns nil on unknown tid"
    (is (nil? (ResourceTypes/name 0)))))

(deftest names-test
  (testing "is a vector"
    (is (vector? (ResourceTypes/names))))

  (testing "contains all types exactly once"
    (is (= 146 (count (ResourceTypes/names)) (count (set (ResourceTypes/names)))))))
