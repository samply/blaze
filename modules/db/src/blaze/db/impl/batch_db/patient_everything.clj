(ns blaze.db.impl.batch-db.patient-everything
  (:refer-clojure :exclude [str])
  (:require
   [blaze.coll.core :as coll]
   [blaze.db.impl.index :as index]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param.date :as spd]
   [blaze.db.search-param-registry :as sr]
   [blaze.util :refer [str]]))

(defn- non-compartment-types [search-param-registry]
  (apply disj (sr/all-types search-param-registry)
         "Bundle"
         "CapabilityStatement"
         "CompartmentDefinition"
         "ConceptMap"
         "GraphDefinition"
         "ImplementationGuide"
         "MessageDefinition"
         "MessageHeader"
         "OperationDefinition"
         "SearchParameter"
         "Subscription"
         "TerminologyCapabilities"
         "TestReport"
         "TestScript"
         (map first (sr/compartment-resources search-param-registry "Patient"))))

(defn- supporting-codes
  "Returns all codes of search params of resources with `type` that point to one
  of the `non-compartment-types`."
  [search-param-registry non-compartment-types type]
  (into
   []
   (comp
    (filter (comp #{"reference"} :type))
    (filter (comp (partial some non-compartment-types) :target))
    (map :code))
   (sr/list-by-type search-param-registry type)))

(def ^:private ^:const clinical-date
  "http://hl7.org/fhir/SearchParameter/clinical-date")

(defn- date-search-param
  "Returns the clinical-date search param if `type` is part of its base."
  [{{:keys [search-param-registry]} :node} type]
  (when-let [search-param (sr/get-by-url search-param-registry clinical-date)]
    (when (some #{type} (:base search-param))
      search-param)))

(defn- date-start-clause [search-param start]
  [search-param nil [(str "ge" start)] [(spd/ge-value start)]])

(defn- date-end-clause [search-param end]
  [search-param nil [(str "le" end)] [(spd/le-value end)]])

(defn- date-start-end-filter [batch-db source-type start end]
  (when-let [search-param (date-search-param batch-db source-type)]
    (let [start-clause (date-start-clause search-param start)
          end-clause (date-end-clause search-param end)]
      (index/other-clauses-resource-handle-filter batch-db [[start-clause]
                                                            [end-clause]]))))

(defn- date-start-filter [batch-db source-type start]
  (when-let [search-param (date-search-param batch-db source-type)]
    (let [clause (date-start-clause search-param start)]
      (index/other-clauses-resource-handle-filter batch-db [[clause]]))))

(defn- date-end-filter [batch-db source-type end]
  (when-let [search-param (date-search-param batch-db source-type)]
    (let [clause (date-end-clause search-param end)]
      (index/other-clauses-resource-handle-filter batch-db [[clause]]))))

(defn- date-filter [batch-db source-type start end]
  (cond
    (and start end)
    (date-start-end-filter batch-db source-type start end)
    start
    (date-start-filter batch-db source-type start)
    end
    (date-end-filter batch-db source-type end)))

(defn- rev-include [batch-db resource-handle start end]
  (fn [source-type code]
    (let [filter (date-filter batch-db source-type start end)]
      (cond->> (p/-rev-include batch-db resource-handle source-type code)
        filter
        (coll/eduction filter)))))

(defn patient-everything
  {:arglists '([batch-db patient-handle start end])}
  [{{:keys [search-param-registry]} :node :as batch-db} patient-handle start end]
  (let [non-compartment-types (non-compartment-types search-param-registry)
        supporting-codes #(supporting-codes search-param-registry non-compartment-types %)
        rev-include (rev-include batch-db patient-handle start end)]
    (coll/eduction
     cat
     [[patient-handle]
      (coll/eduction
       (comp
        (mapcat
         (fn [[type codes]]
           (let [supporting-codes (supporting-codes type)]
             (coll/eduction
              (comp
               (mapcat #(rev-include type %))
               (mapcat
                (fn [resource-handle]
                  (into
                   [resource-handle]
                   (comp
                    (mapcat #(p/-include batch-db resource-handle %))
                    (filter (comp non-compartment-types name :fhir/type)))
                   supporting-codes))))
              codes))))
        (distinct))
       (sr/compartment-resources search-param-registry "Patient"))])))
