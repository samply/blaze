(ns blaze.interaction.history.type-test
  "Specifications relevant for the FHIR history interaction:

  https://www.hl7.org/fhir/http.html#history
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.interaction.history.type]
    [blaze.interaction.history.type-spec]
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
(log/set-level! :trace)


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def ^:private base-url "base-url-144600")


(def router
  (reitit/router
    [["/Patient" {:name :Patient/type}]]
    {:syntax :bracket}))


(def match
  {:data
   {:blaze/base-url ""
    :blaze/context-path ""
    :fhir.resource/type "Patient"}
   :path "/Patient/_history"})


(defn- handler [node]
  (-> (ig/init
        {:blaze.interaction.history/type
         {:node node}})
      (:blaze.interaction.history/type)))


(defn- handler-with [txs]
  (fn [request]
    (with-open [node (mem-node-with txs)]
      @((handler node)
        (assoc request
          :blaze/base-url base-url
          ::reitit/router router)))))


(defn- link-url [body link-relation]
  (->> body :link (filter (comp #{link-relation} :relation)) first :url))


(deftest handler-test
  (testing "with one patient"
    (let [{:keys [status body]}
          ((handler-with [[[:put {:fhir/type :fhir/Patient :id "0"}]]])
            {::reitit/match match})]

      (is (= 200 status))

      (is (= :fhir/Bundle (:fhir/type body)))

      (is (string? (:id body)))

      (is (= #fhir/code"history" (:type body)))

      (is (= #fhir/unsignedInt 1 (:total body)))

      (testing "has self link"
        (is (= #fhir/uri"base-url-144600/Patient/_history?__t=1&__page-t=1&__page-id=0"
               (link-url body "self"))))

      (testing "the bundle contains one entry"
        (is (= 1 (count (:entry body)))))

      (given (-> body :entry first)
        :fullUrl := #fhir/uri"base-url-144600/Patient/0"
        [:request :method] := #fhir/code"PUT"
        [:request :url] := #fhir/uri"/Patient/0"
        [:resource :id] := "0"
        [:resource :fhir/type] := :fhir/Patient
        [:resource :meta :versionId] := #fhir/id"1"
        [:response :status] := "201"
        [:response :etag] := "W/\"1\""
        [:response :lastModified] := Instant/EPOCH))))
