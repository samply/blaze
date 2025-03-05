(ns blaze.rest-api.structure-definitions-test
  (:require
   [blaze.db.api :as d]
   [blaze.db.api-stub :refer [mem-node-config]]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.module.test-util :refer [with-system]]
   [blaze.rest-api.structure-definitions :as structure-definitions]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [taoensso.timbre :as log]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def config
  (assoc
   mem-node-config
   :blaze.test/fixed-clock {}
   :blaze.test/incrementing-rng-fn {}))

(deftest ensure-code-systems-test
  (log/set-min-level! :info)
  (with-system [{:blaze.db/keys [node]
                 :blaze.test/keys [fixed-clock incrementing-rng-fn]} config]
    (let [context {:node node :structure-definition-repo structure-definition-repo
                   :clock fixed-clock :rng-fn incrementing-rng-fn}]

      (testing "after creation"
        (let [db @(structure-definitions/ensure-structure-definitions context)]

          (testing "over 100 code systems are available"
            (is (< 100 (d/type-total db "StructureDefinition"))))))

      (testing "a second call does nothing"
        (is (nil? @(structure-definitions/ensure-structure-definitions context)))))))
