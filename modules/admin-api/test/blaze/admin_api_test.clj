(ns blaze.admin-api-test
  (:require
   [blaze.admin-api :as admin-api]
   [blaze.async.comp :refer [do-sync]]
   [blaze.db.api-stub :refer [mem-node-config with-system-data]]
   [blaze.db.kv :as-alias kv]
   [blaze.db.kv.rocksdb :as rocksdb]
   [blaze.db.node :refer [node?]]
   [blaze.db.resource-store :as rs]
   [blaze.db.tx-log :as tx-log]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.interaction.create]
   [blaze.interaction.read]
   [blaze.interaction.search-type]
   [blaze.job-scheduler]
   [blaze.middleware.fhir.db-spec]
   [blaze.module.test-util :refer [with-system]]
   [blaze.page-store-spec]
   [blaze.page-store.local]
   [blaze.test-util :as tu :refer [given-thrown]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [java-time.api :as time]
   [jsonista.core :as j]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [java.nio.file Files]
   [java.nio.file.attribute FileAttribute]
   [java.time Instant]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-level! :trace)

(test/use-fixtures :each tu/fixture)

(def minimal-config
  (assoc
   mem-node-config
   :blaze/admin-api
   {:context-path "/fhir"
    :admin-node (ig/ref :blaze.db/node)
    :job-scheduler (ig/ref :blaze/job-scheduler)
    :create-handler (ig/ref :blaze.interaction/create)
    :read-handler (ig/ref :blaze.interaction/read)
    :search-type-handler (ig/ref :blaze.interaction/search-type)
    :settings []
    :features []}

   :blaze/job-scheduler
   {:node (ig/ref :blaze.db/node)
    :scheduler (ig/ref :blaze/scheduler)}

   :blaze/scheduler {}

   :blaze.interaction/create
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}

   :blaze.interaction/read {}

   :blaze.interaction/search-type
   {:clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :page-store (ig/ref :blaze/page-store)}

   :blaze.page-store/local
   {:secure-rng (ig/ref :blaze.test/fixed-rng)}

   :blaze.test/fixed-rng {}
   :blaze.test/fixed-rng-fn {}))

(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze/admin-api nil})
      :key := :blaze/admin-api
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze/admin-api {}})
      :key := :blaze/admin-api
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :context-path))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :admin-node))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :job-scheduler))
      [:explain ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :create-handler))
      [:explain ::s/problems 4 :pred] := `(fn ~'[%] (contains? ~'% :read-handler))
      [:explain ::s/problems 5 :pred] := `(fn ~'[%] (contains? ~'% :search-type-handler))
      [:explain ::s/problems 6 :pred] := `(fn ~'[%] (contains? ~'% :settings))
      [:explain ::s/problems 7 :pred] := `(fn ~'[%] (contains? ~'% :features))))

  (testing "invalid context path"
    (given-thrown (ig/init {:blaze/admin-api {:context-path ::invalid}})
      :key := :blaze/admin-api
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :admin-node))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :job-scheduler))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :create-handler))
      [:explain ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :read-handler))
      [:explain ::s/problems 4 :pred] := `(fn ~'[%] (contains? ~'% :search-type-handler))
      [:explain ::s/problems 5 :pred] := `(fn ~'[%] (contains? ~'% :settings))
      [:explain ::s/problems 6 :pred] := `(fn ~'[%] (contains? ~'% :features))
      [:explain ::s/problems 7 :via] := [:blaze/context-path]
      [:explain ::s/problems 7 :pred] := `string?
      [:explain ::s/problems 7 :val] := ::invalid))

  (testing "invalid admin node"
    (given-thrown (ig/init {:blaze/admin-api {:admin-node ::invalid}})
      :key := :blaze/admin-api
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :context-path))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :job-scheduler))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :create-handler))
      [:explain ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :read-handler))
      [:explain ::s/problems 4 :pred] := `(fn ~'[%] (contains? ~'% :search-type-handler))
      [:explain ::s/problems 5 :pred] := `(fn ~'[%] (contains? ~'% :settings))
      [:explain ::s/problems 6 :pred] := `(fn ~'[%] (contains? ~'% :features))
      [:explain ::s/problems 7 :via] := [:blaze.db/node]
      [:explain ::s/problems 7 :pred] := `node?
      [:explain ::s/problems 7 :val] := ::invalid))

  (testing "invalid settings"
    (given-thrown (ig/init {:blaze/admin-api {:settings ::invalid}})
      :key := :blaze/admin-api
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :context-path))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :admin-node))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :job-scheduler))
      [:explain ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :create-handler))
      [:explain ::s/problems 4 :pred] := `(fn ~'[%] (contains? ~'% :read-handler))
      [:explain ::s/problems 5 :pred] := `(fn ~'[%] (contains? ~'% :search-type-handler))
      [:explain ::s/problems 6 :pred] := `(fn ~'[%] (contains? ~'% :features))
      [:explain ::s/problems 7 :via] := [::admin-api/settings]
      [:explain ::s/problems 7 :pred] := `coll?
      [:explain ::s/problems 7 :val] := ::invalid))

  (testing "invalid features"
    (given-thrown (ig/init {:blaze/admin-api {:features ::invalid}})
      :key := :blaze/admin-api
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :context-path))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :admin-node))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :job-scheduler))
      [:explain ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :create-handler))
      [:explain ::s/problems 4 :pred] := `(fn ~'[%] (contains? ~'% :read-handler))
      [:explain ::s/problems 5 :pred] := `(fn ~'[%] (contains? ~'% :search-type-handler))
      [:explain ::s/problems 6 :pred] := `(fn ~'[%] (contains? ~'% :settings))
      [:explain ::s/problems 7 :via] := [::admin-api/features]
      [:explain ::s/problems 7 :pred] := `coll?
      [:explain ::s/problems 7 :val] := ::invalid))

  (testing "with minimal config"
    (with-system [{handler :blaze/admin-api} minimal-config]
      (is (fn? handler)))))

(defn- new-temp-dir! []
  (str (Files/createTempDirectory "blaze" (make-array FileAttribute 0))))

(defn- config [dir]
  {:blaze.db/node
   {:tx-log (ig/ref :blaze.db.main/tx-log)
    :tx-cache (ig/ref :blaze.db.main/tx-cache)
    :indexer-executor (ig/ref :blaze.db.node.main/indexer-executor)
    :resource-store (ig/ref :blaze.db.main/resource-store)
    :kv-store (ig/ref :blaze.db.main/index-kv-store)
    :resource-indexer (ig/ref :blaze.db.node.main/resource-indexer)
    :search-param-registry (ig/ref :blaze.db/search-param-registry)
    :poll-timeout (time/millis 10)}

   [:blaze.db/node :blaze.db.admin/node]
   {:tx-log (ig/ref :blaze.db.admin/tx-log)
    :tx-cache (ig/ref :blaze.db.admin/tx-cache)
    :indexer-executor (ig/ref :blaze.db.node.admin/indexer-executor)
    :resource-store (ig/ref :blaze.db.admin/resource-store)
    :kv-store (ig/ref :blaze.db.admin/index-kv-store)
    :resource-indexer (ig/ref :blaze.db.node.admin/resource-indexer)
    :search-param-registry (ig/ref :blaze.db/search-param-registry)
    :poll-timeout (time/millis 10)}

   [::tx-log/local :blaze.db.main/tx-log]
   {:kv-store (ig/ref :blaze.db.main/transaction-kv-store)
    :clock (ig/ref :blaze.test/fixed-clock)}

   [::tx-log/local :blaze.db.admin/tx-log]
   {:kv-store (ig/ref :blaze.db.admin/transaction-kv-store)
    :clock (ig/ref :blaze.test/fixed-clock)}

   [::kv/mem :blaze.db.main/transaction-kv-store]
   {:column-families {}}

   [::kv/mem :blaze.db.admin/transaction-kv-store]
   {:column-families {}}

   :blaze.test/fixed-clock {}

   [:blaze.db/tx-cache :blaze.db.main/tx-cache]
   {:kv-store (ig/ref :blaze.db.main/index-kv-store)}

   [:blaze.db/tx-cache :blaze.db.admin/tx-cache]
   {:kv-store (ig/ref :blaze.db.admin/index-kv-store)}

   [:blaze.db.node/indexer-executor :blaze.db.node.main/indexer-executor] {}
   [:blaze.db.node/indexer-executor :blaze.db.node.admin/indexer-executor] {}

   [::kv/rocksdb :blaze.db.main/index-kv-store]
   {:dir (str dir "/index")
    :block-cache (ig/ref ::rocksdb/block-cache)
    :column-families
    {:search-param-value-index
     {:write-buffer-size-in-mb 1
      :max-write-buffer-number 1
      :max-bytes-for-level-base-in-mb 1
      :target-file-size-base-in-mb 1}
     :resource-value-index nil
     :compartment-search-param-value-index
     {:write-buffer-size-in-mb 1
      :max-write-buffer-number 1
      :max-bytes-for-level-base-in-mb 1
      :target-file-size-base-in-mb 1}
     :compartment-resource-type-index nil
     :active-search-params nil
     :tx-success-index {:reverse-comparator? true}
     :tx-error-index nil
     :t-by-instant-index {:reverse-comparator? true}
     :resource-as-of-index nil
     :type-as-of-index nil
     :system-as-of-index nil
     :patient-last-change-index
     {:write-buffer-size-in-mb 1
      :max-write-buffer-number 1
      :max-bytes-for-level-base-in-mb 1
      :target-file-size-base-in-mb 1}
     :type-stats-index nil
     :system-stats-index nil}}

   [::kv/mem :blaze.db.admin/index-kv-store]
   {:column-families
    {:search-param-value-index nil
     :resource-value-index nil
     :compartment-search-param-value-index nil
     :compartment-resource-type-index nil
     :active-search-params nil
     :tx-success-index {:reverse-comparator? true}
     :tx-error-index nil
     :t-by-instant-index {:reverse-comparator? true}
     :resource-as-of-index nil
     :type-as-of-index nil
     :system-as-of-index nil
     :type-stats-index nil
     :system-stats-index nil}}

   [::rs/kv :blaze.db.main/resource-store]
   {:kv-store (ig/ref :blaze.db.main/resource-kv-store)}

   [::rs/kv :blaze.db.admin/resource-store]
   {:kv-store (ig/ref :blaze.db.admin/resource-kv-store)}

   [::kv/rocksdb :blaze.db.main/resource-kv-store]
   {:dir (str dir "/resource")
    :column-families {}}

   [::kv/mem :blaze.db.admin/resource-kv-store]
   {:column-families {}}

   [:blaze.db.node/resource-indexer :blaze.db.node.main/resource-indexer]
   {:kv-store (ig/ref :blaze.db.main/index-kv-store)
    :resource-store (ig/ref :blaze.db.main/resource-store)
    :search-param-registry (ig/ref :blaze.db/search-param-registry)}

   [:blaze.db.node/resource-indexer :blaze.db.node.admin/resource-indexer]
   {:kv-store (ig/ref :blaze.db.admin/index-kv-store)
    :resource-store (ig/ref :blaze.db.admin/resource-store)
    :search-param-registry (ig/ref :blaze.db/search-param-registry)}

   :blaze.db/search-param-registry
   {:structure-definition-repo structure-definition-repo}

   ::rocksdb/block-cache {:size-in-mb 1}

   :blaze/admin-api
   {:context-path "/fhir"
    :admin-node (ig/ref :blaze.db.admin/node)
    :job-scheduler (ig/ref :blaze/job-scheduler)
    :create-handler (ig/ref :blaze.interaction/create)
    :read-handler (ig/ref :blaze.interaction/read)
    :search-type-handler (ig/ref :blaze.interaction/search-type)
    :dbs {"index" (ig/ref :blaze.db.main/index-kv-store)
          "resource" (ig/ref :blaze.db.main/resource-kv-store)}
    :settings []
    :features []
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}

   :blaze/job-scheduler
   {:node (ig/ref :blaze.db.admin/node)
    :scheduler (ig/ref :blaze/scheduler)}

   :blaze/scheduler {}

   :blaze.interaction/create
   {:node (ig/ref :blaze.db.admin/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}

   :blaze.interaction/read {}

   :blaze.interaction/search-type
   {:clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :page-store (ig/ref :blaze/page-store)}

   :blaze.page-store/local
   {:secure-rng (ig/ref :blaze.test/fixed-rng)}

   :blaze.test/fixed-rng {}
   :blaze.test/fixed-rng-fn {}})

(defn wrap-read-json [handler]
  (fn [request]
    (do-sync [response (handler request)]
      (update response :body j/read-value))))

(defmacro with-handler [[handler-binding & [system-binding]] config txs & body]
  `(with-system-data [{handler# :blaze/admin-api :as system#} ~config]
     ~txs
     (let [~handler-binding (wrap-read-json handler#)
           ~(or system-binding '_) system#]
       ~@body)))

(deftest handler-not-found-test
  (with-handler [handler] (config (new-temp-dir!)) []
    (given @(handler
             {:request-method :get
              :uri "/fhir/__admin/foo"})
      :status := 404)))

(deftest handler-openapi-test
  (with-handler [handler] (config (new-temp-dir!)) []
    (let [{:keys [status body]} @(handler
                                  {:request-method :get
                                   :uri "/fhir/__admin/openapi.json"})]

      (testing "status"
        (is (= 200 status)))

      (testing "openapi version"
        (is (= "3.1.0" (get body "openapi"))))

      (testing "info"
        (given (get body "info")
          "title" := "Blaze Admin API"
          "description" := "The Blaze Admin API is used to monitor and control a Blaze server instance."))

      (testing "schemas"
        (given (get-in body ["components" "schemas"])
          ["ColumnFamilyMetadata" "type"] := "object"
          ["ColumnFamilyMetadata" "description"] := "Metadata about a column family like total and level based number of files."))

      (testing "parameters"
        (given (get-in body ["components" "parameters"])
          ["db" "name"] := "db"
          ["db" "in"] := "path"
          ["db" "description"] := "The name of the database like index, transaction or resource."
          ["db" "required"] := true
          ["db" "schema" "type"] := "string"
          ["columnFamily" "name"] := "column-family"
          ["columnFamily" "in"] := "path"
          ["columnFamily" "description"] := "The name of the column family like default."
          ["columnFamily" "required"] := true
          ["columnFamily" "schema" "type"] := "string"))

      (testing "operations"
        (testing "getDatabases"
          (let [op (get-in body ["paths" "/fhir/__admin/dbs" "get"])]
            (given op
              "operationId" := "getDatabases"
              "summary" := "Fetch the list of all database names."
              "parameters" := nil)

            (testing "responses"
              (testing "200"
                (given (get-in op ["responses" "200"])
                  "description" := "List of database names."
                  ["content" "application/json" "schema" "type"] := "array"
                  ["content" "application/json" "schema" "items" "type"] := "string")))))

        (testing "getDatabaseStats"
          (let [op (get-in body ["paths" "/fhir/__admin/dbs/{db}/stats" "get"])]
            (given op
              "operationId" := "getDatabaseStats"
              "summary" := "Fetch stats of a database."
              ["parameters" count] := 1
              ["parameters" 0 "$ref"] := "#/components/parameters/db")

            (testing "responses"
              (testing "200"
                (given (get-in op ["responses" "200"])
                  "description" := "Database statistics."
                  ["content" "application/json" "schema" "type"] := "object")))))

        (testing "getDatabaseColumnFamilies"
          (let [op (get-in body ["paths" "/fhir/__admin/dbs/{db}/column-families" "get"])]
            (given op
              "operationId" := "getDatabaseColumnFamilies"
              "summary" := "Fetch the list of all column families of a database."
              ["parameters" count] := 1
              ["parameters" 0 "$ref"] := "#/components/parameters/db")

            (testing "responses"
              (testing "200"
                (given (get-in op ["responses" "200"])
                  ["description"] := "A list of column families."
                  ["content" "application/json" "schema" "type"] := "array")))))

        (testing "getColumnFamilyMetadata"
          (let [op (get-in body ["paths" "/fhir/__admin/dbs/{db}/column-families/{column-family}/metadata" "get"])]
            (given op
              "operationId" := "getColumnFamilyMetadata"
              "summary" := "Fetch the metadata of a column family of a database."
              ["parameters" count] := 2
              ["parameters" 0 "$ref"] := "#/components/parameters/db"
              ["parameters" 1 "$ref"] := "#/components/parameters/columnFamily")

            (testing "responses"
              (testing "200"
                (given (get-in op ["responses" "200"])
                  "description" := "Column family metadata."
                  ["content" "application/json" "schema" "$ref"] := "#/components/schemas/ColumnFamilyMetadata")))))

        (testing "getColumnFamilyTables"
          (let [op (get-in body ["paths" "/fhir/__admin/dbs/{db}/column-families/{column-family}/tables" "get"])]
            (given op
              "operationId" := "getColumnFamilyTables"
              "summary" := "Fetch the list of all tables of a column family of a database."
              ["parameters" count] := 2
              ["parameters" 0 "$ref"] := "#/components/parameters/db"
              ["parameters" 1 "$ref"] := "#/components/parameters/columnFamily")

            (testing "responses"
              (testing "200"
                (given (get-in op ["responses" "200"])
                  "description" := "A list of column family tables."
                  ["content" "application/json" "schema" "type"] := "array"
                  ["content" "application/json" "schema" "items" "type"] := "object"
                  ["content" "application/json" "schema" "items" "properties" "dataSize" "type"] := "number"
                  ["content" "application/json" "schema" "items" "properties" "totalRawKeySize" "type"] := "number")))))))))

(deftest root-test
  (testing "without settings and features"
    (with-handler [handler] (config (new-temp-dir!)) []
      (testing "success"
        (given @(handler
                 {:request-method :get
                  :uri "/fhir/__admin"})
          :status := 200
          [:body "settings" count] := 0
          [:body "features" count] := 0))))

  (testing "with one setting"
    (with-handler [handler]
      (assoc-in (config (new-temp-dir!))
                [:blaze/admin-api :settings]
                [{:name "SERVER_PORT"
                  :value 8081
                  :default-value 8080}])
      []
      (testing "success"
        (given @(handler
                 {:request-method :get
                  :uri "/fhir/__admin"})
          :status := 200
          [:body "settings" count] := 1
          [:body "settings" 0 "name"] := "SERVER_PORT"
          [:body "settings" 0 "value"] := 8081
          [:body "settings" 0 "defaultValue"] := 8080
          [:body "features" count] := 0))))

  (testing "with one feature"
    (with-handler [handler]
      (assoc-in (config (new-temp-dir!))
                [:blaze/admin-api :features]
                [{:name "OpenID Authentication"
                  :toggle "OPENID_PROVIDER_URL"
                  :enabled true}])
      []
      (testing "success"
        (given @(handler
                 {:request-method :get
                  :uri "/fhir/__admin"})
          :status := 200
          [:body "settings" count] := 0
          [:body "features" count] := 1
          [:body "features" 0 "name"] := "OpenID Authentication"
          [:body "features" 0 "toggle"] := "OPENID_PROVIDER_URL"
          [:body "features" 0 "enabled"] := true)))))

(deftest databases-test
  (testing "with minimal config"
    (with-handler [handler] minimal-config []
      (testing "success"
        (given @(handler
                 {:request-method :get
                  :uri "/fhir/__admin/dbs"})
          :status := 200
          [:body count] := 0))))

  (testing "with normal config"
    (with-handler [handler] (config (new-temp-dir!)) []
      (testing "success"
        (given @(handler
                 {:request-method :get
                  :uri "/fhir/__admin/dbs"})
          :status := 200
          [:body count] := 2
          [:body 0 "name"] := "index"
          [:body 1 "name"] := "resource")))))

(deftest db-stats-test
  (with-handler [handler] (config (new-temp-dir!)) []
    (testing "with unknown database"
      (testing "success"
        (given @(handler
                 {:request-method :get
                  :uri "/fhir/__admin/dbs/unknown/stats"})
          :status := 404
          [:body "msg"] := "The database `unknown` was not found.")))

    (testing "without block cache"
      (testing "success"
        (given @(handler
                 {:request-method :get
                  :uri "/fhir/__admin/dbs/resource/stats"})
          :status := 200
          [:body "estimateLiveDataSize"] := 0
          [:body "usableSpace"] :? pos-int?
          [:body "blockCache"] := nil
          [:body "compactions" "pending"] := 0
          [:body "compactions" "running"] := 0)))

    (testing "with block cache"
      (testing "success"
        (given @(handler
                 {:request-method :get
                  :uri "/fhir/__admin/dbs/index/stats"})
          :status := 200
          [:body "estimateLiveDataSize"] := 0
          [:body "usableSpace"] :? pos-int?
          [:body "blockCache" "capacity"] :? pos-int?
          [:body "blockCache" "usage"] :? pos-int?
          [:body "compactions" "pending"] := 0
          [:body "compactions" "running"] := 0)))))

(deftest column-families-test
  (testing "index database"
    (with-handler [handler] (config (new-temp-dir!)) []
      (testing "success"
        (given @(handler
                 {:request-method :get
                  :uri "/fhir/__admin/dbs/index/column-families"})
          :status := 200
          [:body 0 "name"] := "active-search-params"
          [:body 0 "estimateNumKeys"] := 0
          [:body 0 "estimateLiveDataSize"] := 0
          [:body 0 "liveSstFilesSize"] := 0
          [:body 0 "sizeAllMemTables"] := 2048
          [:body 1 "name"] := "compartment-resource-type-index"))))

  (testing "resource database"
    (with-handler [handler] (config (new-temp-dir!)) []
      (testing "success"
        (given @(handler
                 {:request-method :get
                  :uri "/fhir/__admin/dbs/resource/column-families"})
          :status := 200
          [:body count] := 1
          [:body 0 "name"] := "default"
          [:body 0 "estimateNumKeys"] := 0
          [:body 0 "estimateLiveDataSize"] := 0
          [:body 0 "liveSstFilesSize"] := 0
          [:body 0 "sizeAllMemTables"] := 2048)))))

(deftest column-family-metadata-test
  (with-handler [handler] (config (new-temp-dir!)) []
    (testing "search-param-value-index in index database"
      (given @(handler
               {:request-method :get
                :uri "/fhir/__admin/dbs/index/column-families/search-param-value-index/metadata"})
        :status := 200
        [:body "name"] := "search-param-value-index"
        [:body "numFiles"] := 0
        [:body "fileSize"] := 0
        [:body "levels" count] := 7
        [:body "levels" 0 "level"] := 0
        [:body "levels" 0 "numFiles"] := 0
        [:body "levels" 0 "fileSize"] := 0
        [:body "levels" 1 "level"] := 1))

    (testing "unknown column-family in index database"
      (given @(handler
               {:request-method :get
                :uri "/fhir/__admin/dbs/index/column-families/foo/metadata"})
        :status := 404
        [:body "msg"] := "The column family `foo` in database `index` was not found."))))

(deftest rocksdb-tables-test
  (with-handler [handler] (config (new-temp-dir!))
    (-> (fn [pat-id]
          (into
           [[:put {:fhir/type :fhir/Patient :id (str pat-id)}]]
           (map (fn [obs-id]
                  [:put {:fhir/type :fhir/Observation :id (str obs-id)
                         :code
                         #fhir/CodeableConcept
                          {:coding
                           [#fhir/Coding
                             {:system #fhir/uri"system-192253"
                              :code #fhir/code"code-192300"}]}
                         :subject (type/map->Reference {:reference (str "Patient/" pat-id)})}]))
           (range 120)))
        (mapv (range 100)))

    (Thread/sleep 1000)

    (testing "success"
      (given @(handler
               {:request-method :get
                :uri "/fhir/__admin/dbs/index/column-families/compartment-search-param-value-index/tables"})
        :status := 200
        [:body count] := 1
        [:body 0 "name"] :? string?
        [:body 0 "compressionName"] := "ZSTD"
        [:body 0 "dataSize"] :? pos-int?
        [:body 0 "numEntries"] :? pos-int?
        [:body 0 "numDataBlocks"] :? pos-int?
        [:body 0 "totalRawKeySize"] :? pos-int?
        [:body 0 "totalRawValueSize"] := 0))

    (testing "not-found"
      (given @(handler
               {:request-method :get
                :uri "/fhir/__admin/dbs/index/column-families/foo/tables"})
        :status := 404
        [:body "msg"] := "The column family `foo` in database `index` was not found."))))

(def re-index-job
  {:fhir/type :fhir/Task
   :meta #fhir/Meta{:profile [#fhir/canonical"https://samply.github.io/blaze/fhir/StructureDefinition/ReIndexJob"]}
   :status #fhir/code"draft"
   :intent #fhir/code"order"
   :code #fhir/CodeableConcept
          {:coding
           [#fhir/Coding
             {:system #fhir/uri"https://samply.github.io/blaze/fhir/CodeSystem/JobType"
              :code #fhir/code"re-index"
              :display "(Re)Index a Search Parameter"}]}
   :input
   [{:fhir/type :fhir.Task/input
     :type #fhir/CodeableConcept
            {:coding
             [#fhir/Coding
               {:system #fhir/uri"https://samply.github.io/blaze/fhir/CodeSystem/ReIndexJobParameter"
                :code #fhir/code"search-param-url"}]}
     :value #fhir/canonical"http://hl7.org/fhir/SearchParameter/Resource-profile"}]})

(def compact-job
  {:fhir/type :fhir/Task
   :meta #fhir/Meta{:profile [#fhir/canonical"https://samply.github.io/blaze/fhir/StructureDefinition/CompactJob"]}
   :status #fhir/code"draft"
   :intent #fhir/code"order"
   :code #fhir/CodeableConcept
          {:coding
           [#fhir/Coding
             {:system #fhir/uri"https://samply.github.io/blaze/fhir/CodeSystem/JobType"
              :code #fhir/code"compact"
              :display "Compact Database Column Families"}]}
   :input
   [{:fhir/type :fhir.Task/input
     :type #fhir/CodeableConcept
            {:coding
             [#fhir/Coding
               {:system #fhir/uri"https://samply.github.io/blaze/fhir/CodeSystem/CompactJobParameter"
                :code #fhir/code"column-family-name"}]}
     :value "SearchParamValueIndex"}]})

(deftest create-job-test
  (testing "no profile"
    (with-handler [handler] (config (new-temp-dir!)) []
      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :uri "/fhir/__admin/jobs"
               :headers {"content-type" "application/fhir+json"}
               :body (fhir-spec/unform-json
                      {:fhir/type :fhir/Task
                       :status #fhir/code"draft"
                       :intent #fhir/code"order"})})]

        (is (= 400 status))

        (given body
          "resourceType" := "OperationOutcome"
          ["issue" 0 "severity"] := "error"
          ["issue" 0 "code"] := "value"
          ["issue" 0 "details" "text"] := "No allowed profile found."))))

  (testing "wrong profile"
    (with-handler [handler] (config (new-temp-dir!)) []
      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :uri "/fhir/__admin/jobs"
               :headers {"content-type" "application/fhir+json"}
               :body (fhir-spec/unform-json
                      {:fhir/type :fhir/Task
                       :meta #fhir/Meta{:profile [#fhir/canonical"https://samply.github.io/blaze/fhir/StructureDefinition/Foo"]}
                       :status #fhir/code"draft"
                       :intent #fhir/code"order"})})]

        (is (= 400 status))

        (given body
          "resourceType" := "OperationOutcome"
          ["issue" 0 "severity"] := "error"
          ["issue" 0 "code"] := "value"
          ["issue" 0 "details" "text"] := "No allowed profile found."))))

  (testing "missing code"
    (with-handler [handler] (config (new-temp-dir!)) []
      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :uri "/fhir/__admin/jobs"
               :headers {"content-type" "application/fhir+json"}
               :body (fhir-spec/unform-json (dissoc re-index-job :code))})]

        (is (= 400 status))

        (given body
          "resourceType" := "OperationOutcome"
          ["issue" 0 "severity"] := "error"
          ["issue" 0 "code"] := "structure"
          ["issue" 0 "details" "text"] := "Task.code: minimum required = 1, but only found 0 (from https://samply.github.io/blaze/fhir/StructureDefinition/ReIndexJob)"))))

  (testing "wrong code"
    (with-handler [handler] (config (new-temp-dir!)) []
      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :uri "/fhir/__admin/jobs"
               :headers {"content-type" "application/fhir+json"}
               :body (fhir-spec/unform-json (update-in re-index-job [:code :coding 0] merge {:code #fhir/code"compact" :display "Compact Database Column Families"}))})]

        (is (= 400 status))

        (given body
          "resourceType" := "OperationOutcome"
          ["issue" 0 "severity"] := "error"
          ["issue" 0 "code"] := "value"
          ["issue" 0 "details" "text"] := "The pattern [system https://samply.github.io/blaze/fhir/CodeSystem/JobType, code re-index, and display '(Re)Index a Search Parameter'] defined in the profile https://samply.github.io/blaze/fhir/StructureDefinition/ReIndexJob not found. Issues: [ValidationMessage[level=ERROR,type=VALUE,location=Task.code.coding.code,message=Value is 'compact' but must be 're-index'], ValidationMessage[level=ERROR,type=VALUE,location=Task.code.coding.display,message=Value is 'Compact Database Column Families' but must be '(Re)Index a Search Parameter']]"))))

  (testing "re-index job"
    (with-handler [handler] (config (new-temp-dir!)) []
      (let [{:keys [status headers body]}
            @(handler
              {:request-method :post
               :uri "/fhir/__admin/jobs"
               :headers {"content-type" "application/fhir+json"}
               :body (fhir-spec/unform-json re-index-job)})]

        (is (= 201 status))

        (testing "Location header"
          (is (= "/fhir/__admin/jobs/AAAAAAAAAAAAAAAA/_history/1"
                 (get headers "Location"))))

        (given body
          "resourceType" := "Task"
          "id" := "AAAAAAAAAAAAAAAA"
          ["meta" "versionId"] := "1"
          ["meta" "lastUpdated"] := (str Instant/EPOCH)
          "status" := "draft"))))

  (testing "compact job"
    (with-handler [handler] (config (new-temp-dir!)) []
      (let [{:keys [status headers body]}
            @(handler
              {:request-method :post
               :uri "/fhir/__admin/jobs"
               :headers {"content-type" "application/fhir+json"}
               :body (fhir-spec/unform-json compact-job)})]

        (is (= 201 status))

        (testing "Location header"
          (is (= "/fhir/__admin/jobs/AAAAAAAAAAAAAAAA/_history/1"
                 (get headers "Location"))))

        (given body
          "resourceType" := "Task"
          "id" := "AAAAAAAAAAAAAAAA"
          ["meta" "versionId"] := "1"
          ["meta" "lastUpdated"] := (str Instant/EPOCH)
          "status" := "draft")))))

(deftest read-job-test
  (testing "existing job"
    (with-handler [handler] (config (new-temp-dir!)) []
      @(handler
        {:request-method :post
         :uri "/fhir/__admin/jobs"
         :headers {"content-type" "application/fhir+json"}
         :body (fhir-spec/unform-json re-index-job)})

      (let [{:keys [status body]}
            @(handler
              {:request-method :get
               :uri "/fhir/__admin/jobs/AAAAAAAAAAAAAAAA"})]

        (is (= 200 status))

        (given body
          "resourceType" := "Task"
          "id" := "AAAAAAAAAAAAAAAA"
          ["meta" "versionId"] := "1"
          ["meta" "lastUpdated"] := (str Instant/EPOCH)
          "status" := "draft"))))

  (testing "non-existing job"
    (with-handler [handler] (config (new-temp-dir!)) []
      (let [{:keys [status body]}
            @(handler
              {:request-method :get
               :uri "/fhir/__admin/jobs/AAAAAAAAAAAAAAAA"})]

        (is (= 404 status))

        (given body
          "resourceType" := "OperationOutcome"
          ["issue" 0 "severity"] := "error"
          ["issue" 0 "code"] := "not-found"
          ["issue" 0 "diagnostics"] := "Resource `Task/AAAAAAAAAAAAAAAA` was not found.")))))

(deftest search-jobs-test
  (with-handler [handler] (config (new-temp-dir!)) []
    @(handler
      {:request-method :post
       :uri "/fhir/__admin/jobs"
       :headers {"content-type" "application/fhir+json"}
       :body (fhir-spec/unform-json re-index-job)})

    (let [{:keys [status] {[first-entry] "entry" :as body} :body}
          @(handler
            {:request-method :get
             :uri "/fhir/__admin/jobs"
             :params {"status" "draft"}})]

      (is (= 200 status))

      (testing "the body contains a bundle"
        (is (= "Bundle" (body "resourceType"))))

      (testing "the bundle id is an LUID"
        (is (= "AAAAAAAAAAAAAAAA" (body "id"))))

      (testing "the bundle type is searchset"
        (is (= "searchset" (body "type"))))

      (testing "the total count is 1"
        (is (= 1 (body "total"))))

      (testing "the bundle contains one entry"
        (is (= 1 (count (body "entry")))))

      (testing "the entry has the right resource"
        (given (first-entry "resource")
          "resourceType" := "Task"
          "id" := "AAAAAAAAAAAAAAAA"
          ["meta" "versionId"] := "1"
          ["meta" "lastUpdated"] := (str Instant/EPOCH)
          "status" := "draft")))))
