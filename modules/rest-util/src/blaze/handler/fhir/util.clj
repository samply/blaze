(ns blaze.handler.fhir.util
  "Utilities for FHIR interactions. Main functions are `upsert-resource` and
  `delete-resource`."
  (:require
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]
    [reitit.core :as reitit]))


(defn t
  "Returns the t (optional) of the database which should be stay stable."
  {:arglists '([query-params])}
  [{t "__t"}]
  (when (some->> t (re-matches #"\d+"))
    (Long/parseLong t)))


(def ^:private ^:const default-page-size 50)
(def ^:private ^:const max-page-size 500)


(defn page-size
  "Returns the page size taken from a possible `_count` query param.

  The default page size is 50 and the maximum page size is 500."
  {:arglists '([query-params])}
  [{count "_count"}]
  (if (some->> count (re-matches #"\d+"))
    (min (Long/parseLong count) max-page-size)
    default-page-size))


(defn page-offset
  "Returns the page offset taken from a possible `__page-offset` query param."
  {:arglists '([query-params])}
  [{page-offset "__page-offset"}]
  (if (some->> page-offset (re-matches #"\d+"))
    (Long/parseLong page-offset)
    0))


(defn page-id
  "Returns the `__page-id` query param in case it is a valid FHIR id."
  {:arglists '([query-params])}
  [{page-id "__page-id"}]
  (when (some->> page-id (s/valid? :blaze.resource/id))
    page-id))


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
