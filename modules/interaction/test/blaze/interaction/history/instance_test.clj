(ns blaze.interaction.history.instance-test
  "Specifications relevant for the FHIR history interaction:

  https://www.hl7.org/fhir/http.html#history
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.interaction.history.instance :refer [handler]]
    [blaze.interaction.history.instance-spec]
    [blaze.middleware.fhir.metrics-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [juxt.iota :refer [given]]
    [reitit.core :as reitit]
    [taoensso.timbre :as log]))


(defn fixture [f]
  (st/instrument)
  (log/with-merged-config {:level :error} (f))
  (st/unstrument))


(test/use-fixtures :each fixture)


(def router
  (reitit/router
    [["/Patient/{id}" {:name :Patient/instance}]]
    {:syntax :bracket}))


(def match
  {:data
   {:blaze/base-url ""
    :blaze/context-path ""
    :fhir.resource/type "Patient"}
   :path "/Patient/0/_history"})


(defn handler-with [txs]
  (handler (mem-node-with txs)))


(deftest handler-test
  (testing "returns not found on empty node"
    (let [{:keys [status body]}
          @((handler-with [])
            {::reitit/router router
             ::reitit/match match
             :path-params {:id "0"}})]

      (is (= 404 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "error" (-> body :issue first :severity)))

      (is (= "not-found" (-> body :issue first :code)))))

  (testing "returns history with one patient"
    (let [{:keys [status body]}
          @((handler-with [[[:put {:resourceType "Patient" :id "0"}]]])
            {::reitit/router router
             ::reitit/match match
             :path-params {:id "0"}})]

      (is (= 200 status))

      (is (= "Bundle" (:resourceType body)))

      (is (= "history" (:type body)))

      (is (= 1 (:total body)))

      (is (= 1 (count (:entry body))))

      (is (= 1 (count (:link body))))

      (is (= "self" (-> body :link first :relation)))

      (is (= "/Patient/0/_history?t=1&page-t=1" (-> body :link first :url)))

      (given (-> body :entry first)
        :fullUrl := "/Patient/0"
        [:request :method] := "PUT"
        [:request :url] := "/Patient/0"
        [:resource :id] := "0"
        [:resource :resourceType] := "Patient"
        [:resource :meta :versionId] := "1"
        [:response :status] := "201"
        [:response :etag] := "W/\"1\""
        [:response :lastModified] := "1970-01-01T00:00:00Z")))

  (testing "returns history with one currently deleted patient"
    (let [{:keys [status body]}
          @((handler-with [[[:put {:resourceType "Patient" :id "0"}]]
                           [[:delete "Patient" "0"]]])
            {::reitit/router router
             ::reitit/match match
             :path-params {:id "0"}})]

      (is (= 200 status))

      (is (= "Bundle" (:resourceType body)))

      (is (= "history" (:type body)))

      (is (= 2 (:total body)))

      (is (= 2 (count (:entry body))))

      (is (= 1 (count (:link body))))

      (is (= "self" (-> body :link first :relation)))

      (is (= "/Patient/0/_history?t=2&page-t=2" (-> body :link first :url)))

      (testing "first entry"
        (given (-> body :entry first)
          :fullUrl := "/Patient/0"
          [:request :method] := "DELETE"
          [:request :url] := "/Patient/0"
          [:response :status] := "204"
          [:response :etag] := "W/\"2\""
          [:response :lastModified] := "1970-01-01T00:00:00Z"))

      (testing "second entry"
        (given (-> body :entry second)
          :fullUrl := "/Patient/0"
          [:request :method] := "PUT"
          [:request :url] := "/Patient/0"
          [:resource :id] := "0"
          [:resource :resourceType] := "Patient"
          [:resource :meta :versionId] := "1"
          [:response :status] := "201"
          [:response :etag] := "W/\"1\""
          [:response :lastModified] := "1970-01-01T00:00:00Z"))))

  (testing "contains a next link on node with two versions and _count=1"
    (let [{:keys [body]}
          @((handler-with
              [[[:put {:resourceType "Patient" :id "0"}]]
               [[:put {:resourceType "Patient" :id "0"}]]])
            {::reitit/router router
             ::reitit/match match
             :path-params {:id "0"}
             :query-params {"_count" "1"}})]

      (is (= "next" (-> body :link second :relation)))

      (is (= "/Patient/0/_history?_count=1&t=2&page-t=1"
             (-> body :link second :url)))))

  (testing "with two versions, calling the second page"
    (let [{:keys [body]}
          @((handler-with
              [[[:put {:resourceType "Patient" :id "0" :gender "male"}]]
               [[:put {:resourceType "Patient" :id "0" :gender "female"}]]])
            {::reitit/router router
             ::reitit/match match
             :path-params {:id "0"}
             :query-params {"_count" "1" "t" "2" "page-t" "1"}})]

      (testing "the total count is still two"
        (is (= 2 (:total body))))

      (testing "is shows the first version"
        (given (-> body :entry first)
          [:resource :gender] := "male")))))
