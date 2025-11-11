(ns blaze.fhir.spec.xml
  (:require
   [clojure.string :as str]))

(defn replace-invalid-chars [s]
  (str/replace s #"[\x00-\x08\x0B\x0C\x0E-\x1F]" "?"))
