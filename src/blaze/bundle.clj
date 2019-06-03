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
    (map #(entry-tempid db %))
    (completing
      (fn [tempids [type id tempid]]
        (assoc-in tempids [type id] tempid)))
    {}
    entries))


(defmulti entry-tx-data (fn [_ _ {{:strs [method]} "request"}] method))


(defmethod entry-tx-data "POST"
  [db tempids {:strs [resource]}]
  (tx/resource-upsert db tempids 0 resource))


(defmethod entry-tx-data "PUT"
  [db tempids {:strs [resource]}]
  (tx/resource-upsert db tempids -2 resource))


(defmethod entry-tx-data "DELETE"
  [db _ {{type "resourceType" id "id"} "resource"}]
  (tx/resource-deletion db type id))


(s/fdef tx-data
  :args (s/cat :db ::ds/db :entries coll?)
  :ret ::ds/tx-data)

(defn tx-data
  "Returns transaction data of all `entries` of a transaction bundle."
  [db entries]
  (let [entries (resolve-entry-links db entries)
        tempids (collect-tempids db entries)]
    (into [] (mapcat #(entry-tx-data db tempids %)) entries)))
