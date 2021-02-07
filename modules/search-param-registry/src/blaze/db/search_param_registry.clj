(ns blaze.db.search-param-registry
  (:require
    [blaze.anomaly :refer [when-ok]]
    [blaze.fhir-path :as fhir-path]
    [blaze.fhir.spec :as fhir-spec]
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [jsonista.core :as j]
    [taoensso.timbre :as log])
  (:refer-clojure :exclude [get]))


(defmulti search-param
  "Converts a FHIR search parameter definition into a search-param.

  This multi-method is used to convert search parameters before storing them
  in the registry. Other namespaces can provide their own implementations here.

  The conversion can return an anomaly."
  {:arglists '([index definition])}
  (fn [_ {:keys [type]}] type))


(defmethod search-param :default
  [_ {:keys [url type]}]
  (log/debug (format "Skip creating search parameter `%s` of type `%s` because it is not implemented." url type))
  {::anom/category ::anom/unsupported})


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
        ;; TODO: use search-params compartment-ids
        (map (fn [id] [def-code id]) (compartment-ids search-param resource)))
      (clojure.core/get compartment-index (name (fhir-spec/fhir-type resource))))))


(def ^:private object-mapper
  (j/object-mapper
    {:decode-key-fn true}))


(defn- read-bundle
  "Reads a bundle from classpath named `resource-name`."
  [resource-name]
  (with-open [rdr (io/reader (io/resource resource-name))]
    (j/read-value rdr object-mapper)))


(defn- index-search-param [index {:keys [url] :as sp}]
  (let [res (search-param index sp)]
    (if-let [category (::anom/category res)]
      (if (= ::anom/unsupported category)
        index
        (reduced res))
      (assoc index url res))))


(defn- read-compartment-def [name]
  (with-open [rdr (io/reader (io/resource name))]
    (j/read-value rdr object-mapper)))


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
             (keep #(get-in search-param-index [res-type %]))
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
   :name "_list"})


(def ^:private has-search-param
  {:type "special"
   :name "_has"})


(defn- add-special
  "Add special search params to `index`.

  See: https://www.hl7.org/fhir/search.html#special"
  [index]
  (-> (assoc-in index ["Resource" "_list"] (search-param nil list-search-param))
      (assoc-in ["Resource" "_has"] (search-param index has-search-param))))


(defn- build-url-index* [index filter entries]
  (transduce
    (comp (map :resource)
          filter)
    (completing index-search-param)
    index
    entries))


(def ^:private remove-composite
  (remove (comp #{"composite"} :type)))


(def ^:private filter-composite
  (filter (comp #{"composite"} :type)))


(defn- build-url-index
  "Builds an index from url to search-param."
  [entries]
  (when-ok [non-composite (build-url-index* {} remove-composite entries)]
    (build-url-index* non-composite filter-composite entries)))


(defn- build-index
  "Builds an index from [type code] to search param."
  [{entries :entry}]
  (when-ok [url-index (build-url-index entries)]
    (transduce
      (map :resource)
      (completing
        (fn [index {:keys [url base code]}]
          (if-let [search-param (clojure.core/get url-index url)]
            (reduce
              (fn [index base]
                (assoc-in index [base code] search-param))
              index
              base)
            index)))
      {}
      entries)))


(defn init-search-param-registry
  "Creates a new search param registry."
  []
  (let [bundle (read-bundle "blaze/db/search-parameters.json")]
    (when-ok [index (build-index bundle)]
      (->MemSearchParamRegistry (add-special index) (index-compartments index)))))


(defmethod ig/init-key :blaze.db/search-param-registry
  [_ _]
  (log/info "Init in-memory fixed R4 search parameter registry")
  (init-search-param-registry))
