(ns blaze.interaction.history.system-test
  "Specifications relevant for the FHIR history interaction:

  https://www.hl7.org/fhir/http.html#history
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.db.api-stub :refer [mem-node mem-node-with]]
    [blaze.interaction.history.system :refer [handler]]
    [blaze.interaction.history.system-spec]
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
    :blaze/context-path ""}
   :path "/_history"})


(defn handler-with [txs]
  (handler (mem-node-with txs)))


(deftest handler-test
  (testing "returns empty history on empty node"

    (let [{:keys [status body]}
          @((handler (mem-node))
            {::reitit/router router
             ::reitit/match match})]

      (is (= 200 status))

      (is (= "Bundle" (:resourceType body)))

      (is (= "history" (:type body)))

      (is (= 0 (:total body)))

      (is (empty? (:entry body)))))

  (testing "returns history with one patient"
    (let [{:keys [status body]}
          @((handler-with [[[:put {:resourceType "Patient" :id "0"}]]])
            {::reitit/router router
             ::reitit/match match})]

      (is (= 200 status))

      (is (= "Bundle" (:resourceType body)))

      (is (= "history" (:type body)))

      (is (= 1 (:total body)))

      (is (= 1 (count (:entry body))))

      (is (= "self" (-> body :link first :relation)))

      (is (= "/_history?t=1&page-t=1&page-type=Patient&page-id=0" (-> body :link first :url)))

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

  (testing "two patients in one transactions"
    (testing "contains a next link with t = page-t"
      (let [{:keys [body]}
            @((handler-with
                [[[:put {:resourceType "Patient" :id "0"}]
                  [:put {:resourceType "Patient" :id "1"}]]])
              {::reitit/router router
               ::reitit/match match
               :query-params {"_count" "1"}})]

        (is (= "next" (-> body :link second :relation)))

        (is (= "/_history?_count=1&t=1&page-t=1&page-type=Patient&page-id=1"
               (-> body :link second :url)))))

    (testing "calling the second page shows the patient with the higher id"
      (let [{:keys [body]}
            @((handler-with
                [[[:put {:resourceType "Patient" :id "0"}]
                  [:put {:resourceType "Patient" :id "1"}]]])
              {::reitit/router router
               ::reitit/match match
               :path-params {:id "0"}
               :query-params {"_count" "1" "t" "1" "page-t" "1"
                              "page-type" "Patient" "page-id" "1"}})]

        (given (-> body :entry first)
          [:resource :id] := "1")))

    (testing "a call with `page-id` but missing `page-type` just ignores `page-id`"
      (let [{:keys [body]}
            @((handler-with
                [[[:put {:resourceType "Patient" :id "0"}]
                  [:put {:resourceType "Patient" :id "1"}]]])
              {::reitit/router router
               ::reitit/match match
               :path-params {:id "0"}
               :query-params {"_count" "1" "t" "1" "page-t" "1" "page-id" "1"}})]

        (given (-> body :entry first)
          [:resource :id] := "0"))))

  (testing "two patients in two transactions"
    (testing "contains a next link with page-t going to the first transaction"
      (let [{:keys [body]}
            @((handler-with
                [[[:put {:resourceType "Patient" :id "0"}]]
                 [[:put {:resourceType "Patient" :id "1"}]]])
              {::reitit/router router
               ::reitit/match match
               :query-params {"_count" "1"}})]

        (is (= "next" (-> body :link second :relation)))

        (is (= "/_history?_count=1&t=2&page-t=1&page-type=Patient&page-id=0"
               (-> body :link second :url)))))

    (testing "calling the second page shows the patient from the first transaction"
      (let [{:keys [body]}
            @((handler-with
                [[[:put {:resourceType "Patient" :id "0"}]]
                 [[:put {:resourceType "Patient" :id "1"}]]])
              {::reitit/router router
               ::reitit/match match
               :path-params {:id "0"}
               :query-params {"_count" "1" "t" "2" "page-t" "1"
                              "page-type" "Patient" "page-id" "0"}})]

        (testing "the total count is still two"
          (is (= 2 (:total body))))

        (testing "is shows the first version"
          (given (-> body :entry first)
            [:resource :id] := "0"))))))
