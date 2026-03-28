(ns blaze.job.async-interaction.request-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.api-stub :as api-stub]
   [blaze.fhir.test-util]
   [blaze.job.async-interaction.request :as req]
   [blaze.job.async-interaction.request-spec]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [ring.util.response :as ring]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn handler [_]
  (ac/completed-future (ring/response ::default-body)))

(def ^:private config
  (assoc
   api-stub/mem-node-config
   :blaze/job-scheduler
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}
   :blaze.test/fixed-rng-fn {}))

(deftest wrap-async-test
  (testing "GET request"
    (with-system [{:blaze/keys [job-scheduler] :blaze.db/keys [node]
                   :blaze.test/keys [fixed-clock fixed-rng-fn]} config]

      (given @(req/handle-async
               {:context-path "/context-path-164322"
                :clock fixed-clock
                :rng-fn fixed-rng-fn}
               {:request-method :get
                :uri "/context-path-164322/Observation"
                :query-string "code=http://loinc.org|10230-1"
                :headers {"prefer" "respond-async"}
                :body ::some-input
                :blaze/job-scheduler job-scheduler
                :blaze/db (d/db node)})

        :status := 202
        [:headers "Content-Location"] := "/context-path-164322/__async-status/AAAAAAAAAAAAAAAA")

      (testing "the request bundle is stored"
        (given @(d/pull node (d/resource-handle (d/db node) "Bundle" "AAAAAAAAAAAAAAAA"))
          :fhir/type := :fhir/Bundle
          :id := "AAAAAAAAAAAAAAAA"
          :type := #fhir/code "batch"
          [:entry count] := 1
          [:entry 0 :fhir/type] := :fhir.Bundle/entry
          [:entry 0 :request :method] := #fhir/code "GET"
          [:entry 0 :request :url] := #fhir/uri "Observation?code=http://loinc.org|10230-1"
          [:entry 0 :resource] := nil))))

  (testing "POST request"
    (with-system [{:blaze/keys [job-scheduler] :blaze.db/keys [node]
                   :blaze.test/keys [fixed-clock fixed-rng-fn]} config]

      (given @(req/handle-async
               {:context-path "/context-path-164322"
                :clock fixed-clock
                :rng-fn fixed-rng-fn}
               {:request-method :post
                :uri "/context-path-164322/Measure/$evaluate-measure"
                :headers {"prefer" "respond-async,return=representation"}
                :body {:fhir/type :fhir/Parameters}
                :blaze/job-scheduler job-scheduler
                :blaze/db (d/db node)})

        :status := 202
        [:headers "Content-Location"] := "/context-path-164322/__async-status/AAAAAAAAAAAAAAAA")

      (testing "the request bundle is stored"
        (given @(d/pull node (d/resource-handle (d/db node) "Bundle" "AAAAAAAAAAAAAAAA"))
          :fhir/type := :fhir/Bundle
          :id := "AAAAAAAAAAAAAAAA"
          :type := #fhir/code "batch"
          [:entry count] := 1
          [:entry 0 :fhir/type] := :fhir.Bundle/entry
          [:entry 0 :request :method] := #fhir/code "POST"
          [:entry 0 :request :url] := #fhir/uri "Measure/$evaluate-measure"
          [:entry 0 :request :extension 0 :url] := "https://samply.github.io/blaze/fhir/StructureDefinition/return-preference"
          [:entry 0 :request :extension 0 :value] := #fhir/code "representation"
          [:entry 0 :resource] := {:fhir/type :fhir/Parameters})))))
