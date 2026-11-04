(ns blaze.db.node.duplicates-test
  (:require
   [blaze.db.node.duplicates :as dup]
   [blaze.db.node.duplicates-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def ^:private put-0 {:op "put" :type "Patient" :id "0"})
(def ^:private delete-0 {:op "delete" :type "Patient" :id "0"})
(def ^:private version-1-of-0 {:op "delete-history-version" :type "Patient" :id "0" :t 1})
(def ^:private version-2-of-0 {:op "delete-history-version" :type "Patient" :id "0" :t 2})

(deftest find-duplicate-test
  (testing "nothing conflicts in an empty transaction"
    (is (nil? (dup/find-duplicate []))))

  (testing "commands on different resources don't conflict"
    (is (nil? (dup/find-duplicate [put-0
                                   {:op "put" :type "Patient" :id "1"}
                                   {:op "put" :type "Observation" :id "0"}
                                   {:op "delete-history-version" :type "Patient" :id "2" :t 1}]))))

  (testing "commands on the whole resource conflict with each other"
    (given (dup/find-duplicate [put-0 delete-0])
      :cmd := delete-0
      :idx := 1
      :existing-cmd := put-0
      :existing-idx := 0))

  (testing "the positions count all commands"
    (given (dup/find-duplicate [{:op "put" :type "Patient" :id "1"} put-0
                                {:op "put" :type "Patient" :id "2"} delete-0])
      :idx := 3
      :existing-idx := 1))

  (testing "commands on a version conflict with commands on the whole resource"
    (given (dup/find-duplicate [put-0 version-1-of-0])
      :cmd := version-1-of-0
      :existing-cmd := put-0))

  (testing "commands on the whole resource conflict with the first command on a version"
    (given (dup/find-duplicate [version-1-of-0 version-2-of-0 put-0])
      :cmd := put-0
      :idx := 2
      :existing-cmd := version-1-of-0
      :existing-idx := 0))

  (testing "commands on the same version conflict"
    (given (dup/find-duplicate [version-1-of-0 version-1-of-0])
      :idx := 1
      :existing-idx := 0))

  (testing "commands on different versions don't conflict"
    (is (nil? (dup/find-duplicate [version-1-of-0 version-2-of-0]))))

  (testing "only delete history version commands act on a single version"
    (testing "so other commands on the same resource conflict, even if they
              carry different `t` values"
      (given (dup/find-duplicate [(assoc put-0 :t 1) (assoc delete-0 :t 2)])
        :idx := 1
        :existing-idx := 0))

    (testing "and conflict with the first delete history version command"
      (given (dup/find-duplicate [version-1-of-0 (assoc put-0 :t 2)])
        :idx := 1
        :existing-idx := 0)))

  (testing "commands not identifying a resource by type and id are skipped"
    (is (nil? (dup/find-duplicate [{:op "conditional-delete" :type "Patient"}
                                   {:op "conditional-delete" :type "Patient"}
                                   {:op "patient-purge" :id "0"}
                                   {:op "patient-purge" :id "0"}])))

    (testing "but still count for the positions"
      (given (dup/find-duplicate [{:op "conditional-delete" :type "Patient"} put-0 delete-0])
        :idx := 2
        :existing-idx := 1))))

(deftest duplicate-msg-test
  (testing "names the earlier command first"
    (is (= "Duplicate transaction commands `put Patient/0` and `delete Patient/0`."
           (dup/duplicate-msg put-0 delete-0))))

  (testing "commands on a version"
    (is (= "Duplicate transaction commands `put Patient/0` and `delete-history-version Patient/0/_history/1`."
           (dup/duplicate-msg put-0 version-1-of-0))))

  (testing "a conditional create resolved to a resource"
    (is (= "Duplicate transaction commands `create Patient?identifier=153229 (resolved to id 0)` and `put Patient/0`."
           (dup/duplicate-msg {:op "hold" :type "Patient" :id "0"
                               :if-none-exist [["identifier" "153229"]]}
                              put-0)))))
