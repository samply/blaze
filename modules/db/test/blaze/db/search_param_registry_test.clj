(ns blaze.db.search-param-registry-test
  (:require
    [blaze.db.search-param-registry :as sr]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest]]
    [juxt.iota :refer [given]]
    [blaze.db.impl.codec :as codec]))


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def search-param-registry (sr/init-mem-search-param-registry))


(deftest linked-compartments
  (given
    (sr/linked-compartments
      search-param-registry
      {:resourceType "Condition"
       :id "id-0"
       :subject
       {:reference "Patient/id-1"}})
    [0 :c-hash] := (codec/c-hash "Patient")
    [0 :res-id codec/hex] := (codec/hex (codec/id-bytes "id-1"))
    1 := nil))
