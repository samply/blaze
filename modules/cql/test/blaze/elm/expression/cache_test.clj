(ns blaze.elm.expression.cache-test
  (:require
    [blaze.db.api-stub :refer [mem-node-config]]
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.test-util :as ctu]
    [blaze.elm.expression :as expr]
    [blaze.elm.expression.cache :as ec]
    [blaze.elm.expression.cache.bloom-filter :as-alias bloom-filter]
    [blaze.elm.expression.cache.codec-spec]
    [blaze.fhir.test-util]
    [blaze.module.test-util :refer [with-system]]
    [blaze.test-util :refer [given-thrown]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [integrant.core :as ig]
    [java-time.api :as time]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log]))


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


(deftest list-test
  (testing "an empty database contains zero Bloom filters"
    (with-system [{::expr/keys [cache]} config]
      (is (zero? (count (ec/list cache))))))

  (testing "an empty database contains zero Bloom filters"
    (with-system [{::expr/keys [cache]} config]
      (let [elm #elm/exists #elm/retrieve{:type "Observation"}
            expr (c/compile {:eval-context "Patient"} elm)]

        (c/attach-cache expr cache)
        (Thread/sleep 100)

        (given (into [] (ec/list cache))
          count := 1
          [0 ::bloom-filter/t] := 0
          [0 ::bloom-filter/expr-form] := "(exists (retrieve \"Observation\"))")))))
