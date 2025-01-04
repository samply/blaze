(ns blaze.terminology-service.local.code-system.sct-test
  (:require
   [blaze.db.api :as d]
   [blaze.db.api-stub :refer [mem-node-config]]
   [blaze.fhir.test-util]
   [blaze.module.test-util :refer [with-system]]
   [blaze.path :refer [path]]
   [blaze.terminology-service.local.code-system :as-alias cs]
   [blaze.terminology-service.local.code-system.sct :as sct]
   [blaze.terminology-service.local.code-system.sct-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def config
  (assoc
   mem-node-config
   :blaze.test/fixed-clock {}
   :blaze.test/incrementing-rng-fn {}
   ::cs/sct {:release-path (path "sct-release")}))

(deftest ensure-code-systems-test
  (with-system [{:blaze.db/keys [node]
                 :blaze.test/keys [fixed-clock incrementing-rng-fn]
                 ::cs/keys [sct]} config]
    (let [context {:node node :clock fixed-clock :rng-fn incrementing-rng-fn}]

      (testing "after creation"
        (let [db @(sct/ensure-code-systems context sct)]

          (testing "82 code systems are available"
            (is (= 82 (d/type-total db "CodeSystem"))))))

      (testing "a second call does nothing"
        (is (nil? @(sct/ensure-code-systems context sct)))))))
