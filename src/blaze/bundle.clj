(ns blaze.bundle
  "FHIR Bundle specific stuff."
  (:require
    [blaze.datomic.transaction :as tx]
    [blaze.datomic.util :as util]
    [clojure.spec.alpha :as s]
    [datomic-spec.core :as ds]))


(defn- resolve-link
  [index link]
  (if-let [{type "resourceType" id "id"} (get index link)]
    (str type "/" id)
    link))


(declare resolve-links)


(defn- resolve-single-element-links
  [{:keys [index] :as context}
   {:db/keys [ident] :element/keys [type-code primitive? type]}
   value]
  (cond
    (= "Reference" type-code)
    (if-let [reference (get value "reference")]
      (assoc value "reference" (resolve-link index reference))
      value)

    primitive?
    value

    (= "BackboneElement" type-code)
    (resolve-links context ident value)

    :else
    (resolve-links context type value)))


(defn- resolve-element-links
  [context {:db/keys [cardinality] :as element} value]
  (if (= :db.cardinality/many cardinality)
    (mapv #(resolve-single-element-links context element %) value)
    (resolve-single-element-links context element value)))


(defn- resolve-links
  [{:keys [db] :as context} type-ident entity]
  (transduce
    (comp
      (map #(util/cached-entity db %)))
    (completing
      (fn [entity element]
        (if-let [[value {:element/keys [json-key] :as element}] (tx/find-json-value db element entity)]
          (assoc entity json-key (resolve-element-links context element value))
          entity)))
    entity
    (:type/elements (util/cached-entity db type-ident))))


(s/fdef resolve-entry-links
  :args (s/cat :db ::ds/db :entries coll?))

(defn resolve-entry-links
  "Resolves all links in `entries` according the transaction processing rules."
  [db entries]
  (let [index (reduce (fn [r {url "fullUrl" :strs [resource]}] (assoc r url resource)) {} entries)]
    (mapv
      (fn [entry]
        (update entry "resource" #(resolve-links {:db db :index index} (keyword (get % "resourceType")) %)))
      entries)))


(defn- entry-tempid
  "Returns a triple of resource type, logical id and tempid for `entries` with
  resources which have to be created in `db`."
  {:arglists '([db entry])}
  [db {{:strs [method]} "request" resource "resource"}]
  (when (#{"POST" "PUT"} method)
    (tx/resource-tempid db resource)))


(s/fdef collect-tempids
  :args (s/cat :db ::ds/db :entries coll?)
  :ret ::tx/tempids)

(defn collect-tempids
  "Returns a tempid lookup map of all resource tempids used in `entries` of a
  transaction bundle."
  [db entries]
  (transduce
    (comp
      (map #(entry-tempid db %))
      (remove nil?))
    (completing
      (fn [tempids [type id tempid]]
        (assoc-in tempids [type id] tempid)))
    {}
    entries))


(defmulti entry-tx-data (fn [_ _ {{:strs [method]} "request"}] method))


(defmethod entry-tx-data "POST"
  [db tempids {{type "resourceType" :as resource} "resource"}]
  {:type (keyword type)
   :total-increment 1
   :version-increment 1
   :tx-data (tx/resource-upsert db tempids :server-assigned-id resource)})


(defmethod entry-tx-data "PUT"
  [db tempids {{type "resourceType" :as resource} "resource"}]
  (let [tx-data (tx/resource-upsert db tempids :client-assigned-id resource)]
    {:type (keyword type)
     :total-increment 0
     :version-increment (if (empty? tx-data) 0 1)
     :tx-data tx-data}))


(defmethod entry-tx-data "DELETE"
  [db _ {{type "resourceType" id "id"} "resource"}]
  (let [tx-data (tx/resource-deletion db type id)]
    {:type (keyword type)
     :total-increment (if (empty? tx-data) 0 -1)
     :version-increment (if (empty? tx-data) 0 1)
     :tx-data tx-data}))


(s/fdef code-tx-data
  :args (s/cat :db ::ds/db :entries coll?)
  :ret ::ds/tx-data)

(defn code-tx-data
  "Returns transaction data for creating codes of all `entries` of a transaction
  bundle."
  [db entries]
  (into [] (mapcat #(tx/resource-codes-creation db (get % "resource"))) entries))


(defn- resource-total-increments
  "Returns a map of resource type to total count increment.

  The total count is incremented for newly created resources through POST or PUT
  and decremented for DELETE. So it can be negative."
  [db entries]
  (reduce
    (fn [res {{:strs [method]} "request" {type "resourceType" id "id"} "resource"}]
      (case method
        "POST"
        (update res type (fnil inc 0))

        "PUT"
        (let [resource (util/resource db type id)]
          (if (or (nil? resource) (util/deleted? resource))
            (update res type (fnil inc 0))
            res))

        "DELETE"
        (let [resource (util/resource db type id)]
          (if (and (some? resource) (not (util/deleted? resource)))
            (update res type (fnil dec 0))
            res))))
    {}
    entries))


(defn- system-tx-data [tx-data-and-increments]
  (into
    [[:fn/increment-system-total
      (reduce
        (fn [sum {:keys [total-increment]}]
          (+ sum total-increment))
        0
        tx-data-and-increments)]
     [:fn/decrement-system-version
      (reduce
        (fn [sum {:keys [version-increment]}]
          (+ sum version-increment))
        0
        tx-data-and-increments)]]
    (mapcat
      (fn [{:keys [type total-increment version-increment]}]
        [[:fn/increment-type-total type total-increment]
         [:fn/decrement-type-version type version-increment]]))
    tx-data-and-increments))


(s/fdef tx-data
  :args (s/cat :db ::ds/db :entries coll?)
  :ret ::ds/tx-data)

(defn tx-data
  "Returns transaction data of all `entries` of a transaction bundle."
  [db entries]
  (let [entries (resolve-entry-links db entries)
        tempids (collect-tempids db entries)
        tx-data-and-increments (mapv #(entry-tx-data db tempids %) entries)]
    (into
      (system-tx-data tx-data-and-increments)
      (mapcat :tx-data) tx-data-and-increments)))
