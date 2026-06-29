(ns blaze.validator-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.fhir.test-util]
   [blaze.test-util :as tu]
   [blaze.validator :as validator]
   [blaze.validator-spec]
   [blaze.validator.protocols :as p]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest]]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest validate-test
  (let [validator (reify p/Validator
                    (-validate [_ resource]
                      (ac/completed-future (assoc resource :id "validated"))))]
    (given @(validator/validate validator {:fhir/type :fhir/Patient :id "0"})
      :fhir/type := :fhir/Patient
      :id := "validated")))
