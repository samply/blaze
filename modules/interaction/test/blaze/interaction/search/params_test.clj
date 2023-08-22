(ns blaze.interaction.search.params-test
  (:require
    [blaze.anomaly :as ba]
    [blaze.async.comp :as ac]
    [blaze.fhir.test-util :refer [given-failed-future]]
    [blaze.interaction.search.params :as params]
    [blaze.interaction.search.params-spec]
    [blaze.page-store.protocols :as p]
    [blaze.test-util :as tu]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [cognitect.anomalies :as anom]
    [cuerdas.core :as c-str]
    [juxt.iota :refer [given]]))


(st/instrument)


(test/use-fixtures :each tu/fixture)


(def page-store
  (reify p/PageStore))


(deftest decode-test
  (testing "unsupported sort parameter"
    (given-failed-future (params/decode page-store
                                        :blaze.preference.handling/lenient
                                        {"_sort" "a,b"})
      ::anom/category := ::anom/unsupported))

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
                  (assert (= (c-str/repeat "A" 32) token))
                  (ac/completed-future [["foo" "bar"]])))
              :blaze.preference.handling/strict
              {"__token" (c-str/repeat "A" 32)})
      :clauses := [["foo" "bar"]]
      :token := (c-str/repeat "A" 32)))

  (testing "token not found"
    (given-failed-future
      (params/decode
        (reify p/PageStore
          (-get [_ token]
            (assert (= (c-str/repeat "A" 32) token))
            (ac/completed-future (ba/not-found "Not Found"))))
        :blaze.preference.handling/strict
        {"__token" (c-str/repeat "A" 32)})
      ::anom/category := ::anom/not-found
      :http/status := nil))

  (testing "decoding _elements"
    (testing "one element"
      (given @(params/decode page-store :blaze.preference.handling/strict
                             {"_elements" "a"})
        :elements := [:a]))

    (testing "two elements"
      (given @(params/decode page-store :blaze.preference.handling/strict
                             {"_elements" "a,b"})
        :elements := [:a :b]))

    (testing "two elements with space after comma"
      (given @(params/decode page-store :blaze.preference.handling/strict
                             {"_elements" "a, b"})
        :elements := [:a :b]))

    (testing "two elements with space before comma"
      (given @(params/decode page-store :blaze.preference.handling/strict
                             {"_elements" "a ,b"})
        :elements := [:a :b]))

    (testing "two elements with space before and after comma"
      (given @(params/decode page-store :blaze.preference.handling/strict
                             {"_elements" "a , b"})
        :elements := [:a :b]))

    (testing "three elements"
      (given @(params/decode page-store :blaze.preference.handling/strict
                             {"_elements" "a,b,c"})
        :elements := [:a :b :c]))))
