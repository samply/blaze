(ns blaze.interaction.search.params-test
  (:require
    [blaze.async.comp :as ac]
    [blaze.interaction.search.params :as params]
    [blaze.interaction.search.params-spec]
    [blaze.page-store.protocols :as p]
    [blaze.test-util :refer [given-failed-future]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [cognitect.anomalies :as anom]
    [cuerdas.core :as str]
    [juxt.iota :refer [given]]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def page-store
  (reify p/PageStore))


(deftest decode-test
  (testing "invalid include parameter"
    (given-failed-future (params/decode page-store
                                        :blaze.preference.handling/strict
                                        {"_include" "Observation"})
      ::anom/category := ::anom/incorrect))

  (testing "decoding clauses from query params"
    (given @(params/decode
             page-store
             :blaze.preference.handling/strict
             {"foo" "bar"})
      :clauses := [["foo" "bar"]]
      :token := nil))

  (testing "decoding clauses from token"
    (given @(params/decode
             (reify p/PageStore
               (-get [_ token]
                 (assert (= (str/repeat "A" 32) token))
                 (ac/completed-future [["foo" "bar"]])))
             :blaze.preference.handling/strict
             {"__token" (str/repeat "A" 32)})
      :clauses := [["foo" "bar"]]
      :token := (str/repeat "A" 32))))
