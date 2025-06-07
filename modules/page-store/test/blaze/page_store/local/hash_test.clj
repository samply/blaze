(ns blaze.page-store.local.hash-test
  (:require
   [blaze.page-store.local.hash :as hash]
   [blaze.page-store.local.hash-spec]
   [blaze.page-store.spec]
   [blaze.spec]
   [blaze.test-util :refer [satisfies-prop]]
   [clojure.spec.alpha :as s]
   [clojure.test :refer [deftest]]
   [clojure.test.check.properties :as prop]))

(deftest hash-clause-test
  (satisfies-prop 100
    (prop/for-all [clause (s/gen :blaze.db.query/clause)]
      (s/valid? :blaze.page-store.local/hash-code (hash/hash-clause clause)))))

(deftest hash-hashes-test
  (satisfies-prop 100
    (prop/for-all [clauses (s/gen :blaze.db.query/clauses)]
      (s/valid? :blaze.page-store.local/hash-code (hash/hash-hashes (mapv hash/hash-clause clauses))))))
