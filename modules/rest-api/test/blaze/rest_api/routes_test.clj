(ns blaze.rest-api.routes-test
  (:require
    [blaze.db.impl.search-param]
    [blaze.rest-api.routes :as routes]
    [blaze.rest-api.routes-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [juxt.iota :refer [given]]
    [reitit.ring]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


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
      [1 :middleware] := []
      [1 :fhir.resource/type] := "Patient"
      [2] := ["" {:name :Patient/type}]
      [3] := ["/_history" {:conflicting true}]
      [4] := ["/_search" {:conflicting true}]
      [5 1 1 :name] := :Patient/instance
      [5 1 1 :conflicting] := true
      [5 1 1 :get :middleware 0 0 :name] := :db
      [5 1 1 :get :middleware 0 1] := ::node
      [5 1 1 :get :handler #(% {})] := ::read
      [5 2 0] := "/_history"
      [5 2 1] := ["" {:name :Patient/history-instance, :conflicting true}])))
