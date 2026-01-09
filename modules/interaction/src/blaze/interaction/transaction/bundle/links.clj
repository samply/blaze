(ns blaze.interaction.transaction.bundle.links
  "Provides the function `resolve-entry-links` that resolves links in a bundle
  according to the transaction processing rules (https://hl7.org/fhir/http.html#trules)
  and resolving references in bundles (https://hl7.org/fhir/bundle.html#references)."
  (:refer-clojure :exclude [str])
  (:require
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.references :as fsr]
   [blaze.util :refer [str]]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]))

(defn- resolve-uri
  "Resolves the possible relative `uri` against `base-url`.

  The `uri` has to have the form `<type>/<id>`. Returns `uri` otherwise."
  [base-url uri]
  (cond->> uri (fsr/split-literal-ref uri) (str base-url "/")))

(defn- resolve-link [{:keys [base-url index]} link]
  (let [uri (some->> (:value link) (resolve-uri base-url))]
    (if-let [{:fhir/keys [type] :keys [id]} (get index uri)]
      (assoc link :value (str (name type) "/" id))
      link)))

(declare resolve-links)

(defn- resolve-single-element-links
  [context value]
  (if-let [type (:fhir/type value)]
    (cond
      (identical? :fhir/Reference type)
      (update value :reference (partial resolve-link context))

      (or (identical? :fhir/uri type)
          (identical? :fhir/url type))
      (resolve-link context value)

      (fhir-spec/primitive-val? value)
      value

      :else
      (resolve-links context value))
    value))

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
   (fn [r {{full-url :value} :fullUrl :keys [resource]}]
     (cond-> r full-url (assoc full-url resource)))
   {}
   entries))

(defn- base-url
  "Tries to find the base URL of the possibly RESTful `full-url`.

  RESTful URLs end with two path segments that conform to `type` and `id` of a
  resource. Returns nil if not found."
  [full-url]
  (let [parts (str/split full-url #"/")]
    (when (< 2 (count parts))
      (let [last-parts (subvec parts (- (count parts) 2))]
        (when (s/valid? :blaze.fhir/literal-ref-tuple last-parts)
          (str/join "/" (subvec parts 0 (- (count parts) 2))))))))

(defn resolve-entry-links
  "Resolves all links in `entries` according the transaction processing rules."
  [entries]
  (let [index (index-resources-by-full-url entries)]
    (mapv
     (fn [{full-url :fullUrl :as entry}]
       (if-let [resource (:resource entry)]
         (let [context {:index index :base-url (some-> full-url :value base-url)}]
           (assoc entry :resource (resolve-links context resource)))
         entry))
     entries)))
