(ns blaze.fhir.writing-context-test
  (:require
   [blaze.fhir.writing-context]
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

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.fhir/writing-context nil}
      :key := :blaze.fhir/writing-context
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.fhir/writing-context {}}
      :key := :blaze.fhir/writing-context
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :structure-definition-repo))))

  (testing "invalid structure-definition-repo"
    (given-failed-system {:blaze.fhir/writing-context {:structure-definition-repo ::invalid}}
      :key := :blaze.fhir/writing-context
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.fhir/structure-definition-repo]
      [:cause-data ::s/problems 0 :val] := ::invalid)))
