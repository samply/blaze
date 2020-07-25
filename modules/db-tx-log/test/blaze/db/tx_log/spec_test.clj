(ns blaze.db.tx-log.spec-test
  (:require
    [blaze.db.hash :as hash]
    [blaze.db.tx-log.spec]
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


(def patient-hash-0 (hash/generate {:resourceType "Patient" :id "0"}))
(def observation-hash-0 (hash/generate {:resourceType "Observation" :id "0"}))


(deftest tx-cmd
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
    {:op "create"
     :type "Patient"
     :id "0"
     :hash patient-hash-0
     :if-match 1}))
