(ns blaze.fhir.spec.type.system.spec
  (:require
   [blaze.fhir.spec.type.system :as system]
   [clojure.spec.alpha :as s]))

(s/def :system/date
  system/date?)

(s/def :system/date-time
  system/date-time?)

(s/def :system/date-or-date-time
  (s/or :date :system/date :date-time :system/date-time))
