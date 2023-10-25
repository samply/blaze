(ns blaze.admin-api-test
  (:require
    [blaze.admin-api]
    [blaze.db.api-stub :refer [mem-node-config with-system-data]]
    [blaze.db.impl.index.patient-last-change :as plc]
    [blaze.db.kv :as-alias kv]
    [blaze.db.kv.rocksdb :as rocksdb]
    [blaze.elm.compiler :as c]
    [blaze.elm.expression :as-alias expr]
    [blaze.elm.expression.cache :as ec]
    [blaze.fhir.spec.type :as type]
    [blaze.test-util :as tu :refer [given-thrown]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
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
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :index-kv-store))))

  (testing "invalid index-kv-store"
    (given-thrown (ig/init {:blaze/admin-api {:index-kv-store ::invalid}})
      :key := :blaze/admin-api
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :context-path)))))


(defn- new-temp-dir! []
  (str (Files/createTempDirectory "blaze" (make-array FileAttribute 0))))


(def ^:private config
  (-> (assoc mem-node-config
        :blaze/admin-api
        {:context-path "/fhir"
         :index-kv-store (ig/ref :blaze.db/index-kv-store)})))


(defn- with-rocksdb [config dir]
  (-> (assoc config
        [::kv/rocksdb :blaze.db/index-kv-store]
        {:dir dir
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
          :cql-bloom-filter nil}}
        ::rocksdb/stats {})
      (dissoc [::kv/mem :blaze.db/index-kv-store])))


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
  (with-handler [handler] config []
    (given @(handler
              {:request-method :get
               :uri "/fhir/__admin/foo"})
      :status := 404)))


(deftest column-families-test
  (with-handler [handler] (with-rocksdb config (new-temp-dir!)) []
    (testing "success"
      (given @(handler
                {:request-method :get
                 :uri "/fhir/__admin/db/index/column-families"})
        :status := 200
        [:body 0 :name] := "search-param-value-index"
        [:body 0 :estimate-num-keys] := 0
        [:body 0 :estimate-live-data-size] := 0
        [:body 0 :live-sst-files-size] := 0
        [:body 0 :size-all-mem-tables] := 2048
        [:body 1 :name] := "resource-value-index"))))


(deftest column-family-state-test
  (with-handler [handler] (with-rocksdb config (new-temp-dir!)) []
    (testing "patient-last-change-index"
      (with-redefs [plc/state (fn [_] ::state)]
        (given @(handler
                  {:request-method :get
                   :uri "/fhir/__admin/db/index/column-families/patient-last-change-index/state"})
          :status := 200
          :body := ::state)))

    (testing "other column-family"
      (given @(handler
                {:request-method :get
                 :uri "/fhir/__admin/db/index/column-families/default/state"})
        :status := 404
        [:body :msg] := "The column family `default` has no state."))))


(deftest rocksdb-tables-test
  (with-handler [handler] (with-rocksdb config (new-temp-dir!))
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
                 :uri "/fhir/__admin/db/index/column-families/compartment-search-param-value-index/rocksdb-tables"})
        :status := 200
        [:body :tables count] := 1
        [:body :tables 0 :name] :? string?
        [:body :tables 0 :compression-name] := "ZSTD"
        [:body :tables 0 :data-size] :? pos-int?
        [:body :tables 0 :num-entries] :? pos-int?
        [:body :tables 0 :num-data-blocks] :? pos-int?
        [:body :tables 0 :total-raw-key-size] :? pos-int?
        [:body :tables 0 :total-raw-value-size] := 0))

    (testing "not-found"
      (given @(handler
                {:request-method :get
                 :uri "/fhir/__admin/db/index/column-families/foo/rocksdb-tables"})
        :status := 404
        [:body :msg] := "The column family `foo` was not found."))))


(deftest cql-bloom-filters-test
  (testing "without expression cache"
    (with-handler [handler] config []
      (testing "not-found"
        (given @(handler
                  {:request-method :get
                   :uri "/fhir/__admin/cql/bloom-filters"})
          :status := 404
          [:body :msg] := "The feature \"CQL Expression Cache\" is disabled."))))

  (with-handler [handler {::expr/keys [cache]}] (with-cql-expr-cache config) []
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
        [:body :bloom-filters count] := 1
        [:body :bloom-filters 0 :t] := 0
        [:body :bloom-filters 0 :expr-form] := "(exists (retrieve \"Observation\"))"
        [:body :bloom-filters 0 :patient-count] := 0
        [:body :bloom-filters 0 :mem-size] := 14))))
