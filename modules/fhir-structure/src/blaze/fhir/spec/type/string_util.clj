(ns blaze.fhir.spec.type.string-util
  (:refer-clojure :exclude [str])
  (:require
   [blaze.util :refer [str]]
   [clojure.string :as str])
  (:import
   [com.google.common.base CaseFormat]))

(set! *warn-on-reflection* true)

(defn capital [s]
  (str (str/upper-case (subs s 0 1)) (subs s 1)))

(defn pascal->kebab [s]
  (.to CaseFormat/UPPER_CAMEL CaseFormat/LOWER_HYPHEN s))
