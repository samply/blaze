(ns blaze.db.node.patient-last-change-index-test
  (:require
   [blaze.db.impl.index.patient-last-change :as plc]
   [blaze.db.impl.index.patient-last-change-test-util :as plc-tu]
   [blaze.db.node.patient-last-change-index :as node-plc]
   [blaze.db.node.patient-last-change-index-spec]
   [blaze.db.test-util :refer [config with-system-data]]
   [blaze.fhir.hash :as hash]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest]]
   [juxt.iota :refer [given]])
  (:import
   [java.nio.charset StandardCharsets]
   [java.time Instant]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def patient-0 {:fhir/type :fhir/Patient :id "0"})
(def observation-0 {:fhir/type :fhir/Observation :id "0"
                    :subject #fhir/Reference{:reference "Patient/0"}})
(def hash-observation-0 (hash/generate observation-0))

(deftest patient-last-change-index-entries-test
  (with-system-data [{:blaze.db/keys [node]} config]
    [[[:put patient-0]]]

    (given (node-plc/index-entries
            node
            {:t 2
             :instant Instant/EPOCH
             :tx-cmds [{:op "put" :type "Observation" :id "0"
                        :hash hash-observation-0 :refs [["Patient" "0"]]}]})
      count := 2
      [0 0] := :patient-last-change-index
      [0 1 plc-tu/decode-key] := {:patient-id "0" :t 2}

      [1 0] := :default
      [1 1 #(String. ^bytes % StandardCharsets/ISO_8859_1)] := "patient-last-change-state"
      [1 2 plc/decode-state] := {:type :building :t 2})))
