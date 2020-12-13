(ns blaze.interaction.history.system-test
  "Specifications relevant for the FHIR history interaction:

  https://www.hl7.org/fhir/http.html#history
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.interaction.history.system]
    [blaze.interaction.history.system-spec]
    [blaze.interaction.history.util-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [integrant.core :as ig]
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
    :blaze/context-path ""}
   :path "/_history"})


(defn- handler [node]
  (-> (ig/init
        {:blaze.interaction.history/system
         {:node node}})
      (:blaze.interaction.history/system)))


(defn- handler-with [txs]
  (fn [request]
    (with-open [node (mem-node-with txs)]
      @((handler node) request))))


(defn- link-url [body link-relation]
  (->> body :link (filter (comp #{link-relation} :relation)) first :url))


(deftest handler-test
  (testing "returns empty history on empty node"

    (let [{:keys [status body]}
          ((handler-with [])
            {::reitit/router router
             ::reitit/match match})]

      (is (= 200 status))

      (is (= :fhir/Bundle (:fhir/type body)))

      (is (string? (:id body)))

      (is (= #fhir/code"history" (:type body)))

      (is (= #fhir/unsignedInt 0 (:total body)))

      (is (empty? (:entry body)))))

  (testing "with one patient"
    (let [{:keys [status body]}
          ((handler-with [[[:put {:fhir/type :fhir/Patient :id "0"}]]])
            {::reitit/router router
             ::reitit/match match})]

      (is (= 200 status))

      (is (= :fhir/Bundle (:fhir/type body)))

      (is (string? (:id body)))

      (is (= #fhir/code"history" (:type body)))

      (is (= #fhir/unsignedInt 1 (:total body)))

      (testing "has a self link"
        (is (= #fhir/uri"/_history?__t=1&__page-t=1&__page-type=Patient&__page-id=0"
               (link-url body "self"))))

      (testing "the bundle contains one entry"
        (is (= 1 (count (:entry body)))))

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

  (testing "with two patients in one transaction"
    (testing "contains a next link with t = page-t"
      (let [{:keys [body]}
            ((handler-with
                [[[:put {:fhir/type :fhir/Patient :id "0"}]
                  [:put {:fhir/type :fhir/Patient :id "1"}]]])
              {::reitit/router router
               ::reitit/match match
               :query-params {"_count" "1"}})]

        (testing "hash next link"
          (is (= #fhir/uri"/_history?_count=1&__t=1&__page-t=1&__page-type=Patient&__page-id=1"
                 (link-url body "next"))))))

    (testing "calling the second page shows the patient with the higher id"
      (let [{:keys [body]}
            ((handler-with
                [[[:put {:fhir/type :fhir/Patient :id "0"}]
                  [:put {:fhir/type :fhir/Patient :id "1"}]]])
              {::reitit/router router
               ::reitit/match match
               :path-params {:id "0"}
               :query-params {"_count" "1" "__t" "1" "__page-t" "1"
                              "__page-type" "Patient" "__page-id" "1"}})]

        (given (-> body :entry first)
          [:resource :id] := "1")))

    (testing "a call with `page-id` but missing `page-type` just ignores `page-id`"
      (let [{:keys [body]}
            ((handler-with
                [[[:put {:fhir/type :fhir/Patient :id "0"}]
                  [:put {:fhir/type :fhir/Patient :id "1"}]]])
              {::reitit/router router
               ::reitit/match match
               :path-params {:id "0"}
               :query-params {"_count" "1" "__t" "1" "__page-t" "1" "__page-id" "1"}})]

        (given (-> body :entry first)
          [:resource :id] := "0"))))

  (testing "two patients in two transactions"
    (testing "contains a next link with page-t going to the first transaction"
      (let [{:keys [body]}
            ((handler-with
                [[[:put {:fhir/type :fhir/Patient :id "0"}]]
                 [[:put {:fhir/type :fhir/Patient :id "1"}]]])
              {::reitit/router router
               ::reitit/match match
               :query-params {"_count" "1"}})]

        (is (= "next" (-> body :link second :relation)))

        (is (= #fhir/uri"/_history?_count=1&__t=2&__page-t=1&__page-type=Patient&__page-id=0"
               (-> body :link second :url)))))

    (testing "calling the second page shows the patient from the first transaction"
      (let [{:keys [body]}
            ((handler-with
                [[[:put {:fhir/type :fhir/Patient :id "0"}]]
                 [[:put {:fhir/type :fhir/Patient :id "1"}]]])
              {::reitit/router router
               ::reitit/match match
               :path-params {:id "0"}
               :query-params {"_count" "1" "__t" "2" "__page-t" "1"
                              "__page-type" "Patient" "__page-id" "0"}})]

        (testing "the total count is still two"
          (is (= #fhir/unsignedInt 2 (:total body))))

        (testing "is shows the first version"
          (given (-> body :entry first)
            [:resource :id] := "0"))))))
