(ns blaze.handler.fhir.util
  "Utilities for FHIR interactions. Main functions are `upsert-resource` and
  `delete-resource`."
  (:require
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]
    [reitit.core :as reitit]))


(defn to-seq [x]
  (if (or (nil? x) (sequential? x)) x [x]))


(defn t
  "Returns the t (optional) of the database which should be stay stable.

  Tries to read the t from the query param `__t` and returns the first valid one
  if their is any."
  {:arglists '([query-params])}
  [{v "__t"}]
  (when-let [t (some #(when (re-matches #"\d+" %) %) (to-seq v))]
    (Long/parseLong t)))


(def ^:private ^:const default-page-size 50)
(def ^:private ^:const max-page-size 500)


(defn page-size
  "Returns the page size taken from a possible `_count` query param.

  Returns the value from the first valid `_count` query param or the default
  value of 50. Limits values to 500."
  {:arglists '([query-params])}
  [{v "_count"}]
  (if-let [count (some #(when (re-matches #"\d+" %) %) (to-seq v))]
    (min (Long/parseLong count) max-page-size)
    default-page-size))


(defn page-offset
  "Returns the page offset taken from a possible `__page-offset` query param.

  Returns the value from the first valid `__page-offset` query param or the
  default value of 0."
  {:arglists '([query-params])}
  [{v "__page-offset"}]
  (if-let [page-offset (some #(when (re-matches #"\d+" %) %) (to-seq v))]
    (Long/parseLong page-offset)
    0))


(defn page-id
  "Returns the value of the first valid `__page-id` query param or nil
  otherwise.

  Values have to be valid FHIR id's."
  {:arglists '([query-params])}
  [{v "__page-id"}]
  (some #(when (s/valid? :blaze.resource/id %) %) (to-seq v)))


(defn type-url
  "Returns the URL of a resource type like `[base]/[type]`."
  [router type]
  (let [{:keys [path] {:blaze/keys [base-url]} :data}
        (reitit/match-by-name router (keyword type "type"))]
    (str base-url path)))


(defn instance-url
  "Returns the URL of a instance (resource) like `[base]/[type]/[id]`."
  [router type id]
  (let [{:keys [path] {:blaze/keys [base-url]} :data}
        (reitit/match-by-name router (keyword type "instance") {:id id})]
    (str base-url path)))


(defn versioned-instance-url
  "Returns the URL of a versioned instance (resource) like
  `[base]/[type]/[id]/_history/[vid]`."
  [router type id vid]
  (let [{:keys [path] {:blaze/keys [base-url]} :data}
        (reitit/match-by-name
          router (keyword type "versioned-instance") {:id id :vid vid})]
    (str base-url path)))
