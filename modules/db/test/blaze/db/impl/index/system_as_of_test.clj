(ns blaze.db.impl.index.system-as-of-test
  (:require
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.system-as-of :as sao]
   [blaze.db.impl.index.system-as-of-spec]
   [blaze.db.impl.index.system-as-of-test-util :as sao-tu]
   [blaze.db.kv :as kv]
   [blaze.db.test-util :refer [config with-system-data]]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest prune-test
  (testing "empty database"
    (with-system [{:blaze.db/keys [node]} config]
      (with-open [snapshot (kv/new-snapshot (:kv-store node))]
        (given (sao/prune snapshot 10 0)
          :delete-entries := []
          :num-entries-processed := 0
          :next := nil))))

  (testing "one non-purged patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]]]

      (with-open [snapshot (kv/new-snapshot (:kv-store node))]
        (given (sao/prune snapshot 10 1)
          :delete-entries := []
          :num-entries-processed := 1
          :next := nil))))

  (testing "one purged patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]]
       [[:patient-purge "0"]]]

      (testing "returns no delete entry at t=1"
        (with-open [snapshot (kv/new-snapshot (:kv-store node))]
          (given (sao/prune snapshot 10 1)
            [:delete-entries count] := 0
            :num-entries-processed := 1
            :next := nil)))

      (testing "returns one delete entry at t=2"
        (with-open [snapshot (kv/new-snapshot (:kv-store node))]
          (given (sao/prune snapshot 10 2)
            [:delete-entries count] := 1
            [:delete-entries 0 0] := :system-as-of-index
            [:delete-entries 0 1 sao-tu/decode-key] := {:t 1 :type "Patient" :id "0"}
            :num-entries-processed := 1
            :next := nil)))))

  (testing "two purged patients"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]
        [:create {:fhir/type :fhir/Patient :id "1"}]]
       [[:patient-purge "0"]]
       [[:patient-purge "1"]]]

      (testing "returns one delete entry at t=2"
        (with-open [snapshot (kv/new-snapshot (:kv-store node))]
          (given (sao/prune snapshot 10 2)
            [:delete-entries count] := 1
            [:delete-entries 0 0] := :system-as-of-index
            [:delete-entries 0 1 sao-tu/decode-key] := {:t 1 :type "Patient" :id "0"}
            :num-entries-processed := 2
            :next := nil)))

      (testing "returns one delete entry at t=3 and n=1"
        (with-open [snapshot (kv/new-snapshot (:kv-store node))]
          (given (sao/prune snapshot 1 3)
            [:delete-entries count] := 1
            [:delete-entries 0 0] := :system-as-of-index
            [:delete-entries 0 1 sao-tu/decode-key] := {:t 1 :type "Patient" :id "0"}
            :num-entries-processed := 1
            [:next :tid] := (codec/tid "Patient")
            [:next :t] := 1
            [:next :id] := (codec/id-byte-string "1")))

        (testing "it's possible to continue with the next entry"
          (with-open [snapshot (kv/new-snapshot (:kv-store node))]
            (given (sao/prune snapshot 1 3 1 (codec/tid "Patient") (codec/id-byte-string "1"))
              [:delete-entries count] := 1
              [:delete-entries 0 0] := :system-as-of-index
              [:delete-entries 0 1 sao-tu/decode-key] := {:t 1 :type "Patient" :id "1"}
              :num-entries-processed := 1
              :next := nil))))

      (testing "returns two delete entries at t=3"
        (with-open [snapshot (kv/new-snapshot (:kv-store node))]
          (given (sao/prune snapshot 10 3)
            [:delete-entries count] := 2
            [:delete-entries 0 0] := :system-as-of-index
            [:delete-entries 0 1 sao-tu/decode-key] := {:t 1 :type "Patient" :id "0"}
            [:delete-entries 1 0] := :system-as-of-index
            [:delete-entries 1 1 sao-tu/decode-key] := {:t 1 :type "Patient" :id "1"}
            :num-entries-processed := 2
            :next := nil))))))
