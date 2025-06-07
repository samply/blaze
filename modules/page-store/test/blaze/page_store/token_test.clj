(ns blaze.page-store.token-test
  (:require
   [blaze.page-store.spec]
   [blaze.page-store.token :as token]
   [blaze.page-store.token-spec]
   [blaze.spec]
   [blaze.test-util :refer [satisfies-prop]]
   [clojure.spec.alpha :as s]
   [clojure.test :refer [deftest is]]
   [clojure.test.check.properties :as prop]))

(deftest generate-test
  ;; Base 16 = 4 bit per char = 64 * 4 bit = 256 bit
  (let [token (token/generate [["active" "true"]])]
    (is (string? token))
    (is (= 64 (count token))))

  (satisfies-prop 100
    (prop/for-all [clauses (s/gen :blaze.db.query/clauses)]
      (= 64 (count (token/generate clauses))))))
