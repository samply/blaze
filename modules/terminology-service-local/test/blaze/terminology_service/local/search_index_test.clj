(ns blaze.terminology-service.local.search-index-test
  (:require
   [blaze.terminology-service.local.search-index :as search-index]
   [blaze.terminology-service.local.search-index-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def ^:private concepts
  {"diabetes"
   {:display {:value "Diabetes mellitus"}
    :designation [{:value {:value "Sugar disease"}}]}

   "hypertension"
   {:display {:value "Essential hypertension"}}

   "blood-pressure"
   {:display {:value "Blood pressure measurement"}}

   "systolic-bp"
   {:display {:value "Systolic blood pressure"}}

   "no-display"
   {}})

(deftest build-test
  (testing "building an index does not throw"
    (is (some? (search-index/build concepts)))))

(deftest search-test
  (let [index (search-index/build concepts)]
    (testing "single term prefix match"
      (is (= ["diabetes"] (search-index/search index "diab" 10))))

    (testing "full word match"
      (is (= ["diabetes"] (search-index/search index "diabetes" 10))))

    (testing "case insensitive"
      (is (= ["diabetes"] (search-index/search index "Diab" 10))))

    (testing "multi-word prefix match"
      (is (= ["blood-pressure"]
             (search-index/search index "blood meas" 10))))

    (testing "matching multiple concepts"
      (let [results (search-index/search index "blood pressure" 10)]
        (is (= 2 (count results)))
        (is (every? #{"blood-pressure" "systolic-bp"} (set results)))))

    (testing "fuzzy match with typo"
      (is (= ["diabetes"] (search-index/search index "diabtes" 10))))

    (testing "fuzzy match with two character typo"
      (is (= ["hypertension"] (search-index/search index "hypertensoin" 10))))

    (testing "no match"
      (is (empty? (search-index/search index "xyz" 10))))

    (testing "empty filter"
      (is (nil? (search-index/search index "" 10))))

    (testing "designation match"
      (is (= ["diabetes"] (search-index/search index "sugar" 10))))

    (testing "max results limits output"
      (let [results (search-index/search index "blood" 1)]
        (is (= 1 (count results))))))

  (testing "code restriction"
    (let [index (search-index/build concepts)]
      (testing "restricts to given codes"
        (is (= ["blood-pressure"]
               (search-index/search index "blood" 10
                                    ["blood-pressure"]))))

      (testing "excludes non-matching codes"
        (is (empty?
             (search-index/search index "blood" 10
                                  ["diabetes" "hypertension"]))))

      (testing "nil codes means no restriction"
        (let [results (search-index/search index "blood" 10 nil)]
          (is (= 2 (count results)))))))

  (testing "concepts with only designation, no display"
    (let [index (search-index/build
                 {"only-desig"
                  {:designation [{:value {:value "Only designation text"}}]}})]
      (is (= ["only-desig"] (search-index/search index "designation" 10)))))

  (testing "empty concepts map"
    (let [index (search-index/build {})]
      (is (empty? (search-index/search index "anything" 10)))))

  (testing "concepts without searchable text are skipped"
    (let [index (search-index/build {"empty" {}})]
      (is (empty? (search-index/search index "anything" 10)))))

  (testing "designation without value is skipped"
    (let [index (search-index/build
                 {"partial"
                  {:display {:value "Visible"}
                   :designation [{:value {:value "Has value"}}
                                 {:value {}}
                                 {}]}})]
      (is (= ["partial"] (search-index/search index "visible" 10)))
      (is (= ["partial"] (search-index/search index "has" 10)))))

  (testing "multiple designations without display"
    (let [index (search-index/build
                 {"no-disp"
                  {:designation [{:value {:value "First term"}}
                                 {:value {:value "Second term"}}]}})]
      (is (= ["no-disp"] (search-index/search index "first" 10)))
      (is (= ["no-disp"] (search-index/search index "second" 10)))))

  (testing "concept with multiple designations"
    (let [index (search-index/build
                 {"multi"
                  {:display {:value "Primary name"}
                   :designation [{:value {:value "Synonym one"}}
                                 {:value {:value "Synonym two"}}]}})]
      (is (= ["multi"] (search-index/search index "synonym" 10)))
      (is (= ["multi"] (search-index/search index "primary" 10)))))

  (testing "whitespace-only filter"
    (let [index (search-index/build concepts)]
      (is (nil? (search-index/search index "   " 10))))))

(deftest build-with-modules-test
  (testing "building an index with modules does not throw"
    (is (some? (search-index/build-with-modules
                [["123" "Diabetes mellitus" "mod-a"]
                 ["456" "Hypertension" "mod-b"]])))))

(deftest search-with-module-ids-test
  (let [index (search-index/build-with-modules
               [["123" "Diabetes mellitus" "mod-a"]
                ["456" "Essential hypertension" "mod-a"]
                ["789" "Diabetes insipidus" "mod-b"]])]
    (testing "no module-id filter returns all matches"
      (let [results (search-index/search index "diab" 10)]
        (is (= 2 (count results)))
        (is (every? #{"123" "789"} (set results)))))

    (testing "module-id filter restricts results"
      (is (= ["123"] (search-index/search index "diab" 10 nil ["mod-a"]))))

    (testing "multiple module-ids"
      (let [results (search-index/search index "diab" 10 nil ["mod-a" "mod-b"])]
        (is (= 2 (count results)))))

    (testing "non-matching module-id returns empty"
      (is (empty? (search-index/search index "diab" 10 nil ["mod-c"]))))

    (testing "combined code and module-id filter"
      (is (= ["123"] (search-index/search index "diab" 10 ["123"] ["mod-a"])))
      (is (empty? (search-index/search index "diab" 10 ["123"] ["mod-b"]))))

    (testing "empty text is skipped"
      (let [index (search-index/build-with-modules
                   [["123" "" "mod-a"]])]
        (is (empty? (search-index/search index "anything" 10)))))))
