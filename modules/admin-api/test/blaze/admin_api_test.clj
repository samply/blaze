(ns blaze.admin-api-test
  (:require
   [blaze.admin-api :as admin-api]
   [blaze.db.api-stub :refer [mem-node-config with-system-data]]
   [blaze.db.impl.index.patient-last-change :as plc]
   [blaze.db.kv :as-alias kv]
   [blaze.db.kv.rocksdb :as rocksdb]
   [blaze.db.kv.rocksdb.column-family-meta-data :as-alias column-family-meta-data]
   [blaze.db.kv.rocksdb.column-family-meta-data.level :as-alias column-family-meta-data-level]
   [blaze.elm.compiler :as c]
   [blaze.elm.expression :as-alias expr]
   [blaze.elm.expression.cache :as ec]
   [blaze.elm.expression.cache.bloom-filter :as bloom-filter]
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
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :dbs))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :settings))
      [:explain ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :features))))

  (testing "invalid databases"
    (given-thrown (ig/init {:blaze/admin-api {:dbs ::invalid}})
      :key := :blaze/admin-api
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :context-path))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :settings))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :features))
      [:explain ::s/problems 3 :via] := [::admin-api/dbs]
      [:explain ::s/problems 3 :pred] := `map?
      [:explain ::s/problems 3 :val] := ::invalid)))

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
         :system-stats-index nil
         :cql-bloom-filter nil
         :cql-bloom-filter-by-t nil}}
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
        :settings {}
        :features {}})
      (dissoc
       [::kv/mem :blaze.db/index-kv-store]
       [::kv/mem :blaze.db/resource-kv-store])))

(defn- with-cql-expr-cache [config]
  (-> (assoc config
             ::expr/cache
             {:node (ig/ref :blaze.db/node)
              :executor (ig/ref :blaze.test/executor)}
             :blaze.test/executor {})
      (assoc-in [:blaze/admin-api ::expr/cache] (ig/ref ::expr/cache))))

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

        (testing "getColumnFamilyState"
          (let [op (get-in body [:paths "/fhir/__admin/dbs/{db}/column-families/{column-family}/state" :get])]
            (given op
              :operation-id := "getColumnFamilyState"
              :summary := "Fetch the state of a column family of a database."
              [:parameters count] := 2
              [:parameters 0 "$ref"] := "#/components/parameters/db"
              [:parameters 1 "$ref"] := "#/components/parameters/columnFamily")

            (testing "responses"
              (testing "200"
                (given (get-in op [:responses 200])
                  [:description] := "Column family state."
                  [:content "application/json" :schema :type] := "object")))))

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
                  [:content "application/json" :schema :items :properties :total-raw-key-size :type] := "number")))))

        (testing "cql-bloom-filters"
          (let [op (get-in body [:paths "/fhir/__admin/cql/bloom-filters" :get])]
            (given op
              :operation-id := "cql-bloom-filters"
              :summary := "Fetch the list of all CQL Bloom filters.")

            (testing "responses"
              (testing "200"
                (given (get-in op [:responses 200])
                  [:description] := "A list of CQL Bloom filters."
                  [:content "application/json" :schema :type] := "array"
                  [:content "application/json" :schema :items "$ref"] := "#/components/schemas/BloomFilter")))))))))

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
  (testing "without block cache"
    (with-handler [handler] (config (new-temp-dir!)) []
      (testing "success"
        (given @(handler
                 {:request-method :get
                  :uri "/fhir/__admin/dbs/resource/stats"})
          :status := 200
          [:body :estimate-live-data-size] := 0
          [:body :usable-space] :? pos-int?
          [:body :block-cache] := nil
          [:body :compactions :pending] := 0
          [:body :compactions :running] := 0))))

  (testing "with block cache"
    (with-handler [handler] (config (new-temp-dir!)) []
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
          [:body 0 :name] := "search-param-value-index"
          [:body 0 :estimate-num-keys] := 0
          [:body 0 :estimate-live-data-size] := 0
          [:body 0 :live-sst-files-size] := 0
          [:body 0 :size-all-mem-tables] := 2048
          [:body 1 :name] := "resource-value-index"))))

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

(deftest column-family-state-test
  (with-handler [handler] (config (new-temp-dir!)) []
    (testing "patient-last-change-index in index database"
      (with-redefs [plc/state (fn [_] ::state)]
        (given @(handler
                 {:request-method :get
                  :uri "/fhir/__admin/dbs/index/column-families/patient-last-change-index/state"})
          :status := 200
          :body := ::state)))

    (testing "other column-family in index database"
      (given @(handler
               {:request-method :get
                :uri "/fhir/__admin/dbs/index/column-families/default/state"})
        :status := 404
        [:body :msg] := "The column family `default` in database `index` has no state."))

    (testing "other column-family in resource database"
      (given @(handler
               {:request-method :get
                :uri "/fhir/__admin/dbs/resource/column-families/default/state"})
        :status := 404
        [:body :msg] := "The column family `default` in database `resource` has no state."))

    (testing "other column-family in other database"
      (given @(handler
               {:request-method :get
                :uri "/fhir/__admin/dbs/foo/column-families/default/state"})
        :status := 404
        [:body :msg] := "The database `foo` was not found."))))

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

(deftest cql-bloom-filters-test
  (testing "without expression cache"
    (with-handler [handler] (config (new-temp-dir!)) []
      (testing "not-found"
        (given @(handler
                 {:request-method :get
                  :uri "/fhir/__admin/cql/bloom-filters"})
          :status := 404
          [:body :msg] := "The feature \"CQL Expression Cache\" is disabled."))))

  (with-handler [handler {::expr/keys [cache]}] (with-cql-expr-cache (config (new-temp-dir!))) []
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
        [:body 0 ::bloom-filter/t] := 0
        [:body 0 ::bloom-filter/expr-form] := "(exists (retrieve \"Observation\"))"
        [:body 0 ::bloom-filter/patient-count] := 0
        [:body 0 ::bloom-filter/mem-size] := 0))))
