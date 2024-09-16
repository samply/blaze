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
   [taoensso.timbre :as log]))

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
       :id "0"}))

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
      {:op "delete-history"
       :type "Patient"})))
