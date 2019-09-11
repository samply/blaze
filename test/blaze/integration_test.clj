(ns blaze.integration-test
  (:require
    [cheshire.core :as json]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
    [datomic.api :as d]
    [datomic-spec.test :as dst]
    [juxt.iota :refer [given]]
    [blaze.bundle :as bundle]
    [blaze.cql-translator :as cql]
    [blaze.datomic.test-util :as test-util]
    [blaze.elm.compiler :as compiler]
    [blaze.elm.date-time :as date-time]
    [blaze.elm.deps-infer :refer [infer-library-deps]]
    [blaze.elm.equiv-relationships :refer [find-equiv-rels-library]]
    [blaze.elm.normalizer :refer [normalize-library]]
    [blaze.elm.spec]
    [blaze.elm.type-infer :refer [infer-library-types]]
    [blaze.elm.evaluator :as evaluator]
    [blaze.terminology-service.extern :as ts]
    [taoensso.timbre :as log])
  (:import
    [java.time OffsetDateTime Year]))


(defonce db (d/db (st/with-instrument-disabled (test-util/connect))))


(defn fixture [f]
  (st/instrument)
  (dst/instrument)
  (log/with-merged-config {:level :error} (f))
  (st/unstrument))


(use-fixtures :each fixture)


(def term-service
  (ts/term-service "http://tx.fhir.org/r4" {} nil nil))


(defn- db-with [{:strs [entries]}]
  (let [entries @(bundle/annotate-codes term-service db entries)
        {db :db-after} (d/with db (bundle/code-tx-data db entries))]
    (:db-after (d/with db (bundle/tx-data db entries)))))


(defn- evaluate [db query]
  @(evaluator/evaluate db (OffsetDateTime/now)
                       (compiler/compile-library db (cql/translate query) {})))


(defn read-data [query-name]
  (-> (slurp (str "integration-test/" query-name "/data.json"))
      (json/parse-string)))


(defn read-query [query-name]
  (slurp (str "integration-test/" query-name "/query.cql")))


(deftest query-test
  (are [query-name num]
    (= num (get-in (evaluate (db-with (read-data query-name))
                             (read-query query-name))
                   ["NumberOfPatients" :result]))

    ;"query-3" 1
    ;"query-5" 3
    ;"query-6" 1
    ;"query-7" 2
    "readme-example" 3))


(deftest arithmetic-test
  (given (evaluate db (read-query "arithmetic"))
    ["OnePlusOne" :result] := 2
    ["OnePointOnePlusOnePointOne" :result] := 2.2M
    ["Year2019PlusOneYear" :result] := (Year/of 2020)
    ["OneYearPlusOneYear" :result] := (date-time/period 2 0 0)
    ["OneYearPlusOneMonth" :result] := (date-time/period 1 1 0)
    ["OneSecondPlusOneSecond" :result] := (date-time/period 0 0 2000)))
