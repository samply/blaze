(ns blaze.terminology-service.local.code-system.sct-test
  (:require
   [blaze.db.api :as d]
   [blaze.db.api-stub :as api-stub]
   [blaze.fhir.test-util]
   [blaze.module.test-util :refer [with-system]]
   [blaze.path :refer [path]]
   [blaze.terminology-service.local.code-system :as cs]
   [blaze.terminology-service.local.code-system.sct :as sct]
   [blaze.terminology-service.local.code-system.sct-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def ^:private config
  (assoc
   api-stub/mem-node-config
   :blaze.test/incrementing-rng-fn {}
   ::cs/sct {:release-path (path "sct-release")}))

(deftest ensure-code-systems-test
  (with-system [{:blaze.db/keys [node]
                 :blaze.test/keys [fixed-clock incrementing-rng-fn]
                 ::cs/keys [sct]} config]
    (let [context {:node node :clock fixed-clock :rng-fn incrementing-rng-fn}]

      (testing "after creation"
        (let [db @(sct/ensure-code-systems context sct)]

          (testing "25 code systems are available"
            (is (= 25 (d/type-total db "CodeSystem"))))))

      (testing "a second call does nothing"
        (is (nil? @(sct/ensure-code-systems context sct)))))))

(deftest resolve-version-test
  (with-system [{context ::cs/sct} {::cs/sct {:release-path (path "sct-release")}}]
    (are [version result] (= result (cs/resolve-version {:sct/context context} "http://snomed.info/sct" version))
      nil "http://snomed.info/sct/900000000000207008/version/20241001"
      "http://snomed.info/sct/900000000000207008" "http://snomed.info/sct/900000000000207008/version/20241001"
      "http://snomed.info/sct/11000274103" "http://snomed.info/sct/11000274103/version/20241115")))
