(ns blaze.db.search-param-registry
  (:require
    [blaze.anomaly :as anomaly :refer [when-ok]]
    [blaze.fhir-path :as fhir-path]
    [blaze.fhir.spec :as fhir-spec]
    [cheshire.core :as json]
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:refer-clojure :exclude [get]))


(defmulti search-param
  "Converts a FHIR search parameter definition into a search-param.

  This multi-method is used to convert search parameters before storing them
  in the registry. Other namespaces can provide their own implementations here.

  The conversion can also return an anomaly."
  (fn [{:keys [type]}] type))


(defmethod search-param :default
  [{:keys [url type]}]
  (log/debug (format "Skip creating search parameter `%s` of type `%s` because the rule is missing." url type)))


(defprotocol SearchParamRegistry
  (-get [_ code] [_ code type])
  (-list-by-type [_ type])
  (-linked-compartments [_ resource]))


(defn get
  "Returns the search parameter with `code` and optional `type`."
  ([search-param-registry code]
   (-get search-param-registry code))
  ([search-param-registry code type]
   (-get search-param-registry code type)))


(defn list-by-type
  "Returns a seq of search params of `type`."
  [search-param-registry type]
  (-list-by-type search-param-registry type))


(defn linked-compartments
  "Returns a list of compartments linked to `resource`.

  For example an Observation may linked to the compartment `[\"Patient\" \"0\"]`
  because its subject points to this patient. Compartments are represented
  through a tuple of `code` and `id`."
  [search-param-registry resource]
  (-linked-compartments search-param-registry resource))


(def stub-resolver
  "A resolver which only returns a resource stub with type and id from the local
  reference itself."
  (reify
    fhir-path/Resolver
    (-resolve [_ uri]
      (let [res (s/conform :blaze.fhir/local-ref uri)]
        (when-not (s/invalid? res)
          (let [[type id] res]
            {:fhir/type (keyword "fhir" type)
             :id id}))))))


;; TODO: the search-param needs to have an :expression which isn't certain
(defn- compartment-ids
  "Returns all compartments `resource` is part of, according to `search-param`."
  {:arglists '([search-param resource])}
  [{:keys [expression]} resource]
  (when-ok [values (fhir-path/eval stub-resolver expression resource)]
    (into
      []
      (mapcat
        (fn [value]
          (case (fhir-spec/fhir-type value)
            :fhir/Reference
            (let [{:keys [reference]} value]
              (when reference
                (let [res (s/conform :blaze.fhir/local-ref reference)]
                  (when-not (s/invalid? res)
                    (rest res))))))))
      values)))


(deftype MemSearchParamRegistry [index compartment-index]
  SearchParamRegistry
  (-get [_ code]
    (get-in index ["Resource" code]))

  (-get [this code type]
    (or (get-in index [type code])
        (-get this code)))

  (-list-by-type [_ type]
    (into (vec (vals (clojure.core/get index "Resource")))
          (vals (clojure.core/get index type))))

  (-linked-compartments [_ resource]
    (mapcat
      (fn [{:keys [def-code search-param]}]
        (map (fn [id] [def-code id]) (compartment-ids search-param resource)))
      (clojure.core/get compartment-index (name (fhir-spec/fhir-type resource))))))


(defn- read-bundle
  "Reads a bundle from classpath named `resource-name`."
  [resource-name]
  (with-open [rdr (io/reader (io/resource resource-name))]
    (json/parse-stream rdr keyword)))


(defn- index-search-param [index {:keys [base code] :as sp}]
  (anomaly/ensure-reduced
    (reduce
      (fn [index base]
        (if-let [res (search-param sp)]
          (if (::anom/category res)
            (reduced res)
            (assoc-in index [base code] res))
          index))
      index
      base)))


(defn- read-compartment-def [name]
  (with-open [rdr (io/reader (io/resource name))]
    (json/parse-stream rdr keyword)))


(defn- index-compartment-def
  "Returns a map from linked resource type to "
  {:arglists '([search-param-index compartment-def])}
  [search-param-index {def-code :code resource-defs :resource}]
  (into
    {}
    (map
      (fn [{res-type :code param-codes :param}]
        [res-type
         (into
           []
           (comp
             (map #(get-in search-param-index [res-type %]))
             (remove nil?)
             (map
               (fn [search-param]
                 {:search-param search-param
                  :def-code def-code})))
           param-codes)]))
    resource-defs))


(defn- index-compartments [search-param-index]
  (->> (read-compartment-def "blaze/db/compartment/patient.json")
       (index-compartment-def search-param-index)))


(def ^:private list-search-param
  {:type "special"
   :code "_list"})


(defn- add-special
  "Add special search params to `index`.

  See: https://www.hl7.org/fhir/search.html#special"
  [index]
  (assoc-in index ["Resource" "_list"] (search-param list-search-param)))


(defn init-search-param-registry
  "Creates a new search param registry."
  []
  (let [bundle (read-bundle "blaze/db/search-parameters.json")]
    (when-ok [index (transduce (map :resource) (completing index-search-param)
                               {} (:entry bundle))]
      (->MemSearchParamRegistry (add-special index) (index-compartments index)))))


(defmethod ig/init-key :blaze.db/search-param-registry
  [_ _]
  (log/info "Init in-memory fixed R4 search parameter registry")
  (init-search-param-registry))
