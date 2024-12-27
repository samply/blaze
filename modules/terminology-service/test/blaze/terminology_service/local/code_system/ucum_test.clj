(ns blaze.terminology-service.local.code-system.ucum-test
  (:require
   [blaze.db.api :as d]
   [blaze.db.api-stub :refer [mem-node-config]]
   [blaze.fhir.test-util]
   [blaze.module.test-util :refer [with-system]]
   [blaze.terminology-service.local.code-system.ucum :as ucum]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def config
  (assoc
   mem-node-config
   :blaze.test/fixed-clock {}
   :blaze.test/incrementing-rng-fn {}))

(deftest ensure-code-system-test
  (with-system [{:blaze.db/keys [node] :blaze.test/keys [fixed-clock incrementing-rng-fn]} config]
    (let [context {:node node :clock fixed-clock :rng-fn incrementing-rng-fn}]
      (testing "after creation"
        (let [db @(ucum/ensure-code-system context)]

          (testing "the code system is available"
            (is (= 1 (d/type-total db "CodeSystem"))))))

      (testing "a second call does nothing"
        (is (nil? @(ucum/ensure-code-system context)))))))
