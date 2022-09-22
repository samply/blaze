(ns blaze.interaction.util-test
  (:require
    [blaze.interaction.util :as iu]
    [blaze.interaction.util-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest etag->t-test
  (testing "accepts nil"
    (is (nil? (iu/etag->t nil))))

  (testing "valid ETag"
    (is (= 1 (iu/etag->t "W/\"1\""))))

  (testing "invalid ETag"
    (are [s] (nil? (iu/etag->t s))
      "foo"
      "W/1"
      "W/\"a\"")))


(deftest clauses-test
  (testing "nil"
    (is (empty? (iu/clauses nil)))
    (is (empty? (iu/search-clauses nil))))

  (testing "empty map"
    (is (empty? (iu/clauses {})))
    (is (empty? (iu/search-clauses {}))))

  (testing "empty key and value"
    (is (= [["" ""]] (iu/clauses {"" ""})))
    (is (= [["" ""]] (iu/search-clauses {"" ""}))))

  (testing "empty key and two empty values"
    (is (= [["" ""] ["" ""]] (iu/clauses {"" ["" ""]}))))

  (testing "one key"
    (testing "and one value"
      (is (= [["a" "b"]] (iu/clauses {"a" "b"})))

      (testing "with two parts"
        (is (= [["a" "b" "c"]] (iu/clauses {"a" "b,c"}))))

      (testing "with three parts"
        (is (= [["a" "b" "c" "d"]] (iu/clauses {"a" "b,c,d"})))))

    (testing "and two values"
      (is (= [["a" "b"] ["a" "c"]] (iu/clauses {"a" ["b" "c"]}))))

    (testing "with leading whitespace"
      (is (= [["a" "b"]] (iu/clauses {"a" " b"})))

      (testing "with two parts"
        (is (= [["a" "b" "c"]] (iu/clauses {"a" " b, c"})))))

    (testing "with trailing whitespace"
      (is (= [["a" "b"]] (iu/clauses {"a" "b "})))

      (testing "with two parts"
        (is (= [["a" "b" "c"]] (iu/clauses {"a" "b ,c "})))))

    (testing "with leading and trailing whitespace"
      (is (= [["a" "b"]] (iu/clauses {"a" " b "})))

      (testing "with two parts"
        (is (= [["a" "b" "c"]] (iu/clauses {"a" " b , c "}))))))

  (testing "one sort param"
    (testing "ascending"
      (is (= [[:sort "a" :asc]] (iu/clauses {"_sort" "a"})))
      (is (= [] (iu/search-clauses {"_sort" "a"})))

      (testing "with leading whitespace"
        (is (= [[:sort "a" :asc]] (iu/clauses {"_sort" " a"}))))

      (testing "with trailing whitespace"
        (is (= [[:sort "a" :asc]] (iu/clauses {"_sort" "a "})))))

    (testing "descending"
      (is (= [[:sort "a" :desc]] (iu/clauses {"_sort" "-a"})))

      (testing "with leading whitespace"
        (is (= [[:sort "a" :desc]] (iu/clauses {"_sort" " -a"}))))

      (testing "with trailing whitespace"
        (is (= [[:sort "a" :desc]] (iu/clauses {"_sort" "-a "})))))

    (testing "with two parts is unsupported"
      (given (iu/clauses {"_sort" "a,b"})
        ::anom/category := ::anom/unsupported
        ::anom/message := "More than one sort parameter is unsupported.")))

  (testing "one sort and one other param"
    (testing "sort param comes always first"
      (is (= [[:sort "a" :asc] ["b" "c"]]
             (iu/clauses {"_sort" "a" "b" "c"})
             (iu/clauses {"b" "c" "_sort" "a" }))))

    (testing "with two parts is unsupported"
      (given (iu/clauses {"_sort" "a,b" "c" "d"})
        ::anom/category := ::anom/unsupported
        ::anom/message := "More than one sort parameter is unsupported.")))

  (testing "removes keys"
    (are [key] (empty? (iu/clauses {key "bar"}))
      "_foo"
      "__token"
      "__t")))
