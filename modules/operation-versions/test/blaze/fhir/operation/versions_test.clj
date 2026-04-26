(ns blaze.fhir.operation.versions-test
  (:require
   [blaze.fhir.operation.versions]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def ^:private config
  {:blaze.fhir.operation/versions {}})

(deftest handler-test
  (testing "returns supported and default FHIR versions"
    (with-system [{handler :blaze.fhir.operation/versions} config]
      (let [{:keys [status body]} @(handler {})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/Parameters
          [:parameter count] := 2
          [:parameter 0 :name] := #fhir/string "version"
          [:parameter 0 :value] := #fhir/string "4.0"
          [:parameter 1 :name] := #fhir/string "default"
          [:parameter 1 :value] := #fhir/string "4.0")))))
