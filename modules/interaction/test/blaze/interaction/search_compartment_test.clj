(ns blaze.interaction.search-compartment-test
  "Specifications relevant for the FHIR search interaction:

  https://www.hl7.org/fhir/http.html#vsearch"
  (:require
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.interaction.search-compartment :refer [handler]]
    [blaze.interaction.search-compartment-spec]
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
    [["/Patient/{id}/{type}" {:name :Patient/compartment}]
     ["/Observation/{id}" {:name :Observation/instance}]]
    {:syntax :bracket}))


(def match
  {:data
   {:blaze/base-url ""
    :blaze/context-path ""
    :fhir.compartment/code "Patient"}
   :path "/Patient/0/Observation"})


(defn- handler-with [txs]
  (handler (mem-node-with txs)))


(defn- link-url [body link-relation]
  (->> body :link (filter (comp #{link-relation} :relation)) first :url))


(deftest handler-test
  (testing "Returns an Error on Invalid Id"
    (let [{:keys [status body]}
          @((handler-with [])
            {:path-params {:id "<invalid>" :type "Observation"}
             ::reitit/router router
             ::reitit/match match})]

      (is (= 400 status))

      (given body
        :resourceType := "OperationOutcome"
        [:issue 0 :severity] := "error"
        [:issue 0 :code] := "value"
        [:issue 0 :diagnostics] := "The identifier `<invalid>` is invalid.")))

  (testing "Returns an Error on Invalid Type"
    (let [{:keys [status body]}
          @((handler-with [])
            {:path-params {:id "0" :type "<invalid>"}
             ::reitit/router router
             ::reitit/match match})]

      (is (= 400 status))

      (given body
        :resourceType := "OperationOutcome"
        [:issue 0 :severity] := "error"
        [:issue 0 :code] := "value"
        [:issue 0 :diagnostics] := "The type `<invalid>` is invalid.")))

  (testing "Returns an empty Bundle on Non-Existing Compartment"
    (let [{:keys [status body]}
          @((handler-with [])
            {:path-params {:id "0" :type "Observation"}
             ::reitit/router router
             ::reitit/match match})]

      (is (= 200 status))

      (given body
        :resourceType := "Bundle"
        :type := "searchset"
        :total := 0)))

  (testing "with one Observation"
    (let [handler
          (handler-with
            [[[:put {:resourceType "Patient" :id "0"}]
              [:put {:resourceType "Observation" :id "0"
                     :status "final"
                     :subject {:reference "Patient/0"}}]
              [:put {:resourceType "Observation" :id "1"
                     :status "preliminary"
                     :subject {:reference "Patient/0"}}]]])
          request
          {:path-params {:id "0" :type "Observation"}
           ::reitit/router router
           ::reitit/match match}]

      (testing "with _summary=count"
        (let [{:keys [status body]}
              @(handler (assoc-in request [:params "_summary"] "count"))]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= "Bundle" (:resourceType body))))

          (testing "the bundle type is searchset"
            (is (= "searchset" (:type body))))

          (testing "the total count is 2"
            (is (= 2 (:total body))))

          (testing "the bundle contains no entries"
            (is (empty? (:entry body))))))

      (testing "with _summary=count and status=final"
        (let [{:keys [status body]}
              @(handler (-> (assoc-in request [:params "_summary"] "count")
                            (assoc-in [:params "status"] "final")))]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= "Bundle" (:resourceType body))))

          (testing "the bundle type is searchset"
            (is (= "searchset" (:type body))))

          (testing "the total count is 1"
            (is (= 1 (:total body))))

          (testing "the bundle contains no entries"
            (is (empty? (:entry body))))))

      (testing "with no query param"
        (let [{:keys [status body]} @(handler request)]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= "Bundle" (:resourceType body))))

          (testing "the bundle type is searchset"
            (is (= "searchset" (:type body))))

          (testing "the total count is 2"
            (is (= 2 (:total body))))

          (testing "has a self link"
            (is (= "/Patient/0/Observation?_count=50&__t=1&__page-offset=0"
                   (link-url body "self"))))

          (testing "the bundle contains two entries"
            (is (= 2 (count (:entry body)))))

          (testing "the first entry"
            (given (-> body :entry first)
              :fullUrl := "/Observation/0"
              [:resource :resourceType] := "Observation"
              [:resource :id] := "0"))

          (testing "the second entry"
            (given (-> body :entry second)
              :fullUrl := "/Observation/1"
              [:resource :resourceType] := "Observation"
              [:resource :id] := "1")))))))
