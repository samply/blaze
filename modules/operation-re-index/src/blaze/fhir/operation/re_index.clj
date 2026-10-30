(ns blaze.fhir.operation.re-index
  "Main entry point into the $re-index operation."
  (:require
   [blaze.anomaly :as ba]
   [blaze.db.search-param-registry :as sr]
   [blaze.db.search-param-registry.spec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.util :as fu]
   [blaze.handler.async-job :as async-job]
   [blaze.job.re-index :as job-re-index]
   [blaze.module :as m]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [taoensso.timbre :as log]))

(defn- check-search-param-url
  "Returns `canonical` if it is the URL of a search parameter known to
  `search-param-registry`, otherwise an anomaly.

  A versioned canonical is rejected, because the registry holds only one search
  parameter per URL without its version, so the version can't be checked."
  [search-param-registry canonical]
  (let [[url version] (fu/split-canonical canonical)]
    (cond
      version
      (ba/incorrect (format "Versioned canonical `%s` is not supported. Please omit the version." canonical))

      (sr/get-by-url search-param-registry url)
      url

      :else
      (ba/incorrect (format "Search parameter with URL `%s` not found." url)))))

(defn- coerce-search-param-url [search-param-registry]
  (fn [value]
    (if (or (type/canonical? value) (type/uri? value))
      (if-some [canonical (:value value)]
        (check-search-param-url search-param-registry canonical)
        (ba/incorrect "Missing value."))
      (ba/incorrect "Has to be a canonical."))))

(defn- param-specs
  "Specs of the job parameters."
  [search-param-registry]
  {"search-param-url"
   {:action :copy
    :required true
    :coerce (coerce-search-param-url search-param-registry)}})

(defmethod m/pre-init-spec :blaze.fhir.operation/re-index [_]
  (s/keys :req-un [:blaze.db/search-param-registry :blaze/clock]
          :opt-un [:blaze/context-path]))

(defmethod ig/init-key :blaze.fhir.operation/re-index
  [_ {:keys [search-param-registry] :as context}]
  (log/info "Init FHIR $re-index operation handler")
  (async-job/handler context (param-specs search-param-registry) job-re-index/job))
