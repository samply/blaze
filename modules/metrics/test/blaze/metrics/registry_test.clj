(ns blaze.metrics.registry-test
  (:require
    [blaze.metrics.core :as metrics]
    [blaze.metrics.registry]
    [blaze.test-util :refer [given-thrown with-system]]
    [clojure.datafy :as datafy]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [integrant.core :as ig]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def collector
  (metrics/collector [(metrics/counter-metric "foo_total" "" [] [])]))


(defn- samples [registry]
  (mapv datafy/datafy (iterator-seq (.asIterator (.metricFamilySamples registry)))))


(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze.metrics/registry nil})
      :key := :blaze.metrics/registry
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze.metrics/registry {}})
      :key := :blaze.metrics/registry
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :collectors))))

  (testing "invalid collectors"
    (given-thrown (ig/init {:blaze.metrics/registry {:collectors ::invalid}})
      :key := :blaze.metrics/registry
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `coll?
      [:explain ::s/problems 0 :val] := ::invalid))

  (testing "with one collector"
    (with-system [{:blaze.metrics/keys [registry]} {:blaze.metrics/registry
                                                    {:collectors [collector]}}]
      (is (= 1 (count (filter (comp #{"foo"} :name) (samples registry))))))))
