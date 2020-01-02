(ns blaze.interaction.history.type-test
  "Specifications relevant for the FHIR history interaction:

  https://www.hl7.org/fhir/http.html#history
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.interaction.history.type :refer [handler]]
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
   :path "/Patient/_history"})


(defn handler-with [txs]
  (handler (mem-node-with txs)))


(deftest handler-test
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

      (is (= 1 (count (:link body))))

      (is (= "self" (-> body :link first :relation)))

      (is (= "/Patient/_history?t=1&page-t=1&page-id=0" (-> body :link first :url)))

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
