(ns blaze.db.tx-log.spec-test
  (:require
   [blaze.db.tx-log.spec]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.hash-spec]
   [blaze.fhir.test-util]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest testing]]
   [taoensso.timbre :as log])
  (:import
   [java.time Instant]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest t-test
  (are [x] (s/valid? :blaze.db/t x)
    0
    1
    0xFFFFFFFFFFFFFF))

(def patient-hash-0 (hash/generate {:fhir/type :fhir/Patient :id "0"}))
(def observation-hash-0 (hash/generate {:fhir/type :fhir/Observation :id "0"}))

(deftest tx-cmd-test
  (testing "valid"
    (are [tx-cmd] (s/valid? :blaze.db/tx-cmd tx-cmd)
      {:op "create"
       :type "Patient"
       :id "0"
       :hash patient-hash-0}
      {:op "create"
       :type "Observation"
       :id "0"
       :hash observation-hash-0
       :refs [["Patient" "0"]]}
      {:op "put"
       :type "Patient"
       :id "0"
       :hash patient-hash-0}
      {:op "put"
       :type "Patient"
       :id "0"
       :hash patient-hash-0
       :if-match 1}
      {:op "create"
       :type "Patient"
       :id "0"
       :hash patient-hash-0
       :access-control ["read-only"]}
      {:op "put"
       :type "Patient"
       :id "0"
       :hash patient-hash-0
       :access-control ["internal" "read-only"]}
      {:op "keep"
       :type "Patient"
       :id "0"
       :hash patient-hash-0}
      {:op "delete"
       :type "Patient"
       :id "0"
       :if-match 1}
      {:op "delete"
       :type "Patient"
       :id "0"
       :check-refs true}
      {:op "conditional-delete"
       :type "Patient"}
      {:op "delete-history"
       :type "Patient"
       :id "0"}
      {:op "delete-history-version"
       :type "Patient"
       :id "0"
       :t 1}
      {:op "patient-purge"
       :id "0"}
      {:op "patient-purge"
       :id "0"
       :check-refs true
       :graph "http://example.com/purge|1"}))

  (testing "invalid"
    (are [tx-cmd] (not (s/valid? :blaze.db/tx-cmd tx-cmd))
      nil
      1
      {:op "create"
       :type "Patient"
       :id "0"}
      {:op "put"
       :type "Patient"
       :id "0"}
      {:op "delete"
       :type "Patient"}
      {:op "delete"
       :type "Patient"
       :id "0"
       :check-refs "i should be a boolean"}
      {:op "conditional-delete"}
      {:op "create"
       :type "Patient"
       :id "0"
       :hash patient-hash-0
       :access-control []}
      {:op "put"
       :type "Patient"
       :id "0"
       :hash patient-hash-0
       :access-control #{"read-only"}}
      {:op "put"
       :type "Patient"
       :id "0"
       :hash patient-hash-0
       :access-control [1]}
      {:op "delete-history"
       :type "Patient"}
      {:op "delete-history-version"
       :type "Patient"
       :id "0"}
      {:op "delete-history-version"
       :type "Patient"
       :id "0"
       :t "1"}
      {:op "patient-purge"}
      {:op "patient-purge"
       :id "0"
       :graph 1}
      {:op "patient-purge"
       :id "0"
       :graph "http://example.com/purge"}
      {:op "patient-purge"
       :id "0"
       :graph "http://example.com/purge|"}
      {:op "patient-purge"
       :id "0"
       :graph "http://example.com/purge|1|2"})))

(def tx-data
  {:t 1
   :instant Instant/EPOCH
   :tx-cmds [{:op "create" :type "Patient" :id "0" :hash patient-hash-0}]})

(deftest tx-data-test
  (testing "valid"
    (are [tx-data] (s/valid? :blaze.db/tx-data tx-data)
      tx-data
      (assoc tx-data :permissions {})
      (assoc tx-data :permissions {:write-access-control #{}})
      (assoc tx-data :permissions {:write-access-control #{"internal" "read-only"}})))

  (testing "invalid"
    (are [tx-data] (not (s/valid? :blaze.db/tx-data tx-data))
      (assoc tx-data :permissions nil)
      (assoc tx-data :permissions [])
      (assoc tx-data :permissions {:write-access-control ["read-only"]})
      (assoc tx-data :permissions {:write-access-control #{1}}))))
