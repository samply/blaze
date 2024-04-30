(ns blaze.module-test
  (:require
   [blaze.module :as m :refer [reg-collector]]
   [blaze.test-util  :as tu :refer [given-thrown]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
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

(defmethod m/pre-init-spec ::bar [_] int?)

(deftest pre-init-spec-test
  (testing "returns nil per default"
    (is (nil? (m/pre-init-spec ::foo))))

  (testing "returns the defined spec"
    (is (= int? (m/pre-init-spec ::bar)))))

(deftest assert-key-test
  (testing "doesn't throw on default nil spec"
    (ig/assert-key ::foo 0))

  (testing "doesn't throw on valid value"
    (ig/assert-key ::bar 0))

  (testing "doesn't throw on valid value"
    (given-thrown (ig/assert-key ::bar "a")
      ::s/value := "a"
      [::s/problems 0 :pred] := `int?
      [::s/problems 0 :val] := "a")))
