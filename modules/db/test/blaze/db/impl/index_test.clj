(ns blaze.db.impl.index-test
  (:require
   [blaze.byte-string :as bs]
   [blaze.db.impl.index :as index]
   [blaze.db.impl.index-spec]
   [blaze.db.kv.mem]
   [blaze.db.kv.mem-spec]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
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
        [0 3 0] :? bs/byte-string?))))
