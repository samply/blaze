(ns blaze.db.impl.search-param.util-test
  (:refer-clojure :exclude [hash])
  (:require
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.index-handle :as ih]
   [blaze.db.impl.index.index-handle-spec]
   [blaze.db.impl.index.single-version-id :as svi]
   [blaze.db.impl.index.single-version-id-spec]
   [blaze.db.impl.search-param.util :as u]
   [blaze.db.impl.search-param.util-spec]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
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

(deftest by-id-grouper-test
  (testing "works with reduced values"
    (testing "on single id"
      (let [id (codec/id-byte-string "0")]
        (is (= (transduce
                u/by-id-grouper
                (completing (fn [_ x] (reduced (ih/id x))))
                nil
                [(svi/single-version-id id hash)])
               id))))

    (testing "on subsequent different id"
      (let [id-0 (codec/id-byte-string "0")
            id-1 (codec/id-byte-string "1")]
        (is (= (transduce
                u/by-id-grouper
                (completing (fn [_ x] (reduced (ih/id x))))
                nil
                [(svi/single-version-id id-0 hash)
                 (svi/single-version-id id-1 hash)])
               id-0))))))
