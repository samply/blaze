(ns blaze.fhir.spec.type.system-test-perf
  (:require
    [blaze.fhir.spec.type.system :as system]
    [blaze.test-util]
    [criterium.core :as criterium])
  (:import
    [java.time LocalDate]))


(comment
  (criterium/quick-bench (system/parse-date "2020-01-01"))
  (criterium/quick-bench (system/parse-date-time "2020-01-01"))
  (criterium/quick-bench (LocalDate/parse "2020-01-01"))
  (criterium/quick-bench (DateTime. nil nil "2020-01-01"))
  )
