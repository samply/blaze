(ns blaze.page-store.hash-test
  (:require
   [blaze.page-store.hash :as hash]
   [blaze.page-store.hash-spec]
   [blaze.page-store.spec]
   [blaze.spec]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [clojure.test.check.properties :as prop]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest hash-clause-test
  (satisfies-prop 100
    (prop/for-all [clause (s/gen :blaze.db.query/clause)]
      (s/valid? :blaze.page-store/hash-code (hash/hash-clause clause)))))

(deftest hash-clause-unambiguous-test
  (testing "different splits of the same characters"
    (is (not= (hash/hash-clause ["code" "a" "b"])
              (hash/hash-clause ["code" "ab"])))
    (is (not= (hash/hash-clause ["code" "ab" "c"])
              (hash/hash-clause ["code" "a" "bc"])))
    (is (not= (hash/hash-clause ["codea" "b"])
              (hash/hash-clause ["code" "ab"]))))

  (testing "keywords and strings with the same text"
    (is (not= (hash/hash-clause [:sort "a" :asc])
              (hash/hash-clause [":sort" "a" ":asc"]))))

  (testing "different clauses have different hashes"
    (satisfies-prop 100
      (prop/for-all [clause-1 (s/gen :blaze.db.query/clause)
                     clause-2 (s/gen :blaze.db.query/clause)]
        (= (= clause-1 clause-2)
           (= (hash/hash-clause clause-1) (hash/hash-clause clause-2)))))))

(deftest hash-hashes-test
  (let [h1 (hash/hash-clause ["code" "a"])
        h2 (hash/hash-clause ["code" "b"])]

    (testing "one disjunction of two clauses differs from a conjunction of two clauses"
      (is (not= (hash/hash-hashes [[h1 h2]])
                (hash/hash-hashes [[h1] [h2]]))))

    (testing "different disjunction boundaries"
      (is (not= (hash/hash-hashes [[h1 h2] [h1]])
                (hash/hash-hashes [[h1] [h2 h1]]))))))

(deftest hash-clauses-test
  (let [h1 (hash/hash-clause ["code" "a"])
        h2 (hash/hash-clause ["code" "b"])
        h3 (hash/hash-clause ["active" "true"])]

    (testing "one clause"
      (given (hash/hash-clauses [["code" "a"]])
        :hash := (hash/hash-hashes [[h1]])
        :hashes := [[h1]]
        :clauses := {h1 ["code" "a"]}))

    (testing "a disjunction of one clause equals that clause"
      (is (= (hash/hash-clauses [[["code" "a"]]])
             (hash/hash-clauses [["code" "a"]]))))

    (testing "a disjunction of two clauses and one clause"
      (given (hash/hash-clauses [[["code" "a"] ["code" "b"]] ["active" "true"]])
        :hash := (hash/hash-hashes [[h1 h2] [h3]])
        :hashes := [[h1 h2] [h3]]
        :clauses := {h1 ["code" "a"] h2 ["code" "b"] h3 ["active" "true"]}))

    (testing "a disjunction of two clauses differs from a conjunction of them"
      (is (not= (:hash (hash/hash-clauses [[["code" "a"] ["code" "b"]]]))
                (:hash (hash/hash-clauses [["code" "a"] ["code" "b"]]))))))

  (satisfies-prop 100
    (prop/for-all [clauses (s/gen :blaze.db.query/clauses)]
      (s/valid? :blaze.page-store/hash-code (:hash (hash/hash-clauses clauses))))))
