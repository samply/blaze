(ns blaze.interaction.search.params-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.interaction.search.params :as params]
   [blaze.interaction.search.params-spec]
   [blaze.page-store.protocols :as p]
   [blaze.preference.handling :as-alias handling]
   [blaze.test-util :as tu :refer [given-failed-future]]
   [blaze.value-set-expander.protocols :as vsep]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest is testing]]
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
                                        {"_sort" "a,b"}
                                        nil)
      ::anom/category := ::anom/unsupported))

  (testing "invalid include parameter"
    (given-failed-future (params/decode page-store
                                        ::handling/strict
                                        {"_include" "Observation"}
                                        nil)
      ::anom/category := ::anom/incorrect
      ::anom/message := "Missing search parameter code in _include search parameter with source type `Observation`."))

  (testing "decoding clauses from query params"
    (given @(params/decode page-store ::handling/strict {"foo" "bar"} nil)
      :clauses := [["foo" "bar"]]
      :token := nil))

  (testing "decoding clauses from token"
    (given @(params/decode
             (reify p/PageStore
               (-get [_ token]
                 (assert (= (str/join (repeat 64 "A")) token))
                 (ac/completed-future [["foo" "bar"]])))
             ::handling/strict
             {}
             {"__token" (str/join (repeat 64 "A"))})
      :clauses := [["foo" "bar"]]
      :token := (str/join (repeat 64 "A"))))

  (testing "token in query params is ignored"
    (given @(params/decode page-store ::handling/strict
                           {"foo" "bar" "__token" (str/join (repeat 64 "A"))}
                           nil)
      :clauses := [["foo" "bar"]]
      :token := nil))

  (testing "paging params in query params are ignored"
    (given @(params/decode page-store ::handling/strict
                           {"__page-type" "Patient"
                            "__page-id" "0"
                            "__page-id-stack" [""]
                            "__page-offset" "1"}
                           nil)
      :page-type := nil
      :page-id := nil
      :page-id-stack := []
      :page-offset := 0))

  (testing "decoding paging params"
    (given @(params/decode page-store ::handling/strict {}
                           {"__page-type" "Patient"
                            "__page-id" "0"
                            "__page-id-stack" [""]
                            "__page-offset" "1"})
      :page-type := "Patient"
      :page-id := "0"
      :page-id-stack := [""]
      :page-offset := 1))

  (testing "token not found"
    (given-failed-future
     (params/decode
      (reify p/PageStore
        (-get [_ token]
          (assert (= (str/join (repeat 64 "A")) token))
          (ac/completed-future (ba/not-found "Not Found"))))
      ::handling/strict
      {}
      {"__token" (str/join (repeat 64 "A"))})
      ::anom/category := ::anom/not-found
      :http/status := nil))

  (testing "token with client clauses"
    (let [token (str/join (repeat 64 "A"))
          page-store (reify p/PageStore
                       (-get [_ _]
                         (ac/completed-future [["code" "system-130010|code-130016"]])))]
      (given @(params/decode page-store ::handling/strict {"code:in" "url-130029"}
                             {"__token" token})
        :clauses := [["code" "system-130010|code-130016"]]
        :token := token
        :client-clauses := [["code:in" "url-130029"]]))

    (testing "without client clauses"
      (given @(params/decode
               (reify p/PageStore
                 (-get [_ _]
                   (ac/completed-future [["code" "code-130224"]])))
               ::handling/strict {} {"__token" (str/join (repeat 64 "A"))})
        :clauses := [["code" "code-130224"]]
        :client-clauses := nil))

    (testing "with unsupported sort parameter"
      (given-failed-future
       (params/decode
        (reify p/PageStore
          (-get [_ _]
            (ac/completed-future [["code" "code-151012"]])))
        ::handling/strict {"_sort" "a,b"} {"__token" (str/join (repeat 64 "A"))})
        ::anom/category := ::anom/unsupported
        ::anom/message := "More than one sort parameter is unsupported.")))

  (testing "invalid token"
    (doseq [token ["invalid-token-175424"
                   (str/join (repeat 63 "A"))
                   (str/join (repeat 65 "A"))
                   (str/join (repeat 64 "a"))
                   (str/join (repeat 64 "G"))]]
      (given-failed-future
       (params/decode page-store ::handling/strict {} {"__token" token})
        ::anom/category := ::anom/incorrect
        ::anom/message := (format "Invalid token `%s`." token)
        :http/status := 422)))

  (testing "decoding _elements"
    (testing "one element"
      (doseq [handling [::handling/strict ::handling/lenient nil]]
        (given @(params/decode page-store handling {"_elements" "a"} nil)
          :elements := [:a])))

    (testing "two elements"
      (doseq [handling [::handling/strict ::handling/lenient nil]]
        (given @(params/decode page-store handling {"_elements" "a,b"} nil)
          :elements := [:a :b])))

    (testing "two elements with space after comma"
      (doseq [handling [::handling/strict ::handling/lenient nil]]
        (given @(params/decode page-store handling {"_elements" "a, b"} nil)
          :elements := [:a :b])))

    (testing "two elements with space before comma"
      (doseq [handling [::handling/strict ::handling/lenient nil]]
        (given @(params/decode page-store handling {"_elements" "a ,b"} nil)
          :elements := [:a :b])))

    (testing "two elements with space before and after comma"
      (doseq [handling [::handling/strict ::handling/lenient nil]]
        (given @(params/decode page-store handling {"_elements" "a , b"} nil)
          :elements := [:a :b])))

    (testing "three elements"
      (doseq [handling [::handling/strict ::handling/lenient nil]]
        (given @(params/decode page-store handling {"_elements" "a,b,c"} nil)
          :elements := [:a :b :c])))

    (testing "two elements parameters"
      (doseq [handling [::handling/strict ::handling/lenient nil]]
        (given @(params/decode page-store handling {"_elements" ["a" "b"]} nil)
          :elements := [:a :b]))))

  (testing "decoding _summary"
    (testing "true"
      (doseq [handling [::handling/strict ::handling/lenient nil]]
        (given @(params/decode page-store handling {"_summary" "true"} nil)
          :summary? := false
          :summary := "true")))

    (testing "count"
      (doseq [handling [::handling/strict ::handling/lenient nil]]
        (given @(params/decode page-store handling {"_summary" "count"} nil)
          :summary? := true
          :summary := "count")))

    (testing "false"
      (doseq [handling [::handling/strict ::handling/lenient nil]]
        (given @(params/decode page-store handling {"_summary" "false"} nil)
          :summary? := false
          :summary := nil)))

    (testing "invalid counts"
      (doseq [handling [::handling/lenient nil]]
        (given @(params/decode page-store handling {"_summary" "counts"} nil)
          :summary? := false
          :summary := nil))

      (given-failed-future (params/decode page-store ::handling/strict {"_summary" "counts"} nil)
        ::anom/category := ::anom/unsupported
        ::anom/message := "Unsupported _summary search param with value(s): counts"))

    (testing "count and invalid counts"
      (doseq [handling [::handling/strict ::handling/lenient nil]]
        (given @(params/decode page-store handling {"_summary" ["count" "counts"]} nil)
          :summary? := true
          :summary := "count")))

    (testing "false and unsupported text"
      (testing "is tolerated, because at least one value is supported"
        (doseq [handling [::handling/strict ::handling/lenient nil]]
          (given @(params/decode page-store handling {"_summary" ["false" "text"]} nil)
            :summary? := false
            :summary := nil))))

    (testing "unsupported text"
      (doseq [handling [::handling/lenient nil]]
        (given @(params/decode page-store handling {"_summary" "text"} nil)
          :summary? := false
          :summary := nil))

      (given-failed-future (params/decode page-store ::handling/strict {"_summary" "text"} nil)
        ::anom/category := ::anom/unsupported
        ::anom/message := "Unsupported _summary search param with value(s): text"))

    (testing "unsupported data and text"
      (doseq [handling [::handling/lenient nil]]
        (given @(params/decode page-store handling {"_summary" ["data" "text"]} nil)
          :summary? := false
          :summary := nil))

      (given-failed-future (params/decode page-store ::handling/strict {"_summary" ["data" "text"]} nil)
        ::anom/category := ::anom/unsupported
        ::anom/message := "Unsupported _summary search param with value(s): data, text"))))

(defn- value-set-expander [expanded-clauses]
  (reify vsep/ValueSetExpander
    (-expand-clauses [_ type clauses]
      (assert (= "Condition" type))
      (assert (= [["code:in" "url-110249"]] clauses))
      (ac/completed-future expanded-clauses))))

(deftest expand-value-sets-test
  (testing "the clauses are replaced by the expanded ones"
    (given @(params/expand-value-sets
             (value-set-expander [["code" "system-110315|code-110321"]])
             "Condition" {:clauses [["code:in" "url-110249"]] :page-size 50})
      :clauses := [["code" "system-110315|code-110321"]]
      :page-size := 50
      :unsatisfiable? := nil))

  (testing "clauses that can't match anything are kept"
    (given @(params/expand-value-sets
             (value-set-expander nil)
             "Condition" {:clauses [["code:in" "url-110249"]]})
      :clauses := [["code:in" "url-110249"]]
      :unsatisfiable? := true))

  (testing "expansion errors"
    (given-failed-future
     (params/expand-value-sets
      (reify vsep/ValueSetExpander
        (-expand-clauses [_ _ _]
          (ac/completed-future (ba/fault "msg-110716"))))
      "Condition" {:clauses [["code:in" "url-110249"]]})
      ::anom/category := ::anom/fault
      ::anom/message := "msg-110716"))

  (testing "params without clauses stay the same"
    (is (= {:page-size 50}
           @(params/expand-value-sets
             (reify vsep/ValueSetExpander)
             "Condition" {:page-size 50})))))
