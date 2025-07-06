(ns blaze.db.impl.index.multi-version-id
  (:refer-clojure :exclude [conj])
  (:import
   [blaze.db.impl.index SingleVersionId MultiVersionId]))

(set! *warn-on-reflection* true)

(defn from-single-version-id
  "Creates a `MultiVersionId` from `single-version-id`."
  [single-version-id]
  (MultiVersionId/fromSingleVersionId ^SingleVersionId single-version-id))

(defn id
  "Returns the id of `multi-version-id`."
  [multi-version-id]
  (.id ^MultiVersionId multi-version-id))

(defn matches-hash?
  "Tests whether `multi-version-id` contains the version of `hash`."
  [multi-version-id hash]
  (.matchesHash ^MultiVersionId multi-version-id hash))

(defn conj
  "Returns a new `MultiVersionId` with the version of `single-version-id` added
  to that of `multi-version-id`."
  [multi-version-id single-version-id]
  (.conj ^MultiVersionId multi-version-id single-version-id))
