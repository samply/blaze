(ns blaze.db.search-param-registry
  (:require
    [blaze.anomaly :as anomaly :refer [when-ok]]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.search-param :as search-param]
    [cheshire.core :as json]
    [clojure.java.io :as io]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:refer-clojure :exclude [get]))


(defprotocol SearchParamRegistry
  (-get [_ code type])
  (-list-by-type [_ type])
  (-linked-compartments [_ resource]))


(defn get [search-param-registry code type]
  (-get search-param-registry code type))


(defn list-by-type [search-param-registry type]
  (-list-by-type search-param-registry type))


(defn linked-compartments
  "Returns a list of compartments linked to `resource`.

  For example an Observation may linked to the compartment `Patient/0` because
  its subject points to this patient."
  [search-param-registry resource]
  (-linked-compartments search-param-registry resource))


(deftype MemSearchParamRegistry [index compartment-index]
  SearchParamRegistry
  (-get [_ code type]
    (or (get-in index [type code])
        (get-in index ["Resource" code])))

  (-list-by-type [_ type]
    (into (vec (vals (clojure.core/get index "Resource")))
          (vals (clojure.core/get index type))))

  (-linked-compartments [_ {type :resourceType :as resource}]
    (mapcat
      (fn [{:keys [def-code search-param]}]
        (map
          (fn [id]
            {:c-hash (codec/c-hash def-code)
             :res-id (codec/id-bytes id)})
          (search-param/compartment-ids search-param resource)))
      (clojure.core/get compartment-index type))))


(defn- read-bundle
  "Reads a bundle from classpath named `resource-name`."
  [resource-name]
  (with-open [rdr (io/reader (io/resource resource-name))]
    (json/parse-stream rdr keyword)))


(defn- index-search-param [index {:keys [base code] :as search-param}]
  (anomaly/ensure-reduced
    (reduce
      (fn [index base]
        (if-let [res (search-param/search-param search-param)]
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
  (->> (read-compartment-def "blaze/db/impl/compartment/patient.json")
       (index-compartment-def search-param-index)))


(defn init-mem-search-param-registry []
  (let [bundle (read-bundle "blaze/db/impl/search-parameters.json")]
    (when-ok [index (transduce (map :resource) (completing index-search-param) {} (:entry bundle))]
      (->MemSearchParamRegistry index (index-compartments index)))))


(defmethod ig/init-key :blaze.db/search-param-registry
  [_ _]
  (log/info "Init in-memory fixed R4 search parameter registry")
  (init-mem-search-param-registry))
