(ns blaze.rest-api-test
  (:require
    [blaze.rest-api :as rest-api]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest testing]]
    [juxt.iota :refer [given]]
    [reitit.core :as reitit]
    [reitit.ring]
    [taoensso.timbre :as log]))


(defn fixture [f]
  (st/instrument)
  (log/with-merged-config {:level :fatal} (f))
  (st/unstrument))


(test/use-fixtures :each fixture)


(def config
  #:blaze.rest-api
      {})


(def router
  (rest-api/router
    {:base-url "base-url-111523"
     :structure-definitions [{:kind "resource" :name "Patient"}]
     :resource-patterns
     [#:blaze.rest-api.resource-pattern
         {:type :default
          :interactions
          {:read
           #:blaze.rest-api.interaction
               {:handler (fn [_] {::handler ::read})}
           :vread
           #:blaze.rest-api.interaction
               {:handler (fn [_] {::handler ::vread})}
           :update
           #:blaze.rest-api.interaction
               {:handler (fn [_] {::handler ::update})}
           :delete
           #:blaze.rest-api.interaction
               {:handler (fn [_] {::handler ::delete})}
           :history-instance
           #:blaze.rest-api.interaction
               {:handler (fn [_] {::handler ::history-instance})}
           :history-type
           #:blaze.rest-api.interaction
               {:handler (fn [_] {::handler ::history-type})}
           :create
           #:blaze.rest-api.interaction
               {:handler (fn [_] {::handler ::create})}
           :search-type
           #:blaze.rest-api.interaction
               {:handler (fn [_] {::handler ::search-type})}}}]
     :operations
     [#:blaze.rest-api.operation
         {:code "evaluate-measure"
          :resource-types ["Measure"]
          :type-handler (fn [_] {::handler ::evaluate-measure-type})
          :instance-handler (fn [_] {::handler ::evaluate-measure-instance})}]}
    (fn [_])))


(comment
  (reitit/router-name router)
  (doseq [route (reitit/routes router)]
    (prn route)))


(deftest router-test
  (testing "Patient matches"
    (are [path request-method handler]
      (= handler
         (::handler
           @((get-in
               (reitit/match-by-path router path)
               [:result request-method :handler])
             {})))
      "/Patient" :get ::search-type
      "/Patient" :post ::create
      "/Patient/_history" :get ::history-type
      "/Patient/_search" :post ::search-type
      "/Patient/0" :get ::read
      "/Patient/0" :put ::update
      "/Patient/0" :delete ::delete
      "/Patient/0/_history" :get ::history-instance
      "/Patient/0/_history/42" :get ::vread
      "/Measure/$evaluate-measure" :get ::evaluate-measure-type
      "/Measure/$evaluate-measure" :post ::evaluate-measure-type
      "/Measure/0/$evaluate-measure" :get ::evaluate-measure-instance
      "/Measure/0/$evaluate-measure" :post ::evaluate-measure-instance))

  (testing "Patient instance POST is not allowed"
    (given ((reitit.ring/ring-handler router rest-api/default-handler)
            {:uri "/Patient/0" :request-method :post})
      :status := 405
      [:body :resourceType] := "OperationOutcome"
      [:body :issue 0 :severity] := "error"
      [:body :issue 0 :code] := "processing"))

  (testing "Observations are not found"
    (given ((reitit.ring/ring-handler router rest-api/default-handler)
            {:uri "/Observation" :request-method :get})
      :status := 404
      [:body :resourceType] := "OperationOutcome"
      [:body :issue 0 :severity] := "error"
      [:body :issue 0 :code] := "not-found")))


(deftest router-match-by-name-test
  (are [name params path]
    (= (reitit/match->path (reitit/match-by-name router name params)) path)

    :Patient/type
    {}
    "/Patient"

    :Patient/instance
    {:id "23"}
    "/Patient/23"

    :Patient/versioned-instance
    {:id "23" :vid "42"}
    "/Patient/23/_history/42"))


(deftest capabilities-handler-test
  (testing "minimal config"
    (given
      (-> ((rest-api/capabilities-handler
             {:base-url "base-url-131713"
              :version "version-131640"
              :structure-definitions
              [{:kind "resource" :name "Patient"}]})
           {})
          :body)
      :resourceType := "CapabilityStatement"
      :status := "active"
      :kind := "instance"
      [:software :name] := "Blaze"
      [:software :version] := "version-131640"
      [:implementation :url] := "base-url-131713"
      :fhirVersion := "4.0.0"
      :format := ["application/fhir+json"]))

  (testing "one interaction"
    (given
      (-> ((rest-api/capabilities-handler
             {:base-url "base-url-131713"
              :version "version-131640"
              :structure-definitions
              [{:kind "resource" :name "Patient"}]
              :resource-patterns
              [#:blaze.rest-api.resource-pattern
                  {:type "Patient"
                   :interactions
                   {:read
                    #:blaze.rest-api.interaction
                        {:handler (fn [_])}}}]})
           {})
          :body)
      :resourceType := "CapabilityStatement"
      [:rest 0 :resource 0 :type] := "Patient"
      [:rest 0 :resource 0 :interaction 0 :code] := "read"))

  (testing "one operation"
    (given
      (-> ((rest-api/capabilities-handler
             {:base-url "base-url-131713"
              :version "version-131640"
              :structure-definitions
              [{:kind "resource" :name "Measure"}]
              :resource-patterns
              [#:blaze.rest-api.resource-pattern
                  {:type "Measure"
                   :interactions
                   {:read
                    #:blaze.rest-api.interaction
                        {:handler (fn [_])}}}]
              :operations
              [#:blaze.rest-api.operation
                  {:code "evaluate-measure"
                   :def-uri
                   "http://hl7.org/fhir/OperationDefinition/Measure-evaluate-measure"
                   :resource-types ["Measure"]
                   :type-handler (fn [_])
                   :instance-handler (fn [_])}]})
           {})
          :body)
      :resourceType := "CapabilityStatement"
      [:rest 0 :resource 0 :type] := "Measure"
      [:rest 0 :resource 0 :operation 0 :name] := "evaluate-measure"
      [:rest 0 :resource 0 :operation 0 :definition] :=
      "http://hl7.org/fhir/OperationDefinition/Measure-evaluate-measure")))
