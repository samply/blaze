(ns blaze.db.search-param-registry
  (:refer-clojure :exclude [get])
  (:require
    [blaze.anomaly :as ba :refer [if-ok when-ok]]
    [blaze.coll.core :as coll]
    [blaze.db.impl.protocols :as p]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.impl.search-param.core :as sc]
    [blaze.db.search-param-registry.spec]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.util :refer [conj-vec]]
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
    [jsonista.core :as j]
    [taoensso.timbre :as log]))


(defn get
  "Returns the search parameter with `code` and optional `type`."
  ([search-param-registry code]
   (p/-get search-param-registry code))
  ([search-param-registry code type]
   (p/-get search-param-registry code type)))


(defn list-by-type
  "Returns a seq of search params of `type`."
  [search-param-registry type]
  (p/-list-by-type search-param-registry type))


(defn list-by-target
  "Returns a seq of search params of `target`."
  [search-param-registry target]
  (p/-list-by-target search-param-registry target))


(defn linked-compartments
  "Returns a list of compartments linked to `resource`.

  For example an Observation may be linked to the compartment `[\"Patient\" \"0\"]`
  because its subject points to this patient. Compartments are represented
  through a tuple of `code` and `id`."
  [search-param-registry resource]
  (p/-linked-compartments search-param-registry resource))


(defn compartment-resources
  "Returns a seq of [type code] tuples of resources in compartment of
  `compartment-type` or a list of codes if the optional `type` is given.

  Example:
  * [\"Observation\" \"subject\"] and others for \"Patient\"
  * [\"subject\"] and others for \"Patient\" and \"Observation\""
  ([search-param-registry compartment-type]
   (p/-compartment-resources search-param-registry compartment-type))
  ([search-param-registry compartment-type type]
   (p/-compartment-resources search-param-registry compartment-type type)))


(deftype MemSearchParamRegistry [index target-index compartment-index
                                 compartment-resource-index
                                 compartment-resource-index-by-type]
  p/SearchParamRegistry
  (-get [_ code]
    (get-in index ["Resource" code]))

  (-get [this code type]
    (or (get-in index [type code])
        (p/-get this code)))

  (-list-by-type [_ type]
    (-> (into [] (map val) (index "Resource"))
        (into (map val) (index type))))

  (-list-by-target [_ target]
    (target-index target))

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
      (compartment-index (name (fhir-spec/fhir-type resource)))))

  (-compartment-resources [_ compartment-type]
    (compartment-resource-index compartment-type []))

  (-compartment-resources [_ compartment-type type]
    (get-in compartment-resource-index-by-type [compartment-type type] [])))


(def ^:private object-mapper
  (j/object-mapper
    {:decode-key-fn true}))


(defn- read-json-resource [x]
  (with-open [rdr (io/reader x)]
    (j/read-value rdr object-mapper)))


(defn- read-classpath-json-resource
  "Reads the JSON encoded resource with `name` from classpath."
  [name]
  (log/trace (format "Read resource `%s` from class path." name))
  (read-json-resource (io/resource name)))


(defn- read-file-json-resource
  "Reads the JSON encoded resource with `name` from filesystem."
  [name]
  (log/trace (format "Read resource `%s` from filesystem." name))
  (read-json-resource name))


(defn- read-bundle-entries [extra-bundle-file]
  (let [{entries :entry} (read-classpath-json-resource "blaze/db/search-parameters.json")]
    (cond-> entries
      extra-bundle-file
      (into (:entry (read-file-json-resource extra-bundle-file))))))


(defn- index-search-param [index {:keys [url] :as sp}]
  (if-ok [search-param (sc/search-param index sp)]
    (assoc index url search-param)
    #(if (ba/unsupported? %)
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


(defn- index-compartment-resources [{def-code :code resource-defs :resource}]
  {def-code
   (into
     []
     (mapcat
       (fn [{res-type :code param-codes :param}]
         (coll/eduction (map (partial vector res-type)) param-codes)))
     resource-defs)})


(defn- index-compartment-resources-by-type [{def-code :code resource-defs :resource}]
  {def-code
   (reduce
     (fn [res {res-type :code param-codes :param}]
       (cond-> res param-codes (assoc res-type param-codes)))
     {}
     resource-defs)})


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
  "Builds an index from url to search-param.

  Ensures that non-composite search params are build first so that composite
  search params will find it's components in the already partial build index."
  [entries]
  (when-ok [non-composite (build-url-index* {} remove-composite entries)]
    (build-url-index* non-composite filter-composite entries)))


(defn- build-index
  "Builds an index from [type code] to search param."
  [url-index entries]
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
    entries))


(defn- build-target-index [url-index entries]
  (transduce
    (comp
      (map :resource)
      (filter (comp #{"reference"} :type)))
    (completing
      (fn [index {:keys [url target]}]
        (if-let [search-param (clojure.core/get url-index url)]
          (reduce
            (fn [index target]
              (update index target conj-vec search-param))
            index
            target)
          index)))
    {}
    entries))


(defmethod ig/pre-init-spec :blaze.db/search-param-registry [_]
  (s/keys :req-un [:blaze.fhir/structure-definition-repo]
          :opt-un [::extra-bundle-file]))


(defmethod ig/init-key :blaze.db/search-param-registry
  [_ {:keys [extra-bundle-file]}]
  (log/info
    (cond-> "Init in-memory fixed R4 search parameter registry"
      extra-bundle-file
      (str " including extra search parameters from file: " extra-bundle-file)))
  (let [entries (read-bundle-entries extra-bundle-file)
        patient-compartment (read-classpath-json-resource "blaze/db/compartment/patient.json")]
    (when-ok [url-index (build-url-index entries)
              index (build-index url-index entries)]
      (->MemSearchParamRegistry
        (add-special index)
        (build-target-index url-index entries)
        (index-compartment-def index patient-compartment)
        (index-compartment-resources patient-compartment)
        (index-compartment-resources-by-type patient-compartment)))))
