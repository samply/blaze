(ns blaze.interaction.history.util-test
  (:require
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.interaction.history.util :refer [build-entry]]
    [clojure.test :as test :refer [deftest testing]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [juxt.iota :refer [given]]
    [reitit.core :as reitit])
  (:import [java.time Instant]))


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn stub-type-url [router type url]
  (st/instrument
    [`fhir-util/type-url]
    {:spec
     {`fhir-util/type-url
      (s/fspec
        :args (s/cat :router #{router} :type #{type})
        :ret #{url})}
     :stub
     #{`fhir-util/type-url}}))


(def router
  (reitit/router
    [["/Patient" {:name :Patient/type}]
     ["/Patient/{id}" {:name :Patient/instance}]]
    {:syntax :bracket}))


(deftest build-entry-test
  (testing "Initial version with server assigned id"
    (given
      (build-entry
        router
        (with-meta
          {:resourceType "Patient"
           :id "0"
           :meta {:versionId "1"}}
          {:blaze.db/op :create
           :blaze.db/num-changes 1
           :blaze.db/tx {:blaze.db.tx/instant Instant/EPOCH}}))
      :fullUrl := "/Patient/0"
      [:request :method] := "POST"
      [:request :url] := "/Patient"
      [:resource :resourceType] := "Patient"
      [:resource :id] := "0"
      [:response :status] := "201"
      [:response :lastModified] := "1970-01-01T00:00:00Z"
      [:response :etag] := "W/\"1\""))


  (testing "Initial version with client assigned id"
    (given
      (build-entry
        router
        (with-meta
          {:resourceType "Patient"
           :id "0"
           :meta {:versionId "1"}}
          {:blaze.db/op :put
           :blaze.db/num-changes 1
           :blaze.db/tx {:blaze.db.tx/instant Instant/EPOCH}}))
      :fullUrl := "/Patient/0"
      [:request :method] := "PUT"
      [:request :url] := "/Patient/0"
      [:resource :resourceType] := "Patient"
      [:resource :id] := "0"
      [:response :status] := "201"
      [:response :lastModified] := "1970-01-01T00:00:00Z"
      [:response :etag] := "W/\"1\""))


  (testing "Non-initial version"
    (given
      (build-entry
        router
        (with-meta
          {:resourceType "Patient"
           :id "0"
           :meta {:versionId "2"}}
          {:blaze.db/op :put
           :blaze.db/num-changes 2
           :blaze.db/tx {:blaze.db.tx/instant Instant/EPOCH}}))
      :fullUrl := "/Patient/0"
      [:request :method] := "PUT"
      [:request :url] := "/Patient/0"
      [:resource :resourceType] := "Patient"
      [:resource :id] := "0"
      [:response :status] := "200"
      [:response :lastModified] := "1970-01-01T00:00:00Z"
      [:response :etag] := "W/\"2\""))


  (testing "Deleted version"
    (given
      (build-entry
        router
        (with-meta
          {:resourceType "Patient"
           :id "0"
           :meta {:versionId "2"}}
          {:blaze.db/op :delete
           :blaze.db/num-changes 2
           :blaze.db/tx {:blaze.db.tx/instant Instant/EPOCH}}))
      :fullUrl := "/Patient/0"
      [:request :method] := "DELETE"
      [:request :url] := "/Patient/0"
      [:response :status] := "204"
      [:response :lastModified] := "1970-01-01T00:00:00Z"
      [:response :etag] := "W/\"2\"")))
