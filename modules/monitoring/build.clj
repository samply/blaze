(ns build
  (:require
   [blaze.monitoring.dashboard :as dashboard]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [jsonista.core :as j])
  (:import
   [com.fasterxml.jackson.core PrettyPrinter]
   [com.fasterxml.jackson.core.util DefaultIndenter DefaultPrettyPrinter
    Separators Separators$Spacing]
   [com.fasterxml.jackson.databind ObjectWriter]))

(set! *warn-on-reflection* true)

(def ^:private source-file "dashboard.edn")
(def ^:private target-file "target/blaze-dashboard.json")

(def ^:private separators
  "Separators of the common JSON style: no space before the colon and empty
  objects and arrays without inner space."
  (-> (Separators/createDefaultInstance)
      (.withObjectFieldValueSpacing Separators$Spacing/AFTER)
      (.withObjectEmptySeparator "")
      (.withArrayEmptySeparator "")))

(def ^:private ^PrettyPrinter pretty-printer
  (let [indenter (DefaultIndenter. "  " "\n")]
    (-> (DefaultPrettyPrinter.)
        (.withSeparators separators)
        (.withArrayIndenter indenter)
        (.withObjectIndenter indenter))))

(def ^:private ^ObjectWriter writer
  (.writer (j/object-mapper) pretty-printer))

(defn gen
  "Generates the Grafana dashboard JSON from the dashboard source."
  [_]
  (let [dashboard (dashboard/dashboard (edn/read-string (slurp source-file)))]
    (io/make-parents target-file)
    (spit target-file (str (.writeValueAsString writer dashboard) "\n"))
    (println "Wrote" target-file)))
