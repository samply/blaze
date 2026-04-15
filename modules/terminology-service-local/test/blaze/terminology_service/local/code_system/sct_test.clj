(ns blaze.terminology-service.local.code-system.sct-test
  (:require
   [blaze.db.api :as d]
   [blaze.db.api-stub :as api-stub]
   [blaze.fhir.test-util]
   [blaze.module.test-util :refer [given-failed-future with-system]]
   [blaze.path :refer [path]]
   [blaze.terminology-service.local.code-system :as cs]
   [blaze.terminology-service.local.code-system.core :as c]
   [blaze.terminology-service.local.code-system.sct :as sct]
   [blaze.terminology-service.local.code-system.sct-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

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

(deftest find-test
  (with-system [{:blaze.db/keys [node] context ::cs/sct} config]
    (let [ctx {:sct/context context :db (d/db node)}]

      (testing "without version"
        (given @(cs/find ctx "http://snomed.info/sct")
          :sct/context :? some?
          :sct/module-id :? some?))

      (testing "with version"
        (given @(cs/find ctx "http://snomed.info/sct"
                         "http://snomed.info/sct/900000000000207008/version/20241001")
          :sct/context :? some?))

      (testing "not found"
        (given-failed-future (cs/find ctx "http://snomed.info/sct" "http://snomed.info/sct/000/version/00000000")
          ::anom/category := ::anom/not-found)))))

(deftest enhance-test
  (with-system [{:blaze.db/keys [node] context ::cs/sct} config]
    (let [ctx {:sct/context context :db (d/db node)}
          code-system @(cs/find ctx "http://snomed.info/sct")]

      (testing "enhances a code-system with context"
        (given (c/enhance ctx code-system)
          :sct/context :? some?
          :sct/module-id :? some?))

      (testing "with invalid version returns anomaly"
        (let [bad-cs {:fhir/type :fhir/CodeSystem
                      :url #fhir/uri "http://snomed.info/sct"
                      :version #fhir/string "invalid"
                      :content #fhir/code "not-present"
                      :status #fhir/code "active"}]
          (given (c/enhance ctx bad-cs)
            ::anom/category := ::anom/incorrect))))))

(deftest expand-complete-test
  (with-system [{:blaze.db/keys [node] context ::cs/sct} config]
    (let [code-system @(cs/find {:sct/context context :db (d/db node)}
                                "http://snomed.info/sct")]

      (testing "without filter returns too-costly"
        (given (cs/expand-complete code-system {})
          ::anom/category := ::anom/conflict))

      (testing "with filter"
        (let [results (cs/expand-complete code-system {:filter "Eluate"})]
          (is (seq results))
          (is (some (comp #{"40511003"} :value :code) results))))

      (testing "with filter and active-only"
        (let [results (cs/expand-complete code-system {:filter "Eluate"
                                                       :active-only true})]
          (is (seq results))
          (is (some (comp #{"40511003"} :value :code) results)))))))

(deftest find-complete-test
  (with-system [{:blaze.db/keys [node] context ::cs/sct} config]
    (let [code-system @(cs/find {:sct/context context :db (d/db node)}
                                "http://snomed.info/sct")]

      (testing "existing concept"
        (given (cs/find-complete code-system {:clause {:code "40511003"}})
          [:code :value] := "40511003"
          :display :? some?
          :version :? some?
          :designation :? seq))

      (testing "inactive concept without descriptions"
        (given (cs/find-complete code-system {:clause {:code "430220009"}})
          [:code :value] := "430220009"
          :inactive := #fhir/boolean true))

      (testing "non-existing concept"
        (is (nil? (cs/find-complete code-system {:clause {:code "999999999"}}))))

      (testing "invalid code"
        (is (nil? (cs/find-complete code-system {:clause {:code "not-a-number"}})))))))
