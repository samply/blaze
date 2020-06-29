(ns blaze.interaction.history.type-test
  "Specifications relevant for the FHIR history interaction:

  https://www.hl7.org/fhir/http.html#history
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.interaction.history.type :refer [handler]]
    [blaze.interaction.history.type-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [juxt.iota :refer [given]]
    [reitit.core :as reitit]
    [taoensso.timbre :as log]))


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
   :path "/Patient/_history"})


(defn- handler-with [txs]
  (fn [request]
    (with-open [node (mem-node-with txs)]
      @((handler node) request))))


(defn- link-url [body link-relation]
  (->> body :link (filter (comp #{link-relation} :relation)) first :url))


(deftest handler-test
  (testing "with one patient"
    (let [{:keys [status body]}
          ((handler-with [[[:put {:resourceType "Patient" :id "0"}]]])
            {::reitit/router router
             ::reitit/match match})]

      (is (= 200 status))

      (is (= "Bundle" (:resourceType body)))

      (is (= "history" (:type body)))

      (is (= 1 (:total body)))

      (testing "has self link"
        (is (= "/Patient/_history?__t=1&__page-t=1&__page-id=0" (link-url body "self"))))

      (testing "the bundle contains one entry"
        (is (= 1 (count (:entry body)))))

      (given (-> body :entry first)
        :fullUrl := "/Patient/0"
        [:request :method] := "PUT"
        [:request :url] := "/Patient/0"
        [:resource :id] := "0"
        [:resource :resourceType] := "Patient"
        [:resource :meta :versionId] := "1"
        [:response :status] := "201"
        [:response :etag] := "W/\"1\""
        [:response :lastModified] := "1970-01-01T00:00:00Z"))))
