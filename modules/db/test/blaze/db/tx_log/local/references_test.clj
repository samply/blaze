(ns blaze.db.tx-log.local.references-test
  (:require
    [blaze.db.tx-log.local.references :as references]
    [blaze.db.tx-log.local.references-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [taoensso.timbre :as log]))


(defn fixture [f]
  (st/instrument)
  (log/with-merged-config {:level :error} (f))
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest extract-references
  (testing "Observation.subject"
    (is
      (= [["Patient" "0"]]
         (references/extract-references
           {:resourceType "Observation" :id "0"
            :subject {:reference "Patient/0"}}))))

  (testing "List.item"
    (is
      (= [["Patient" "0"]
          ["Patient" "1"]]
         (references/extract-references
           {:resourceType "List" :id "0"
            :entry
            [{:item {:reference "Patient/0"}}
             {:item {:reference "Patient/1"}}]})))))
