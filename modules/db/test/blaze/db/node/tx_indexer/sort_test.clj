(ns blaze.db.node.tx-indexer.sort-test
  (:require
    [blaze.byte-string :as bs]
    [blaze.db.node.tx-indexer.sort :as sort]
    [blaze.db.node.tx-indexer.sort-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [cuerdas.core :as str]
    [juxt.iota :refer [given]]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def hash-0 (bs/from-utf8-string (str/repeat "0" 32)))


(deftest sort-by-references-test
  (testing "the Patient create command is ordered before the Observation create
            command referring to it"
    (given
      (sort/sort-by-references
        [{:op "create" :type "Observation" :id "0" :hash hash-0
          :refs [["Patient" "0"]]}
         {:op "create" :type "Patient" :id "0" :hash hash-0}])
      [0 :type] := "Patient"
      [1 :type] := "Observation"))

  (testing "the Patient delete command is ordered before the Observation create
            referring to it"
    (given
      (sort/sort-by-references
        [{:op "create" :type "Observation" :id "0" :hash hash-0
          :refs [["Patient" "0"]]}
         {:op "delete" :type "Patient" :id "0" :hash hash-0}])
      [0 :type] := "Patient"
      [1 :type] := "Observation"))

  (testing "delete commands are ordered before create commands"
    (given
      (sort/sort-by-references
        [{:op "create" :type "Patient" :id "1" :hash hash-0}
         {:op "delete" :type "Patient" :id "0" :hash hash-0}])
      [0 :op] := "delete"
      [1 :op] := "create"))

  (testing "single delete command"
    (given
      (sort/sort-by-references
        [{:op "delete" :type "Patient" :id "0" :hash hash-0}])
      [0 :op] := "delete")))
