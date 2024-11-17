(ns blaze.db.impl.search-param.util-test
  (:require
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
  (is (= (u/format-skip-indexing-msg "value-132537" "url-132522" "type-132528")
         "Skip indexing value `value-132537` of type `:fhir/string` for search parameter `url-132522` with type `type-132528` because the rule is missing.")))

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
