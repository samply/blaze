(ns blaze.db.node.util
  (:refer-clojure :exclude [str])
  (:require
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.fhir.spec.type :as type]
   [blaze.util :refer [str]]
   [clojure.string :as str])
  (:import
   [java.time Instant ZoneOffset]))

(set! *warn-on-reflection* true)

(defn name-part [[_ key]]
  (-> key namespace (str/split #"\.") last))

(defn component-name [key suffix]
  (cond->> suffix
    (vector? key)
    (str (name-part key) " ")))

(defn thread-name-template [key suffix]
  (cond->> suffix
    (vector? key)
    (str (name-part key) "-")))

(defn rs-key
  "Returns the resource-store key of `resource-handle` in `variant`."
  [resource-handle variant]
  [(rh/type resource-handle) (rh/hash resource-handle) variant])

(defn instant [last-updated]
  (type/instant (.atOffset ^Instant last-updated ZoneOffset/UTC)))
