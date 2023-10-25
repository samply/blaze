(ns blaze.admin-api
  (:require
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
    [java.nio.file Files]))


(set! *warn-on-reflection* true)


(def ^:private openapi-handler
  (let [handler (openapi/create-openapi-handler)]
    (fn [request]
      (ac/completed-future (handler request)))))


(defn- db-stats-handler [db]
  (fn [_]
    (let [long-property (partial rocksdb/long-property db)
          agg-long-property (partial rocksdb/agg-long-property db)]
      (-> (ring/response
            {:estimate-live-data-size (agg-long-property "rocksdb.estimate-live-data-size")
             :usable-space (.getUsableSpace (Files/getFileStore (rocksdb/path db)))
             :block-cache
             {:capacity (long-property "rocksdb.block-cache-capacity")
              :usage (long-property "rocksdb.block-cache-usage")}
             :compactions
             {:pending (long-property "rocksdb.compaction-pending")
              :running (long-property "rocksdb.num-running-compactions")}})
          (ac/completed-future)))))


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
   :default 16
   :patient-as-of-index 17})


(defn- column-family-data [db column-family]
  (let [long-property (partial rocksdb/long-property db column-family)]
    {:name (name column-family)
     :estimate-num-keys (long-property "rocksdb.estimate-num-keys")
     :estimate-live-data-size (long-property "rocksdb.estimate-live-data-size")
     :live-sst-files-size (long-property "rocksdb.live-sst-files-size")
     :size-all-mem-tables (long-property "rocksdb.size-all-mem-tables")}))


(defn- rocksdb-column-families-handler [index-kv-store]
  (fn [_]
    (-> (ring/response
          (->> (rocksdb/column-families index-kv-store)
               (sort-by column-family-order)
               (map (partial column-family-data index-kv-store))
               (vec)))
        (ac/completed-future))))


(defn- cf-state-handler [index-kv-store]
  (fn [{{:keys [column-family]} :path-params}]
    (-> (if (= "patient-last-change-index" column-family)
          (ring/response (plc/state index-kv-store))
          (ring/not-found {:msg (format "The column family `%s` has no state." column-family)}))
        (ac/completed-future))))


(defn- cf-rocksdb-table-handler [index-kv-store]
  (fn [{{:keys [column-family]} :path-params}]
    (-> (if-ok [tables (rocksdb/tables index-kv-store (keyword column-family))]
          (ring/response {:tables tables})
          (fn [_] (ring/not-found {:msg (format "The column family `%s` was not found." column-family)})))
        (ac/completed-future))))


(defn- cf-rocksdb-metadata-handler [index-kv-store]
  (fn [{{:keys [column-family]} :path-params}]
    (-> (if-ok [metadata (rocksdb/column-family-meta-data index-kv-store (keyword column-family))]
          (ring/response metadata)
          (fn [_] (ring/not-found {:msg (format "The column family `%s` was not found." column-family)})))
        (ac/completed-future))))


(def ^:private bloom-filter-xf
  (map
    (fn [{::bloom-filter/keys [t expr-form patient-count mem-size]}]
      {:t t
       :expr-form expr-form
       :patient-count patient-count
       :mem-size mem-size})))


(defn- cql-bloom-filters-handler [{::expr/keys [cache]}]
  (if cache
    (fn [_]
      (-> (ring/response {:bloom-filters (into [] bloom-filter-xf (ec/list cache))})
          (ac/completed-future)))
    (fn [_]
      (-> (ring/not-found {:msg "The feature \"CQL Expression Cache\" is disabled."})
          (ac/completed-future)))))


(defn- router
  [{:keys [context-path index-kv-store] :or {context-path ""} :as context}]
  (reitit.ring/router
    [""
     {:openapi {:id :admin-api}
      :middleware [openapi/openapi-feature]}
     ["/openapi.json"
      {:get {:handler openapi-handler
             :openapi {:info {:title "Blaze Admin API" :version "0.22"}}
             :no-doc true}}]
     ["/db"
      {}
      ["/index"
       {}
       ["/stats"
        {:get
         {:handler (db-stats-handler index-kv-store)}}]
       ["/column-families"
        {}
        [""
         {:get
          {:handler (rocksdb-column-families-handler index-kv-store)
           :summary "Fetch the list of all column families."
           :openapi
           {:responses
            {200
             {:content
              {"application/json"
               {:schema
                {:type "object"
                 :properties
                 [:column-families
                  {:type "array"
                   :items {:type "string"}}]}}}}}}}}]
        ["/{column-family}"
         {}
         ["/state"
          {:get
           {:handler (cf-state-handler index-kv-store)}}]
         ["/metadata"
          {:get
           {:handler (cf-rocksdb-metadata-handler index-kv-store)
            :summary "Fetch the metadata of a column family."
            :openapi
            {:parameters
             [{:name "column-family"
               :in "path"
               :required true}]
             :responses
             {200
              {:content
               {"application/json"
                {:schema
                 {:type "object"
                  :properties
                  [:name {:type "string"}]}}}}}}}}]
         ["/rocksdb-tables"
          {:get
           {:handler (cf-rocksdb-table-handler index-kv-store)
            :summary "Fetch the list of all tables of a column family."
            :openapi
            {:parameters
             [{:name "column-family"
               :in "path"
               :required true}]
             :responses
             {200
              {:content
               {"application/json"
                {:schema
                 {:type "object"
                  :properties
                  [:tables
                   {:type "array"
                    :items
                    {:type "object"
                     :properties
                     [:data-size {:type "integer"}
                      :total-raw-key-size {:type "integer"}]}}]}}}}}}}}]]]]]
     ["/cql"
      {}
      ["/bloom-filters"
       {:get
        {:handler (cql-bloom-filters-handler context)
         :summary "Fetch the list of all CQL Bloom filters."
         :openapi
         {:responses
          {200
           {:content
            {"application/json"
             {:schema
              {:type "object"
               :properties
               [:bloom-filters
                {:type "array"
                 :items {:type "object"}}]}}}}}}}}]]]
    {:path (str context-path "/__admin")
     :syntax :bracket}))


(defmethod ig/pre-init-spec :blaze/admin-api [_]
  (s/keys :req-un [:blaze/context-path :blaze.db/index-kv-store]
          :opt [::expr/cache]))


(defmethod ig/init-key :blaze/admin-api
  [_ context]
  (log/info "Init Admin endpoint")
  (reitit.ring/ring-handler
    (router context)
    (fn [{:keys [uri]}]
      (-> (ring/not-found {:uri uri})
          (ac/completed-future)))))
