(ns blaze.terminology-service.local.code-system.sct.util
  (:require
   [blaze.anomaly :as ba]
   [blaze.terminology-service.local.code-system.sct.type :refer [parse-sctid]]))

(def module-only-version-pattern
  #"http\:\/\/snomed\.info\/sct\/(\d+)")

(defn module-version [version]
  (if-let [[_ module version] (re-find #"http\:\/\/snomed\.info\/sct\/(\d+)\/version\/(\d{8})" version)]
    [(parse-sctid module) (parse-sctid version)]
    (ba/incorrect (format "Incorrectly formatted SNOMED CT version `%s`." version))))
