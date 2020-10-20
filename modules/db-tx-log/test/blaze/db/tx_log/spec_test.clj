(ns blaze.db.tx-log.spec-test
  (:require
    [blaze.db.tx-log.spec]
    [blaze.fhir.hash :as hash]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest]]
    [taoensso.timbre :as log]))


(defn fixture [f]
  (st/instrument)
  (log/set-level! :trace)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest t-test
  (are [x] (s/valid? :blaze.db/t x)
    0
    1
    0xFFFFFFFFFFFFFF))


(def patient-hash-0 (hash/generate {:fhir/type :fhir/Patient :id "0"}))
(def observation-hash-0 (hash/generate {:fhir/type :fhir/Observation :id "0"}))


(deftest tx-cmd-test
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
     :hash patient-hash-0
     :if-match 1}
    {:op "delete"
     :type "Patient"
     :id "0"
     :hash patient-hash-0
     :if-match 1}))
