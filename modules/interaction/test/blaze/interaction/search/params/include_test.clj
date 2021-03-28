(ns blaze.interaction.search.params.include-test
  (:require
    [blaze.interaction.search.params.include :as include]
    [blaze.interaction.search.params.include-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest include-defs-test
  (testing "one direct forward param"
    (given (include/include-defs nil {"_include" "Observation:subject"})
      [:direct :forward "Observation" 0 :code] := "subject")

    (testing "with target type"
      (given (include/include-defs nil {"_include" "Observation:subject:Group"})
        [:direct :forward "Observation" 0 :code] := "subject"
        [:direct :forward "Observation" 0 :target-type] := "Group")))

  (testing "two direct forward params"
    (testing "of the same type"
      (given (include/include-defs
               nil {"_include" ["Observation:subject" "Observation:encounter"]})
        [:direct :forward "Observation" 0 :code] := "subject"
        [:direct :forward "Observation" 1 :code] := "encounter"))

    (testing "of different types"
      (given (include/include-defs
               nil {"_include" ["Observation:subject" "Patient:organization"]})
        [:direct :forward "Observation" 0 :code] := "subject"
        [:direct :forward "Patient" 0 :code] := "organization")))

  (testing "one direct and one iterate forward param"
    (given (include/include-defs
             nil {"_include" "Observation:subject"
                  "_include:iterate" "Patient:organization"})
      [:direct :forward "Observation" 0 :code] := "subject"
      [:iterate :forward "Patient" 0 :code] := "organization"))

  (testing "one direct reverse param"
    (given (include/include-defs nil {"_revinclude" "Observation:subject"})
      [:direct :reverse :any 0 :source-type] := "Observation"
      [:direct :reverse :any 0 :code] := "subject")

    (testing "with target type"
      (given (include/include-defs nil {"_revinclude" "Observation:subject:Group"})
        [:direct :reverse "Group" 0 :source-type] := "Observation"
        [:direct :reverse "Group" 0 :code] := "subject")))

  (testing "two direct reverse params"
    (given (include/include-defs
             nil {"_revinclude" ["Observation:subject" "Condition:subject"]})
      [:direct :reverse :any 0 :source-type] := "Observation"
      [:direct :reverse :any 0 :code] := "subject"
      [:direct :reverse :any 1 :source-type] := "Condition"
      [:direct :reverse :any 1 :code] := "subject"))

  (testing "one direct and one iterate reverse param"
    (given (include/include-defs
             nil {"_revinclude" "Observation:subject"
                  "_revinclude:iterate" "Provenance:target"})
      [:direct :reverse :any 0 :source-type] := "Observation"
      [:direct :reverse :any 0 :code] := "subject"
      [:iterate :reverse :any 0 :source-type] := "Provenance"
      [:iterate :reverse :any 0 :code] := "target"))

  (testing "missing search parameter code"
    (testing "forward"
      (testing "direct"
        (is (nil? (include/include-defs nil {"_include" "Observation"})))

        (testing "with one valid parameter"
          (given (include/include-defs
                   nil {"_include" ["Observation" "Observation:encounter"]})
            [:direct :forward "Observation" count] := 1
            [:direct :forward "Observation" 0 :code] := "encounter"))

        (testing "with strict handling"
          (given (include/include-defs "strict" {"_include" "Observation"})
            ::anom/category := ::anom/incorrect
            ::anom/message := "Missing search parameter code in _include search parameter with source type `Observation`.")))

      (testing "iterate"
        (is (nil? (include/include-defs nil {"_include:iterate" "Observation"})))

        (testing "with strict handling"
          (given (include/include-defs "strict" {"_include:iterate" "Observation"})
            ::anom/category := ::anom/incorrect
            ::anom/message := "Missing search parameter code in _include search parameter with source type `Observation`."))))

    (testing "reverse"
      (testing "direct"
        (is (nil? (include/include-defs nil {"_revinclude" "Observation"})))

        (testing "with one valid parameter"
          (given (include/include-defs
                   nil {"_revinclude" ["Observation" "Observation:encounter"]})
            [:direct :reverse :any count] := 1
            [:direct :reverse :any 0 :source-type] := "Observation"
            [:direct :reverse :any 0 :code] := "encounter"))

        (testing "with strict handling"
          (given (include/include-defs "strict" {"_revinclude" "Observation"})
            ::anom/category := ::anom/incorrect
            ::anom/message := "Missing search parameter code in _include search parameter with source type `Observation`.")))

      (testing "iterate"
        (is (nil? (include/include-defs nil {"_revinclude:iterate" "Observation"})))

        (testing "with strict handling"
          (given (include/include-defs "strict" {"_revinclude:iterate" "Observation"})
            ::anom/category := ::anom/incorrect
            ::anom/message := "Missing search parameter code in _include search parameter with source type `Observation`."))))))
