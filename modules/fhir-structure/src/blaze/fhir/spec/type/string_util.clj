(ns blaze.fhir.spec.type.string-util
  (:refer-clojure :exclude [str])
  (:require
   [blaze.util :refer [str]]
   [clojure.string :as str])
  (:import
   [com.google.common.base CaseFormat]))

(set! *warn-on-reflection* true)

(defn capital
  "Converts the first character of `s` into upper case."
  [s]
  (if (or (empty? s) (Character/isUpperCase ^char (.charAt ^String s 0)))
    s
    (str (str/upper-case (subs s 0 1)) (subs s 1))))

(defn pascal->kebab [s]
  (.to CaseFormat/UPPER_CAMEL CaseFormat/LOWER_HYPHEN s))
