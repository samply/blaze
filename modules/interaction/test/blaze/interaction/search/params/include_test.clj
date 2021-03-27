(ns blaze.interaction.search.params.include-test
  (:require
    [blaze.interaction.search.params.include :as include]
    [blaze.interaction.search.params.include-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [juxt.iota :refer [given]]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest include-defs-test
  (testing "one direct param"
    (given (include/include-defs {"_include" "Observation:subject"})
      [:direct "Observation" 0 :code] := "subject")

    (testing "with target type"
      (given (include/include-defs {"_include" "Observation:subject:Group"})
        [:direct "Observation" 0 :code] := "subject"
        [:direct "Observation" 0 :target-type] := "Group")))

  (testing "two direct params"
    (testing "of the same type"
      (given (include/include-defs
               {"_include" ["Observation:subject" "Observation:encounter"]})
        [:direct "Observation" 0 :code] := "subject"
        [:direct "Observation" 1 :code] := "encounter"))

    (testing "of different types"
      (given (include/include-defs
               {"_include" ["Observation:subject" "Patient:organization"]})
        [:direct "Observation" 0 :code] := "subject"
        [:direct "Patient" 0 :code] := "organization")))

  (testing "one direct and one iterate param"
    (given (include/include-defs
             {"_include" "Observation:subject"
              "_include:iterate" "Patient:organization"})
      [:direct "Observation" 0 :code] := "subject"
      [:iterate "Patient" 0 :code] := "organization")))
