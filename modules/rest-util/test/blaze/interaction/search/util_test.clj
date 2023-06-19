(ns blaze.interaction.search.util-test
  (:require
    [blaze.interaction.search.util :as search-util]
    [blaze.test-util :as tu]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [juxt.iota :refer [given]]
    [reitit.core :as reitit]))


(st/instrument)


(test/use-fixtures :each tu/fixture)


(def router
  (reitit/router
    [["/Patient" {:name :Patient/type}]]
    {:syntax :bracket
     :path ""}))


(def context
  {:blaze/base-url ""
   ::reitit/router router})


(deftest entry-test
  (testing "default mode is match"
    (given (search-util/entry context {:fhir/type :fhir/Patient :id "0"})
      :fhir/type := :fhir.Bundle/entry
      :fullUrl := "/Patient/0"
      [:resource :fhir/type] := :fhir/Patient
      [:resource :id] := "0"
      [:search :mode] #fhir/code "match"))

  (testing "search mode include"
    (given (search-util/entry context {:fhir/type :fhir/Patient :id "0"}
                              search-util/include)
      :fhir/type := :fhir.Bundle/entry
      :fullUrl := "/Patient/0"
      [:resource :fhir/type] := :fhir/Patient
      [:resource :id] := "0"
      [:search :mode] #fhir/code "include")))
