(ns blaze.elm.expression.cache-test
  (:require
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.db.api-stub :refer [mem-node-config with-system-data]]
   [blaze.elm.compiler :as c]
   [blaze.elm.compiler.test-util :as ctu]
   [blaze.elm.expression :as expr]
   [blaze.elm.expression.cache :as ec]
   [blaze.elm.expression.cache.bloom-filter :as-alias bloom-filter]
   [blaze.elm.expression.cache.codec-spec]
   [blaze.elm.expression.cache.codec.by-t-spec]
   [blaze.elm.literal :as elm]
   [blaze.fhir.test-util]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :refer [given-thrown]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [java-time.api :as time]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import [com.github.benmanes.caffeine.cache AsyncLoadingCache]))

(set! *warn-on-reflection* true)
(st/instrument)
(ctu/instrument-compile)
(log/set-level! :trace)

(defn- fixture [f]
  (st/instrument)
  (ctu/instrument-compile)
  (f)
  (st/unstrument))

(test/use-fixtures :each fixture)

(def ^:private config
  (assoc mem-node-config
         ::expr/cache
         {:node (ig/ref :blaze.db/node)
          :executor (ig/ref :blaze.test/executor)}
         :blaze.test/executor {}))

(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {::expr/cache nil})
      :key := ::expr/cache
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {::expr/cache {}})
      :key := ::expr/cache
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :executor))))

  (testing "invalid max size"
    (given-thrown (ig/init {::expr/cache {:max-size-in-mb ::invalid}})
      :key := ::expr/cache
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :executor))
      [:explain ::s/problems 2 :pred] := `nat-int?
      [:explain ::s/problems 2 :val] := ::invalid))

  (testing "invalid refresh"
    (given-thrown (ig/init {::expr/cache {:refresh ::invalid}})
      :key := ::expr/cache
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :executor))
      [:explain ::s/problems 2 :pred] := `time/duration?
      [:explain ::s/problems 2 :val] := ::invalid))

  (testing "init"
    (with-system [{::expr/keys [cache]} config]
      (is (s/valid? ::expr/cache cache)))))

;; TODO: executor test

(def ^:private config
  (assoc mem-node-config
         ::expr/cache
         {:node (ig/ref :blaze.db/node)
          :executor (ig/ref :blaze.test/executor)}
         :blaze.test/executor {}))

(defn- compile-exists-expr [resource-type]
  (let [elm (elm/exists (elm/retrieve {:type resource-type}))]
    (c/compile {:eval-context "Patient"} elm)))

(defn- create-bloom-filter!
  "Creates the Bloom filters used in `expr` and wait's some time to ensure that
  the creation is finished."
  [expr cache]
  (c/attach-cache expr cache)
  (Thread/sleep 100))

(deftest list-by-t-test
  (testing "an empty database contains zero Bloom filters"
    (with-system [{::expr/keys [cache]} config]
      (is (coll/empty? (ec/list-by-t cache)))))

  (testing "one Bloom filter on empty database"
    (with-system [{::expr/keys [cache]} config]
      (create-bloom-filter! (compile-exists-expr "Observation") cache)

      (given (into [] (ec/list-by-t cache))
        count := 1
        [0 ::bloom-filter/t] := 0
        [0 ::bloom-filter/expr-form] := "(exists (retrieve \"Observation\"))"
        [0 ::bloom-filter/patient-count] := 0
        [0 ::bloom-filter/mem-size] := 0)))

  (testing "one Bloom filter on database with one patient"
    (with-system-data [{::expr/keys [cache]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}}]]]

      (create-bloom-filter! (compile-exists-expr "Observation") cache)

      (given (into [] (ec/list-by-t cache))
        count := 1
        [0 ::bloom-filter/t] := 1
        [0 ::bloom-filter/expr-form] := "(exists (retrieve \"Observation\"))"
        [0 ::bloom-filter/patient-count] := 1
        [0 ::bloom-filter/mem-size] := 1)))

  (testing "two Bloom filters on database with one patient"
    (with-system-data [{::expr/keys [cache]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}}]]]

      (create-bloom-filter! (compile-exists-expr "Observation") cache)
      (create-bloom-filter! (compile-exists-expr "Condition") cache)

      (given (into [] (ec/list-by-t cache))
        count := 2
        [0 ::bloom-filter/t] := 1
        [0 ::bloom-filter/expr-form] := "(exists (retrieve \"Observation\"))"
        [0 ::bloom-filter/patient-count] := 1
        [0 ::bloom-filter/mem-size] := 1
        [1 ::bloom-filter/t] := 1
        [1 ::bloom-filter/expr-form] := "(exists (retrieve \"Condition\"))"
        [1 ::bloom-filter/patient-count] := 0
        [1 ::bloom-filter/mem-size] := 0)))

  (testing "Bloom filter updates are reflected in the list"
    (with-system-data [{::expr/keys [cache] :blaze.db/keys [node]}
                       (assoc-in config [::expr/cache :refresh] (time/millis 1))]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}}]]]

      (create-bloom-filter! (compile-exists-expr "Observation") cache)

      @(d/transact node [[:put {:fhir/type :fhir/Patient :id "1"}]
                         [:put {:fhir/type :fhir/Observation :id "1"
                                :subject #fhir/Reference{:reference "Patient/1"}}]])

      (create-bloom-filter! (compile-exists-expr "Observation") cache)

      (given (into [] (ec/list-by-t cache))
        count := 1
        [0 ::bloom-filter/t] := 2
        [0 ::bloom-filter/expr-form] := "(exists (retrieve \"Observation\"))"
        [0 ::bloom-filter/patient-count] := 2
        [0 ::bloom-filter/mem-size] := 2)))

  (testing "an old Bloom filter is loaded from the store even if the t was increased in the meantime"
    (with-system-data [{::expr/keys [cache] :blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}}]]]

      (testing "creates the Bloom filter with t=1"
        (create-bloom-filter! (compile-exists-expr "Observation") cache)

        (given (into [] (ec/list-by-t cache))
          count := 1
          [0 ::bloom-filter/t] := 1))

      ;; invalidates the cache
      (.invalidateAll (.synchronous ^AsyncLoadingCache (:mem-cache cache)))

      ;; advances the database
      @(d/transact node [[:put {:fhir/type :fhir/Patient :id "1"}]
                         [:put {:fhir/type :fhir/Observation :id "1"
                                :subject #fhir/Reference{:reference "Patient/1"}}]])

      (testing "doesn't create a new Bloom filter because the old one is still in the store"
        (create-bloom-filter! (compile-exists-expr "Observation") cache)

        (given (into [] (ec/list-by-t cache))
          count := 1
          [0 ::bloom-filter/t] := 1)))))
