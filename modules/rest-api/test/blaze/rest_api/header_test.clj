(ns blaze.rest-api.header-test
  (:require
   [blaze.rest-api.header :as header]
   [blaze.rest-api.header-spec]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest if-none-match->tags-test
  (testing "works with nil"
    (is (set? (header/if-none-match->tags nil)))
    (is (empty? (header/if-none-match->tags nil))))

  (testing "works with all input strings"
    (satisfies-prop 10000
      (prop/for-all [s gen/string]
        (let [tags (header/if-none-match->tags s)]
          (and (set? tags) (every? string? tags)))))))
