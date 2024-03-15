(ns blaze.admin-api-test
  (:require
   [blaze.admin-api :as admin-api]
   [blaze.db.api-stub :refer [mem-node-config with-system-data]]
   [blaze.db.kv :as-alias kv]
   [blaze.db.kv.rocksdb :as rocksdb]
   [blaze.db.kv.rocksdb.column-family-meta-data :as-alias column-family-meta-data]
   [blaze.db.kv.rocksdb.column-family-meta-data.level :as-alias column-family-meta-data-level]
   [blaze.fhir.spec.type :as type]
   [blaze.test-util :as tu :refer [given-thrown]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [java.nio.file Files]
   [java.nio.file.attribute FileAttribute]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-level! :trace)

(test/use-fixtures :each tu/fixture)

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
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :settings))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :features))))

  (testing "invalid context path"
    (given-thrown (ig/init {:blaze/admin-api {:context-path ::invalid}})
      :key := :blaze/admin-api
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :settings))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :features))
      [:explain ::s/problems 2 :via] := [:blaze/context-path]
      [:explain ::s/problems 2 :pred] := `string?
      [:explain ::s/problems 2 :val] := ::invalid))

  (testing "invalid databases"
    (given-thrown (ig/init {:blaze/admin-api {:dbs ::invalid}})
      :key := :blaze/admin-api
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :context-path))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :settings))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :features))
      [:explain ::s/problems 3 :via] := [::admin-api/dbs]
      [:explain ::s/problems 3 :pred] := `map?
      [:explain ::s/problems 3 :val] := ::invalid))

  (testing "invalid settings"
    (given-thrown (ig/init {:blaze/admin-api {:settings ::invalid}})
      :key := :blaze/admin-api
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :context-path))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :features))
      [:explain ::s/problems 2 :via] := [::admin-api/settings]
      [:explain ::s/problems 2 :pred] := `coll?
      [:explain ::s/problems 2 :val] := ::invalid))

  (testing "invalid features"
    (given-thrown (ig/init {:blaze/admin-api {:features ::invalid}})
      :key := :blaze/admin-api
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :context-path))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :settings))
      [:explain ::s/problems 2 :via] := [::admin-api/features]
      [:explain ::s/problems 2 :pred] := `coll?
      [:explain ::s/problems 2 :val] := ::invalid)))

(defn- new-temp-dir! []
  (str (Files/createTempDirectory "blaze" (make-array FileAttribute 0))))

(defn- config [dir]
  (-> (assoc
       mem-node-config
       [::kv/rocksdb :blaze.db/index-kv-store]
       {:dir (str dir "/index")
        :block-cache (ig/ref ::rocksdb/block-cache)
        :stats (ig/ref ::rocksdb/stats)
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
       [::kv/rocksdb :blaze.db/resource-kv-store]
       {:dir (str dir "/resource")
        :stats (ig/ref ::rocksdb/stats)
        :column-families {}}
       ::rocksdb/block-cache {:size-in-mb 1}
       ::rocksdb/stats {}
       :blaze/admin-api
       {:context-path "/fhir"
        :dbs {"index" (ig/ref :blaze.db/index-kv-store)
              "resource" (ig/ref :blaze.db/resource-kv-store)}
        :settings []
        :features []})
      (dissoc
       [::kv/mem :blaze.db/index-kv-store]
       [::kv/mem :blaze.db/resource-kv-store])))

(defmacro with-handler [[handler-binding & [system-binding]] config txs & body]
  `(with-system-data [{handler# :blaze/admin-api :as system#} ~config]
     ~txs
     (let [~handler-binding handler#
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
        (is (= "3.1.0" (:openapi body))))

      (testing "info"
        (given (:info body)
          :title := "Blaze Admin API"
          :description := "The Blaze Admin API is used to monitor and control a Blaze server instance."))

      (testing "schemas"
        (given (-> body :components :schemas)
          ["ColumnFamilyMetadata" :type] := "object"
          ["ColumnFamilyMetadata" :description] := "Metadata about a column family like total and level based number of files."))

      (testing "parameters"
        (given (-> body :components :parameters)
          [:db :name] := "db"
          [:db :in] := "path"
          [:db :description] := "The name of the database like index, transaction or resource."
          [:db :required] := true
          [:db :schema :type] := "string"
          [:column-family :name] := "column-family"
          [:column-family :in] := "path"
          [:column-family :description] := "The name of the column family like default."
          [:column-family :required] := true
          [:column-family :schema :type] := "string"))

      (testing "operations"
        (testing "getDatabases"
          (let [op (get-in body [:paths "/fhir/__admin/dbs" :get])]
            (given op
              :operation-id := "getDatabases"
              :summary := "Fetch the list of all database names."
              :parameters := nil)

            (testing "responses"
              (testing "200"
                (given (get-in op [:responses 200])
                  [:description] := "List of database names."
                  [:content "application/json" :schema :type] := "array"
                  [:content "application/json" :schema :items :type] := "string")))))

        (testing "getDatabaseStats"
          (let [op (get-in body [:paths "/fhir/__admin/dbs/{db}/stats" :get])]
            (given op
              :operation-id := "getDatabaseStats"
              :summary := "Fetch stats of a database."
              [:parameters count] := 1
              [:parameters 0 "$ref"] := "#/components/parameters/db")

            (testing "responses"
              (testing "200"
                (given (get-in op [:responses 200])
                  [:description] := "Database statistics."
                  [:content "application/json" :schema :type] := "object")))))

        (testing "getDatabaseColumnFamilies"
          (let [op (get-in body [:paths "/fhir/__admin/dbs/{db}/column-families" :get])]
            (given op
              :operation-id := "getDatabaseColumnFamilies"
              :summary := "Fetch the list of all column families of a database."
              [:parameters count] := 1
              [:parameters 0 "$ref"] := "#/components/parameters/db")

            (testing "responses"
              (testing "200"
                (given (get-in op [:responses 200])
                  [:description] := "A list of column families."
                  [:content "application/json" :schema :type] := "array")))))

        (testing "getColumnFamilyMetadata"
          (let [op (get-in body [:paths "/fhir/__admin/dbs/{db}/column-families/{column-family}/metadata" :get])]
            (given op
              :operation-id := "getColumnFamilyMetadata"
              :summary := "Fetch the metadata of a column family of a database."
              [:parameters count] := 2
              [:parameters 0 "$ref"] := "#/components/parameters/db"
              [:parameters 1 "$ref"] := "#/components/parameters/columnFamily")

            (testing "responses"
              (testing "200"
                (given (get-in op [:responses 200])
                  [:description] := "Column family metadata."
                  [:content "application/json" :schema "$ref"] := "#/components/schemas/ColumnFamilyMetadata")))))

        (testing "getColumnFamilyTables"
          (let [op (get-in body [:paths "/fhir/__admin/dbs/{db}/column-families/{column-family}/tables" :get])]
            (given op
              :operation-id := "getColumnFamilyTables"
              :summary := "Fetch the list of all tables of a column family of a database."
              [:parameters count] := 2
              [:parameters 0 "$ref"] := "#/components/parameters/db"
              [:parameters 1 "$ref"] := "#/components/parameters/columnFamily")

            (testing "responses"
              (testing "200"
                (given (get-in op [:responses 200])
                  [:description] := "A list of column family tables."
                  [:content "application/json" :schema :type] := "array"
                  [:content "application/json" :schema :items :type] := "object"
                  [:content "application/json" :schema :items :properties :data-size :type] := "number"
                  [:content "application/json" :schema :items :properties :total-raw-key-size :type] := "number")))))))))

(deftest root-test
  (testing "without settings and features"
    (with-handler [handler] (config (new-temp-dir!)) []
      (testing "success"
        (given @(handler
                 {:request-method :get
                  :uri "/fhir/__admin"})
          :status := 200
          [:body :settings count] := 0
          [:body :features count] := 0))))

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
          [:body :settings count] := 1
          [:body :settings 0 :name] := "SERVER_PORT"
          [:body :settings 0 :value] := 8081
          [:body :settings 0 :default-value] := 8080
          [:body :features count] := 0))))

  (testing "with one feature"
    (with-handler [handler]
      (assoc-in (config (new-temp-dir!))
                [:blaze/admin-api :features]
                [{:name "Frontend"
                  :toggle "ENABLE_FRONTEND"
                  :enabled true}])
      []
      (testing "success"
        (given @(handler
                 {:request-method :get
                  :uri "/fhir/__admin"})
          :status := 200
          [:body :settings count] := 0
          [:body :features count] := 1
          [:body :features 0 :name] := "Frontend"
          [:body :features 0 :toggle] := "ENABLE_FRONTEND"
          [:body :features 0 :enabled] := true)))))

(deftest databases-test
  (with-handler [handler] (config (new-temp-dir!)) []
    (testing "success"
      (given @(handler
               {:request-method :get
                :uri "/fhir/__admin/dbs"})
        :status := 200
        [:body count] := 2
        [:body 0 :name] := "index"
        [:body 1 :name] := "resource"))))

(deftest db-stats-test
  (with-handler [handler] (config (new-temp-dir!)) []
    (testing "with unknown database"
      (testing "success"
        (given @(handler
                 {:request-method :get
                  :uri "/fhir/__admin/dbs/unknown/stats"})
          :status := 404
          [:body :msg] := "The database `unknown` was not found.")))

    (testing "without block cache"
      (testing "success"
        (given @(handler
                 {:request-method :get
                  :uri "/fhir/__admin/dbs/resource/stats"})
          :status := 200
          [:body :estimate-live-data-size] := 0
          [:body :usable-space] :? pos-int?
          [:body :block-cache] := nil
          [:body :compactions :pending] := 0
          [:body :compactions :running] := 0)))

    (testing "with block cache"
      (testing "success"
        (given @(handler
                 {:request-method :get
                  :uri "/fhir/__admin/dbs/index/stats"})
          :status := 200
          [:body :estimate-live-data-size] := 0
          [:body :usable-space] :? pos-int?
          [:body :block-cache :capacity] :? pos-int?
          [:body :block-cache :usage] :? pos-int?
          [:body :compactions :pending] := 0
          [:body :compactions :running] := 0)))))

(deftest column-families-test
  (testing "index database"
    (with-handler [handler] (config (new-temp-dir!)) []
      (testing "success"
        (given @(handler
                 {:request-method :get
                  :uri "/fhir/__admin/dbs/index/column-families"})
          :status := 200
          [:body 0 :name] := "active-search-params"
          [:body 0 :estimate-num-keys] := 0
          [:body 0 :estimate-live-data-size] := 0
          [:body 0 :live-sst-files-size] := 0
          [:body 0 :size-all-mem-tables] := 2048
          [:body 1 :name] := "compartment-resource-type-index"))))

  (testing "resource database"
    (with-handler [handler] (config (new-temp-dir!)) []
      (testing "success"
        (given @(handler
                 {:request-method :get
                  :uri "/fhir/__admin/dbs/resource/column-families"})
          :status := 200
          [:body count] := 1
          [:body 0 :name] := "default"
          [:body 0 :estimate-num-keys] := 0
          [:body 0 :estimate-live-data-size] := 0
          [:body 0 :live-sst-files-size] := 0
          [:body 0 :size-all-mem-tables] := 2048)))))

(deftest column-family-metadata-test
  (with-handler [handler] (config (new-temp-dir!)) []
    (testing "search-param-value-index in index database"
      (given @(handler
               {:request-method :get
                :uri "/fhir/__admin/dbs/index/column-families/search-param-value-index/metadata"})
        :status := 200
        [:body ::column-family-meta-data/name] := "search-param-value-index"
        [:body ::column-family-meta-data/num-files] := 0
        [:body ::column-family-meta-data/file-size] := 0
        [:body ::column-family-meta-data/levels count] := 7
        [:body ::column-family-meta-data/levels 0 ::column-family-meta-data-level/level] := 0
        [:body ::column-family-meta-data/levels 0 ::column-family-meta-data-level/num-files] := 0
        [:body ::column-family-meta-data/levels 0 ::column-family-meta-data-level/file-size] := 0
        [:body ::column-family-meta-data/levels 1 ::column-family-meta-data-level/level] := 1))

    (testing "unknown column-family in index database"
      (given @(handler
               {:request-method :get
                :uri "/fhir/__admin/dbs/index/column-families/foo/metadata"})
        :status := 404
        [:body :msg] := "The column family `foo` in database `index` was not found."))))

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
        [:body 0 :name] :? string?
        [:body 0 :compression-name] := "ZSTD"
        [:body 0 :data-size] :? pos-int?
        [:body 0 :num-entries] :? pos-int?
        [:body 0 :num-data-blocks] :? pos-int?
        [:body 0 :total-raw-key-size] :? pos-int?
        [:body 0 :total-raw-value-size] := 0))

    (testing "not-found"
      (given @(handler
               {:request-method :get
                :uri "/fhir/__admin/dbs/index/column-families/foo/tables"})
        :status := 404
        [:body :msg] := "The column family `foo` in database `index` was not found."))))
