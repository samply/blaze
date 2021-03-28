(ns blaze.interaction.search.params-test
  (:require
    [blaze.interaction.search.params :as params]
    [blaze.interaction.search.params-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest decode-test
  (testing "invalid include parameter"
    (given (params/decode "strict" {"_include" "Observation"})
      ::anom/category := ::anom/incorrect)))
