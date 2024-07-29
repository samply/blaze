(ns blaze.db.impl.index-test
  (:require
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

(def config
  {:blaze.db/search-param-registry
   {:structure-definition-repo structure-definition-repo}})

(deftest resolve-search-params-test
  (testing "invalid clauses are detected"
    (with-system [{:blaze.db/keys [search-param-registry]} config]
      (given (st/with-instrument-disabled
               (index/resolve-search-params search-param-registry "Observation"
                                            [["code"]] false))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Clause `[\"code\"]` isn't valid."))))
