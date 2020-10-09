(ns blaze.db.tx-log.local.references-test
  (:require
    [blaze.db.tx-log.local.references :as references]
    [blaze.db.tx-log.local.references-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [taoensso.timbre :as log]))


(defn fixture [f]
  (st/instrument)
  (log/set-level! :trace)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest extract-references
  (testing "Observation.subject"
    (is
      (= [["Patient" "0"]]
         (references/extract-references
           {:fhir/type :fhir/Observation :id "0"
            :subject
            {:fhir/type :fhir/Reference
             :reference "Patient/0"}}))))

  (testing "List.item"
    (is
      (= [["Patient" "0"]
          ["Patient" "1"]]
         (references/extract-references
           {:fhir/type :fhir/List :id "0"
            :entry
            [{:fhir/type :fhir.List/entry
              :item
              {:fhir/type :fhir/Reference
               :reference "Patient/0"}}
             {:fhir/type :fhir.List/entry
              :item
              {:fhir/type :fhir/Reference
               :reference "Patient/1"}}]})))))
