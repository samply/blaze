(ns blaze.db.node.validation-test
  (:require
    [blaze.db.node.validation :as validation]
    [blaze.db.node.validation-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest validate-ops-test
  (testing "single delete"
    (is (nil? (validation/validate-ops [[:delete "Patient" "0"]]))))

  (testing "duplicate delete"
    (given (validation/validate-ops [[:delete "Patient" "0"]
                                     [:delete "Patient" "0"]])
      ::anom/category := ::anom/incorrect
      :cognitect.anomalies/message := "Duplicate resource `Patient/0`.",
      :fhir/issue := "invariant")))
