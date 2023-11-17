(ns blaze.fhir.structure-definition-repo.impl
  (:require
   [clojure.java.io :as io]
   [jsonista.core :as j]
   [taoensso.timbre :as log]))

(def ^:private object-mapper
  (j/object-mapper
   {:decode-key-fn true}))

(defn- read-bundle
  "Reads a bundle from classpath named `resource-name`."
  [resource-name]
  (log/trace (format "Read FHIR bundle `%s`." resource-name))
  (with-open [rdr (io/reader (io/resource resource-name))]
    (j/read-value rdr object-mapper)))

(defn data-types []
  (->> (:entry (read-bundle "blaze/fhir/r4/profiles-types.json"))
       (mapv :resource)))

(defn resources []
  (->> (:entry (read-bundle "blaze/fhir/r4/profiles-resources.json"))
       (mapv :resource)))
