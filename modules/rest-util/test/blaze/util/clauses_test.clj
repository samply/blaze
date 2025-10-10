(ns blaze.util.clauses-test
  (:require
   [blaze.db.tx-log.spec]
   [blaze.test-util :as tu]
   [blaze.util.clauses :as uc]
   [blaze.util.clauses-spec]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest clauses-test
  (testing "nil"
    (is (empty? (uc/clauses nil)))
    (is (empty? (uc/search-clauses nil))))

  (testing "empty map"
    (is (empty? (uc/clauses {})))
    (is (empty? (uc/search-clauses {}))))

  (testing "empty key and value"
    (is (empty? (uc/clauses {"" ""})))
    (is (empty? (uc/search-clauses {"" ""}))))

  (testing "empty key and two empty values"
    (is (empty? (uc/clauses {"" ["" ""]}))))

  (testing "empty value"
    (is (empty? (uc/clauses {"a" ""}))))

  (testing "two empty values"
    (is (empty? (uc/clauses {"a" ["" ""]}))))

  (testing "one normal and one empty value"
    (is (= [["a" "b"]] (uc/clauses {"a" ["b" ""]}))))

  (testing "one key"
    (testing "and one value"
      (is (= [["a" "b"]] (uc/clauses {"a" "b"})))

      (testing "with two parts"
        (is (= [["a" "b" "c"]] (uc/clauses {"a" "b,c"}))))

      (testing "with three parts"
        (is (= [["a" "b" "c" "d"]] (uc/clauses {"a" "b,c,d"}))))

      (testing "duplicate parts are removed"
        (is (= [["a" "b" "c"]] (uc/clauses {"a" "b,c,b"})))
        (is (= [["a" "b" "c" "d"]] (uc/clauses {"a" "b,c,b,d"})))))

    (testing "and two values"
      (is (= [["a" "b"] ["a" "c"]] (uc/clauses {"a" ["b" "c"]})))

      (testing "that are duplicates"
        (is (= [["a" "b"]] (uc/clauses {"a" ["b" "b"]})))))

    (testing "with leading whitespace"
      (is (= [["a" "b"]] (uc/clauses {"a" " b"})))

      (testing "with two parts"
        (is (= [["a" "b" "c"]] (uc/clauses {"a" " b, c"})))))

    (testing "with trailing whitespace"
      (is (= [["a" "b"]] (uc/clauses {"a" "b "})))

      (testing "with two parts"
        (is (= [["a" "b" "c"]] (uc/clauses {"a" "b ,c "})))))

    (testing "with leading and trailing whitespace"
      (is (= [["a" "b"]] (uc/clauses {"a" " b "})))

      (testing "with two parts"
        (is (= [["a" "b" "c"]] (uc/clauses {"a" " b , c "})))

        (testing "that are duplicates"
          (is (= [["a" "b"]] (uc/clauses {"a" " b , b "})))))))

  (testing "one sort param"
    (testing "ascending"
      (is (= [[:sort "a" :asc]] (uc/clauses {"_sort" "a"})))
      (is (= [] (uc/search-clauses {"_sort" "a"})))

      (testing "with leading whitespace"
        (is (= [[:sort "a" :asc]] (uc/clauses {"_sort" " a"}))))

      (testing "with trailing whitespace"
        (is (= [[:sort "a" :asc]] (uc/clauses {"_sort" "a "})))))

    (testing "descending"
      (is (= [[:sort "a" :desc]] (uc/clauses {"_sort" "-a"})))

      (testing "with leading whitespace"
        (is (= [[:sort "a" :desc]] (uc/clauses {"_sort" " -a"}))))

      (testing "with trailing whitespace"
        (is (= [[:sort "a" :desc]] (uc/clauses {"_sort" "-a "})))))

    (testing "with two parts is unsupported"
      (given (uc/clauses {"_sort" "a,b"})
        ::anom/category := ::anom/unsupported
        ::anom/message := "More than one sort parameter is unsupported.")))

  (testing "one sort and one other param"
    (testing "sort param comes always first"
      (is (= [[:sort "a" :asc] ["b" "c"]]
             (uc/clauses {"_sort" "a" "b" "c"})
             (uc/clauses {"b" "c" "_sort" "a"}))))

    (testing "with two parts is unsupported"
      (given (uc/clauses {"_sort" "a,b" "c" "d"})
        ::anom/category := ::anom/unsupported
        ::anom/message := "More than one sort parameter is unsupported.")))

  (testing "removes redundant sort clause"
    (is (= [["_id" "b"]]
           (uc/clauses {"_sort" "a" "_id" "b"})
           (uc/clauses {"_id" "b" "_sort" "a"})))

    (is (= [["_id" "b"] ["c" "d"]]
           (uc/clauses {"_sort" "a" "_id" "b" "c" "d"})
           (uc/clauses {"_id" "b" "_sort" "a" "c" "d"})
           (uc/clauses {"_id" "b" "c" "d" "_sort" "a"}))))

  (testing "removes keys"
    (are [key] (empty? (uc/clauses {key "bar"}))
      "_foo" "__token" "__t" "_total"))

  (testing "keeps keys"
    (are [key] (seq (uc/clauses {key "bar"}))
      "_id" "_list" "_profile" "_tag" "_lastUpdated" "_has")))
