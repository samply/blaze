(ns blaze.terminology-service.local.code-system.loinc-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.db.api :as d]
   [blaze.db.api-stub :as api-stub :refer [with-db]]
   [blaze.fhir.test-util]
   [blaze.module.test-util :refer [with-system]]
   [blaze.terminology-service.local.code-system :as-alias cs]
   [blaze.terminology-service.local.code-system.loinc :as loinc]
   [blaze.terminology-service.local.code-system.loinc-spec]
   [blaze.test-util :as tu :refer [given-failed-future]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def ^:private config
  (assoc
   api-stub/mem-node-config
   :blaze.test/incrementing-rng-fn {}
   ::cs/loinc {}))

(deftest ensure-code-systems-test
  (with-system [{:blaze.db/keys [node]
                 :blaze.test/keys [fixed-clock incrementing-rng-fn]
                 ::cs/keys [loinc]} config]
    (let [context {:node node :clock fixed-clock :rng-fn incrementing-rng-fn}]

      (testing "after creation"
        (with-db [db node @(loinc/ensure-code-systems context loinc)]

          (testing "the code system is available"
            (is (= 1 (d/type-total db "CodeSystem"))))))

      (testing "a second call does nothing"
        (is (nil? @(loinc/ensure-code-systems context loinc))))

      (testing "failing CodeSystem query"
        (with-redefs [d/type-query (fn [_ _ _] (ba/fault "msg-144311"))]
          (given-failed-future (loinc/ensure-code-systems context loinc)
            ::anom/category := ::anom/fault
            ::anom/message := "msg-144311"))))))
