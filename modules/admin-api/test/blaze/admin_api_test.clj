(ns blaze.admin-api-test
  (:require
   [blaze.admin-api :as admin-api]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.db.api-stub]
   [blaze.db.impl.index.patient-last-change :as plc]
   [blaze.db.kv :as-alias kv]
   [blaze.db.kv.rocksdb :as rocksdb]
   [blaze.db.node :as node]
   [blaze.db.resource-cache]
   [blaze.db.resource-store :as rs]
   [blaze.db.resource-store.kv :as rs-kv]
   [blaze.db.tx-log :as tx-log]
   [blaze.elm.compiler :as c]
   [blaze.elm.expression :as-alias expr]
   [blaze.elm.expression.cache :as ec]
   [blaze.fhir.parsing-context]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.fhir.writing-context]
   [blaze.interaction.create]
   [blaze.interaction.history.instance]
   [blaze.interaction.read]
   [blaze.interaction.search-type]
   [blaze.interaction.search.util :as search-util]
   [blaze.job-scheduler :as js]
   [blaze.middleware.fhir.db-spec]
   [blaze.middleware.fhir.output-spec]
   [blaze.middleware.fhir.resource-spec]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.page-store-spec]
   [blaze.page-store.local]
   [blaze.test-util :as tu]
   [blaze.util-spec]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [java-time.api :as time]
   [jsonista.core :as j]
   [juxt.iota :refer [given]]
   [ring.core.protocols :as rp]
   [taoensso.timbre :as log])
  (:import
   [java.io ByteArrayOutputStream]
   [java.nio.file Files]
   [java.nio.file.attribute FileAttribute]
   [java.time Instant]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(defn- new-temp-dir! []
  (str (Files/createTempDirectory "blaze" (make-array FileAttribute 0))))

(defn- config! []
  (let [dir (new-temp-dir!)]
    {[:blaze.db/node :blaze.db.main/node]
     {:tx-log (ig/ref :blaze.db.main/tx-log)
      :tx-cache (ig/ref :blaze.db.main/tx-cache)
      :indexer-executor (ig/ref :blaze.db.node.main/indexer-executor)
      :resource-cache (ig/ref :blaze.db/resource-cache)
      :resource-store (ig/ref :blaze.db/resource-store)
      :kv-store (ig/ref :blaze.db.main/index-kv-store)
      :resource-indexer (ig/ref :blaze.db.node.main/resource-indexer)
      :search-param-registry (ig/ref :blaze.db/search-param-registry)
      :scheduler (ig/ref :blaze/scheduler)
      :poll-timeout (time/millis 10)}

     [:blaze.db/node :blaze.db.admin/node]
     {:tx-log (ig/ref :blaze.db.admin/tx-log)
      :tx-cache (ig/ref :blaze.db.admin/tx-cache)
      :indexer-executor (ig/ref :blaze.db.node.admin/indexer-executor)
      :resource-cache (ig/ref :blaze.db/resource-cache)
      :resource-store (ig/ref :blaze.db/resource-store)
      :kv-store (ig/ref :blaze.db.admin/index-kv-store)
      :resource-indexer (ig/ref :blaze.db.node.admin/resource-indexer)
      :search-param-registry (ig/ref :blaze.db/search-param-registry)
      :scheduler (ig/ref :blaze/scheduler)
      :poll-timeout (time/millis 10)}

     :blaze.db/resource-cache
     {:resource-store (ig/ref :blaze.db/resource-store)}

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

     [::node/indexer-executor :blaze.db.node.main/indexer-executor] {}
     [::node/indexer-executor :blaze.db.node.admin/indexer-executor] {}

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
       :system-stats-index nil
       :cql-bloom-filter nil
       :cql-bloom-filter-by-t nil}}

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

     ::rs/kv
     {:kv-store (ig/ref :blaze.db/resource-kv-store)
      :parsing-context (ig/ref :blaze.fhir.parsing-context/resource-store)
      :writing-context (ig/ref :blaze.fhir/writing-context)
      :executor (ig/ref ::rs-kv/executor)}

     [::kv/rocksdb :blaze.db/resource-kv-store]
     {:dir (str dir "/resource")
      :column-families {}}

     ::rs-kv/executor {}

     [::node/resource-indexer :blaze.db.node.main/resource-indexer]
     {:kv-store (ig/ref :blaze.db.main/index-kv-store)
      :resource-store (ig/ref :blaze.db/resource-store)
      :search-param-registry (ig/ref :blaze.db/search-param-registry)
      :executor (ig/ref :blaze.db.node.resource-indexer.main/executor)}

     [:blaze.db.node.resource-indexer/executor :blaze.db.node.resource-indexer.main/executor] {}

     [::node/resource-indexer :blaze.db.node.admin/resource-indexer]
     {:kv-store (ig/ref :blaze.db.admin/index-kv-store)
      :resource-store (ig/ref :blaze.db/resource-store)
      :search-param-registry (ig/ref :blaze.db/search-param-registry)
      :executor (ig/ref :blaze.db.node.resource-indexer.admin/executor)}

     [:blaze.db.node.resource-indexer/executor :blaze.db.node.resource-indexer.admin/executor] {}

     :blaze.db/search-param-registry
     {:structure-definition-repo structure-definition-repo}

     [:blaze.fhir/parsing-context :blaze.fhir.parsing-context/default]
     {:structure-definition-repo structure-definition-repo}

     [:blaze.fhir/parsing-context :blaze.fhir.parsing-context/resource-store]
     {:structure-definition-repo structure-definition-repo
      :fail-on-unknown-property false
      :include-summary-only true
      :use-regex false}

     :blaze.fhir/writing-context
     {:structure-definition-repo structure-definition-repo}

     ::rocksdb/block-cache {:size-in-mb 1}

     :blaze/admin-api
     {:context-path "/fhir"
      :admin-node (ig/ref :blaze.db.admin/node)
      :parsing-context (ig/ref :blaze.fhir.parsing-context/default)
      :writing-context (ig/ref :blaze.fhir/writing-context)
      :job-scheduler (ig/ref :blaze/job-scheduler)
      :read-job-handler (ig/ref :blaze.interaction/read)
      :history-job-handler (ig/ref :blaze.interaction.history/instance)
      :search-type-job-handler (ig/ref :blaze.interaction/search-type)
      :dbs {"index" (ig/ref :blaze.db.main/index-kv-store)
            "resource" (ig/ref :blaze.db/resource-kv-store)}
      :settings []
      :features []
      :clock (ig/ref :blaze.test/fixed-clock)
      :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}

     :blaze/job-scheduler
     {:node (ig/ref :blaze.db.admin/node)
      :clock (ig/ref :blaze.test/fixed-clock)
      :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}

     :blaze.interaction/create
     {:node (ig/ref :blaze.db.admin/node)
      :clock (ig/ref :blaze.test/fixed-clock)
      :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}

     :blaze.interaction/read {}

     :blaze.interaction.history/instance
     {::search-util/link (ig/ref ::search-util/link)
      :clock (ig/ref :blaze.test/fixed-clock)
      :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
      :page-id-cipher (ig/ref :blaze.test/page-id-cipher)}

     :blaze.interaction/search-type
     {::search-util/link (ig/ref ::search-util/link)
      :clock (ig/ref :blaze.test/fixed-clock)
      :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
      :page-store (ig/ref :blaze/page-store)
      :page-id-cipher (ig/ref :blaze.test/page-id-cipher)
      :context-path "/fhir"}

     ::search-util/link {:fhir/version "4.0.1"}

     :blaze.page-store/local {}

     :blaze/scheduler {}

     :blaze.test/fixed-rng {}
     :blaze.test/fixed-rng-fn {}
     :blaze.test/page-id-cipher {}

     :blaze.test/json-writer
     {:writing-context (ig/ref :blaze.fhir/writing-context)}}))

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze/admin-api nil}
      :key := :blaze/admin-api
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze/admin-api {}}
      :key := :blaze/admin-api
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :context-path))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :admin-node))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :parsing-context))
      [:cause-data ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :writing-context))
      [:cause-data ::s/problems 4 :pred] := `(fn ~'[%] (contains? ~'% :job-scheduler))
      [:cause-data ::s/problems 5 :pred] := `(fn ~'[%] (contains? ~'% :read-job-handler))
      [:cause-data ::s/problems 6 :pred] := `(fn ~'[%] (contains? ~'% :history-job-handler))
      [:cause-data ::s/problems 7 :pred] := `(fn ~'[%] (contains? ~'% :search-type-job-handler))
      [:cause-data ::s/problems 8 :pred] := `(fn ~'[%] (contains? ~'% :settings))
      [:cause-data ::s/problems 9 :pred] := `(fn ~'[%] (contains? ~'% :features))))

  (testing "invalid context path"
    (given-failed-system (assoc-in (config!) [:blaze/admin-api :context-path] ::invalid)
      :key := :blaze/admin-api
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/context-path]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid admin node"
    (given-failed-system (assoc-in (config!) [:blaze/admin-api :admin-node] ::invalid)
      :key := :blaze/admin-api
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/node]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid parsing context"
    (given-failed-system (assoc-in (config!) [:blaze/admin-api :parsing-context] ::invalid)
      :key := :blaze/admin-api
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.fhir/parsing-context]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid writing context"
    (given-failed-system (assoc-in (config!) [:blaze/admin-api :writing-context] ::invalid)
      :key := :blaze/admin-api
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.fhir/writing-context]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid settings"
    (given-failed-system (assoc-in (config!) [:blaze/admin-api :settings] ::invalid)
      :key := :blaze/admin-api
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::admin-api/settings]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid features"
    (given-failed-system (assoc-in (config!) [:blaze/admin-api :features] ::invalid)
      :key := :blaze/admin-api
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::admin-api/features]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "with minimal config"
    (with-system [{handler :blaze/admin-api} (config!)]
      (is (fn? handler)))))

(defn- read-body [body]
  (let [out (ByteArrayOutputStream.)]
    (rp/write-body-to-stream body nil out)
    (j/read-value (.toByteArray out))))

(defn- wrap-read-json [handler]
  (fn [request]
    (do-sync [response (handler request)]
      (update response :body read-body))))

(defmacro with-system-data
  [[binding-form config] txs & body]
  `(with-system [system# ~config]
     (run! #(deref (d/transact (system# [:blaze.db/node :blaze.db.main/node]) %)) ~txs)
     (let [~binding-form system#] ~@body)))

(defmacro with-handler [[handler-binding & [system-binding]] config txs & body]
  `(with-system-data [{handler# :blaze/admin-api :as system#} ~config]
     ~txs
     (let [~handler-binding (wrap-read-json handler#)
           ~(or system-binding '_) system#]
       ~@body)))

(deftest handler-not-found-test
  (with-handler [handler] (config!) []
    (given @(handler
             {:request-method :get
              :uri "/fhir/__admin/foo"})
      :status := 404)))

(deftest handler-openapi-test
  (with-handler [handler] (config!) []
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
                  ["content" "application/json" "schema" "items" "properties" "totalRawKeySize" "type"] := "number")))))

        (testing "cql-bloom-filters"
          (let [op (get-in body ["paths" "/fhir/__admin/cql/bloom-filters" "get"])]
            (given op
              "operationId" := "cql-bloom-filters"
              "summary" := "Fetch the list of all CQL Bloom filters.")

            (testing "responses"
              (testing "200"
                (given (get-in op ["responses" "200"])
                  "description" := "A list of CQL Bloom filters."
                  ["content" "application/json" "schema" "type"] := "array"
                  ["content" "application/json" "schema" "items" "$ref"] := "#/components/schemas/BloomFilter")))))))))

(deftest root-test
  (testing "without settings and features"
    (with-handler [handler] (config!) []
      (testing "success"
        (given @(handler
                 {:request-method :get
                  :uri "/fhir/__admin"})
          :status := 200
          [:body "settings" count] := 0
          [:body "features" count] := 0))))

  (testing "with one setting"
    (with-handler [handler]
      (assoc-in (config!)
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
          [:body "features" count] := 0)))

    (testing "without default value"
      (with-handler [handler]
        (assoc-in (config!)
                  [:blaze/admin-api :settings]
                  [{:name "SERVER_PORT"
                    :value 8081}])
        []
        (testing "success"
          (given @(handler
                   {:request-method :get
                    :uri "/fhir/__admin"})
            :status := 200
            [:body "settings" count] := 1
            [:body "settings" 0 "name"] := "SERVER_PORT"
            [:body "settings" 0 "value"] := 8081
            [:body "features" count] := 0))))

    (testing "with masked value"
      (with-handler [handler]
        (assoc-in (config!)
                  [:blaze/admin-api :settings]
                  [{:name "SERVER_PORT"
                    :masked true}])
        []
        (testing "success"
          (given @(handler
                   {:request-method :get
                    :uri "/fhir/__admin"})
            :status := 200
            [:body "settings" count] := 1
            [:body "settings" 0 "name"] := "SERVER_PORT"
            [:body "settings" 0 "masked"] := true
            [:body "features" count] := 0)))))

  (testing "with one feature"
    (with-handler [handler]
      (assoc-in (config!)
                [:blaze/admin-api :features]
                [{:key "open-id-authentication"
                  :name "OpenID Authentication"
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
          [:body "features" 0 count] := 4
          [:body "features" 0 "key"] := "open-id-authentication"
          [:body "features" 0 "name"] := "OpenID Authentication"
          [:body "features" 0 "toggle"] := "OPENID_PROVIDER_URL"
          [:body "features" 0 "enabled"] := true)))))

(deftest databases-test
  (testing "with normal config"
    (with-handler [handler] (config!) []
      (testing "success"
        (given @(handler
                 {:request-method :get
                  :uri "/fhir/__admin/dbs"})
          :status := 200
          [:body count] := 2
          [:body 0 "name"] := "index"
          [:body 1 "name"] := "resource")))))

(deftest db-stats-test
  (with-handler [handler] (config!) []
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
    (with-handler [handler] (config!) []
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
    (with-handler [handler] (config!) []
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

(deftest column-family-state-test
  (with-handler [handler] (config!) []
    (testing "patient-last-change-index"
      (with-redefs [plc/state (fn [_] {:type :current})]
        (given @(handler
                 {:request-method :get
                  :uri "/fhir/__admin/dbs/index/column-families/patient-last-change-index/state"})
          :status := 200
          :body := {"type" "current"})))

    (testing "other column-family"
      (given @(handler
               {:request-method :get
                :uri "/fhir/__admin/dbs/index/column-families/default/state"})
        :status := 404
        [:body "msg"] := "The state of the column family `default` in database `index` was not found."))))

(deftest column-family-metadata-test
  (with-handler [handler] (config!) []
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
  (with-handler [handler] (config!)
    (-> (fn [pat-id]
          (into
           [[:put {:fhir/type :fhir/Patient :id (str pat-id)}]]
           (map (fn [obs-id]
                  [:put {:fhir/type :fhir/Observation :id (str obs-id)
                         :code
                         #fhir/CodeableConcept
                          {:coding
                           [#fhir/Coding
                             {:system #fhir/uri "system-192253"
                              :code #fhir/code "code-192300"}]}
                         :subject (type/reference {:reference (type/string (str "Patient/" pat-id))})}]))
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
   :meta #fhir/Meta{:profile [#fhir/canonical "https://samply.github.io/blaze/fhir/StructureDefinition/ReIndexJob"]}
   :status #fhir/code "draft"
   :intent #fhir/code "order"
   :code #fhir/CodeableConcept
          {:coding
           [#fhir/Coding
             {:system #fhir/uri "https://samply.github.io/blaze/fhir/CodeSystem/JobType"
              :code #fhir/code "re-index"
              :display #fhir/string "(Re)Index a Search Parameter"}]}
   :authoredOn #fhir/dateTime #system/date-time "2024-04-13T10:05:20.927Z"
   :input
   [{:fhir/type :fhir.Task/input
     :type #fhir/CodeableConcept
            {:coding
             [#fhir/Coding
               {:system #fhir/uri "https://samply.github.io/blaze/fhir/CodeSystem/ReIndexJobParameter"
                :code #fhir/code "search-param-url"}]}
     :value #fhir/canonical "http://hl7.org/fhir/SearchParameter/Resource-profile"}]})

(def compact-job
  {:fhir/type :fhir/Task
   :meta #fhir/Meta{:profile [#fhir/canonical "https://samply.github.io/blaze/fhir/StructureDefinition/CompactJob"]}
   :status #fhir/code "draft"
   :intent #fhir/code "order"
   :code #fhir/CodeableConcept
          {:coding
           [#fhir/Coding
             {:system #fhir/uri "https://samply.github.io/blaze/fhir/CodeSystem/JobType"
              :code #fhir/code "compact"
              :display #fhir/string "Compact a Database Column Family"}]}
   :authoredOn #fhir/dateTime #system/date-time "2024-04-13T10:05:20.927Z"
   :input
   [{:fhir/type :fhir.Task/input
     :type #fhir/CodeableConcept
            {:coding
             [#fhir/Coding
               {:system #fhir/uri "https://samply.github.io/blaze/fhir/CodeSystem/CompactJobParameter"
                :code #fhir/code "database"}]}
     :value #fhir/code "index"}
    {:fhir/type :fhir.Task/input
     :type #fhir/CodeableConcept
            {:coding
             [#fhir/Coding
               {:system #fhir/uri "https://samply.github.io/blaze/fhir/CodeSystem/CompactJobParameter"
                :code #fhir/code "column-family"}]}
     :value #fhir/code "search-param-value-index"}]})

(deftest create-job-test
  (testing "no profile"
    (with-handler [handler {:blaze.test/keys [json-writer]}] (config!) []
      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :uri "/fhir/__admin/Task"
               :headers {"content-type" "application/fhir+json"}
               :body (json-writer
                      {:fhir/type :fhir/Task
                       :status #fhir/code "draft"
                       :intent #fhir/code "order"})})]

        (is (= 400 status))

        (given body
          "resourceType" := "OperationOutcome"
          ["issue" 0 "severity"] := "error"
          ["issue" 0 "code"] := "value"
          ["issue" 0 "details" "text"] := "No allowed profile found."))))

  (testing "wrong profile"
    (with-handler [handler {:blaze.test/keys [json-writer]}] (config!) []
      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :uri "/fhir/__admin/Task"
               :headers {"content-type" "application/fhir+json"}
               :body (json-writer
                      {:fhir/type :fhir/Task
                       :meta #fhir/Meta{:profile [#fhir/canonical "https://samply.github.io/blaze/fhir/StructureDefinition/Foo"]}
                       :status #fhir/code "draft"
                       :intent #fhir/code "order"})})]

        (is (= 400 status))

        (given body
          "resourceType" := "OperationOutcome"
          ["issue" 0 "severity"] := "error"
          ["issue" 0 "code"] := "value"
          ["issue" 0 "details" "text"] := "No allowed profile found."))))

  (testing "missing code"
    (with-handler [handler {:blaze.test/keys [json-writer]}] (config!) []
      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :uri "/fhir/__admin/Task"
               :headers {"content-type" "application/fhir+json"}
               :body (json-writer (dissoc re-index-job :code))})]

        (is (= 400 status))

        (given body
          "resourceType" := "OperationOutcome"
          ["issue" 0 "severity"] := "error"
          ["issue" 0 "code"] := "processing"
          ["issue" 0 "diagnostics"] := "Task.code: minimum required = 1, but only found 0 (from https://samply.github.io/blaze/fhir/StructureDefinition/ReIndexJob)"))))

  (testing "missing authoredOn"
    (with-handler [handler {:blaze.test/keys [json-writer]}] (config!) []
      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :uri "/fhir/__admin/Task"
               :headers {"content-type" "application/fhir+json"}
               :body (json-writer (dissoc re-index-job :authoredOn))})]

        (is (= 400 status))

        (given body
          "resourceType" := "OperationOutcome"
          ["issue" 0 "severity"] := "error"
          ["issue" 0 "code"] := "processing"
          ["issue" 0 "diagnostics"] := "Task.authoredOn: minimum required = 1, but only found 0 (from https://samply.github.io/blaze/fhir/StructureDefinition/ReIndexJob)"))))

  (testing "wrong code"
    (with-handler [handler {:blaze.test/keys [json-writer]}] (config!) []
      (let [{:keys [status body]}
            @(handler
              {:request-method :post
               :uri "/fhir/__admin/Task"
               :headers {"content-type" "application/fhir+json"}
               :body (json-writer (update-in re-index-job [:code :coding 0] merge {:code #fhir/code "compact" :display #fhir/string "Compact a Database Column Family"}))})]

        (is (= 400 status))

        (given body
          "resourceType" := "OperationOutcome"
          ["issue" 0 "severity"] := "error"
          ["issue" 0 "code"] := "processing"
          ["issue" 0 "diagnostics"] := "The pattern [system https://samply.github.io/blaze/fhir/CodeSystem/JobType, code re-index, and display '(Re)Index a Search Parameter'] defined in the profile https://samply.github.io/blaze/fhir/StructureDefinition/ReIndexJob not found. Issues: [ValidationMessage[level=ERROR,type=VALUE,location=Task.code.coding.code,message=Value is 'compact' but is fixed to 're-index' in the profile https://samply.github.io/blaze/fhir/StructureDefinition/ReIndexJob#Task], ValidationMessage[level=ERROR,type=VALUE,location=Task.code.coding.display,message=Value is 'Compact a Database Column Family' but is fixed to '(Re)Index a Search Parameter' in the profile https://samply.github.io/blaze/fhir/StructureDefinition/ReIndexJob#Task]]"))))

  (testing "re-index job"
    (testing "non-absolute search-param-url"
      (with-handler [handler {:blaze.test/keys [json-writer]}] (config!) []
        (let [{:keys [status body]}
              @(handler
                {:request-method :post
                 :uri "/fhir/__admin/Task"
                 :headers {"content-type" "application/fhir+json"}
                 :body (json-writer (assoc-in re-index-job [:input 0 :value] #fhir/canonical "foo"))})]

          (is (= 400 status))

          (given body
            "resourceType" := "OperationOutcome"
            ["issue" 0 "severity"] := "error"
            ["issue" 0 "code"] := "processing"
            ["issue" 0 "diagnostics"] := "Canonical URLs must be absolute URLs if they are not fragment references (foo)"))))

    (with-handler [handler {:blaze.test/keys [json-writer]}] (config!) []
      (let [{:keys [status headers body]}
            @(handler
              {:request-method :post
               :uri "/fhir/__admin/Task"
               :headers {"content-type" "application/fhir+json"}
               :body (json-writer re-index-job)})]

        (is (= 201 status))

        (testing "Location header"
          (is (= "/fhir/__admin/Task/AAAAAAAAAAAAAAAA/_history/2"
                 (get headers "Location"))))

        (given body
          "resourceType" := "Task"
          "id" := "AAAAAAAAAAAAAAAA"
          ["meta" "versionId"] := "2"
          ["meta" "lastUpdated"] := (str Instant/EPOCH)
          ["identifier" 0 "system"] := "https://samply.github.io/blaze/fhir/sid/JobNumber"
          ["identifier" 0 "value"] := "1"
          "status" := "draft"))))

  (testing "compact job"
    (testing "unknown database code"
      (with-handler [handler {:blaze.test/keys [json-writer]}] (config!) []
        (let [{:keys [status body]}
              @(handler
                {:request-method :post
                 :uri "/fhir/__admin/Task"
                 :headers {"content-type" "application/fhir+json"}
                 :body (json-writer (assoc-in compact-job [:input 0 :value] #fhir/code "foo"))})]

          (is (= 400 status))

          (given body
            "resourceType" := "OperationOutcome"
            ["issue" 0 "severity"] := "error"
            ["issue" 0 "code"] := "processing"
            ["issue" 0 "diagnostics"] := "Unknown code 'https://samply.github.io/blaze/fhir/CodeSystem/Database#foo'"
            ["issue" 1 "severity"] := "error"
            ["issue" 1 "code"] := "processing"
            ["issue" 1 "diagnostics"] := "The value provided ('foo') was not found in the value set 'Database Value Set' (https://samply.github.io/blaze/fhir/ValueSet/Database), and a code is required from this value set  (error message = Unknown code 'https://samply.github.io/blaze/fhir/CodeSystem/Database#foo' for in-memory expansion of ValueSet 'https://samply.github.io/blaze/fhir/ValueSet/Database')"))))

    (with-handler [handler {:blaze.test/keys [json-writer]}] (config!) []
      (let [{:keys [status headers body]}
            @(handler
              {:request-method :post
               :uri "/fhir/__admin/Task"
               :headers {"content-type" "application/fhir+json"}
               :body (json-writer compact-job)})]

        (is (= 201 status))

        (testing "Location header"
          (is (= "/fhir/__admin/Task/AAAAAAAAAAAAAAAA/_history/2"
                 (get headers "Location"))))

        (given body
          "resourceType" := "Task"
          "id" := "AAAAAAAAAAAAAAAA"
          ["meta" "versionId"] := "2"
          ["meta" "lastUpdated"] := (str Instant/EPOCH)
          ["identifier" 0 "system"] := "https://samply.github.io/blaze/fhir/sid/JobNumber"
          ["identifier" 0 "value"] := "1"
          "status" := "draft")))))

(deftest read-job-test
  (testing "existing job"
    (with-handler [handler {:blaze.test/keys [json-writer]}] (config!) []
      @(handler
        {:request-method :post
         :uri "/fhir/__admin/Task"
         :headers {"content-type" "application/fhir+json"}
         :body (json-writer re-index-job)})

      (let [{:keys [status body]}
            @(handler
              {:request-method :get
               :uri "/fhir/__admin/Task/AAAAAAAAAAAAAAAA"})]

        (is (= 200 status))

        (given body
          "resourceType" := "Task"
          "id" := "AAAAAAAAAAAAAAAA"
          ["meta" "versionId"] := "2"
          ["meta" "lastUpdated"] := (str Instant/EPOCH)
          "status" := "draft"))))

  (testing "non-existing job"
    (with-handler [handler] (config!) []
      (let [{:keys [status body]}
            @(handler
              {:request-method :get
               :uri "/fhir/__admin/Task/AAAAAAAAAAAAAAAA"})]

        (is (= 404 status))

        (given body
          "resourceType" := "OperationOutcome"
          ["issue" 0 "severity"] := "error"
          ["issue" 0 "code"] := "not-found"
          ["issue" 0 "diagnostics"] := "Resource `Task/AAAAAAAAAAAAAAAA` was not found.")))))

(deftest history-job-test
  (testing "existing job"
    (with-handler [handler {:blaze.test/keys [json-writer]}] (config!) []
      @(handler
        {:request-method :post
         :uri "/fhir/__admin/Task"
         :headers {"content-type" "application/fhir+json"}
         :body (json-writer re-index-job)})

      (let [{:keys [status body]}
            @(handler
              {:request-method :get
               :uri "/fhir/__admin/Task/AAAAAAAAAAAAAAAA/_history"})]

        (is (= 200 status))

        (given body
          "resourceType" := "Bundle"
          "type" := "history"
          ["entry" 0 "request" "method"] := "POST"
          ["entry" 0 "request" "url"] := "Task"
          ["entry" 0 "resource" "id"] := "AAAAAAAAAAAAAAAA"))))

  (testing "non-existing job"
    (with-handler [handler] (config!) []
      (let [{:keys [status body]}
            @(handler
              {:request-method :get
               :uri "/fhir/__admin/Task/AAAAAAAAAAAAAAAA/_history"})]

        (is (= 404 status))

        (given body
          "resourceType" := "OperationOutcome"
          ["issue" 0 "severity"] := "error"
          ["issue" 0 "code"] := "not-found"
          ["issue" 0 "diagnostics"] := "Resource `Task/AAAAAAAAAAAAAAAA` was not found.")))))

(deftest search-jobs-test
  (with-handler [handler {:blaze.test/keys [json-writer]}] (config!) []
    @(handler
      {:request-method :post
       :uri "/fhir/__admin/Task"
       :headers {"content-type" "application/fhir+json"}
       :body (json-writer re-index-job)})

    (let [{:keys [status] {[first-entry] "entry" :as body} :body}
          @(handler
            {:request-method :get
             :uri "/fhir/__admin/Task"
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
          ["meta" "versionId"] := "2"
          ["meta" "lastUpdated"] := (str Instant/EPOCH)
          "status" := "draft")))))

(deftest pause-job-test
  (with-redefs
   [js/pause-job
    (fn [_job-scheduler _id]
      (ac/completed-future
       (with-meta (assoc re-index-job :status #fhir/code "on-hold")
         {:blaze.db/tx
          {:blaze.db.tx/instant Instant/EPOCH
           :blaze.db/t 1}})))]
    (with-handler [handler] (config!) []
      (let [{:keys [status headers body]}
            @(handler
              {:request-method :post
               :uri "/fhir/__admin/Task/AAAAAAAAAAAAAAAA/$pause"
               :headers {"content-type" "application/fhir+json"}})]

        (is (= 200 status))

        (given headers
          "Last-Modified" := "Thu, 1 Jan 1970 00:00:00 GMT"
          "ETag" := "W/\"1\"")

        (given body
          "resourceType" := "Task"
          "status" := "on-hold")))))

(deftest resume-job-test
  (with-redefs
   [js/resume-job
    (fn [_job-scheduler _id]
      (ac/completed-future
       (with-meta (assoc re-index-job :status #fhir/code "in-progress")
         {:blaze.db/tx
          {:blaze.db.tx/instant Instant/EPOCH
           :blaze.db/t 1}})))]
    (with-handler [handler] (config!) []
      (let [{:keys [status headers body]}
            @(handler
              {:request-method :post
               :uri "/fhir/__admin/Task/AAAAAAAAAAAAAAAA/$resume"
               :headers {"content-type" "application/fhir+json"}})]

        (is (= 200 status))

        (given headers
          "Last-Modified" := "Thu, 1 Jan 1970 00:00:00 GMT"
          "ETag" := "W/\"1\"")

        (given body
          "resourceType" := "Task"
          "status" := "in-progress")))))

(deftest cancel-job-test
  (with-redefs
   [js/cancel-job
    (fn [_job-scheduler _id]
      (ac/completed-future
       (with-meta (assoc re-index-job :status #fhir/code "cancelled")
         {:blaze.db/tx
          {:blaze.db.tx/instant Instant/EPOCH
           :blaze.db/t 1}})))]
    (with-handler [handler] (config!) []
      (let [{:keys [status headers body]}
            @(handler
              {:request-method :post
               :uri "/fhir/__admin/Task/AAAAAAAAAAAAAAAA/$cancel"
               :headers {"content-type" "application/fhir+json"}})]

        (is (= 200 status))

        (given headers
          "Last-Modified" := "Thu, 1 Jan 1970 00:00:00 GMT"
          "ETag" := "W/\"1\"")

        (given body
          "resourceType" := "Task"
          "status" := "cancelled")))))

(defn- with-cql-expr-cache [config]
  (-> (assoc config
             ::expr/cache
             {:node (ig/ref :blaze.db.main/node)
              :executor (ig/ref :blaze.test/executor)}
             :blaze.test/executor {})
      (assoc-in [:blaze/admin-api ::expr/cache] (ig/ref ::expr/cache))))

(deftest cql-bloom-filters-test
  (testing "without expression cache"
    (with-handler [handler] (config!) []
      (testing "not-found"
        (given @(handler
                 {:request-method :get
                  :uri "/fhir/__admin/cql/bloom-filters"})
          :status := 404
          [:body "msg"] := "The feature \"CQL Expression Cache\" is disabled."))))

  (with-handler [handler {::expr/keys [cache]}] (with-cql-expr-cache (config!)) []
    (let [elm {:type "Exists"
               :operand {:type "Retrieve" :dataType "{http://hl7.org/fhir}Observation"}}
          expr (c/compile {:eval-context "Patient"} elm)]
      (ec/get cache expr))

    (Thread/sleep 100)

    (testing "success"
      (given @(handler
               {:request-method :get
                :uri "/fhir/__admin/cql/bloom-filters"})
        :status := 200
        [:body count] := 1
        [:body 0 "hash"] := "78c3f9b9e187480870ce815ad6d324713dfa2cbd12968c5b14727fef7377b985"
        [:body 0 "t"] := 0
        [:body 0 "exprForm"] := "(exists (retrieve \"Observation\"))"
        [:body 0 "patientCount"] := 0
        [:body 0 "memSize"] := 11981))))
