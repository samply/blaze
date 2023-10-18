(ns blaze.page-store.cassandra.codec-test
  (:require
    [blaze.page-store.cassandra.codec :as codec]
    [blaze.spec]
    [blaze.test-util :as tu :refer [satisfies-prop]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.string :as str]
    [clojure.test :as test :refer [deftest]]
    [clojure.test.check.properties :as prop]))


(st/instrument)


(test/use-fixtures :each tu/fixture)


(def token (str/join (repeat 32 "A")))


(deftest encode-decode-test
  (satisfies-prop 100
    (prop/for-all [clauses (s/gen :blaze.db.query/clauses)]
      (= clauses (codec/decode (codec/encode clauses) token)))))
