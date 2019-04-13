(ns life-fhir-store.integration-test
  (:require
    [cheshire.core :as json]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
    [datomic.api :as d]
    [life-fhir-store.cql-translator :as cql]
    [life-fhir-store.datomic.cql :refer [list-resource-by-code-cache]]
    [life-fhir-store.datomic.schema :as schema]
    [life-fhir-store.datomic.transaction :as tx]
    [life-fhir-store.elm.compiler :as compiler]
    [life-fhir-store.elm.date-time :as date-time]
    [life-fhir-store.elm.deps-infer :refer [infer-library-deps]]
    [life-fhir-store.elm.equiv-relationships :refer [find-equiv-rels-library]]
    [life-fhir-store.elm.normalizer :refer [normalize-library]]
    [life-fhir-store.elm.spec]
    [life-fhir-store.elm.type-infer :refer [infer-library-types]]
    [life-fhir-store.elm.evaluator :as evaluator]
    [life-fhir-store.structure-definition :refer [read-structure-definitions]]
    [clojure.core.cache :as cache]
    [juxt.iota :refer [given]])
  (:import
    [java.time OffsetDateTime Year]))


(st/instrument)


(def structure-definitions (read-structure-definitions "fhir/r4"))


(defn- insert-tx-data
  [structure-definitions {:keys [resourceType] :as resource}]
  (assert resourceType)
  (let [old {:db/id (d/tempid (keyword "life.part" resourceType))}]
    (tx/update-tx-data structure-definitions old (dissoc resource :meta :text))))


(defn- connect []
  (d/delete-database "datomic:mem://test")
  (d/create-database "datomic:mem://test")
  (reset! list-resource-by-code-cache (cache/lru-cache-factory {}))
  (let [conn (d/connect "datomic:mem://test")]
    @(d/transact conn (schema/all-schema (vals structure-definitions)))
    conn))


(defn- db-with [data]
  (let [tx-data (into [] (mapcat #(insert-tx-data structure-definitions %)) data)]
    (:db-after @(d/transact (connect) tx-data))))


(defn- evaluate [db query]
  @(evaluator/evaluate db (OffsetDateTime/now)
                       (compiler/compile-library (cql/translate query) {})))


(defn read-data [query-name]
  (-> (slurp (str "integration-test/" query-name "/data.json"))
      (json/parse-string keyword)))

(defn read-query [query-name]
  (slurp (str "integration-test/" query-name "/query.cql")))

(deftest query-test
  (are [query-name num]
    (= num (get-in (evaluate (db-with (read-data query-name))
                             (read-query query-name))
                   ["NumberOfPatients" :result]))

    "query-3" 1
    "query-5" 3
    "query-6" 1
    "query-7" 2))

(deftest arithmetic-test
  (given (evaluate (db-with []) (read-query "arithmetic"))
    ["OnePlusOne" :result] := 2
    ["OnePointOnePlusOnePointOne" :result] := 2.2M
    ["Year2019PlusOneYear" :result] := (Year/of 2020)
    ["OneYearPlusOneYear" :result] := (date-time/period 2 0 0)
    ["OneYearPlusOneMonth" :result] := (date-time/period 1 1 0)
    ["OneSecondPlusOneSecond" :result] := (date-time/period 0 0 2)))

(comment
  (cql/translate (read-query "arithmetic"))
  (clojure.repl/pst)
  )
