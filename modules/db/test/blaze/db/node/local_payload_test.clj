(ns blaze.db.node.local-payload-test
  (:require
   [blaze.db.node.local-payload :as lp]
   [blaze.db.node.local-payload-spec]
   [blaze.fhir.hash :as hash]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]])
  (:import
   [java.lang.ref SoftReference]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(def ^:private patient
  {:fhir/type :fhir/Patient :id "0"})

(def ^:private entries
  {(hash/generate patient) patient})

(deftest unwrap-test
  (testing "returns the wrapped entries"
    (is (= entries (lp/unwrap (lp/wrap entries)))))

  (testing "returns nil if the entries were discarded"
    (let [payload (lp/wrap entries)]
      (.clear ^SoftReference payload)
      (is (nil? (lp/unwrap payload))))))
