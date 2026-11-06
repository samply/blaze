(ns blaze.fhir.search-clauses-test
  (:require
   [blaze.fhir.search-clauses :as fsc]
   [blaze.fhir.search-clauses-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest clauses-test
  (testing "nil"
    (is (empty? (fsc/clauses nil)))
    (is (empty? (fsc/search-clauses nil))))

  (testing "empty map"
    (is (empty? (fsc/clauses {})))
    (is (empty? (fsc/search-clauses {}))))

  (testing "empty key and value"
    (is (empty? (fsc/clauses {"" ""})))
    (is (empty? (fsc/search-clauses {"" ""}))))

  (testing "empty key and two empty values"
    (is (empty? (fsc/clauses {"" ["" ""]}))))

  (testing "empty value"
    (is (empty? (fsc/clauses {"a" ""}))))

  (testing "two empty values"
    (is (empty? (fsc/clauses {"a" ["" ""]}))))

  (testing "one normal and one empty value"
    (is (= [["a" "b"]] (fsc/clauses {"a" ["b" ""]}))))

  (testing "one key"
    (testing "and one value"
      (is (= [["a" "b"]] (fsc/clauses {"a" "b"})))

      (testing "with two parts"
        (is (= [["a" "b" "c"]] (fsc/clauses {"a" "b,c"}))))

      (testing "with three parts"
        (is (= [["a" "b" "c" "d"]] (fsc/clauses {"a" "b,c,d"}))))

      (testing "duplicate parts are removed"
        (is (= [["a" "b" "c"]] (fsc/clauses {"a" "b,c,b"})))
        (is (= [["a" "b" "c" "d"]] (fsc/clauses {"a" "b,c,b,d"})))))

    (testing "and two values"
      (is (= [["a" "b"] ["a" "c"]] (fsc/clauses {"a" ["b" "c"]})))

      (testing "that are duplicates"
        (is (= [["a" "b"]] (fsc/clauses {"a" ["b" "b"]})))))

    (testing "with leading whitespace"
      (is (= [["a" "b"]] (fsc/clauses {"a" " b"})))

      (testing "with two parts"
        (is (= [["a" "b" "c"]] (fsc/clauses {"a" " b, c"})))))

    (testing "with trailing whitespace"
      (is (= [["a" "b"]] (fsc/clauses {"a" "b "})))

      (testing "with two parts"
        (is (= [["a" "b" "c"]] (fsc/clauses {"a" "b ,c "})))))

    (testing "with leading and trailing whitespace"
      (is (= [["a" "b"]] (fsc/clauses {"a" " b "})))

      (testing "with two parts"
        (is (= [["a" "b" "c"]] (fsc/clauses {"a" " b , c "})))

        (testing "that are duplicates"
          (is (= [["a" "b"]] (fsc/clauses {"a" " b , b "})))))))

  (testing "one sort param"
    (testing "ascending"
      (is (= [[:sort "a" :asc]] (fsc/clauses {"_sort" "a"})))
      (is (= [] (fsc/search-clauses {"_sort" "a"})))

      (testing "with leading whitespace"
        (is (= [[:sort "a" :asc]] (fsc/clauses {"_sort" " a"}))))

      (testing "with trailing whitespace"
        (is (= [[:sort "a" :asc]] (fsc/clauses {"_sort" "a "})))))

    (testing "descending"
      (is (= [[:sort "a" :desc]] (fsc/clauses {"_sort" "-a"})))

      (testing "with leading whitespace"
        (is (= [[:sort "a" :desc]] (fsc/clauses {"_sort" " -a"}))))

      (testing "with trailing whitespace"
        (is (= [[:sort "a" :desc]] (fsc/clauses {"_sort" "-a "})))))

    (testing "with two parts is unsupported"
      (given (fsc/clauses {"_sort" "a,b"})
        ::anom/category := ::anom/unsupported
        ::anom/message := "More than one sort parameter is unsupported.")))

  (testing "one sort and one other param"
    (testing "sort param comes always first"
      (is (= [[:sort "a" :asc] ["b" "c"]]
             (fsc/clauses {"_sort" "a" "b" "c"})
             (fsc/clauses {"b" "c" "_sort" "a"}))))

    (testing "with two parts is unsupported"
      (given (fsc/clauses {"_sort" "a,b" "c" "d"})
        ::anom/category := ::anom/unsupported
        ::anom/message := "More than one sort parameter is unsupported.")))

  (testing "removes redundant sort clause"
    (is (= [["_id" "b"]]
           (fsc/clauses {"_sort" "a" "_id" "b"})
           (fsc/clauses {"_id" "b" "_sort" "a"})))

    (is (= [["_id" "b"] ["c" "d"]]
           (fsc/clauses {"_sort" "a" "_id" "b" "c" "d"})
           (fsc/clauses {"_id" "b" "_sort" "a" "c" "d"})
           (fsc/clauses {"_id" "b" "c" "d" "_sort" "a"}))))

  (testing "removes keys"
    (are [key] (empty? (fsc/clauses {key "bar"}))
      "_foo" "__token" "__t" "_total"))

  (testing "keeps keys"
    (are [key] (seq (fsc/clauses {key "bar"}))
      "_id" "_list" "_profile" "_security" "_source" "_tag" "_lastUpdated"
      "_has")))
