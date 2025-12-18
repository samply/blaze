(ns blaze.terminology-service.not-available-test
  (:require
   [blaze.fhir.test-util]
   [blaze.module.test-util :refer [given-failed-future with-system]]
   [blaze.terminology-service :as ts]
   [blaze.terminology-service-spec]
   [blaze.terminology-service.not-available]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest]]
   [cognitect.anomalies :as anom]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def ^:private config
  {::ts/not-available {}})

(deftest code-systems-test
  (with-system [{terminology-service ::ts/not-available} config]
    (given-failed-future (ts/code-systems terminology-service)
      ::anom/category := ::anom/unsupported
      ::anom/message := "Terminology operations are not supported. Please enable either the external or the internal terminology service.")))

(deftest code-system-validate-code-test
  (with-system [{terminology-service ::ts/not-available} config]
    (given-failed-future (ts/code-system-validate-code terminology-service {:fhir/type :fhir/Parameters})
      ::anom/category := ::anom/unsupported
      ::anom/message := "Terminology operations are not supported. Please enable either the external or the internal terminology service.")))

(deftest expand-value-set-test
  (with-system [{terminology-service ::ts/not-available} config]
    (given-failed-future (ts/expand-value-set terminology-service {:fhir/type :fhir/Parameters})
      ::anom/category := ::anom/unsupported
      ::anom/message := "Terminology operations are not supported. Please enable either the external or the internal terminology service.")))

(deftest value-set-validate-code-test
  (with-system [{terminology-service ::ts/not-available} config]
    (given-failed-future (ts/value-set-validate-code terminology-service {:fhir/type :fhir/Parameters})
      ::anom/category := ::anom/unsupported
      ::anom/message := "Terminology operations are not supported. Please enable either the external or the internal terminology service.")))
