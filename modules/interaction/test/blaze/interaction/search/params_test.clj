(ns blaze.interaction.search.params-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.interaction.search.params :as params]
   [blaze.interaction.search.params-spec]
   [blaze.module.test-util :refer [given-failed-future]]
   [blaze.page-store.protocols :as p]
   [blaze.preference.handling :as-alias handling]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def page-store
  (reify p/PageStore))

(deftest decode-test
  (testing "unsupported sort parameter"
    (given-failed-future (params/decode page-store
                                        ::handling/lenient
                                        {"_sort" "a,b"})
      ::anom/category := ::anom/unsupported))

  (testing "invalid include parameter"
    (given-failed-future (params/decode page-store
                                        ::handling/strict
                                        {"_include" "Observation"})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Missing search parameter code in _include search parameter with source type `Observation`."))

  (testing "decoding clauses from query params"
    (given @(params/decode page-store ::handling/strict {"foo" "bar"})
      :clauses := [["foo" "bar"]]
      :token := nil))

  (testing "decoding clauses from token"
    (given @(params/decode
             (reify p/PageStore
               (-get [_ token]
                 (assert (= (str/join (repeat 64 "A")) token))
                 (ac/completed-future [["foo" "bar"]])))
             ::handling/strict
             {"__token" (str/join (repeat 64 "A"))})
      :clauses := [["foo" "bar"]]
      :token := (str/join (repeat 64 "A"))))

  (testing "token not found"
    (given-failed-future
     (params/decode
      (reify p/PageStore
        (-get [_ token]
          (assert (= (str/join (repeat 64 "A")) token))
          (ac/completed-future (ba/not-found "Not Found"))))
      ::handling/strict
      {"__token" (str/join (repeat 64 "A"))})
      ::anom/category := ::anom/not-found
      :http/status := nil))

  (testing "decoding _elements"
    (testing "one element"
      (doseq [handling [::handling/strict ::handling/lenient nil]]
        (given @(params/decode page-store handling {"_elements" "a"})
          :elements := [:a])))

    (testing "two elements"
      (doseq [handling [::handling/strict ::handling/lenient nil]]
        (given @(params/decode page-store handling {"_elements" "a,b"})
          :elements := [:a :b])))

    (testing "two elements with space after comma"
      (doseq [handling [::handling/strict ::handling/lenient nil]]
        (given @(params/decode page-store handling {"_elements" "a, b"})
          :elements := [:a :b])))

    (testing "two elements with space before comma"
      (doseq [handling [::handling/strict ::handling/lenient nil]]
        (given @(params/decode page-store handling {"_elements" "a ,b"})
          :elements := [:a :b])))

    (testing "two elements with space before and after comma"
      (doseq [handling [::handling/strict ::handling/lenient nil]]
        (given @(params/decode page-store handling {"_elements" "a , b"})
          :elements := [:a :b])))

    (testing "three elements"
      (doseq [handling [::handling/strict ::handling/lenient nil]]
        (given @(params/decode page-store handling {"_elements" "a,b,c"})
          :elements := [:a :b :c])))

    (testing "two elements parameters"
      (doseq [handling [::handling/strict ::handling/lenient nil]]
        (given @(params/decode page-store handling {"_elements" ["a" "b"]})
          :elements := [:a :b]))))

  (testing "decoding _summary"
    (testing "true"
      (doseq [handling [::handling/strict ::handling/lenient nil]]
        (given @(params/decode page-store handling {"_summary" "true"})
          :summary? := false
          :summary := "true")))

    (testing "count"
      (doseq [handling [::handling/strict ::handling/lenient nil]]
        (given @(params/decode page-store handling {"_summary" "count"})
          :summary? := true
          :summary := "count")))

    (testing "invalid counts"
      (doseq [handling [::handling/lenient nil]]
        (given @(params/decode page-store handling {"_summary" "counts"})
          :summary? := false
          :summary := nil))

      (given-failed-future (params/decode page-store ::handling/strict {"_summary" "counts"})
        ::anom/category := ::anom/unsupported
        ::anom/message := "Unsupported _summary search param with value(s): counts"))

    (testing "count and invalid counts"
      (doseq [handling [::handling/strict ::handling/lenient nil]]
        (given @(params/decode page-store handling {"_summary" ["count" "counts"]})
          :summary? := true
          :summary := "count")))

    (testing "unsupported text"
      (doseq [handling [::handling/lenient nil]]
        (given @(params/decode page-store handling {"_summary" "text"})
          :summary? := false
          :summary := nil))

      (given-failed-future (params/decode page-store ::handling/strict {"_summary" "text"})
        ::anom/category := ::anom/unsupported
        ::anom/message := "Unsupported _summary search param with value(s): text"))

    (testing "unsupported data and text"
      (doseq [handling [::handling/lenient nil]]
        (given @(params/decode page-store handling {"_summary" ["data" "text"]})
          :summary? := false
          :summary := nil))

      (given-failed-future (params/decode page-store ::handling/strict {"_summary" ["data" "text"]})
        ::anom/category := ::anom/unsupported
        ::anom/message := "Unsupported _summary search param with value(s): data, text"))))
