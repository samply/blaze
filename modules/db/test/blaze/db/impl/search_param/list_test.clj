(ns blaze.db.impl.search-param.list-test
  (:require
    [blaze.db.impl.search-param-spec]
    [blaze.db.search-param-registry :as sr]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is]]
    [taoensso.timbre :as log]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (log/set-level! :trace)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def search-param-registry (sr/init-search-param-registry))


(def list-param
  (sr/get search-param-registry "_list" "Patient"))


(deftest code-test
  (is (= "_list" (:code list-param))))


(deftest name-test
  (is (= "_list" (:name list-param))))
