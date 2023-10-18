(ns blaze.interaction.search.nav-test
  (:require
    [blaze.async.comp :as ac]
    [blaze.interaction.search.nav :as nav]
    [blaze.interaction.search.nav-spec]
    [blaze.page-store.protocols :as p]
    [blaze.test-util :as tu]
    [clojure.spec.test.alpha :as st]
    [clojure.string :as str]
    [clojure.test :as test :refer [deftest is testing]]))


(st/instrument)


(test/use-fixtures :each tu/fixture)


(def match
  {:data
   {:blaze/base-url ""
    :fhir.resource/type "Observation"}
   :path "/Observation"})


(deftest url-test
  (testing "url encoding of clauses"
    (testing "Observation code with URL"
      (is (= "base/Observation?code=http%3A%2F%2Floinc.org%7C8480-6&__t=1"
             (nav/url "base" match nil [["code" "http://loinc.org|8480-6"]] 1 nil))))

    (testing "Observation code with multiple values"
      (is (= "base/Observation?code=8480-6%2C8310-5&__t=1"
             (nav/url "base" match nil [["code" "8480-6,8310-5"]] 1 nil)))))

  (testing "two clauses with the same code"
    (is (= "base-url-110421/Observation?combo-code-value-quantity=8480-6%24ge140&combo-code-value-quantity=8462-4%24ge90&__t=1"
           (nav/url "base-url-110421" match nil
                    [["combo-code-value-quantity" "8480-6$ge140"]
                     ["combo-code-value-quantity" "8462-4$ge90"]]
                    1 nil))))

  (testing "sort clause"
    (testing "ascending"
      (is (= "base-url-110407/Observation?_sort=foo&__t=1"
             (nav/url "base-url-110407" match nil [[:sort "foo" :asc]] 1 nil))))

    (testing "descending"
      (is (= "base-url-110407/Observation?_sort=-foo&__t=1"
             (nav/url "base-url-110407" match nil [[:sort "foo" :desc]] 1 nil)))))

  (testing "_summary"
    (is (= "base-url-110407/Observation?_summary=true&__t=1"
           (nav/url "base-url-110407" match {:summary "true"} [] 1 nil))))

  (testing "_elements"
    (testing "zero element"
      (is (= "base-url-110407/Observation?__t=1"
             (nav/url "base-url-110407" match {:elements []} [] 1 nil))))

    (testing "one element"
      (is (= "base-url-110407/Observation?_elements=a&__t=1"
             (nav/url "base-url-110407" match {:elements [:a]} [] 1 nil))))

    (testing "two elements"
      (is (= "base-url-110407/Observation?_elements=a%2Cb&__t=1"
             (nav/url "base-url-110407" match {:elements [:a :b]} [] 1 nil)))))

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


(def clauses-1
  [["foo" "bar"]])


(def page-store
  (reify p/PageStore
    (-put [_ clauses]
      (assert (= clauses-1 clauses))
      (ac/completed-future (str/join (repeat 32 "A"))))))


(deftest token-url-test
  (testing "stores clauses and puts token into the query params"
    (is (= "base-url-195241/Observation?__token=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA&__t=195312"
           @(nav/token-url! page-store "base-url-195241" match {} clauses-1 195312 nil))))

  (testing "reuses existing token"
    (is (= "base-url-195241/Observation?__token=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB&__t=195312"
           @(nav/token-url!
              (reify p/PageStore
                (-put [_ _]
                  (assert false)))
              "base-url-195241" match
              {:token "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB"}
              clauses-1
              195312
              nil))))

  (testing "_summary"
    (is (= "base-url-134538/Observation?__token=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB&_summary=true&__t=1"
           @(nav/token-url!
              (reify p/PageStore
                (-put [_ _]
                  (assert false)))
              "base-url-134538" match
              {:token "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB"
               :summary "true"}
              []
              1
              nil))))

  (testing "_elements"
    (is (= "base-url-134538/Observation?__token=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB&_elements=a&__t=1"
           @(nav/token-url!
              (reify p/PageStore
                (-put [_ _]
                  (assert false)))
              "base-url-134538" match
              {:token "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB"
               :elements [:a]}
              []
              1
              nil)))))
