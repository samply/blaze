(ns blaze.db.node.validation-test
  (:require
   [blaze.db.node.validation :as validation]
   [blaze.db.node.validation-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def ^:private hash-a
  #blaze/hash"7B3980C2BFCF43A8CDD61662E1AABDA9CA6431964820BC8D52958AEC9A270378")

(def ^:private hash-b
  #blaze/hash"C9ADE22457D5AD750735B6B166E3CE8D6878D09B64C2C2868DCB6DE4C9EFBD4F")

(def ^:private put-0 {:op "put" :type "Patient" :id "0" :hash hash-a})
(def ^:private keep-0 {:op "keep" :type "Patient" :id "0" :hash hash-b})
(def ^:private delete-0 {:op "delete" :type "Patient" :id "0"})
(def ^:private delete-history-0 {:op "delete-history" :type "Patient" :id "0"})

(defn- delete-version-of-0 [t]
  {:op "delete-history-version" :type "Patient" :id "0" :t t})

(deftest validate-tx-cmds-test
  (testing "single commands"
    (doseq [cmd [put-0 keep-0 delete-0 delete-history-0 (delete-version-of-0 1)]]
      (is (nil? (validation/validate-tx-cmds [cmd])))))

  (testing "duplicate commands"
    (doseq [cmd [put-0 keep-0 delete-0 delete-history-0]]
      (given (validation/validate-tx-cmds [cmd cmd])
        ::anom/category := ::anom/incorrect
        :fhir/issue := "invariant"
        :blaze.db/tx-cmd-indices := [0 1])))

  (testing "keep and delete"
    (given (validation/validate-tx-cmds [keep-0 delete-0])
      ::anom/category := ::anom/incorrect
      ::anom/message := "Duplicate transaction commands `keep Patient/0` and `delete Patient/0`."
      :fhir/issue := "invariant"
      :blaze.db/tx-cmd-indices := [0 1]))

  (testing "put and delete history"
    (given (validation/validate-tx-cmds [put-0 delete-history-0])
      ::anom/category := ::anom/incorrect
      ::anom/message := "Duplicate transaction commands `put Patient/0` and `delete-history Patient/0`."
      :blaze.db/tx-cmd-indices := [0 1]))

  (testing "delete history version"
    (testing "of different versions of the same resource"
      (is (nil? (validation/validate-tx-cmds [(delete-version-of-0 1) (delete-version-of-0 2)]))))

    (testing "of the same version twice"
      (given (validation/validate-tx-cmds [(delete-version-of-0 1) (delete-version-of-0 1)])
        ::anom/category := ::anom/incorrect
        ::anom/message := "Duplicate transaction commands `delete-history-version Patient/0/_history/1` and `delete-history-version Patient/0/_history/1`."
        :blaze.db/tx-cmd-indices := [0 1]))

    (testing "after a put of the same resource"
      (given (validation/validate-tx-cmds [put-0 (delete-version-of-0 1)])
        ::anom/category := ::anom/incorrect
        ::anom/message := "Duplicate transaction commands `put Patient/0` and `delete-history-version Patient/0/_history/1`."
        :blaze.db/tx-cmd-indices := [0 1]))

    (testing "before other commands on the same resource"
      (doseq [cmd [put-0 delete-0 delete-history-0]]
        (given (validation/validate-tx-cmds [(delete-version-of-0 1) (delete-version-of-0 2) cmd])
          ::anom/category := ::anom/incorrect
          :blaze.db/tx-cmd-indices := [0 2]))))

  (testing "commands on other resources"
    (is (nil? (validation/validate-tx-cmds [{:op "put" :type "Patient" :id "1" :hash hash-a}
                                            {:op "delete-history" :type "Patient" :id "2"}
                                            (delete-version-of-0 1)]))))

  (testing "conditional deletes and patient purges are skipped, because they are
            expanded only later"
    (is (nil? (validation/validate-tx-cmds [{:op "conditional-delete" :type "Patient"}
                                            {:op "conditional-delete" :type "Patient"}
                                            {:op "patient-purge" :id "0"}
                                            {:op "patient-purge" :id "0"}])))))
