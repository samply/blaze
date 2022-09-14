(ns blaze.db.kv.rocksdb.impl-test
  (:require
    [blaze.anomaly :as ba]
    [blaze.db.kv.rocksdb.impl :as impl]
    [blaze.db.kv.rocksdb.impl-spec]
    [blaze.metrics.core-spec]
    [clojure.core.protocols :as p]
    [clojure.datafy :as datafy]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]])
  (:import
    [com.google.common.io BaseEncoding]
    [java.nio.file Files]
    [java.nio.file.attribute FileAttribute]
    [org.rocksdb
     BlockBasedTableConfig ColumnFamilyDescriptor ColumnFamilyOptions
     CompressionType DBOptions LRUCache Statistics RocksDB WriteBatchInterface
     ColumnFamilyHandle WriteOptions]))


(set! *warn-on-reflection* true)
(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn- from-hex [s]
  (.decode (BaseEncoding/base16) s))


(defn- to-hex [bytes]
  (.encode (BaseEncoding/base16) bytes))


(extend-protocol p/Datafiable
  ColumnFamilyDescriptor
  (datafy [desc]
    {:name (String. ^bytes (.getName desc))
     :options (datafy/datafy (.getOptions desc))})

  ColumnFamilyOptions
  (datafy [options]
    {:level-compaction-dynamic-level-bytes (.levelCompactionDynamicLevelBytes options)
     :compression-type (.compressionType options)
     :bottommost-compression-type (.bottommostCompressionType options)
     :write-buffer-size-in-mb (bit-shift-right (.writeBufferSize options) 20)
     :max-write-buffer-number (.maxWriteBufferNumber options)
     :max-bytes-for-level-base-in-mb (bit-shift-right (.maxBytesForLevelBase options) 20)
     :level0-file-num-compaction-trigger (.level0FileNumCompactionTrigger options)
     :min-write-buffer-number-to-merge (.minWriteBufferNumberToMerge options)
     :target-file-size-base-in-mb (bit-shift-right (.targetFileSizeBase options) 20)
     :table-format-config (datafy/datafy (.tableFormatConfig options))
     :memtable-whole-key-filtering? (.memtableWholeKeyFiltering options)
     :optimize-filters-for-hits? (.optimizeFiltersForHits options)})

  BlockBasedTableConfig
  (datafy [config]
    {:verify-compression (.verifyCompression config)
     :cache-index-and-filter-blocks (.cacheIndexAndFilterBlocks config)
     :pin-l0-filter-and-index-blocks-in-cache (.pinL0FilterAndIndexBlocksInCache config)
     :block-size (.blockSize config)
     :bloom-filter? (some? (.filterPolicy config))})

  DBOptions
  (datafy [options]
    {:wal-dir (.walDir options)
     :max-background-jobs (.maxBackgroundJobs options)
     :compaction-readahead-size (.compactionReadaheadSize options)
     :manual-wal-flush? (.manualWalFlush options)
     :enable-pipelined-write (.enablePipelinedWrite options)
     :create-if-missing (.createIfMissing options)
     :create-missing-column-families (.createMissingColumnFamilies options)})

  WriteOptions
  (datafy [options]
    {:sync? (.sync options)
     :disable-wal? (.disableWAL options)}))


(defn- column-family-descriptor [block-cache key opts]
  (datafy/datafy (impl/column-family-descriptor block-cache [key opts])))


(deftest column-family-descriptor-test
  (testing "with defaults"
    (with-open [block-cache (LRUCache. 0)]
      (given (column-family-descriptor block-cache :default nil)
        :name := "default"
        [:options :level-compaction-dynamic-level-bytes] := true
        [:options :compression-type] := CompressionType/LZ4_COMPRESSION
        [:options :bottommost-compression-type] := CompressionType/ZSTD_COMPRESSION
        [:options :write-buffer-size-in-mb] := 64
        [:options :max-write-buffer-number] := 2
        [:options :max-bytes-for-level-base-in-mb] := 256
        [:options :level0-file-num-compaction-trigger] := 4
        [:options :min-write-buffer-number-to-merge] := 1
        [:options :target-file-size-base-in-mb] := 64
        [:options :table-format-config :verify-compression] := false
        [:options :table-format-config :cache-index-and-filter-blocks] := true
        [:options :table-format-config :pin-l0-filter-and-index-blocks-in-cache] := true
        [:options :table-format-config :block-size] := 4096
        [:options :table-format-config :bloom-filter?] := false
        [:options :memtable-whole-key-filtering?] := false
        [:options :optimize-filters-for-hits?] := false)))

  (with-open [block-cache (LRUCache. 0)]
    (are [key value]
      (given (column-family-descriptor block-cache :default {key value})
        [:options key] := value)

      :write-buffer-size-in-mb 128
      :max-write-buffer-number 4
      :level0-file-num-compaction-trigger 8
      :min-write-buffer-number-to-merge 2
      :max-bytes-for-level-base-in-mb 512
      :target-file-size-base-in-mb 64
      :memtable-whole-key-filtering? true
      :optimize-filters-for-hits? true))

  (testing "can't verify that reverse-comparator? is set; so only running the code"
    (with-open [block-cache (LRUCache. 0)]
      (column-family-descriptor block-cache :default {:reverse-comparator? true})))

  (testing "can't verify that merge-operator is set; so only running the code"
    (with-open [block-cache (LRUCache. 0)]
      (column-family-descriptor block-cache :default {:merge-operator :stringappend})))

  (with-open [block-cache (LRUCache. 0)]
    (are [key value]
      (given (column-family-descriptor block-cache :default {key value})
        [:options :table-format-config key] := value)

      :block-size 16384
      :bloom-filter? true)))


(deftest db-options-test
  (testing "with defaults"
    (given (datafy/datafy (impl/db-options (Statistics.) nil))
      :wal-dir := ""
      :max-background-jobs := 2
      :compaction-readahead-size := 0
      :manual-wal-flush? := false
      :enable-pipelined-write := true
      :create-if-missing := true
      :create-missing-column-families := true)

    (are [key value]
      (given (datafy/datafy (impl/db-options (Statistics.) {key value}))
        key := value)

      :wal-dir "wal"
      :max-background-jobs 4
      :compaction-readahead-size 10
      :manual-wal-flush? true)))


(deftest write-options-test
  (testing "with defaults"
    (given (datafy/datafy (impl/write-options nil))
      :sync? := false
      :disable-wal? := false)

    (are [key value]
      (given (datafy/datafy (impl/write-options {key value}))
        key := value)

      :sync? true
      :disable-wal? true)))


(defn- new-temp-dir! []
  (str (Files/createTempDirectory "blaze" (make-array FileAttribute 0))))


(defn- kv-put-wb [state]
  (reify WriteBatchInterface
    (^void put [_ ^bytes key ^bytes val]
      (swap! state conj [(to-hex key) (to-hex val)]))))


(defn- cf-put-wb [state]
  (reify WriteBatchInterface
    (^void put [_ ^ColumnFamilyHandle cfh ^bytes key ^bytes val]
      (swap! state conj [cfh (to-hex key) (to-hex val)]))))


(defn- cfh [db name]
  (.createColumnFamily ^RocksDB db (ColumnFamilyDescriptor. (from-hex name))))


(deftest put-wb-test
  (testing "without column family"
    (are [entries state-val]
      (let [state (atom [])]
        (impl/put-wb! {} (kv-put-wb state) entries)
        (is (= state-val @state)))

      [[(from-hex "01") (from-hex "02")]]
      [["01" "02"]]

      [[(from-hex "01") (from-hex "02")]
       [(from-hex "03") (from-hex "04")]]
      [["01" "02"]
       ["03" "04"]]))

  (testing "with column families"
    (with-open [db (RocksDB/open (str (new-temp-dir!)))]
      (let [cfh-1 (cfh db "01")
            cfh-2 (cfh db "02")]
        (are [entries state-val]
          (let [state (atom [])]
            (impl/put-wb!
              {:cf-1 cfh-1
               :cf-2 cfh-2}
              (cf-put-wb state)
              entries)
            (is (= state-val @state)))

          [[:cf-1 (from-hex "01") (from-hex "02")]]
          [[cfh-1 "01" "02"]]

          [[:cf-1 (from-hex "01") (from-hex "02")]
           [:cf-1 (from-hex "03") (from-hex "04")]]
          [[cfh-1 "01" "02"]
           [cfh-1 "03" "04"]]

          [[:cf-1 (from-hex "01") (from-hex "02")]
           [:cf-2 (from-hex "03") (from-hex "04")]]
          [[cfh-1 "01" "02"]
           [cfh-2 "03" "04"]]))))

  (testing "with missing column family"
    (let [entries [[:cf-1 (byte-array 0) (byte-array 0)]]]
      (given (ba/try-anomaly (impl/put-wb! {} (cf-put-wb nil) entries))
        ::anom/category := ::anom/not-found
        ::anom/message := "column family `cf-1` not found"))))


(defn- kv-delete-wb [state]
  (reify WriteBatchInterface
    (^void delete [_ ^bytes key]
      (swap! state conj (to-hex key)))))


(deftest delete-wb-test
  (testing "without column family"
    (are [keys state-val]
      (let [state (atom [])]
        (impl/delete-wb! (kv-delete-wb state) keys)
        (is (= state-val @state)))

      [(from-hex "01")]
      ["01"]

      [(from-hex "01") (from-hex "02")]
      ["01" "02"])))


(defn- kv-merge-wb [state]
  (reify WriteBatchInterface
    (^void merge [_ ^bytes key ^bytes val]
      (swap! state conj [(to-hex key) (to-hex val)]))))


(defn- cf-merge-wb [state]
  (reify WriteBatchInterface
    (^void merge [_ ^ColumnFamilyHandle cfh ^bytes key ^bytes val]
      (swap! state conj [cfh (to-hex key) (to-hex val)]))))


(defn- cf-delete-wb [state]
  (reify WriteBatchInterface
    (^void delete [_ ^ColumnFamilyHandle cfh ^bytes key]
      (swap! state conj [cfh (to-hex key)]))))


(deftest write-wb-test
  (testing "without column family"
    (testing "put"
      (are [entries state-val]
        (let [state (atom [])]
          (impl/write-wb! {} (kv-put-wb state) entries)
          (is (= state-val @state)))

        [[:put (from-hex "01") (from-hex "02")]]
        [["01" "02"]]

        [[:put (from-hex "01") (from-hex "02")]
         [:put (from-hex "03") (from-hex "04")]]
        [["01" "02"]
         ["03" "04"]]))

    (testing "merge"
      (are [entries state-val]
        (let [state (atom [])]
          (impl/write-wb! {} (kv-merge-wb state) entries)
          (is (= state-val @state)))

        [[:merge (from-hex "01") (from-hex "02")]]
        [["01" "02"]]

        [[:merge (from-hex "01") (from-hex "02")]
         [:merge (from-hex "03") (from-hex "04")]]
        [["01" "02"]
         ["03" "04"]]))

    (testing "delete"
      (are [entries state-val]
        (let [state (atom [])]
          (impl/write-wb! {} (kv-delete-wb state) entries)
          (is (= state-val @state)))

        [[:delete (from-hex "01")]]
        ["01"]

        [[:delete (from-hex "01")]
         [:delete (from-hex "02")]]
        ["01" "02"])))

  (testing "with column families"
    (with-open [db (RocksDB/open (str (new-temp-dir!)))]
      (let [cfh-1 (cfh db "01")
            cfh-2 (cfh db "02")]
        (testing "put"
          (are [entries state-val]
            (let [state (atom [])]
              (impl/write-wb!
                {:cf-1 cfh-1
                 :cf-2 cfh-2}
                (cf-put-wb state)
                entries)
              (is (= state-val @state)))

            [[:put :cf-1 (from-hex "01") (from-hex "02")]]
            [[cfh-1 "01" "02"]]

            [[:put :cf-1 (from-hex "01") (from-hex "02")]
             [:put :cf-1 (from-hex "03") (from-hex "04")]]
            [[cfh-1 "01" "02"]
             [cfh-1 "03" "04"]]

            [[:put :cf-1 (from-hex "01") (from-hex "02")]
             [:put :cf-2 (from-hex "03") (from-hex "04")]]
            [[cfh-1 "01" "02"]
             [cfh-2 "03" "04"]]))

        (testing "merge"
          (are [entries state-val]
            (let [state (atom [])]
              (impl/write-wb!
                {:cf-1 cfh-1
                 :cf-2 cfh-2}
                (cf-merge-wb state)
                entries)
              (is (= state-val @state)))

            [[:merge :cf-1 (from-hex "01") (from-hex "02")]]
            [[cfh-1 "01" "02"]]

            [[:merge :cf-1 (from-hex "01") (from-hex "02")]
             [:merge :cf-1 (from-hex "03") (from-hex "04")]]
            [[cfh-1 "01" "02"]
             [cfh-1 "03" "04"]]

            [[:merge :cf-1 (from-hex "01") (from-hex "02")]
             [:merge :cf-2 (from-hex "03") (from-hex "04")]]
            [[cfh-1 "01" "02"]
             [cfh-2 "03" "04"]]))

        (testing "delete"
            (are [entries state-val]
              (let [state (atom [])]
                (impl/write-wb!
                  {:cf-1 cfh-1
                   :cf-2 cfh-2}
                  (cf-delete-wb state)
                  entries)
                (is (= state-val @state)))

              [[:delete :cf-1 (from-hex "01")]]
              [[cfh-1 "01"]]

              [[:delete :cf-1 (from-hex "01")]
               [:delete :cf-1 (from-hex "02")]]
              [[cfh-1 "01"]
               [cfh-1 "02"]]

              [[:delete :cf-1 (from-hex "01")]
               [:delete :cf-2 (from-hex "02")]]
              [[cfh-1 "01"]
               [cfh-2 "02"]])))))

  (testing "with missing column family"
    (let [entries [[:put :cf-1 (byte-array 0) (byte-array 0)]]]
      (given (ba/try-anomaly (impl/write-wb! {} (cf-put-wb nil) entries))
        ::anom/category := ::anom/not-found
        ::anom/message := "column family `cf-1` not found")))

  (testing "non matching op"
    ;; although a non-matching op isn't allowed in the spec, it could happen at
    ;; runtime, and we have to test that case
    (st/unstrument `impl/write-wb!)
    (try
      (impl/write-wb! {} (cf-put-wb nil) [[:foo]])
      (catch Exception e
        (is (= "No matching clause: :foo" (ex-message e)))))))
