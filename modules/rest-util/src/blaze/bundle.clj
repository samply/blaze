(ns blaze.bundle
  "FHIR Bundle specific stuff."
  (:require
    [blaze.fhir.spec :as fhir-spec]
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


(defn- resolve-link
  [index link]
  (if-let [{type :resourceType id :id} (get index link)]
    (str type "/" id)
    link))


(declare resolve-links)


(defn- resolve-single-element-links
  [{:keys [index] :as context}
   spec
   value]
  (cond
    (identical? :fhir/Reference spec)
    (if-let [reference (:reference value)]
      (assoc value :reference (resolve-link index reference))
      value)

    (fhir-spec/primitive? spec)
    value

    (identical? :fhir/Resource spec)
    (resolve-links context (keyword "fhir" (:resourceType value)) value)

    :else
    (resolve-links context spec value)))


(defn- resolve-element-links
  [context spec value]
  (if (= :many (fhir-spec/cardinality spec))
    (mapv #(resolve-single-element-links context (fhir-spec/type-spec spec) %) value)
    (resolve-single-element-links context spec value)))


(defn- resolve-links
  [context spec resource]
  (let [child-specs (fhir-spec/child-specs spec)]
    (into
      {}
      (map
        (fn [[key val]]
          (if-let [spec (get child-specs key)]
            [key (resolve-element-links context spec val)]
            [key val])))
      resource)))


(defn- index-resources-by-full-url [entries]
  (reduce
    (fn [r {:keys [fullUrl resource]}]
      (assoc r fullUrl resource))
    {}
    entries))


(defn resolve-entry-links
  "Resolves all links in `entries` according the transaction processing rules."
  [entries]
  (let [index (index-resources-by-full-url entries)]
    (mapv
      (fn [entry]
        (if-let [{type :resourceType :as resource} (:resource entry)]
          (assoc entry :resource (resolve-links {:index index} (keyword "fhir" type) resource))
          entry))
      entries)))


(defmulti entry-tx-op (fn [{{:keys [method]} :request}] method))


(defmethod entry-tx-op "POST"
  [{:keys [resource]}]
  [:create resource])


(defmethod entry-tx-op "PUT"
  [{:keys [resource]}]
  [:put resource])


(defmethod entry-tx-op "DELETE"
  [{{:keys [url]} :request}]
  (let [[type id] (match-url url)]
    [:delete type id]))


(defn tx-ops
  "Returns transaction operations of all `entries` of a transaction bundle."
  [entries]
  (mapv entry-tx-op (resolve-entry-links entries)))
