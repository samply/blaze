(ns blaze.job.async-interaction.util-test
  (:require
   [blaze.db.api-spec]
   [blaze.db.api-stub :refer [mem-node-config with-system-data]]
   [blaze.db.kv.mem]
   [blaze.db.search-param-registry]
   [blaze.db.search-param-registry-spec]
   [blaze.db.tx-cache]
   [blaze.db.tx-log.local]
   [blaze.fhir.spec.references-spec]
   [blaze.handler.fhir.util-spec]
   [blaze.job.async-interaction-spec]
   [blaze.job.async-interaction.util :as u]
   [blaze.job.async-interaction.util-spec]
   [blaze.module.test-util :as mtu :refer [given-failed-future with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest processing-duration-test
  (given (u/processing-duration (System/nanoTime))
    :fhir/type := :fhir/Quantity
    [:value :fhir/type] := :fhir/decimal
    [:value :value] :? #(and (decimal? %) (pos? %))
    :unit := #fhir/string "s"
    :system := #fhir/uri "http://unitsofmeasure.org"
    :code := #fhir/code "s"))

(deftest pull-request-bundle-test
  (testing "missing request bundle reference"
    (with-system [{:blaze.db/keys [node]} mem-node-config]
      (given-failed-future (u/pull-request-bundle node {:fhir/type :fhir/Task})
        ::anom/category := ::anom/incorrect
        ::anom/message := "Missing request bundle reference.")))

  (testing "invalid request bundle reference"
    (with-system [{:blaze.db/keys [node]} mem-node-config]
      (given-failed-future
       (u/pull-request-bundle
        node
        {:fhir/type :fhir/Task
         :input [(u/request-bundle-input "invalid-173750")]})
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid request bundle reference `invalid-173750`.")))

  (testing "request bundle not found"
    (with-system [{:blaze.db/keys [node]} mem-node-config]
      (given-failed-future
       (u/pull-request-bundle
        node
        {:fhir/type :fhir/Task
         :id "175832"
         :input [(u/request-bundle-input "Bundle/175805")]})
        ::anom/category := ::anom/not-found
        ::anom/message := "Can't find the request bundle with id `175805` of job with id `175832`.")))

  (testing "request bundle deleted"
    (with-system-data [{:blaze.db/keys [node]} mem-node-config]
      [[[:create {:fhir/type :fhir/Bundle :id "180302"}]]
       [[:delete "Bundle" "180302"]]]

      (given-failed-future
       (u/pull-request-bundle
        node
        {:fhir/type :fhir/Task
         :id "180340"
         :input [(u/request-bundle-input "Bundle/180302")]})
        ::anom/category := ::anom/not-found
        ::anom/message := "The request bundle with id `180302` of job with id `180340` was deleted.")))

  (testing "success"
    (with-system-data [{:blaze.db/keys [node]} mem-node-config]
      [[[:create {:fhir/type :fhir/Bundle :id "180302"}]]]

      (let [task {:fhir/type :fhir/Task :id "180340"
                  :input [(u/request-bundle-input "Bundle/180302")]}]

        (given @(mtu/assoc-thread-name (u/pull-request-bundle node task))
          [meta :thread-name] :? mtu/common-pool-thread?
          :fhir/type := :fhir/Bundle
          :id := "180302")))))
