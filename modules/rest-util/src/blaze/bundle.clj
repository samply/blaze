(ns blaze.bundle
  "FHIR Bundle specific stuff."
  (:require
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec.type :as type]
    [blaze.handler.fhir.util :as fhir-util]
    [reitit.core :as reitit]))


(def ^:private router
  (reitit/router
    [["{type}" :type]
     ["{type}/{id}" :resource]]
    {:syntax :bracket}))


(defn match-url [url]
  (let [match (reitit/match-by-path router url)
        {:keys [type id]} (:path-params match)]
    [type id]))


(defn- resolve-link [index link]
  (if-let [{:fhir/keys [type] :keys [id]} (get index link)]
    (str (name type) "/" id)
    link))


(declare resolve-links)


(defn- resolve-single-element-links
  [{:keys [index] :as context} value]
  (let [type (fhir-spec/fhir-type value)]
    (cond
      (identical? :fhir/Reference type)
      (if-let [reference (:reference value)]
        (assoc value :reference (resolve-link index reference))
        value)

      (fhir-spec/primitive? type)
      value

      :else
      (resolve-links context value))))


(defn- resolve-element-links [context value]
  (if (sequential? value)
    (mapv #(resolve-single-element-links context %) value)
    (resolve-single-element-links context value)))


(defn- resolve-links [context complex-value]
  (reduce-kv
    (fn [m key val]
      (if (identical? :fhir/type key)
        m
        (let [new-val (resolve-element-links context val)]
          (if (identical? val new-val) m (assoc m key new-val)))))
    complex-value
    complex-value))


(defn- index-resources-by-full-url [entries]
  (reduce
    (fn [r {:keys [fullUrl resource]}]
      (assoc r (some-> fullUrl type/value) resource))
    {}
    entries))


(defn resolve-entry-links
  "Resolves all links in `entries` according the transaction processing rules."
  [entries]
  (let [index (index-resources-by-full-url entries)]
    (mapv
      (fn [entry]
        (if-let [resource (:resource entry)]
          (assoc entry :resource (resolve-links {:index index} resource))
          entry))
      entries)))


(defmulti entry-tx-op (fn [{{:keys [method]} :request}] (type/value method)))


(defmethod entry-tx-op "POST"
  [{:keys [resource]}]
  [:create resource])


(defmethod entry-tx-op "PUT"
  [{{if-match :ifMatch} :request :keys [resource]}]
  (let [t (fhir-util/etag->t if-match)]
    (cond-> [:put resource] t (conj t))))


(defmethod entry-tx-op "DELETE"
  [{{:keys [url]} :request}]
  (let [[type id] (match-url (type/value url))]
    [:delete type id]))


(defn tx-ops
  "Returns transaction operations of all `entries` of a transaction bundle."
  [entries]
  (mapv entry-tx-op (resolve-entry-links entries)))
