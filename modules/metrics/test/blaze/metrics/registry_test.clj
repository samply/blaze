(ns blaze.metrics.registry-test
  (:require
   [blaze.metrics.core :as metrics]
   [blaze.metrics.registry]
   [blaze.metrics.spec]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.test-util :as tu]
   [clojure.datafy :as datafy]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [taoensso.timbre :as log])
  (:import
   [io.prometheus.client CollectorRegistry]))

(set! *warn-on-reflection* true)

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.metrics/registry nil}
      :key := :blaze.metrics/registry
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "invalid collectors"
    (given-failed-system {:blaze.metrics/registry {:collectors ::invalid}}
      :key := :blaze.metrics/registry
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.metrics/collectors]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(def collector
  (metrics/collector [(metrics/counter-metric "foo_total" "" [] [])]))

(def config
  {:blaze.metrics/registry {:collectors [collector]}})

(defn- samples [registry]
  (mapv datafy/datafy (iterator-seq (.asIterator (.metricFamilySamples ^CollectorRegistry registry)))))

(deftest registry-test
  (testing "with one collector"
    (with-system [{:blaze.metrics/keys [registry]} config]
      (is (= 1 (count (filter (comp #{"foo"} :name) (samples registry))))))))
