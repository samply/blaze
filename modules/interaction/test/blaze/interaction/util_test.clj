(ns blaze.interaction.util-test
  (:require
   [blaze.db.api :as d]
   [blaze.db.api-stub :refer [mem-node-config with-system-data]]
   [blaze.db.tx-log.spec]
   [blaze.interaction.util :as iu]
   [blaze.interaction.util-spec]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [are deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest etag->t-test
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
             (iu/clauses {"b" "c" "_sort" "a"}))))

    (testing "with two parts is unsupported"
      (given (iu/clauses {"_sort" "a,b" "c" "d"})
        ::anom/category := ::anom/unsupported
        ::anom/message := "More than one sort parameter is unsupported.")))

  (testing "removes redundant sort clause"
    (is (= [["_id" "b"]]
           (iu/clauses {"_sort" "a" "_id" "b"})
           (iu/clauses {"_id" "b" "_sort" "a"})))

    (is (= [["_id" "b"] ["c" "d"]]
           (iu/clauses {"_sort" "a" "_id" "b" "c" "d"})
           (iu/clauses {"_id" "b" "_sort" "a" "c" "d"})
           (iu/clauses {"_id" "b" "c" "d" "_sort" "a"}))))

  (testing "removes keys"
    (are [key] (empty? (iu/clauses {key "bar"}))
      "_foo"
      "__token"
      "__t")))

(deftest put-tx-op-test
  (testing "on empty database"
    (with-system [{:blaze.db/keys [node]} mem-node-config]
      (testing "with empty if-match header"
        (given (iu/update-tx-op (d/db node) {:fhir/type :fhir/Patient :id "0"} "" nil)
          ::anom/category := ::anom/conflict
          ::anom/message := "Empty precondition failed on `Patient/0`."
          :http/status := 412))

      (testing "with invalid if-match header"
        (satisfies-prop 1000
          (prop/for-all [if-match (gen/such-that (complement str/blank?) gen/string)]
            (let [anom (iu/update-tx-op (d/db node) {:fhir/type :fhir/Patient :id "0"} if-match nil)]
              (and (= ::anom/conflict (::anom/category anom))
                   (= (format "Precondition `%s` failed on `Patient/0`." if-match) (::anom/message anom))
                   (= 412 (:http/status anom)))))))

      (testing "without preconditions"
        (is (= (iu/update-tx-op (d/db node) {:fhir/type :fhir/Patient :id "0"} nil nil)
               [:put {:fhir/type :fhir/Patient :id "0"}])))

      (testing "with some precondition"
        (satisfies-prop 10
          (prop/for-all [ts (gen/vector (gen/choose 1 10) 1 3)]
            (is (= (iu/update-tx-op (d/db node) {:fhir/type :fhir/Patient :id "0"} (str/join "," (map (partial format "W/\"%d\"") ts)) nil)
                   [:put {:fhir/type :fhir/Patient :id "0"} (into [:if-match] ts)])))))))

  (testing "with an existing, identical patient; the other patient is there in order to show that the t depends only on the matching patient"
    (let [hash #blaze/hash"9D4C35D80AFF36B057C99523FDF18423110AAB69ED4F744EB85445F9C7D16443"
          male-patient {:fhir/type :fhir/Patient :id "0" :gender #fhir/code"male"}]
      (with-system-data [{:blaze.db/keys [node]} mem-node-config]
        [[[:put {:fhir/type :fhir/Patient :id "0" :gender #fhir/code"female"}]]
         [[:put male-patient]]
         [[:put {:fhir/type :fhir/Patient :id "1"}]]]

        (testing "without preconditions"
          (testing "generates a keep op with the hash of the current patient version"
            (is (= (iu/update-tx-op (d/db node) male-patient nil nil)
                   [:keep "Patient" "0" hash]))))

        (testing "with one matching if-match"
          (testing "generates a keep op with the hash of the current patient version and the t of the if-match header"
            (is (= (iu/update-tx-op (d/db node) male-patient "W/\"2\"" nil)
                   [:keep "Patient" "0" hash [2]]))))

        (testing "with newer if-match"
          (testing "generates a put op because the newer t could still match inside the transaction"
            (is (= (iu/update-tx-op (d/db node) male-patient "W/\"3\"" nil)
                   [:put male-patient [:if-match 3]]))))

        (testing "with older if-match"
          (testing "returns an conflict, because the older t will never match again"
            (given (iu/update-tx-op (d/db node) male-patient "W/\"1\"" nil)
              ::anom/category := ::anom/conflict
              ::anom/message := "Precondition `W/\"1\"` failed on `Patient/0`."
              :http/status := 412)))

        (testing "with the same and older if-match"
          (testing "generates a keep op with the hash of the current patient version"
            (is (= (iu/update-tx-op (d/db node) male-patient "W/\"1\",W/\"2\"" nil)
                   [:keep "Patient" "0" hash [2]]))))

        (testing "with the same and newer if-match"
          (testing "generates a keep op with the hash of the current patient version"
            (is (= (iu/update-tx-op (d/db node) male-patient "W/\"2\",W/\"3\"" nil)
                   [:keep "Patient" "0" hash [2 3]]))))

        (testing "with newer and older if-match"
          (testing "generates a put op because the newer t could still match inside the transaction and removes the older t that can't match anymore"
            (is (= (iu/update-tx-op (d/db node) male-patient "W/\"1\",W/\"3\"" nil)
                   [:put male-patient [:if-match 3]]))))))))
