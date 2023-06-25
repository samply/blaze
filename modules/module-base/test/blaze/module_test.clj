(ns blaze.module-test
  (:require
    [blaze.module :refer [reg-collector]]
    [blaze.test-util :as tu]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is]]
    [integrant.core :as ig]
    [prometheus.alpha :refer [defcounter]])
  (:import
    [io.prometheus.client Collector]))


(st/instrument)


(test/use-fixtures :each tu/fixture)


(defcounter collector
  "Collector")


(deftest reg-collector-test
  (reg-collector ::collector
    collector)

  (is (instance? Collector (::collector (ig/init {::collector nil})))))
