(ns blaze.bundle
  "FHIR Bundle specific stuff."
  (:require
    [blaze.datomic.transaction :as tx]
    [blaze.datomic.util :as util]
    [blaze.terminology-service :refer [term-service?]]
    [clojure.spec.alpha :as s]
    [datomic-spec.core :as ds]
    [manifold.deferred :as md :refer [deferred?]]
    [prometheus.alpha :as prom :refer [defhistogram]]
    [reitit.core :as reitit]))


(def ^:private router
  (reitit/router
    [["{type}" :type]
     ["{type}/{id}" :resource]]
    {:syntax :bracket}))


(defn match-url [url]
  (let [match (reitit/match-by-path router url)
        {:keys [type id]} (:path-params match)]
    [type id]))


(defn- resolve-link
  [index link]
  (if-let [{type "resourceType" id "id"} (get index link)]
    (str type "/" id)
    link))


(declare resolve-links)


(defn- resolve-single-element-links
  [{:keys [index] :as context}
   {:element/keys [type-code primitive? type]}
   value]
  (cond
    (= "Reference" type-code)
    (if-let [reference (get value "reference")]
      (assoc value "reference" (resolve-link index reference))
      value)

    primitive?
    value

    (= "Resource" type-code)
    (resolve-links context (keyword (get value "resourceType")) value)

    :else
    (resolve-links context type value)))


(defn- resolve-element-links
  [context {:db/keys [cardinality] :as element} value]
  (if (= :db.cardinality/many cardinality)
    (mapv #(resolve-single-element-links context element %) value)
    (resolve-single-element-links context element value)))


(defn- resolve-links
  [{:keys [db] :as context} type-ident resource]
  (transduce
    (map #(util/cached-entity db %))
    (completing
      (fn [resource element]
        (if-let [[value {:element/keys [json-key] :as element}]
                 (tx/find-json-value db element resource)]
          (assoc resource
            json-key (resolve-element-links context element value))
          resource)))
    resource
    (:type/elements (util/cached-entity db type-ident))))


(s/fdef resolve-entry-links
  :args (s/cat :db ::ds/db :entries coll?))

(defn resolve-entry-links
  "Resolves all links in `entries` according the transaction processing rules."
  [db entries]
  (let [index (reduce (fn [r {url "fullUrl" :strs [resource]}] (assoc r url resource)) {} entries)]
    (mapv
      (fn [entry]
        (if-let [{type "resourceType" :as resource} (get entry "resource")]
          (assoc entry "resource" (resolve-links {:db db :index index} (keyword type) resource))
          entry))
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


(s/fdef annotate-codes
  :args (s/cat :term-service term-service? :db ::ds/db :entries coll?)
  :ret deferred?)

(defn annotate-codes
  [term-service db entries]
  (md/loop [[entry & entries] entries
            res []]
    (if-let [{:strs [resource] :as entry} entry]
      (if resource
        (-> (tx/annotate-codes term-service db resource)
            (md/chain' #(md/recur entries (conj res (assoc entry "resource" %)))))
        (md/recur entries (conj res entry)))
      res)))


(defmulti entry-tx-data (fn [_ _ {{:strs [method]} "request"}] method))


(defmethod entry-tx-data "POST"
  [db tempids {{type "resourceType" id "id" :as resource} "resource"}]
  {:type (keyword type)
   :tempid (get-in tempids [type id])
   :total-increment 1
   :version-increment 1
   :tx-data (tx/resource-upsert db tempids :server-assigned-id resource)})


(defmethod entry-tx-data "PUT"
  [db tempids {{type "resourceType" id "id" :as resource} "resource"}]
  (let [tx-data (tx/resource-upsert db tempids :client-assigned-id resource)
        tempid (get-in tempids [type id])]
    (cond->
      {:type (keyword type)
       :total-increment (if tempid 1 0)
       :version-increment (if (empty? tx-data) 0 1)
       :tx-data tx-data}

      (and tempid (seq tx-data))
      (assoc :tempid tempid)

      (and (nil? tempid) (seq tx-data))
      (assoc :eid (:db/id (util/resource db type id))))))


(defmethod entry-tx-data "DELETE"
  [db _ {{:strs [url]} "request"}]
  (let [[type id] (match-url url)
        tx-data (tx/resource-deletion db type id)
        resource (util/resource db type id)]
    (cond->
      {:type (keyword type)
       :total-increment (if (empty? tx-data) 0 -1)
       :version-increment (if (empty? tx-data) 0 1)
       :tx-data tx-data}

      (and resource (not (util/deleted? resource)))
      (assoc :eid (:db/id resource)))))


(s/fdef code-tx-data
  :args (s/cat :db ::ds/db :entries coll?)
  :ret ::ds/tx-data)

(defn code-tx-data
  "Returns transaction data for creating codes of all `entries` of a transaction
  bundle."
  [db entries]
  (into [] (mapcat #(tx/resource-codes-creation db (get % "resource"))) entries))


(defn- sum [key ms]
  (reduce
    (fn [sum m]
      (+ sum (get m key)))
    0
    ms))


(defn- system-and-type-tx-data [increments]
  (-> (let [total (sum :total-increment increments)
            version (sum :version-increment increments)]
        (cond-> []
          (not (zero? total)) (conj [:fn/increment-system-total total])
          (pos? version) (conj [:fn/increment-system-version version])))
      (into
        (mapcat
          (fn [[type increments]]
            (let [total (sum :total-increment increments)
                  version (sum :version-increment increments)]
              (cond-> []
                (not (zero? total)) (conj [:fn/increment-type-total type total])
                (pos? version) (conj [:fn/increment-type-version type version])))))
        (group-by :type increments))
      (into
        (mapcat
          (fn [{:keys [tempid eid]}]
            (cond
              tempid [[:db/add "datomic.tx" :tx/resources tempid]]
              eid [[:db/add "datomic.tx" :tx/resources eid]]
              :else [])))
        increments)))


(defhistogram tx-data-duration-seconds
  "FHIR bundle transaction data generating latencies in seconds."
  {:namespace "fhir"
   :subsystem "bundle"}
  (take 12 (iterate #(* 2 %) 0.01)))


(s/fdef tx-data
  :args (s/cat :db ::ds/db :entries coll?)
  :ret ::ds/tx-data)

(defn tx-data
  "Returns transaction data of all `entries` of a transaction bundle."
  [db entries]
  (with-open [_ (prom/timer tx-data-duration-seconds)]
    (let [entries (resolve-entry-links db entries)
          tempids (collect-tempids db entries)
          tx-data-and-increments (mapv #(entry-tx-data db tempids %) entries)]
      (into
        (system-and-type-tx-data tx-data-and-increments)
        (mapcat :tx-data) tx-data-and-increments))))
