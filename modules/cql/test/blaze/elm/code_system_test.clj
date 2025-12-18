(ns blaze.elm.code-system-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.elm.code :as code]
   [blaze.elm.code-system :as code-system]
   [blaze.elm.code-system-spec]
   [blaze.elm.concept :as concept]
   [blaze.elm.util-spec]
   [blaze.terminology-service.protocols :as p]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest lookup-test
  (let [terminology-service (reify p/TerminologyService)
        code-system (code-system/code-system terminology-service {:name "foo" :id "code-system-url-100226" :version "version-100337"})]

    (is (= "code-system-url-100226" (:system code-system)))
    (is (= "version-100337" (:version code-system)))
    (is (= ::bar (::foo code-system ::bar)))))

(deftest contains-test
  (testing "with failing terminology service"
    (let [terminology-service
          (reify p/TerminologyService
            (-code-system-validate-code [_ _]
              (ac/completed-future (ba/fault "msg-094502"))))
          code-system (code-system/code-system terminology-service {:name "foo" :id "code-system-url-165123"})]

      (given (ba/try-anomaly (code-system/contains-string? code-system "code-094531"))
        ::anom/category := ::anom/fault
        ::anom/message := "Error while testing that the code `code-094531` is in CodeSystem `code-system-url-165123`. Cause: msg-094502")

      (given (ba/try-anomaly (code-system/contains-code? code-system (code/code "system-165208" nil "code-094531")))
        ::anom/category := ::anom/fault
        ::anom/message := "Error while testing that the Code {system: `system-165208`, code: `code-094531`} is in CodeSystem `code-system-url-165123`. Cause: msg-094502")

      (given (ba/try-anomaly (code-system/contains-code? code-system (code/code "system-191937" "version-191942" "code-191945")))
        ::anom/category := ::anom/fault
        ::anom/message := "Error while testing that the Code {system: `system-191937`, version: `version-191942`, code: `code-191945`} is in CodeSystem `code-system-url-165123`. Cause: msg-094502")

      (given (ba/try-anomaly (code-system/contains-concept? code-system (concept/concept [(code/code "system-165208" nil "code-094531")])))
        ::anom/category := ::anom/fault
        ::anom/message := "Error while testing that the Concept {Code {system: `system-165208`, code: `code-094531`}} is in CodeSystem `code-system-url-165123`. Cause: msg-094502"))))
