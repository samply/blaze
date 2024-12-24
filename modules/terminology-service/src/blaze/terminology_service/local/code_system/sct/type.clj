(ns blaze.terminology-service.local.code-system.sct.type
  (:require [clojure.string :as str]))

(defn parse-sctid
  "Parses `s` as SCTID which is an integer between 6 and 18 digits long."
  [s]
  (when-not (str/starts-with? s "0")
    (parse-long s)))
