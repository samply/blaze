(ns blaze.elm.value-set-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.elm.code :as code]
   [blaze.elm.concept :as concept]
   [blaze.elm.util-spec]
   [blaze.elm.value-set :as value-set]
   [blaze.terminology-service.protocols :as p]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest contains-test
  (testing "with failing terminology service"
    (let [terminology-service
          (reify p/TerminologyService
            (-value-set-validate-code [_ _]
              (ac/completed-future (ba/fault "msg-094502"))))
          value-set (value-set/value-set terminology-service "value-set-url-165123")]

      (given (ba/try-anomaly (value-set/contains-string? value-set "code-094531"))
        ::anom/category := ::anom/fault
        ::anom/message := "Error while testing that the code `code-094531` is in ValueSet `value-set-url-165123`. Cause: msg-094502")

      (given (ba/try-anomaly (value-set/contains-code? value-set (code/code "system-165208" nil "code-094531")))
        ::anom/category := ::anom/fault
        ::anom/message := "Error while testing that the Code {system: `system-165208`, code: `code-094531`} is in ValueSet `value-set-url-165123`. Cause: msg-094502")

      (given (ba/try-anomaly (value-set/contains-code? value-set (code/code "system-191937" "version-191942" "code-191945")))
        ::anom/category := ::anom/fault
        ::anom/message := "Error while testing that the Code {system: `system-191937`, version: `version-191942`, code: `code-191945`} is in ValueSet `value-set-url-165123`. Cause: msg-094502")

      (given (ba/try-anomaly (value-set/contains-concept? value-set (concept/concept [(code/code "system-165208" nil "code-094531")])))
        ::anom/category := ::anom/fault
        ::anom/message := "Error while testing that the Concept {Code {system: `system-165208`, code: `code-094531`}} is in ValueSet `value-set-url-165123`. Cause: msg-094502"))))
