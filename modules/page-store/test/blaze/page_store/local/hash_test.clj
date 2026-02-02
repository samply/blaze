(ns blaze.page-store.local.hash-test
  (:require
   [blaze.page-store.local :as-alias local]
   [blaze.page-store.local.hash :as hash]
   [blaze.page-store.local.hash-spec]
   [blaze.page-store.spec]
   [blaze.spec]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest]]
   [clojure.test.check.properties :as prop]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest hash-clause-test
  (satisfies-prop 100
    (prop/for-all [clause (s/gen :blaze.db.query/clause)]
      (s/valid? ::local/hash-code (hash/hash-clause clause)))))
