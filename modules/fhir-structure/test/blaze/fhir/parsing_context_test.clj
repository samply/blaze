(ns blaze.fhir.parsing-context-test
  (:require
   [blaze.fhir.parsing-context :as parsing-context]
   [blaze.fhir.parsing-context.spec]
   [blaze.module.test-util :refer [given-failed-system]]
   [blaze.test-util :as tu]
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
    (given-failed-system {:blaze.fhir/parsing-context nil}
      :key := :blaze.fhir/parsing-context
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.fhir/parsing-context {}}
      :key := :blaze.fhir/parsing-context
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :structure-definition-repo))))

  (testing "invalid structure-definition-repo"
    (given-failed-system {:blaze.fhir/parsing-context {:structure-definition-repo ::invalid}}
      :key := :blaze.fhir/parsing-context
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.fhir/structure-definition-repo]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid fail-on-unknown-property"
    (given-failed-system (assoc-in config [:blaze.fhir/parsing-context :fail-on-unknown-property] ::invalid)
      :key := :blaze.fhir/parsing-context
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::parsing-context/fail-on-unknown-property]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid include-summary-only"
    (given-failed-system (assoc-in config [:blaze.fhir/parsing-context :include-summary-only] ::invalid)
      :key := :blaze.fhir/parsing-context
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::parsing-context/include-summary-only]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid use-regex"
    (given-failed-system (assoc-in config [:blaze.fhir/parsing-context :use-regex] ::invalid)
      :key := :blaze.fhir/parsing-context
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::parsing-context/use-regex]
      [:cause-data ::s/problems 0 :val] := ::invalid)))
