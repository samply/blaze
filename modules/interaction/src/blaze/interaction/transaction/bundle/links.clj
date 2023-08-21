(ns blaze.interaction.transaction.bundle.links
  (:require
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec.type :as type]))


(defn- resolve-link [index creator link]
  (if-let [{:fhir/keys [type] :keys [id]} (get index (type/value link))]
    (if (record? link)
      (assoc link :value (str (name type) "/" id))
      (creator (str (name type) "/" id)))
    link))


(declare resolve-links)


(defn- resolve-single-element-links
  [{:keys [index] :as context} value]
  (let [type (fhir-spec/fhir-type value)]
    (cond
      (identical? :fhir/Reference type)
      (update value :reference (partial resolve-link index type/string))

      (identical? :fhir/uri type)
      (resolve-link index type/uri value)

      (identical? :fhir/url type)
      (resolve-link index type/url value)

      (fhir-spec/primitive? type)
      value

      :else
      (resolve-links context value))))


(defn- resolve-element-links [context value]
  (if (sequential? value)
    (mapv (partial resolve-single-element-links context) value)
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
      (if-let [fullUrl (type/value fullUrl)]
        (assoc r fullUrl resource)
        r))
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
