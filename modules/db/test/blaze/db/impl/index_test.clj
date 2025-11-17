(ns blaze.db.impl.index-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.byte-string :as bs]
   [blaze.db.impl.index :as index]
   [blaze.db.impl.index-spec]
   [blaze.db.kv.mem]
   [blaze.db.kv.mem-spec]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def ^:private config
  {:blaze.db/search-param-registry
   {:structure-definition-repo structure-definition-repo}})

(deftest resolve-search-params-test
  (testing "invalid clauses are detected"
    (testing "search clause"
      (with-system [{:blaze.db/keys [search-param-registry]} config]
        (given (st/with-instrument-disabled
                 (index/resolve-search-params search-param-registry "Observation"
                                              [["code"]] false))
          ::anom/category := ::anom/incorrect
          ::anom/message := "Clause `[\"code\"]` isn't valid.")))

    (testing "sort clause"
      (with-system [{:blaze.db/keys [search-param-registry]} config]
        (given (st/with-instrument-disabled
                 (index/resolve-search-params search-param-registry "Observation"
                                              [[:sort "code" :invalid]] false))
          ::anom/category := ::anom/incorrect
          ::anom/message := "Clause `[:sort \"code\" :invalid]` isn't valid."))))

  (testing "invalid clauses are detected"
    (with-system [{:blaze.db/keys [search-param-registry]} config]
      (given (index/resolve-search-params search-param-registry "GraphDefinition"
                                          [["url" "foo"]] false)
        count := 1
        [0 count] := 4
        [0 0 :code] := "url"
        [0 1] := nil
        [0 2 0] := "foo"
        [0 3 0] :? bs/byte-string?)))

  (testing "modifier handling"
    (with-system [{:blaze.db/keys [search-param-registry]} config]
      (let [resolve-sp
            (fn [param lenient]
              (index/resolve-search-params
               search-param-registry "Observation" [[param "Patient/1"]] lenient))]
        (testing "without modifier"
          (is (not (ba/anomaly? (resolve-sp "patient" false))))
          (is (not (ba/anomaly? (resolve-sp "patient" true)))))

        (testing "implemented modifier"
          (is (not (ba/anomaly? (resolve-sp "subject:Patient" false))))
          (is (not (ba/anomaly? (resolve-sp "subject:Patient" true)))))

        (testing "unknown modifier"
          (testing "strict gives anomaly"
            (is (ba/anomaly? (resolve-sp "patient:unknown" false))))

          (testing "lenient ignores"
            (is (not (ba/anomaly? (resolve-sp "patient:unknown" true))))))

        (testing "modifier not implemented"
          (testing "strict gives anomaly"
            (is (ba/anomaly? (resolve-sp "value-string:exact" false))))

          (testing "lenient ignores"
            (is (not (ba/anomaly? (resolve-sp "value-string:exact" true))))))))))
