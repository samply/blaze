(ns blaze.interaction.history.instance-test
  "Specifications relevant for the FHIR history interaction:

  https://www.hl7.org/fhir/http.html#history
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.interaction.history.instance :refer [handler]]
    [blaze.interaction.history.instance-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [juxt.iota :refer [given]]
    [reitit.core :as reitit]
    [taoensso.timbre :as log])
  (:import
    [java.time Instant]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (log/set-level! :trace)
  (f)
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


(defn- handler-with [txs]
  (fn [request]
    (with-open [node (mem-node-with txs)]
      @((handler node) request))))


(deftest handler-test
  (testing "returns not found on empty node"
    (let [{:keys [status body]}
          ((handler-with [])
            {::reitit/router router
             ::reitit/match match
             :path-params {:id "0"}})]

      (is (= 404 status))

      (given body
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code"error"
        [:issue 0 :code] := #fhir/code"not-found")))

  (testing "returns history with one patient"
    (let [{:keys [status body]}
          ((handler-with [[[:put {:fhir/type :fhir/Patient :id "0"}]]])
            {::reitit/router router
             ::reitit/match match
             :path-params {:id "0"}})]

      (is (= 200 status))

      (is (= :fhir/Bundle (:fhir/type body)))

      (is (string? (:id body)))

      (is (= #fhir/code"history" (:type body)))

      (is (= #fhir/unsignedInt 1 (:total body)))

      (is (= 1 (count (:entry body))))

      (is (= 1 (count (:link body))))

      (is (= "self" (-> body :link first :relation)))

      (is (= #fhir/uri"/Patient/0/_history?__t=1&__page-t=1" (-> body :link first :url)))

      (given (-> body :entry first)
        :fullUrl := #fhir/uri"/Patient/0"
        [:request :method] := #fhir/code"PUT"
        [:request :url] := #fhir/uri"/Patient/0"
        [:resource :id] := "0"
        [:resource :fhir/type] := :fhir/Patient
        [:resource :meta :versionId] := #fhir/id"1"
        [:response :status] := "201"
        [:response :etag] := "W/\"1\""
        [:response :lastModified] := Instant/EPOCH)))

  (testing "returns history with one currently deleted patient"
    (let [{:keys [status body]}
          ((handler-with [[[:put {:fhir/type :fhir/Patient :id "0"}]]
                           [[:delete "Patient" "0"]]])
            {::reitit/router router
             ::reitit/match match
             :path-params {:id "0"}})]

      (is (= 200 status))

      (is (= :fhir/Bundle (:fhir/type body)))

      (is (string? (:id body)))

      (is (= #fhir/code"history" (:type body)))

      (is (= #fhir/unsignedInt 2 (:total body)))

      (is (= 2 (count (:entry body))))

      (is (= 1 (count (:link body))))

      (is (= "self" (-> body :link first :relation)))

      (is (= #fhir/uri"/Patient/0/_history?__t=2&__page-t=2" (-> body :link first :url)))

      (testing "first entry"
        (given (-> body :entry first)
          :fullUrl := #fhir/uri"/Patient/0"
          [:request :method] := #fhir/code"DELETE"
          [:request :url] := #fhir/uri"/Patient/0"
          [:response :status] := "204"
          [:response :etag] := "W/\"2\""
          [:response :lastModified] := Instant/EPOCH))

      (testing "second entry"
        (given (-> body :entry second)
          :fullUrl := #fhir/uri"/Patient/0"
          [:request :method] := #fhir/code"PUT"
          [:request :url] := #fhir/uri"/Patient/0"
          [:resource :id] := "0"
          [:resource :fhir/type] := :fhir/Patient
          [:resource :meta :versionId] := #fhir/id"1"
          [:response :status] := "201"
          [:response :etag] := "W/\"1\""
          [:response :lastModified] := Instant/EPOCH))))

  (testing "contains a next link on node with two versions and _count=1"
    (let [{:keys [body]}
          ((handler-with
              [[[:put {:fhir/type :fhir/Patient :id "0" :gender #fhir/code"male"}]]
               [[:put {:fhir/type :fhir/Patient :id "0" :gender #fhir/code"female"}]]])
            {::reitit/router router
             ::reitit/match match
             :path-params {:id "0"}
             :query-params {"_count" "1"}})]

      (is (= "next" (-> body :link second :relation)))

      (is (= #fhir/uri"/Patient/0/_history?_count=1&__t=2&__page-t=1"
             (-> body :link second :url)))))

  (testing "with two versions, calling the second page"
    (let [{:keys [body]}
          ((handler-with
              [[[:put {:fhir/type :fhir/Patient :id "0" :gender #fhir/code"male"}]]
               [[:put {:fhir/type :fhir/Patient :id "0" :gender #fhir/code"female"}]]])
            {::reitit/router router
             ::reitit/match match
             :path-params {:id "0"}
             :query-params {"_count" "1" "t" "2" "__page-t" "1"}})]

      (testing "the total count is still two"
        (is (= #fhir/unsignedInt 2 (:total body))))

      (testing "is shows the first version"
        (given (-> body :entry first)
          [:resource :gender] := #fhir/code"male")))))
