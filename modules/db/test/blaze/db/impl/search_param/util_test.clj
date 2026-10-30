(ns blaze.db.impl.search-param.util-test
  (:refer-clojure :exclude [hash])
  (:require
   [blaze.db.api :as d]
   [blaze.db.impl.codec :as codec]
   [blaze.coll.core :as coll]
   [blaze.db.impl.index.index-handle :as ih]
   [blaze.db.impl.index.index-handle-spec]
   [blaze.db.impl.index.single-version-id :as svi]
   [blaze.db.impl.index.single-version-id-spec]
   [blaze.db.impl.search-param.util :as u]
   [blaze.db.impl.search-param.util-spec]
   [blaze.db.test-util :as dtu :refer [with-system-data]]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest separate-op-test
  (are [value tuple] (= tuple (u/separate-op value))
    "1" [:eq "1"]
    " 1" [:eq "1"]
    "1 " [:eq "1"]
    " 1 " [:eq "1"]
    "eq1" [:eq "1"]
    "eq 1" [:eq "1"]
    "ne1" [:ne "1"]
    "fo1" [:eq "fo1"]
    "1ne" [:eq "1ne"]))

(deftest compile-id-test
  (testing "valid"
    (is (= "id-1" (u/compile-id "id-1"))))

  (testing "invalid"
    (doseq [id ["" (apply str (repeat 65 "a")) "a b" "Ł"]]
      (given (u/compile-id id)
        ::anom/category := ::anom/incorrect
        ::anom/message := (format "Invalid resource id `%s`." id)))))

(deftest format-skip-indexing-msg-test
  (is (= (u/format-skip-indexing-msg #fhir/string "value-132537" "url-132522" "type-132528")
         "Skip indexing value `String{id=null, extension=[], value='value-132537'}` of type `:fhir/string` for search parameter `url-132522` with type `type-132528` because the rule is missing.")))

(deftest soundex-test
  (testing "question mark from issue #903"
    (is (empty? (u/soundex "?"))))

  (testing "unmapped character"
    (are [s] (nil? (u/soundex s))
      "Õ"
      "ü"
      "Müller"))

  (testing "similar words"
    (are [a b] (= (u/soundex a) (u/soundex b))
      "Doe" "Day"
      "John" "Jane"))

  (testing "random strings"
    (satisfies-prop 10000
      (prop/for-all [s gen/string]
        (let [r (u/soundex s)]
          (or (string? r) (nil? r)))))))

(deftest canonical-parts-test
  (are [canonical parts] (= parts (u/canonical-parts canonical))
    "|" ["" nil]
    "url" ["url" nil]
    "url|" ["url" nil]
    "url|." ["url" nil]
    "url|1" ["url" ["1"]]
    "url|1.2" ["url" ["1" "1.2"]]
    "url|1.2-alpha" ["url" ["1" "1.2-alpha"]]
    "url|1.2.3" ["url" ["1" "1.2"]]
    "url|1.2.3-draft" ["url" ["1" "1.2"]])

  (testing "random strings"
    (satisfies-prop 10000
      (prop/for-all [s gen/string]
        (let [[url version-parts] (u/canonical-parts s)]
          (and (string? url)
               (every? string? version-parts)
               (<= (count version-parts) 2)))))))

(def ^:private hash
  #blaze/hash"C9ADE22457D5AD750735B6B166E3CE8D6878D09B64C2C2868DCB6DE4C9EFBD4F")

(def ^:private hash-2
  #blaze/hash"2D2BE7B10F1DEE3B4F3C5E4B5C8A9E0D1F2A3B4C5D6E7F8091A2B3C4D5E6F708")

(defn- svi [id hash]
  (svi/single-version-id id hash))

(defn- index-handle [svi & more]
  (reduce ih/conj (ih/from-single-version-id svi) more))

(deftest resource-value-test
  (with-system-data [{:blaze.db/keys [node]} dtu/config]
    [[[:put {:fhir/type :fhir/Patient :id "0"
             :name [#fhir/HumanName{:family #fhir/string "Fred"
                                    :given [#fhir/string "Bob"]}]}]]]

    (with-open [batch-db (d/new-batch-db (d/db node))]
      (let [c-hash (codec/c-hash "name")
            tid (codec/tid "Patient")]
        (testing "returns the value starting with the prefix"
          (is (= (codec/string "fred")
                 (u/resource-value batch-db c-hash tid "0" (codec/string "fr"))))
          (is (= (codec/string "bob")
                 (u/resource-value batch-db c-hash tid "0" (codec/string "bo")))))

        (testing "returns nil if no value starts with the prefix"
          (is (nil? (u/resource-value batch-db c-hash tid "0" (codec/string "x")))))))))

(deftest by-id-grouper-test
  (testing "empty input"
    (is (empty? (into [] u/by-id-grouper []))))

  (testing "single version id"
    (is (= [(index-handle (svi "0" hash))]
           (into [] u/by-id-grouper [(svi "0" hash)]))))

  (testing "consecutive single version ids with the same id are grouped"
    (is (= [(index-handle (svi "0" hash) (svi "0" hash-2))
            (index-handle (svi "1" hash))]
           (into [] u/by-id-grouper [(svi "0" hash)
                                     (svi "0" hash-2)
                                     (svi "1" hash)]))))

  (testing "the same hash is only added once"
    (is (= [(index-handle (svi "0" hash))]
           (into [] u/by-id-grouper [(svi "0" hash) (svi "0" hash)]))))

  (testing "non-consecutive single version ids with the same id aren't grouped"
    (is (= [(index-handle (svi "0" hash))
            (index-handle (svi "1" hash))
            (index-handle (svi "0" hash-2))]
           (into [] u/by-id-grouper [(svi "0" hash)
                                     (svi "1" hash)
                                     (svi "0" hash-2)]))))

  (testing "works with reduced values"
    (testing "on single id"
      (let [id "0"]
        (is (= (transduce
                u/by-id-grouper
                (completing (fn [_ x] (reduced (ih/id x))))
                nil
                [(svi/single-version-id id hash)])
               id))))

    (testing "on subsequent different id"
      (let [id-0 "0"
            id-1 "1"]
        (is (= (transduce
                u/by-id-grouper
                (completing (fn [_ x] (reduced (ih/id x))))
                nil
                [(svi/single-version-id id-0 hash)
                 (svi/single-version-id id-1 hash)])
               id-0))))))

(deftest distinct-by-id-test
  (testing "empty input"
    (is (empty? (into [] u/distinct-by-id []))))

  (testing "distinct ids are kept in order"
    (is (= [{:id "1"} {:id "0"}]
           (into [] u/distinct-by-id [{:id "1"} {:id "0"}]))))

  (testing "only the first one of the same id is kept"
    (is (= [{:id "0" :t 1} {:id "1" :t 1}]
           (into [] u/distinct-by-id [{:id "0" :t 1} {:id "1" :t 1}
                                      {:id "0" :t 2}]))))

  (testing "each reduction starts with no ids seen"
    (let [coll (coll/eduction u/distinct-by-id [{:id "0"} {:id "0"}])]
      (testing "reduce"
        (is (= [{:id "0"}] (into [] coll)))
        (is (= [{:id "0"}] (into [] coll))))

      (testing "iterator"
        (is (= [{:id "0"}] (seq coll)))
        (is (= [{:id "0"}] (seq coll))))))

  (testing "init delegates to the reducing function"
    (is (= [] ((u/distinct-by-id conj)))))

  (testing "works with reduced values"
    (is (= {:id "0"}
           (transduce
            u/distinct-by-id
            (completing (fn [_ x] (reduced x)))
            nil
            [{:id "0"} {:id "1"}])))))
