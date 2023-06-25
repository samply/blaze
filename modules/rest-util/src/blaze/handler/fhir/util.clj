(ns blaze.handler.fhir.util
  "Utilities for FHIR interactions."
  (:refer-clojure :exclude [sync])
  (:require
    [blaze.fhir.spec]
    [blaze.util :as u]
    [clojure.spec.alpha :as s]
    [reitit.core :as reitit]))


(defn parse-nat-long [s]
  (when-let [n (parse-long s)]
    (when-not (neg? n)
      n)))


(defn t
  "Returns the t (optional) of the database which should be stay stable.

  Tries to read the t from the query param `__t` and returns the first valid one
  if there is any."
  {:arglists '([query-params])}
  [{v "__t"}]
  (some parse-nat-long (u/to-seq v)))


(def ^:private ^:const default-page-size 50)
(def ^:private ^:const max-page-size 10000)


(defn page-size
  "Returns the page size taken from a possible `_count` query param.

  Returns the value from the first valid `_count` query param or the default
  value of 50. Limits value to 10000."
  {:arglists '([query-params])}
  [{v "_count"}]
  (or (some #(some-> (parse-nat-long %) (min max-page-size)) (u/to-seq v))
      default-page-size))


(defn page-offset
  "Returns the page offset taken from a possible `__page-offset` query param.

  Returns the value from the first valid `__page-offset` query param or the
  default value of 0."
  {:arglists '([query-params])}
  [{v "__page-offset"}]
  (or (some parse-nat-long (u/to-seq v)) 0))


(defn page-type
  "Returns the value of the first valid `__page-type` query param or nil
  otherwise.

  Values have to be valid FHIR resource type names."
  {:arglists '([query-params])}
  [{v "__page-type"}]
  (some #(when (s/valid? :fhir.resource/type %) %) (u/to-seq v)))


(defn page-id
  "Returns the value of the first valid `__page-id` query param or nil
  otherwise.

  Values have to be valid FHIR id's."
  {:arglists '([query-params])}
  [{v "__page-id"}]
  (some #(when (s/valid? :blaze.resource/id %) %) (u/to-seq v)))


(defn type-url
  "Returns the URL of a resource type like `[base]/[type]`."
  [{:blaze/keys [base-url] ::reitit/keys [router]} type]
  (let [{:keys [path]} (reitit/match-by-name router (keyword type "type"))]
    (str base-url path)))


(defn instance-url
  "Returns the URL of an instance (resource) like `[base]/[type]/[id]`."
  [context type id]
  ;; URLs are build by hand here, because id's do not need to be URL encoded
  ;; and the URL encoding in reitit is slow: https://github.com/metosin/reitit/issues/477
  (str (type-url context type) "/" id))


(defn versioned-instance-url
  "Returns the URL of a versioned instance (resource) like
  `[base]/[type]/[id]/_history/[vid]`."
  [context type id vid]
  ;; URLs are build by hand here, because id's do not need to be URL encoded
  ;; and the URL encoding in reitit is slow: https://github.com/metosin/reitit/issues/477
  (str (instance-url context type id) "/_history/" vid))
