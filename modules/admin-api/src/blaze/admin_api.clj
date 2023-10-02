(ns blaze.admin-api
  (:require
   [blaze.admin-api.spec]
   [blaze.anomaly :refer [if-ok]]
   [blaze.async.comp :as ac]
   [blaze.db.impl.index.patient-last-change :as plc]
   [blaze.db.kv.rocksdb :as rocksdb]
   [blaze.elm.expression :as-alias expr]
   [blaze.elm.expression.cache :as ec]
   [blaze.elm.expression.cache.bloom-filter :as-alias bloom-filter]
   [blaze.elm.expression.spec]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [reitit.openapi :as openapi]
   [reitit.ring]
   [reitit.ring.spec]
   [ring.util.response :as ring]
   [taoensso.timbre :as log])
  (:import
   [java.io File]
   [java.nio.file Files]))

(set! *warn-on-reflection* true)

(defn- root-handler [{:keys [settings features]}]
  (fn [_]
    (-> (ring/response {:settings settings :features features})
        (ac/completed-future))))

(def ^:private openapi-handler
  (let [handler (openapi/create-openapi-handler)]
    (fn [request]
      (ac/completed-future (handler request)))))

(defn- block-cache [long-property]
  (if-ok [capacity (long-property "rocksdb.block-cache-capacity")
          usage (long-property "rocksdb.block-cache-usage")]
    {:capacity capacity
     :usage usage}
    (constantly nil)))

(defn- database-stats [[db-name db]]
  (let [long-property (partial rocksdb/long-property db)
        agg-long-property (partial rocksdb/agg-long-property db)]
    {:name db-name
     :estimate-live-data-size (agg-long-property "rocksdb.estimate-live-data-size")
     :usable-space (.getUsableSpace (Files/getFileStore (.toPath (File. ^String (rocksdb/path db)))))
     :block-cache (block-cache long-property)
     :compactions
     {:pending (long-property "rocksdb.compaction-pending")
      :running (long-property "rocksdb.num-running-compactions")}}))

(defn- databases-handler [dbs]
  (fn [_]
    (-> (ring/response (mapv database-stats dbs))
        (ac/completed-future))))

(def ^:private db-stats-handler
  (fn [{:keys [db] {db-name :db} :path-params}]
    (-> (ring/response (database-stats [db-name db]))
        (ac/completed-future))))

(def ^:private column-family-order
  {:search-param-value-index 1
   :resource-value-index 2
   :compartment-search-param-value-index 3
   :compartment-resource-type-index 4
   :active-search-params 5
   :tx-success-index 6
   :tx-error-index 7
   :t-by-instant-index 8
   :resource-as-of-index 9
   :type-as-of-index 10
   :system-as-of-index 11
   :patient-last-change-index 12
   :type-stats-index 13
   :system-stats-index 14
   :cql-bloom-filter 15
   :cql-bloom-filter-by-t 16
   :default 17
   :patient-as-of-index 18})

(defn- column-family-data [db column-family]
  (let [long-property (partial rocksdb/long-property db column-family)]
    {:name (name column-family)
     :estimate-num-keys (long-property "rocksdb.estimate-num-keys")
     :estimate-live-data-size (long-property "rocksdb.estimate-live-data-size")
     :live-sst-files-size (long-property "rocksdb.live-sst-files-size")
     :size-all-mem-tables (long-property "rocksdb.size-all-mem-tables")}))

(def ^:private column-families-handler
  (fn [{:keys [db]}]
    (-> (ring/response
         (->> (rocksdb/column-families db)
              (sort-by column-family-order)
              (map (partial column-family-data db))
              (vec)))
        (ac/completed-future))))

(def ^:private cf-state-handler
  (fn [{:keys [db] {db-name :db :keys [column-family]} :path-params}]
    (-> (if (and (= "index" db-name) (= "patient-last-change-index" column-family))
          (ring/response (plc/state db))
          (ring/not-found {:msg (format "The column family `%s` in database `%s` has no state." column-family db-name)}))
        (ac/completed-future))))

(defn- column-family-not-found-msg [db-name column-family]
  (format "The column family `%s` in database `%s` was not found." column-family db-name))

(defn- column-family-not-found [db-name column-family]
  (ring/not-found {:msg (column-family-not-found-msg db-name column-family)}))

(def ^:private cf-metadata-handler
  (fn [{:keys [db] {db-name :db :keys [column-family]} :path-params}]
    (-> (if-ok [metadata (rocksdb/column-family-meta-data db (keyword column-family))]
          (ring/response metadata)
          (fn [_] (column-family-not-found db-name column-family)))
        (ac/completed-future))))

(def ^:private cf-tables-handler
  (fn [{:keys [db] {db-name :db :keys [column-family]} :path-params}]
    (-> (if-ok [tables (rocksdb/tables db (keyword column-family))]
          (ring/response tables)
          (fn [_] (column-family-not-found db-name column-family)))
        (ac/completed-future))))

(def ^:private bloom-filter-xf
  (comp
   (take 100)
   (map
    #(select-keys % [::bloom-filter/t ::bloom-filter/expr-form
                     ::bloom-filter/patient-count ::bloom-filter/mem-size]))))

(defn- cql-bloom-filters-handler [{::expr/keys [cache]}]
  (if cache
    (fn [_]
      (-> (ring/response (into [] bloom-filter-xf (ec/list-by-t cache)))
          (ac/completed-future)))
    (fn [_]
      (-> (ring/not-found {:msg "The feature \"CQL Expression Cache\" is disabled."})
          (ac/completed-future)))))

(defn- db-not-found [db-name]
  (ring/not-found {:msg (format "The database `%s` was not found." db-name)}))

(def ^:private wrap-db
  {:name :wrap-db
   :wrap (fn [handler dbs]
           (fn [{{db-name :db} :path-params :as request}]
             (if-let [db (dbs db-name)]
               (handler (assoc request :db db))
               (-> (db-not-found db-name)
                   (ac/completed-future)))))})

(defn- router
  [{:keys [context-path dbs] :or {context-path ""} :as context}]
  (reitit.ring/router
   [""
    {:openapi {:id :admin-api}
     :middleware [openapi/openapi-feature]}
    [""
     {:get
      {:handler (root-handler context)}}]
    ["/openapi.json"
     {:get
      {:handler openapi-handler
       :openapi
       {:info
        {:title "Blaze Admin API"
         :description "The Blaze Admin API is used to monitor and control a Blaze server instance."
         :version "0.22"}
        :components
        {:schemas
         {"ColumnFamilyMetadata"
          {:type "object"
           :description "Metadata about a column family like total and level based number of files."
           :properties
           {:name {:type "string"}
            :size {:type "number"}
            :num-files {:type "number"}
            :levels
            {:type "array"
             :items
             {:type "object"
              :description "Level metadata."
              :properties
              {:level {:type "number"}
               :size {:type "number"}
               :num-files {:type "number"}}}}}}
          "BloomFilter"
          {:type "object"}}
         :parameters
         {:db
          {:name "db"
           :in "path"
           :description "The name of the database like index, transaction or resource."
           :required true
           :schema {:type "string"}}
          :column-family
          {:name "column-family"
           :in "path"
           :description "The name of the column family like default."
           :required true
           :schema {:type "string"}}}}}
       :no-doc true}}]
    ["/dbs"
     {}
     [""
      {:get
       {:handler (databases-handler dbs)
        :summary "Fetch the list of all database names."
        :openapi
        {:operation-id "getDatabases"
         :responses
         {200
          {:description "List of database names."
           :content
           {"application/json"
            {:schema
             {:type "array"
              :items {:type "string"}}}}}}}}}]
     ["/{db}"
      {:middleware [[wrap-db dbs]]}
      ["/stats"
       {:get
        {:handler db-stats-handler
         :summary "Fetch stats of a database."
         :openapi
         {:operation-id "getDatabaseStats"
          :parameters
          [{"$ref" "#/components/parameters/db"}]
          :responses
          {200
           {:description "Database statistics."
            :content
            {"application/json"
             {:schema
              {:type "object"}}}}}}}}]
      ["/column-families"
       {}
       [""
        {:get
         {:handler column-families-handler
          :summary "Fetch the list of all column families of a database."
          :openapi
          {:operation-id "getDatabaseColumnFamilies"
           :parameters
           [{"$ref" "#/components/parameters/db"}]
           :responses
           {200
            {:description "A list of column families."
             :content
             {"application/json"
              {:schema
               {:type "array"}}}}}}}}]
       ["/{column-family}"
        {}
        ["/state"
         {:get
          {:handler cf-state-handler
           :summary "Fetch the state of a column family of a database."
           :openapi
           {:operation-id "getColumnFamilyState"
            :parameters
            [{"$ref" "#/components/parameters/db"}
             {"$ref" "#/components/parameters/columnFamily"}]
            :responses
            {200
             {:description "Column family state."
              :content
              {"application/json"
               {:schema
                {:type "object"}}}}}}}}]
        ["/metadata"
         {:get
          {:handler cf-metadata-handler
           :summary "Fetch the metadata of a column family of a database."
           :openapi
           {:operation-id "getColumnFamilyMetadata"
            :parameters
            [{"$ref" "#/components/parameters/db"}
             {"$ref" "#/components/parameters/columnFamily"}]
            :responses
            {200
             {:description "Column family metadata."
              :content
              {"application/json"
               {:schema
                {"$ref" "#/components/schemas/ColumnFamilyMetadata"}}}}}}}}]
        ["/tables"
         {:get
          {:handler cf-tables-handler
           :summary "Fetch the list of all tables of a column family of a database."
           :openapi
           {:operation-id "getColumnFamilyTables"
            :parameters
            [{"$ref" "#/components/parameters/db"}
             {"$ref" "#/components/parameters/columnFamily"}]
            :responses
            {200
             {:description "A list of column family tables."
              :content
              {"application/json"
               {:schema
                {:type "array"
                 :items
                 {:type "object"
                  :properties
                  {:data-size {:type "number"}
                   :total-raw-key-size {:type "number"}}}}}}}}}}}]]]]]
    ["/cql"
     {}
     ["/bloom-filters"
      {:get
       {:handler (cql-bloom-filters-handler context)
        :summary "Fetch the list of all CQL Bloom filters."
        :openapi
        {:operation-id "cql-bloom-filters"
         :responses
         {200
          {:description "A list of CQL Bloom filters."
           :content
           {"application/json"
            {:schema
             {:type "array"
              :items {"$ref" "#/components/schemas/BloomFilter"}}}}}}}}}]]]
   {:path (str context-path "/__admin")
    :syntax :bracket}))

(defmethod ig/pre-init-spec :blaze/admin-api [_]
  (s/keys :req-un [:blaze/context-path ::dbs ::settings ::features]
          :opt [::expr/cache]))

(defmethod ig/init-key :blaze/admin-api
  [_ context]
  (log/info "Init Admin endpoint")
  (reitit.ring/ring-handler
   (router context)
   (fn [{:keys [uri]}]
     (-> (ring/not-found {:uri uri})
         (ac/completed-future)))))
