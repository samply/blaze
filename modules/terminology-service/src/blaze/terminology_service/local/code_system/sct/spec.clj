(ns blaze.terminology-service.local.code-system.sct.spec
  (:require
   [blaze.fhir.spec]
   [blaze.path.spec]
   [blaze.terminology-service.local.code-system.sct :as-alias sct]
   [clojure.spec.alpha :as s])
  (:import
   [java.time LocalDate]
   [java.time.format DateTimeFormatter DateTimeParseException]))

(set! *warn-on-reflection* true)

(s/def ::sct/release-path
  :blaze/dir)

(s/def :sct/id
  int?)

(s/def :sct/module-id
  :sct/id)

(defn- valid-date? [date]
  (try
    (LocalDate/parse date DateTimeFormatter/BASIC_ISO_DATE)
    (catch DateTimeParseException _)))

(s/def :sct/time
  (s/and int? (comp valid-date? str)))

(s/def :sct/version
  :sct/time)

(s/def :sct/code-systems
  (s/coll-of :fhir/CodeSystem))

(s/def :sct/concept-index
  map?)

(s/def :sct/child-index
  map?)

(s/def :sct/fully-specified-name-index
  map?)

(s/def :sct/synonym-index
  map?)

(s/def :sct/context
  (s/keys
   :req-un
   [:sct/code-systems
    :sct/concept-index
    :sct/child-index
    :sct/fully-specified-name-index
    :sct/synonym-index]))
