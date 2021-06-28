(ns blaze.interaction.search.nav-test
  (:require
    [blaze.interaction.search.nav :as nav]
    [blaze.interaction.search.nav-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def ^:private match
  {:data
   {:blaze/base-url ""
    :blaze/context-path ""
    :fhir.resource/type "Observation"}
   :path "/Observation"})


(deftest url-test
  (testing "url encoding of clauses"
    (testing "Observation code with URL"
      (is (= "base-url-110407/Observation?code=http%3A%2F%2Floinc.org%7C8480-6&__t=1"
             (nav/url "base-url-110407" match nil
                      [["code" "http://loinc.org|8480-6"]] 1 nil)))))

  (testing "two clauses with the same code"
    (is (= "base-url-110421/Observation?combo-code-value-quantity=8480-6%24ge140&combo-code-value-quantity=8462-4%24ge90&__t=1"
           (nav/url "base-url-110421" match nil
                    [["combo-code-value-quantity" "8480-6$ge140"]
                     ["combo-code-value-quantity" "8462-4$ge90"]]
                    1 nil))))

  (testing "with include-defs"
    (testing "empty"
      (is (= "base-url-110439/Observation?__t=1"
             (nav/url "base-url-110439" match
                      {:include-defs {:direct {} :iterate {}}} nil 1
                      nil))))

    (testing "one direct forward include param"
      (is (= "base-url-110542/Observation?_include=Observation%3Asubject&__t=1"
             (nav/url
               "base-url-110542"
               match
               {:include-defs
                {:direct {:forward {"Observation" [{:code "subject"}]}}}}
               nil
               1
               nil)))

      (testing "with target type"
        (is (= "base-url-110553/Observation?_include=Observation%3Asubject%3AGroup&__t=1"
               (nav/url
                 "base-url-110553"
                 match
                 {:include-defs
                  {:direct
                   {:forward
                    {"Observation" [{:code "subject" :target-type "Group"}]}}}}
                 nil
                 1
                 nil)))))

    (testing "two direct forward include params"
      (is (= "base-url-110604/Observation?_include=MedicationStatement%3Amedication&_include=Medication%3Amanufacturer&__t=1"
             (nav/url
               "base-url-110604"
               match
               {:include-defs
                {:direct
                 {:forward
                  {"MedicationStatement" [{:code "medication"}]
                   "Medication" [{:code "manufacturer"}]}}}}
               nil
               1
               nil))))

    (testing "one iterate forward include param"
      (is (= "base-url-110614/Observation?_include%3Aiterate=Observation%3Asubject&__t=1"
             (nav/url
               "base-url-110614"
               match
               {:include-defs
                {:iterate {:forward {"Observation" [{:code "subject"}]}}}}
               nil
               1
               nil))))

    (testing "one direct and one iterate forward include param"
      (is (= "base-url-110624/Observation?_include=MedicationStatement%3Amedication&_include%3Aiterate=Medication%3Amanufacturer&__t=1"
             (nav/url
               "base-url-110624"
               match
               {:include-defs
                {:direct
                 {:forward
                  {"MedicationStatement" [{:code "medication"}]}}
                 :iterate
                 {:forward
                  {"Medication" [{:code "manufacturer"}]}}}}
               nil
               1
               nil))))

    (testing "one direct reverse include param"
      (is (= "base-url-110635/Observation?_revinclude=Observation%3Asubject&__t=1"
             (nav/url
               "base-url-110635"
               match
               {:include-defs
                {:direct
                 {:reverse
                  {:any [{:source-type "Observation" :code "subject"}]}}}}
               nil
               1
               nil)))

      (testing "with target type"
        (is (= "base-url-110645/Observation?_revinclude=Observation%3Asubject%3AGroup&__t=1"
               (nav/url
                 "base-url-110645"
                 match
                 {:include-defs
                  {:direct
                   {:reverse
                    {"Group" [{:source-type "Observation" :code "subject"}]}}}}
                 nil
                 1
                 nil)))))

    (testing "one iterate reverse include param"
      (is (= "base-url-110654/Observation?_revinclude%3Aiterate=Observation%3Asubject&__t=1"
             (nav/url
               "base-url-110654"
               match
               {:include-defs
                {:iterate
                 {:reverse
                  {:any [{:source-type "Observation" :code "subject"}]}}}}
               nil
               1
               nil))))))
