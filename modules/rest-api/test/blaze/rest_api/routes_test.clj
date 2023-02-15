(ns blaze.rest-api.routes-test
  (:require
    [blaze.db.impl.search-param]
    [blaze.rest-api.routes :as routes]
    [blaze.rest-api.routes-spec]
    [blaze.test-util :as tu]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [juxt.iota :refer [given]]
    [reitit.ring]))


(st/instrument)


(test/use-fixtures :each tu/fixture)


(deftest resource-route-test
  (testing "read interaction"
    (given
      (routes/resource-route
        {:node ::node}
        [#:blaze.rest-api.resource-pattern
                {:type :default
                 :interactions
                 {:read
                  #:blaze.rest-api.interaction
                          {:handler (fn [_] ::read)}}}]
        {:kind "resource" :name "Patient"})
      [0] := "/Patient"
      [1 :fhir.resource/type] := "Patient"
      [2] := ["" {:name :Patient/type}]
      [3] := ["/_history" {:conflicting true}]
      [4] := ["/_search" {:name :Patient/search :conflicting true}]
      [5] := ["/__page" {:name :Patient/page :conflicting true}]
      [6 1 1 :name] := :Patient/instance
      [6 1 1 :conflicting] := true
      [6 1 1 :get :middleware 0 0 :name] := :db
      [6 1 1 :get :middleware 0 1] := ::node
      [6 1 1 :get :handler #(% {})] := ::read
      [6 2 0] := "/_history"
      [6 2 1] := ["" {:name :Patient/history-instance, :conflicting true}])))
