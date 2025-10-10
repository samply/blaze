(ns blaze.admin-api
  (:refer-clojure :exclude [str])
  (:require
   [blaze.admin-api.spec]
   [blaze.admin-api.validation]
   [blaze.anomaly :as ba :refer [if-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.db.impl.index.patient-last-change :as plc]
   [blaze.db.kv :as kv]
   [blaze.db.kv.rocksdb :as rocksdb]
   [blaze.elm.expression :as-alias expr]
   [blaze.elm.expression.cache :as ec]
   [blaze.elm.expression.cache.bloom-filter :as-alias bloom-filter]
   [blaze.elm.expression.spec]
   [blaze.fhir.parsing-context.spec]
   [blaze.fhir.response.create :as create-response]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.writing-context.spec]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.handler.util :as handler-util]
   [blaze.interaction.util :as iu]
   [blaze.job-scheduler :as js]
   [blaze.job-scheduler.spec]
   [blaze.job.re-index]
   [blaze.middleware.fhir.db :as db]
   [blaze.middleware.fhir.output :as fhir-output]
   [blaze.middleware.fhir.resource :as resource]
   [blaze.middleware.link-headers :as link-headers]
   [blaze.middleware.output :as output]
   [blaze.module :as m]
   [blaze.spec]
   [blaze.util :refer [str]]
   [clojure.datafy :as datafy]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [jsonista.core :as j]
   [reitit.openapi :as openapi]
   [reitit.ring]
   [reitit.ring.spec]
   [ring.util.response :as ring]
   [taoensso.timbre :as log])
  (:import
   [ca.uhn.fhir.context FhirContext]
   [ca.uhn.fhir.context.support DefaultProfileValidationSupport]
   [ca.uhn.fhir.validation FhirValidator]
   [com.google.common.base CaseFormat]
   [java.io File]
   [java.nio.file Files]
   [org.hl7.fhir.common.hapi.validation.support
    CommonCodeSystemsTerminologyService
    InMemoryTerminologyServerValidationSupport PrePopulatedValidationSupport
    ValidationSupportChain]
   [org.hl7.fhir.common.hapi.validation.validator FhirInstanceValidator]))

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

(defn- column-family-data [db column-family]
  (let [long-property (partial rocksdb/long-property db column-family)]
    {:name (name column-family)
     :estimate-num-keys (kv/estimate-num-keys db column-family)
     :estimate-live-data-size (long-property "rocksdb.estimate-live-data-size")
     :live-sst-files-size (long-property "rocksdb.live-sst-files-size")
     :size-all-mem-tables (long-property "rocksdb.size-all-mem-tables")}))

(def ^:private column-families-handler
  (fn [{:keys [db]}]
    (-> (mapv (partial column-family-data db) (rocksdb/column-families db))
        (ring/response)
        (ac/completed-future))))

(defn- column-family-state-not-found-msg [db-name column-family]
  (format "The state of the column family `%s` in database `%s` was not found." column-family db-name))

(defn- column-family-state-not-found [db-name column-family]
  (ring/not-found {:msg (column-family-state-not-found-msg db-name column-family)}))

(def ^:private cf-state-handler
  (fn [{:keys [db] {db-name :db :keys [column-family]} :path-params}]
    (-> (if (and (= "index" db-name) (= "patient-last-change-index" column-family))
          (ring/response (plc/state db))
          (column-family-state-not-found db-name column-family))
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

(defn- db-not-found [db-name]
  (ring/not-found {:msg (format "The database `%s` was not found." db-name)}))

(def ^:private wrap-dbs-db
  {:name :wrap-dbs-db
   :wrap (fn [handler dbs]
           (fn [{{db-name :db} :path-params :as request}]
             (if-let [db (dbs db-name)]
               (handler (assoc request :db db))
               (-> (db-not-found db-name)
                   (ac/completed-future)))))})

(defn- wrap-json-output
  "Middleware to output data (not resources) in JSON."
  [opts]
  (let [object-mapper (j/object-mapper opts)]
    (fn [handler]
      (fn [request]
        (do-sync [response (handler request)]
          (output/handle-response object-mapper response))))))

(defn- wrap-fhir-output
  "Middleware to output FHIR resources in JSON or XML."
  [handler writing-context]
  (fn [request]
    (do-sync [response (handler request)]
      (fhir-output/handle-response writing-context {} request response))))

(def ^:private wrap-output
  {:name :output
   :compile (fn [{:keys [response-type] :response-type.json/keys [opts]} _]
              (condp = response-type
                :json (fn [handler _writing-context]
                        ((wrap-json-output opts) handler))
                :fhir wrap-fhir-output))})

(def ^:private wrap-db
  {:name :db
   :wrap db/wrap-db})

(def ^:private wrap-resource
  {:name :resource
   :wrap resource/wrap-resource})

(def ^:private wrap-link-headers
  {:name :link-headers
   :wrap link-headers/wrap-link-headers})

(def ^:private allowed-profiles
  #{"https://samply.github.io/blaze/fhir/StructureDefinition/AsyncInteractionJob"
    "https://samply.github.io/blaze/fhir/StructureDefinition/CompactJob"
    "https://samply.github.io/blaze/fhir/StructureDefinition/ReIndexJob"})

(defn- check-profile [resource]
  (if (some allowed-profiles (map :value (-> resource :meta :profile)))
    resource
    (ba/incorrect
     "No allowed profile found."
     :outcome
     {:fhir/type :fhir/OperationOutcome
      :issue
      [{:fhir/type :fhir.OperationOutcome/issue
        :severity #fhir/code "error"
        :code #fhir/code "value"
        :details #fhir/CodeableConcept
                  {:text #fhir/string "No allowed profile found."}}]})))

(defn- validate [^FhirValidator validator writing-context resource]
  (->> ^String (fhir-spec/write-json-as-string writing-context resource)
       (.validateWithResult validator)
       (.toOperationOutcome)
       (datafy/datafy)))

(defn- error-issues [outcome]
  (update outcome :issue (partial filterv (comp #{"error"} :value :severity))))

(def ^:private wrap-validate-job
  {:name :wrap-validate-job
   :wrap (fn [handler validator writing-context]
           (fn [{:keys [body] :as request}]
             (if-ok [body (check-profile body)]
               (let [outcome (error-issues (validate validator writing-context body))]
                 (if (seq (:issue outcome))
                   (ac/completed-future (ring/bad-request outcome))
                   (handler request)))
               #(ac/completed-future (ring/bad-request (:outcome %))))))})

(defn wrap-error* [handler]
  (fn [request]
    (-> (handler request)
        (ac/exceptionally handler-util/error-response))))

(def ^:private wrap-error
  {:name :wrap-error
   :wrap wrap-error*})

(defn- camel [s]
  (.to CaseFormat/LOWER_HYPHEN CaseFormat/LOWER_CAMEL s))

(defn- router
  [{:keys [context-path admin-node validator parsing-context writing-context
           db-sync-timeout dbs create-job-handler read-job-handler
           history-job-handler search-type-job-handler pause-job-handler
           resume-job-handler cancel-job-handler cql-cache-stats-handler
           cql-bloom-filters-handler]
    :or {context-path ""
         db-sync-timeout 10000}
    :as context}]
  (reitit.ring/router
   [""
    {:openapi {:id :admin-api}
     :middleware [[wrap-output writing-context] openapi/openapi-feature]
     :response-type :json
     :response-type.json/opts {:encode-key-fn (comp camel name)}}
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
      {:middleware [[wrap-dbs-db dbs]]}
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
           :summary "Fetch the state of a column family of a database."}}]
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
    ["/Task"
     {:fhir.resource/type "Task"
      :response-type :fhir
      :middleware [wrap-error]}
     [""
      {:name :Task/type
       :get
       {:middleware [[wrap-db admin-node db-sync-timeout]]
        :handler search-type-job-handler}
       :post
       {:middleware [[wrap-resource parsing-context "Task"]
                     [wrap-validate-job validator writing-context]]
        :handler create-job-handler}}]
     ["/{id}"
      [""
       {:get
        {:middleware [[wrap-db admin-node db-sync-timeout]]
         :handler read-job-handler}}]
      ["/_history"
       {:get
        {:middleware [[wrap-db admin-node db-sync-timeout]
                      wrap-link-headers]
         :handler history-job-handler}}]
      ["/$pause"
       {:post
        {:handler pause-job-handler}}]
      ["/$resume"
       {:post
        {:handler resume-job-handler}}]
      ["/$cancel"
       {:post
        {:handler cancel-job-handler}}]]]
    ["/cql"
     {}
     ["/cache-stats"
      {:get
       {:handler cql-cache-stats-handler
        :summary "Fetch CQL cache stats."
        :openapi
        {:operation-id "cql-cache-stats"
         :responses
         {200
          {:description "CQL cache stats."
           :content
           {"application/json"
            {:schema
             {:type "object"}}}}}}}}]
     ["/bloom-filters"
      {:get
       {:handler cql-bloom-filters-handler
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

(defn- load-profile [context name]
  (log/debug "Load profile" name)
  (let [parser (.newJsonParser ^FhirContext context)
        classloader (.getContextClassLoader (Thread/currentThread))]
    (with-open [source (.getResourceAsStream classloader name)]
      (.parseResource parser source))))

(defn- profile-validation-support [context]
  (let [s (PrePopulatedValidationSupport. context)]
    (run!
     #(.addResource s (load-profile context %))
     ["blaze/db/CodeSystem-ColumnFamily.json"
      "blaze/db/CodeSystem-Database.json"
      "blaze/db/ValueSet-ColumnFamily.json"
      "blaze/db/ValueSet-Database.json"
      "blaze/job_scheduler/StructureDefinition-Job.json"
      "blaze/job_scheduler/CodeSystem-JobType.json"
      "blaze/job_scheduler/CodeSystem-JobOutput.json"
      "blaze/job/async_interaction/StructureDefinition-AsyncInteractionJob.json"
      "blaze/job/async_interaction/StructureDefinition-AsyncInteractionRequestBundle.json"
      "blaze/job/async_interaction/StructureDefinition-AsyncInteractionResponseBundle.json"
      "blaze/job/async_interaction/CodeSystem-AsyncInteractionJobOutput.json"
      "blaze/job/async_interaction/CodeSystem-AsyncInteractionJobParameter.json"
      "blaze/job/compact/CodeSystem-CompactJobOutput.json"
      "blaze/job/compact/CodeSystem-CompactJobParameter.json"
      "blaze/job/compact/StructureDefinition-CompactJob.json"
      "blaze/job/re_index/StructureDefinition-ReIndexJob.json"
      "blaze/job/re_index/CodeSystem-ReIndexJobOutput.json"
      "blaze/job/re_index/CodeSystem-ReIndexJobParameter.json"])
    s))

(defn- create-validator* []
  (let [context (FhirContext/forR4)
        _ (.newJsonParser context)
        validator (.newValidator context)
        chain (doto (ValidationSupportChain.)
                (.addValidationSupport (DefaultProfileValidationSupport. context))
                (.addValidationSupport (InMemoryTerminologyServerValidationSupport. context))
                (.addValidationSupport (CommonCodeSystemsTerminologyService. context))
                (.addValidationSupport (profile-validation-support context)))
        instanceValidator (FhirInstanceValidator. chain)]
    (.registerValidatorModule validator instanceValidator)
    validator))

(defn- create-validator []
  (try
    (create-validator*)
    (catch Exception e
      (log/error e)
      (throw e))))

(defn- create-job-handler [job-scheduler]
  (fn [{:keys [body] :as request}]
    (do-sync [job (js/create-job job-scheduler (iu/strip-meta body))]
      (let [{:blaze.db/keys [tx]} (meta job)]
        (-> (ring/response job)
            (ring/status 201)
            (ring/header "Last-Modified" (fhir-util/last-modified tx))
            (ring/header "ETag" (fhir-util/etag tx))
            (create-response/location-header request "Task" (:id job)
                                             (str (:blaze.db/t tx))))))))

(defn- job-action-handler [job-scheduler f]
  (fn [{{:keys [id]} :path-params}]
    (do-sync [job (f job-scheduler id)]
      (let [{:blaze.db/keys [tx]} (meta job)]
        (-> (ring/response job)
            (ring/header "Last-Modified" (fhir-util/last-modified tx))
            (ring/header "ETag" (fhir-util/etag tx)))))))

(def ^:private bloom-filter-xf
  (comp
   (take 100)
   (map
    #(select-keys % [::bloom-filter/hash ::bloom-filter/t ::bloom-filter/expr-form
                     ::bloom-filter/patient-count ::bloom-filter/mem-size]))
   (map #(update % ::bloom-filter/hash str))))

(defn- cql-cache-stats-handler [{::expr/keys [cache]}]
  (if cache
    (fn [_]
      (-> (ring/response {:total (ec/total cache)})
          (ac/completed-future)))
    (fn [_]
      (-> (ring/not-found {:msg "The feature \"CQL Expression Cache\" is disabled."})
          (ac/completed-future)))))

(defn- cql-bloom-filters-handler [{::expr/keys [cache]}]
  (if cache
    (fn [_]
      (-> (ring/response (into [] bloom-filter-xf (ec/list-by-t cache)))
          (ac/completed-future)))
    (fn [_]
      (-> (ring/not-found {:msg "The feature \"CQL Expression Cache\" is disabled."})
          (ac/completed-future)))))

(defmethod m/pre-init-spec :blaze/admin-api [_]
  (s/keys :req-un [:blaze/context-path ::admin-node :blaze.fhir/parsing-context
                   :blaze.fhir/writing-context :blaze/job-scheduler
                   ::read-job-handler ::history-job-handler
                   ::search-type-job-handler ::settings ::features]
          :opt [::dbs ::expr/cache ::db-sync-timeout]))

(defmethod ig/init-key :blaze/admin-api
  [_ {:keys [job-scheduler] :as config}]
  (log/info "Init Admin endpoint")
  (reitit.ring/ring-handler
   (router (assoc config :validator (create-validator)
                  :create-job-handler (create-job-handler job-scheduler)
                  :pause-job-handler (job-action-handler job-scheduler js/pause-job)
                  :resume-job-handler (job-action-handler job-scheduler js/resume-job)
                  :cancel-job-handler (job-action-handler job-scheduler js/cancel-job)
                  :cql-cache-stats-handler (cql-cache-stats-handler config)
                  :cql-bloom-filters-handler (cql-bloom-filters-handler config)))
   ((wrap-json-output {})
    (fn [{:keys [uri]}]
      (-> (ring/not-found {"uri" uri})
          (ac/completed-future))))))
