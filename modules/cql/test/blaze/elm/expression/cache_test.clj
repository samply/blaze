(ns blaze.elm.expression.cache-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.byte-buffer-spec]
   [blaze.cache-collector.protocols :as ccp]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.db.api-stub :refer [mem-node-config with-system-data]]
   [blaze.elm.compiler :as c]
   [blaze.elm.compiler.test-util :as ctu]
   [blaze.elm.expression :as expr]
   [blaze.elm.expression.cache :as ec]
   [blaze.elm.expression.cache-spec]
   [blaze.elm.expression.cache.bloom-filter :as-alias bloom-filter]
   [blaze.elm.expression.cache.codec-spec]
   [blaze.elm.expression.cache.codec.by-t-spec]
   [blaze.elm.expression.cache.spec]
   [blaze.elm.literal :as elm]
   [blaze.executors :as ex]
   [blaze.fhir.test-util]
   [blaze.metrics.spec]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [java-time.api :as time]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [com.github.benmanes.caffeine.cache AsyncLoadingCache]
   [com.github.benmanes.caffeine.cache.stats CacheStats]
   [com.google.common.hash HashCode]))

(set! *warn-on-reflection* true)
(st/instrument)
(ctu/instrument-compile)
(log/set-min-level! :trace)

(defn- fixture [f]
  (st/instrument)
  (ctu/instrument-compile)
  (f)
  (st/unstrument))

(test/use-fixtures :each fixture)

(def ^:private config
  (assoc mem-node-config
         ::expr/cache
         {:node (ig/ref :blaze.db/node)
          :executor (ig/ref :blaze.test/executor)}
         :blaze.test/executor {}))

(deftest init-test
  (testing "nil config"
    (given-failed-system {::expr/cache nil}
      :key := ::expr/cache
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {::expr/cache {}}
      :key := ::expr/cache
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :executor))))

  (testing "invalid max size"
    (given-failed-system (assoc-in config [::expr/cache :max-size-in-mb] ::invalid)
      :key := ::expr/cache
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::ec/max-size-in-mb]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid refresh"
    (given-failed-system (assoc-in config [::expr/cache :refresh] ::invalid)
      :key := ::expr/cache
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::ec/refresh]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "init"
    (with-system [{::expr/keys [cache]} config]
      (is (s/valid? ::expr/cache cache)))))

(deftest executor-init-test
  (testing "nil config"
    (given-failed-system {::ec/executor nil}
      :key := ::ec/executor
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "invalid num-threads"
    (given-failed-system (assoc-in config [::ec/executor :num-threads] ::invalid)
      :key := ::ec/executor
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::ec/num-threads]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(deftest executor-shutdown-timeout-test
  (let [{::ec/keys [executor] :as system} (ig/init {::ec/executor {}})]

    ;; will produce a timeout, because the function runs 11 seconds
    (ex/execute! executor #(Thread/sleep 11000))

    ;; ensure that the function is called before the scheduler is halted
    (Thread/sleep 100)

    (ig/halt! system)

    ;; the scheduler is shut down
    (is (ex/shutdown? executor))

    ;; but it isn't terminated yet
    (is (not (ex/terminated? executor)))))

(deftest bloom-filter-creation-duration-seconds-collector-init-test
  (with-system [{collector ::ec/bloom-filter-creation-duration-seconds} {::ec/bloom-filter-creation-duration-seconds {}}]
    (is (s/valid? :blaze.metrics/collector collector))))

(deftest bloom-filter-useful-total-collector-init-test
  (with-system [{collector ::ec/bloom-filter-useful-total} {::ec/bloom-filter-useful-total {}}]
    (is (s/valid? :blaze.metrics/collector collector))))

(deftest bloom-filter-not-useful-total-collector-init-test
  (with-system [{collector ::ec/bloom-filter-not-useful-total} {::ec/bloom-filter-not-useful-total {}}]
    (is (s/valid? :blaze.metrics/collector collector))))

(deftest bloom-filter-false-positive-total-collector-init-test
  (with-system [{collector ::ec/bloom-filter-false-positive-total} {::ec/bloom-filter-false-positive-total {}}]
    (is (s/valid? :blaze.metrics/collector collector))))

(deftest bloom-filter-bytes-collector-init-test
  (with-system [{collector ::ec/bloom-filter-bytes} {::ec/bloom-filter-bytes {}}]
    (is (s/valid? :blaze.metrics/collector collector))))

(def ^:private config
  (assoc mem-node-config
         ::expr/cache
         {:node (ig/ref :blaze.db/node)
          :executor (ig/ref :blaze.test/executor)}
         :blaze.test/executor {}))

(defn- compile-exists-expr [resource-type]
  (let [elm (elm/exists (elm/retrieve {:type resource-type}))]
    (c/compile {:eval-context "Patient"} elm)))

(defn- create-bloom-filter!
  "Creates the Bloom filters used in `expr` and wait's some time to ensure that
  the creation is finished."
  [expr cache]
  (c/attach-cache expr cache)
  (Thread/sleep 100))

(deftest get-test
  (testing "one Bloom filter on empty database"
    (with-system [{::expr/keys [cache]} config]
      (create-bloom-filter! (compile-exists-expr "Observation") cache)

      (given (ec/get cache (compile-exists-expr "Observation"))
        ::bloom-filter/t := 0
        ::bloom-filter/expr-form := "(exists (retrieve \"Observation\"))"
        ::bloom-filter/patient-count := 0
        ::bloom-filter/mem-size := 11981))))

(deftest get-disk-test
  (testing "an empty database contains no Bloom filter"
    (with-system [{::expr/keys [cache]} config]
      (is (ba/not-found? (ec/get-disk cache (HashCode/fromString "d4fc6cde1636852f9e362a68ca7be027a66bf7cb38ebff9c256c3eb2179c2639"))))))

  (testing "one Bloom filter on empty database"
    (with-system [{::expr/keys [cache]} config]
      (create-bloom-filter! (compile-exists-expr "Observation") cache)

      (given (ec/get-disk cache (HashCode/fromString "78c3f9b9e187480870ce815ad6d324713dfa2cbd12968c5b14727fef7377b985"))
        ::bloom-filter/t := 0
        ::bloom-filter/expr-form := "(exists (retrieve \"Observation\"))"
        ::bloom-filter/patient-count := 0
        ::bloom-filter/mem-size := 11990))))

(deftest delete-test
  (testing "an empty database contains no Bloom filter"
    (with-system [{::expr/keys [cache]} config]
      (is (ba/not-found? (ec/delete-disk! cache (HashCode/fromString "d4fc6cde1636852f9e362a68ca7be027a66bf7cb38ebff9c256c3eb2179c2639"))))))

  (testing "one Bloom filter on empty database"
    (with-system [{::expr/keys [cache]} config]
      (create-bloom-filter! (compile-exists-expr "Observation") cache)

      (let [hash (HashCode/fromString "78c3f9b9e187480870ce815ad6d324713dfa2cbd12968c5b14727fef7377b985")]
        (is (not (ba/anomaly? (ec/get-disk cache hash))))

        (ec/delete-disk! cache hash)

        (is (ba/not-found? (ec/get-disk cache hash)))))))

(deftest list-by-t-test
  (testing "an empty database contains zero Bloom filters"
    (with-system [{::expr/keys [cache]} config]
      (is (coll/empty? (ec/list-by-t cache)))))

  (testing "one Bloom filter on empty database"
    (with-system [{::expr/keys [cache]} config]
      (create-bloom-filter! (compile-exists-expr "Observation") cache)

      (given (into [] (ec/list-by-t cache))
        count := 1
        [0 ::bloom-filter/hash str] := "78c3f9b9e187480870ce815ad6d324713dfa2cbd12968c5b14727fef7377b985"
        [0 ::bloom-filter/t] := 0
        [0 ::bloom-filter/expr-form] := "(exists (retrieve \"Observation\"))"
        [0 ::bloom-filter/patient-count] := 0
        [0 ::bloom-filter/mem-size] := 11981)))

  (testing "one Bloom filter on database with one patient"
    (with-system-data [{::expr/keys [cache]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

      (create-bloom-filter! (compile-exists-expr "Observation") cache)

      (given (into [] (ec/list-by-t cache))
        count := 1
        [0 ::bloom-filter/hash str] := "78c3f9b9e187480870ce815ad6d324713dfa2cbd12968c5b14727fef7377b985"
        [0 ::bloom-filter/t] := 1
        [0 ::bloom-filter/expr-form] := "(exists (retrieve \"Observation\"))"
        [0 ::bloom-filter/patient-count] := 1
        [0 ::bloom-filter/mem-size] := 11981)))

  (testing "two Bloom filters on database with one patient"
    (with-system-data [{::expr/keys [cache]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

      (create-bloom-filter! (compile-exists-expr "Observation") cache)
      (create-bloom-filter! (compile-exists-expr "Condition") cache)

      (given (into [] (ec/list-by-t cache))
        count := 2
        [0 ::bloom-filter/hash str] := "78c3f9b9e187480870ce815ad6d324713dfa2cbd12968c5b14727fef7377b985"
        [0 ::bloom-filter/t] := 1
        [0 ::bloom-filter/expr-form] := "(exists (retrieve \"Observation\"))"
        [0 ::bloom-filter/patient-count] := 1
        [0 ::bloom-filter/mem-size] := 11981
        [1 ::bloom-filter/hash str] := "b24882a623bc9c78572630b7c5f288553a0c5e31d6c0d9a21e0c3ec43a0d78e7"
        [1 ::bloom-filter/t] := 1
        [1 ::bloom-filter/expr-form] := "(exists (retrieve \"Condition\"))"
        [1 ::bloom-filter/patient-count] := 0
        [1 ::bloom-filter/mem-size] := 11981)))

  (testing "Bloom filter updates are reflected in the list"
    (with-system-data [{::expr/keys [cache] :blaze.db/keys [node]}
                       (assoc-in config [::expr/cache :refresh] (time/millis 1))]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

      (create-bloom-filter! (compile-exists-expr "Observation") cache)

      @(d/transact node [[:put {:fhir/type :fhir/Patient :id "1"}]
                         [:put {:fhir/type :fhir/Observation :id "1"
                                :subject #fhir/Reference{:reference #fhir/string "Patient/1"}}]])

      (create-bloom-filter! (compile-exists-expr "Observation") cache)

      (given (into [] (ec/list-by-t cache))
        count := 1
        [0 ::bloom-filter/hash str] := "78c3f9b9e187480870ce815ad6d324713dfa2cbd12968c5b14727fef7377b985"
        [0 ::bloom-filter/t] := 2
        [0 ::bloom-filter/expr-form] := "(exists (retrieve \"Observation\"))"
        [0 ::bloom-filter/patient-count] := 2
        [0 ::bloom-filter/mem-size] := 11981)))

  (testing "an old Bloom filter is loaded from the store even if the t was increased in the meantime"
    (with-system-data [{::expr/keys [cache] :blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

      (testing "creates the Bloom filter with t=1"
        (create-bloom-filter! (compile-exists-expr "Observation") cache)

        (given (into [] (ec/list-by-t cache))
          count := 1
          [0 ::bloom-filter/t] := 1))

      ;; invalidates the cache
      (.invalidateAll (.synchronous ^AsyncLoadingCache (:mem-cache cache)))

      ;; advances the database
      @(d/transact node [[:put {:fhir/type :fhir/Patient :id "1"}]
                         [:put {:fhir/type :fhir/Observation :id "1"
                                :subject #fhir/Reference{:reference #fhir/string "Patient/1"}}]])

      (testing "doesn't create a new Bloom filter because the old one is still in the store"
        (create-bloom-filter! (compile-exists-expr "Observation") cache)

        (given (into [] (ec/list-by-t cache))
          count := 1
          [0 ::bloom-filter/t] := 1)))))

(deftest total-test
  (testing "an empty database contains zero Bloom filters"
    (with-system [{::expr/keys [cache]} config]
      (is (zero? (ec/total cache)))))

  (testing "one Bloom filter on empty database"
    (with-system [{::expr/keys [cache]} config]
      (create-bloom-filter! (compile-exists-expr "Observation") cache)

      (is (= 1 (ec/total cache))))))

(deftest stats-test
  (with-system [{::expr/keys [cache]} config]
    (is (zero? (.hitCount ^CacheStats (ccp/-stats cache))))
    (is (zero? (ccp/-estimated-size cache)))))
