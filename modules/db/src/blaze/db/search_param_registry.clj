(ns blaze.db.search-param-registry
  (:refer-clojure :exclude [get])
  (:require
    [blaze.anomaly :as ba :refer [if-ok when-ok]]
    [blaze.coll.core :as coll]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.impl.search-param.core :as sc]
    [blaze.fhir.spec :as fhir-spec]
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [jsonista.core :as j]
    [taoensso.timbre :as log]))


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

  For example an Observation may be linked to the compartment `[\"Patient\" \"0\"]`
  because its subject points to this patient. Compartments are represented
  through a tuple of `code` and `id`."
  [search-param-registry resource]
  (-linked-compartments search-param-registry resource))


(deftype MemSearchParamRegistry [index compartment-index]
  SearchParamRegistry
  (-get [_ code]
    (get-in index ["Resource" code]))

  (-get [this code type]
    (or (get-in index [type code])
        (-get this code)))

  (-list-by-type [_ type]
    (-> (into [] (map val) (index "Resource"))
        (into (map val) (index type))))

  (-linked-compartments [_ resource]
    (transduce
      (comp
        (map
          (fn [{:keys [def-code search-param]}]
            (when-ok [compartment-ids (search-param/compartment-ids search-param resource)]
              (coll/eduction
                (map (fn [id] [def-code id]))
                compartment-ids))))
        (halt-when ba/anomaly?)
        cat)
      conj
      #{}
      (compartment-index (name (fhir-spec/fhir-type resource))))))


(def ^:private object-mapper
  (j/object-mapper
    {:decode-key-fn true}))


(defn- read-json-resource
  "Reads the JSON encoded resource with `name` from classpath."
  [name]
  (log/trace (format "Read resource `%s` from class path." name))
  (with-open [rdr (io/reader (io/resource name))]
    (j/read-value rdr object-mapper)))


(defn- index-search-param [index {:keys [url] :as sp}]
  (if-ok [search-param (sc/search-param index sp)]
    (assoc index url search-param)
    #(if (= ::anom/unsupported (::anom/category %))
       index
       (reduced %))))


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
  (->> (read-json-resource "blaze/db/compartment/patient.json")
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
  (-> (assoc-in index ["Resource" "_list"] (sc/search-param nil list-search-param))
      (assoc-in ["Resource" "_has"] (sc/search-param index has-search-param))))


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


(defmethod ig/pre-init-spec :blaze.db/search-param-registry [_]
  (s/keys :req-un [:blaze.fhir/structure-definition-repo]))


(defmethod ig/init-key :blaze.db/search-param-registry
  [_ _]
  (log/info "Init in-memory fixed R4 search parameter registry")
  (let [bundle (read-json-resource "blaze/db/search-parameters.json")]
    (when-ok [index (build-index bundle)]
      (->MemSearchParamRegistry (add-special index) (index-compartments index)))))
