(ns blaze.job.re-index-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.api-spec]
   [blaze.db.impl.protocols :as p]
   [blaze.db.kv :as kv]
   [blaze.db.kv.mem]
   [blaze.db.node :as node]
   [blaze.db.resource-store :as rs]
   [blaze.db.resource-store.kv :as rs-kv]
   [blaze.db.search-param-registry]
   [blaze.db.search-param-registry-spec]
   [blaze.db.tx-cache]
   [blaze.db.tx-log :as tx-log]
   [blaze.db.tx-log.local]
   [blaze.fhir.parsing-context]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.fhir.writing-context]
   [blaze.job-scheduler :as js]
   [blaze.job.re-index]
   [blaze.job.test-util :as jtu]
   [blaze.job.util :as job-util]
   [blaze.luid :as luid]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.spec]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [integrant.core :as ig]
   [java-time.api :as time]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(derive :blaze.db.main/node :blaze.db/node)
(derive :blaze.db.admin/node :blaze.db/node)

(def config
  {:blaze/job-scheduler
   {:node (ig/ref :blaze.db.admin/node)
    :handlers {:blaze.job/re-index (ig/ref :blaze.job/re-index)}
    :clock (ig/ref :blaze.test/offset-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}

   :blaze.job/re-index
   {:main-node (ig/ref :blaze.db.main/node)
    :admin-node (ig/ref :blaze.db.admin/node)
    :clock (ig/ref :blaze.test/offset-clock)}

   :blaze.db.main/node
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

   :blaze.db.admin/node
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

   [:blaze.db/tx-cache :blaze.db.main/tx-cache]
   {:kv-store (ig/ref :blaze.db.main/index-kv-store)}

   [:blaze.db/tx-cache :blaze.db.admin/tx-cache]
   {:kv-store (ig/ref :blaze.db.admin/index-kv-store)}

   [::node/indexer-executor :blaze.db.node.main/indexer-executor]
   {}

   [::node/indexer-executor :blaze.db.node.admin/indexer-executor]
   {}

   [::kv/mem :blaze.db.main/index-kv-store]
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

   [::node/resource-indexer :blaze.db.node.main/resource-indexer]
   {:kv-store (ig/ref :blaze.db.main/index-kv-store)
    :resource-store (ig/ref :blaze.db/resource-store)
    :search-param-registry (ig/ref :blaze.db/search-param-registry)
    :executor (ig/ref :blaze.db.node.resource-indexer.main/executor)}

   [::node/resource-indexer :blaze.db.node.admin/resource-indexer]
   {:kv-store (ig/ref :blaze.db.admin/index-kv-store)
    :resource-store (ig/ref :blaze.db/resource-store)
    :search-param-registry (ig/ref :blaze.db/search-param-registry)
    :executor (ig/ref :blaze.db.node.resource-indexer.admin/executor)}

   [:blaze.db.node.resource-indexer/executor :blaze.db.node.resource-indexer.main/executor]
   {}

   [:blaze.db.node.resource-indexer/executor :blaze.db.node.resource-indexer.admin/executor]
   {}

   ::rs/kv
   {:kv-store (ig/ref :blaze.db/resource-kv-store)
    :parsing-context (ig/ref :blaze.fhir.parsing-context/resource-store)
    :writing-context (ig/ref :blaze.fhir/writing-context)
    :executor (ig/ref ::rs-kv/executor)}

   [::kv/mem :blaze.db/resource-kv-store]
   {:column-families {}}

   ::rs-kv/executor {}

   :blaze.db/search-param-registry
   {:structure-definition-repo structure-definition-repo}

   [:blaze.fhir/parsing-context :blaze.fhir.parsing-context/resource-store]
   {:structure-definition-repo structure-definition-repo
    :fail-on-unknown-property false
    :include-summary-only true
    :use-regex false}

   :blaze.fhir/writing-context
   {:structure-definition-repo structure-definition-repo}

   :blaze/scheduler {}

   :blaze.test/fixed-clock {}

   :blaze.test/offset-clock
   {:clock (ig/ref :blaze.test/fixed-clock)
    :offset-seconds 11}

   :blaze.test/fixed-rng-fn {}})

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.job/re-index nil}
      :key := :blaze.job/re-index
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.job/re-index {}}
      :key := :blaze.job/re-index
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :main-node))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :admin-node))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :clock))))

  (testing "invalid main-node"
    (given-failed-system (assoc-in config [:blaze.job/re-index :main-node] ::invalid)
      :key := :blaze.job/re-index
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/node]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid admin-node"
    (given-failed-system (assoc-in config [:blaze.job/re-index :admin-node] ::invalid)
      :key := :blaze.job/re-index
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/node]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid clock"
    (given-failed-system (assoc-in config [:blaze.job/re-index :clock] ::invalid)
      :key := :blaze.job/re-index
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/clock]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(def never-increment-config
  (assoc-in config [:blaze.test/offset-clock :offset-seconds] 10))

(defmacro with-system-data
  [[binding-form config] txs & body]
  `(with-system [system# ~config]
     (run! #(deref (d/transact (:blaze.db.main/node system#) %)) ~txs)
     (let [~binding-form system#] ~@body)))

(def job
  {:fhir/type :fhir/Task
   :meta #fhir/Meta{:profile [#fhir/canonical "https://samply.github.io/blaze/fhir/StructureDefinition/ReIndexJob"]}
   :status #fhir/code "ready"
   :intent #fhir/code "order"
   :code #fhir/CodeableConcept
          {:coding
           [#fhir/Coding
             {:system #fhir/uri "https://samply.github.io/blaze/fhir/CodeSystem/JobType"
              :code #fhir/code "re-index"
              :display #fhir/string "(Re)Index a Search Parameter"}]}})

(def job-clinical-code
  (assoc
   job
   :input
   [{:fhir/type :fhir.Task/input
     :type #fhir/CodeableConcept
            {:coding
             [#fhir/Coding
               {:system #fhir/uri "https://samply.github.io/blaze/fhir/CodeSystem/ReIndexJobParameter"
                :code #fhir/code "search-param-url"}]}
     :value #fhir/canonical "http://hl7.org/fhir/SearchParameter/clinical-code"}]))

(def job-missing-search-param
  job)

(def job-unknown-search-param
  (assoc
   job
   :input
   [{:fhir/type :fhir.Task/input
     :type #fhir/CodeableConcept
            {:coding
             [#fhir/Coding
               {:system #fhir/uri "https://samply.github.io/blaze/fhir/CodeSystem/ReIndexJobParameter"
                :code #fhir/code "search-param-url"}]}
     :value #fhir/canonical "unknown"}]))

(defn- output-value [job code]
  (job-util/output-value job "https://samply.github.io/blaze/fhir/CodeSystem/ReIndexJobOutput" code))

(defn- total-resources [job]
  (output-value job "total-resources"))

(defn- resources-processed [job]
  (output-value job "resources-processed"))

(defn- processing-duration [job]
  (output-value job "processing-duration"))

(defn- next-resource [job]
  (:value (output-value job "next-resource")))

(defn- job-id [{{:keys [clock rng-fn]} :context}]
  (luid/luid clock (rng-fn)))

(defn gen-tx-data [n]
  (mapv
   (fn [id]
     [:put {:fhir/type :fhir/Observation :id (format "%05d" id)
            :code #fhir/CodeableConcept
                   {:coding
                    [#fhir/Coding{:system #fhir/uri "foo" :code #fhir/code "bar"}]}}])
   (range n)))

(deftest simple-job-execution-test
  (testing "success"
    (testing "increment every 10000 resources"
      (with-system-data [{:blaze/keys [job-scheduler] :as system} config]
        [(gen-tx-data 20001)]

        @(js/create-job job-scheduler job-clinical-code)

        (testing "the job is completed"
          (given @(jtu/pull-job system :completed)
            :fhir/type := :fhir/Task
            job-util/job-number := "1"
            jtu/combined-status := :completed
            total-resources := #fhir/unsignedInt 20001
            resources-processed := #fhir/unsignedInt 20001
            [processing-duration :value :fhir/type] := :fhir/decimal
            [processing-duration :value :value] :? #(and (decimal? %) (pos? %))
            [processing-duration :unit] := #fhir/string "s"
            [processing-duration :system] := #fhir/uri "http://unitsofmeasure.org"
            [processing-duration :code] := #fhir/code "s"
            next-resource := nil))

        (testing "job history"
          (given @(jtu/pull-job-history system)
            count := 5

            [0 jtu/combined-status] := :ready
            [1 jtu/combined-status] := :in-progress/started
            [2 jtu/combined-status] := :in-progress/incremented
            [3 jtu/combined-status] := :in-progress/incremented
            [4 jtu/combined-status] := :completed

            [0 total-resources] := nil
            [1 total-resources] := #fhir/unsignedInt 20001
            [2 total-resources] := #fhir/unsignedInt 20001
            [3 total-resources] := #fhir/unsignedInt 20001
            [4 total-resources] := #fhir/unsignedInt 20001

            [0 resources-processed] := nil
            [1 resources-processed] := #fhir/unsignedInt 0
            [2 resources-processed] := #fhir/unsignedInt 10000
            [3 resources-processed] := #fhir/unsignedInt 20000
            [4 resources-processed] := #fhir/unsignedInt 20001

            [0 next-resource] := nil
            [1 next-resource] := nil
            [2 next-resource] := "Observation/10000"
            [3 next-resource] := "Observation/20000"
            [4 next-resource] := nil))))

    (testing "increment never"
      (with-system-data [{:blaze/keys [job-scheduler] :as system} never-increment-config]
        [(gen-tx-data 20001)]

        @(js/create-job job-scheduler job-clinical-code)

        (testing "the job is completed"
          (given @(jtu/pull-job system :completed)
            :fhir/type := :fhir/Task
            job-util/job-number := "1"
            jtu/combined-status := :completed
            total-resources := #fhir/unsignedInt 20001
            resources-processed := #fhir/unsignedInt 20001
            [processing-duration :value :fhir/type] := :fhir/decimal
            [processing-duration :value :value] :? #(and (decimal? %) (pos? %))
            [processing-duration :unit] := #fhir/string "s"
            [processing-duration :system] := #fhir/uri "http://unitsofmeasure.org"
            [processing-duration :code] := #fhir/code "s"))

        (testing "job history"
          (given @(jtu/pull-job-history system)
            count := 3

            [0 jtu/combined-status] := :ready
            [1 jtu/combined-status] := :in-progress/started
            [2 jtu/combined-status] := :completed

            [0 total-resources] := nil
            [1 total-resources] := #fhir/unsignedInt 20001
            [2 total-resources] := #fhir/unsignedInt 20001

            [0 resources-processed] := nil
            [1 resources-processed] := #fhir/unsignedInt 0
            [2 resources-processed] := #fhir/unsignedInt 20001

            [0 next-resource] := nil
            [1 next-resource] := nil
            [2 next-resource] := nil)))))

  (testing "missing search param URL"
    (with-system [{:blaze/keys [job-scheduler] :as system} config]

      @(js/create-job job-scheduler job-missing-search-param)

      (testing "the job has failed"
        (given @(jtu/pull-job system :failed)
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          jtu/combined-status := :failed
          job-util/error-msg := "Missing search parameter URL."))))

  (testing "unknown search param URL"
    (with-system [{:blaze/keys [job-scheduler] :as system} config]

      @(js/create-job job-scheduler job-unknown-search-param)

      (testing "the job has failed"
        (given @(jtu/pull-job system :failed)
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          jtu/combined-status := :failed
          job-util/error-msg := "Search parameter with URL `unknown` not found."))))

  (testing "failing indexing at first re-index call"
    (with-redefs
     [d/re-index
      (fn [_db _search-param-url]
        (ac/completed-future (ba/fault "mock error")))]
      (with-system-data [{:blaze/keys [job-scheduler] :as system} config]
        [(gen-tx-data 20001)]

        @(js/create-job job-scheduler job-clinical-code)

        (testing "the job has failed"
          (given @(jtu/pull-job system :failed)
            :fhir/type := :fhir/Task
            job-util/job-number := "1"
            jtu/combined-status := :failed
            job-util/error-msg := "mock error"))

        (testing "job history"
          (given @(jtu/pull-job-history system)
            count := 3

            [0 jtu/combined-status] := :ready
            [1 jtu/combined-status] := :in-progress/started
            [2 jtu/combined-status] := :failed

            [0 total-resources] := nil
            [1 total-resources] := #fhir/unsignedInt 20001
            [2 total-resources] := #fhir/unsignedInt 20001

            [0 resources-processed] := nil
            [1 resources-processed] := #fhir/unsignedInt 0
            [2 resources-processed] := #fhir/unsignedInt 0)))))

  (testing "failing indexing at second re-index call"
    (with-redefs
     [d/re-index
      (fn
        ([db search-param-url]
         (p/-re-index db search-param-url))
        ([_db _search-param-url _start-type _start-id]
         (ac/completed-future (ba/fault "mock error"))))]
      (with-system-data [{:blaze/keys [job-scheduler] :as system} config]
        [(gen-tx-data 20001)]

        @(js/create-job job-scheduler job-clinical-code)

        (testing "the job has failed"
          (given @(jtu/pull-job system :failed)
            :fhir/type := :fhir/Task
            job-util/job-number := "1"
            jtu/combined-status := :failed
            job-util/error-msg := "mock error"))

        (testing "job history"
          (given @(jtu/pull-job-history system)
            count := 4

            [0 jtu/combined-status] := :ready
            [1 jtu/combined-status] := :in-progress/started
            [2 jtu/combined-status] := :in-progress/incremented
            [3 jtu/combined-status] := :failed

            [0 total-resources] := nil
            [1 total-resources] := #fhir/unsignedInt 20001
            [2 total-resources] := #fhir/unsignedInt 20001
            [3 total-resources] := #fhir/unsignedInt 20001

            [0 resources-processed] := nil
            [1 resources-processed] := #fhir/unsignedInt 0
            [2 resources-processed] := #fhir/unsignedInt 10000
            [3 resources-processed] := #fhir/unsignedInt 10000))))))

(deftest job-execution-with-pause-test
  (testing "resume from started state"
    (with-system-data [{:blaze/keys [job-scheduler] :as system} never-increment-config]
      [(gen-tx-data 60001)]

      @(js/create-job job-scheduler job-clinical-code)

      @(jtu/pull-job system :in-progress/started)

      (given @(js/pause-job job-scheduler (job-id job-scheduler))
        :fhir/type := :fhir/Task
        job-util/job-number := "1"
        jtu/combined-status := :on-hold/paused)

      (given @(js/resume-job job-scheduler (job-id job-scheduler))
        :fhir/type := :fhir/Task
        job-util/job-number := "1"
        jtu/combined-status := :in-progress/resumed)

      (testing "the job is completed"
        (given @(jtu/pull-job system :completed)
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          jtu/combined-status := :completed
          total-resources := #fhir/unsignedInt 60001
          resources-processed := #fhir/unsignedInt 60001
          [processing-duration :value :fhir/type] := :fhir/decimal
          [processing-duration :value :value] :? #(and (decimal? %) (pos? %))
          [processing-duration :unit] := #fhir/string "s"
          [processing-duration :system] := #fhir/uri "http://unitsofmeasure.org"
          [processing-duration :code] := #fhir/code "s"))

      (testing "job history"
        (given @(jtu/pull-job-history system)
          count := 5

          [0 jtu/combined-status] := :ready
          [1 jtu/combined-status] := :in-progress/started
          [2 jtu/combined-status] := :on-hold/paused
          [3 jtu/combined-status] := :in-progress/resumed
          [4 jtu/combined-status] := :completed

          [0 total-resources] := nil
          [1 total-resources] := #fhir/unsignedInt 60001
          [2 total-resources] := #fhir/unsignedInt 60001
          [3 total-resources] := #fhir/unsignedInt 60001
          [4 total-resources] := #fhir/unsignedInt 60001

          [0 resources-processed] := nil
          [1 resources-processed] := #fhir/unsignedInt 0
          [2 resources-processed] := #fhir/unsignedInt 0
          [3 resources-processed] := #fhir/unsignedInt 0
          [4 resources-processed] := #fhir/unsignedInt 60001))))

  (testing "resume from incremented state"
    (with-system-data [{:blaze/keys [job-scheduler] :as system} config]
      [(gen-tx-data 60001)]

      @(js/create-job job-scheduler job-clinical-code)

      @(jtu/pull-job system :in-progress/incremented)

      (given @(js/pause-job job-scheduler (job-id job-scheduler))
        :fhir/type := :fhir/Task
        job-util/job-number := "1"
        jtu/combined-status := :on-hold/paused)

      (given @(js/resume-job job-scheduler (job-id job-scheduler))
        :fhir/type := :fhir/Task
        job-util/job-number := "1"
        jtu/combined-status := :in-progress/resumed)

      (testing "the job is completed"
        (given @(jtu/pull-job system :completed)
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          jtu/combined-status := :completed
          total-resources := #fhir/unsignedInt 60001
          resources-processed := #fhir/unsignedInt 60001
          [processing-duration :value :fhir/type] := :fhir/decimal
          [processing-duration :value :value] :? #(and (decimal? %) (pos? %))
          [processing-duration :unit] := #fhir/string "s"
          [processing-duration :system] := #fhir/uri "http://unitsofmeasure.org"
          [processing-duration :code] := #fhir/code "s"))

      (testing "job history"
        (given @(jtu/pull-job-history system)
          [0 jtu/combined-status] := :ready
          [1 jtu/combined-status] := :in-progress/started
          [2 jtu/combined-status] := :in-progress/incremented)))))
