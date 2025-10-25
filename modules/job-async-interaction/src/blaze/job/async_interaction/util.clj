(ns blaze.job.async-interaction.util
  (:require
   [blaze.anomaly :as ba :refer [if-ok]]
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.fhir.spec.references :as fsr]
   [blaze.fhir.spec.type :as type]
   [blaze.job.util :as job-util]))

(set! *warn-on-reflection* true)

(def parameter-uri
  "https://samply.github.io/blaze/fhir/CodeSystem/AsyncInteractionJobParameter")

(defn request-bundle-input [reference]
  {:fhir/type :fhir.Task/input
   :type (type/codeable-concept
          {:coding
           [(type/coding
             {:system (type/uri-interned parameter-uri)
              :code #fhir/code "bundle"})]})
   :value (type/reference {:reference (type/string reference)})})

(defn processing-duration [start]
  (type/quantity
   {:value (type/decimal (BigDecimal/valueOf (- (System/nanoTime) start) 9))
    :unit #fhir/string "s"
    :system #fhir/uri-interned "http://unitsofmeasure.org"
    :code #fhir/code "s"}))

(defn- request-bundle-ref [job]
  (if-let [reference (-> (job-util/input-value job parameter-uri "bundle") :reference :value)]
    (or (fsr/split-literal-ref reference)
        (ba/incorrect (format "Invalid request bundle reference `%s`." reference)))
    (ba/incorrect "Missing request bundle reference.")))

(defn- deleted-msg [{job-id :id} bundle-id]
  (format "The request bundle with id `%s` of job with id `%s` was deleted."
          bundle-id job-id))

(defn- not-found-msg [{job-id :id} bundle-id]
  (format "Can't find the request bundle with id `%s` of job with id `%s`."
          bundle-id job-id))

(defn pull-request-bundle [node job]
  (if-ok [[type id] (request-bundle-ref job)]
    (if-let [handle (d/resource-handle (d/db node) type id)]
      (if-not (d/deleted? handle)
        (d/pull node handle)
        (ac/completed-future (ba/not-found (deleted-msg job id))))
      (ac/completed-future (ba/not-found (not-found-msg job id))))
    ac/completed-future))
