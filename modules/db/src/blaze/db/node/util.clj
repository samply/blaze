(ns blaze.db.node.util
  (:refer-clojure :exclude [str])
  (:require
   [blaze.fhir.spec.type :as type]
   [blaze.util :refer [str]]
   [clojure.string :as str])
  (:import
   [java.time Instant ZoneOffset]))

(set! *warn-on-reflection* true)

(defn name-part
  "Returns the name of the node the component with the composite Integrant `key`
  belongs to, taken from the last segment of the namespace of its second
  keyword.

  The key `[:blaze.db/node :blaze.db.admin/node]` results in `admin`."
  [[_ key]]
  (-> key namespace (str/split #"\.") last))

(defn node-name
  "Returns the name of the node component with Integrant `key`.

  Defaults to `main` for a non-composite key, because such a key denotes the
  only node of a system."
  [key]
  (if (vector? key) (name-part key) "main"))

(defn component-name
  "Returns the name of the component with Integrant `key`, `suffix` prefixed
  with the node name for a composite key, so that log messages of the nodes of
  a system can be told apart."
  [key suffix]
  (cond->> suffix
    (vector? key)
    (str (name-part key) " ")))

(defn thread-name
  "Returns the thread name of the component with Integrant `key`, `suffix`
  prefixed with the node name for a composite key, so that the threads of the
  nodes of a system can be told apart.

  For thread pools, `suffix` is a name template like `resource-indexer-%d`."
  [key suffix]
  (cond->> suffix
    (vector? key)
    (str (name-part key) "-")))

(defn rs-key
  "Returns the resource-store key of `resource-handle` in `variant`."
  [resource-handle variant]
  [(:fhir/type resource-handle) (:hash resource-handle) variant])

(defn instant
  "Returns the java.time.Instant `last-updated` as FHIR instant at the UTC
  offset."
  [last-updated]
  (type/instant (.atOffset ^Instant last-updated ZoneOffset/UTC)))
