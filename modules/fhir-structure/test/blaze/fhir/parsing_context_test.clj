(ns blaze.fhir.parsing-context-test
  (:require
   [blaze.fhir.parsing-context]
   [blaze.test-util :as tu :refer [given-thrown]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [integrant.core :as ig]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def ^:private config
  {:blaze.fhir/parsing-context
   {:structure-definition-repo (ig/ref :blaze.fhir/structure-definition-repo)}
   :blaze.fhir/structure-definition-repo {}})

(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze.fhir/parsing-context nil})
      :key := :blaze.fhir/parsing-context
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze.fhir/parsing-context {}})
      :key := :blaze.fhir/parsing-context
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :structure-definition-repo))))

  (testing "invalid structure-definition-repo"
    (given-thrown (ig/init {:blaze.fhir/parsing-context {:structure-definition-repo ::invalid}})
      :key := :blaze.fhir/parsing-context
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.fhir/structure-definition-repo]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid fail-on-unknown-property"
    (given-thrown (ig/init (assoc-in config [:blaze.fhir/parsing-context :fail-on-unknown-property] ::invalid))
      :key := :blaze.fhir/parsing-context
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `boolean?
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid include-summary-only"
    (given-thrown (ig/init (assoc-in config [:blaze.fhir/parsing-context :include-summary-only] ::invalid))
      :key := :blaze.fhir/parsing-context
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `boolean?
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid use-regex"
    (given-thrown (ig/init (assoc-in config [:blaze.fhir/parsing-context :use-regex] ::invalid))
      :key := :blaze.fhir/parsing-context
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `boolean?
      [:cause-data ::s/problems 0 :val] := ::invalid)))
