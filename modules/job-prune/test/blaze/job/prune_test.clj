(ns blaze.job.prune-test
  (:require
   [blaze.db.api :as d]
   [blaze.db.api-spec]
   [blaze.db.kv :as kv]
   [blaze.db.kv.mem]
   [blaze.db.node :as node :refer [node?]]
   [blaze.db.node.protocols :as np]
   [blaze.db.resource-store :as rs]
   [blaze.db.resource-store.kv :as rs-kv]
   [blaze.db.search-param-registry]
   [blaze.db.tx-cache]
   [blaze.db.tx-log :as tx-log]
   [blaze.db.tx-log.local]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.job-scheduler :as js]
   [blaze.job.prune]
   [blaze.job.test-util :as jtu]
   [blaze.job.util :as job-util]
   [blaze.log]
   [blaze.luid :as luid]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu :refer [given-thrown]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [integrant.core :as ig]
   [java-time.api :as time]
   [juxt.iota :refer [given]]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze.job/prune nil})
      :key := :blaze.job/prune
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze.job/prune {}})
      :key := :blaze.job/prune
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :main-node))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :admin-node))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :clock))))

  (testing "invalid main-node"
    (given-thrown (ig/init {:blaze.job/prune {:main-node ::invalid}})
      :key := :blaze.job/prune
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :admin-node))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 2 :pred] := `node?
      [:cause-data ::s/problems 2 :val] := ::invalid)))

(derive :blaze.db.main/node :blaze.db/node)
(derive :blaze.db.admin/node :blaze.db/node)

(def config
  {:blaze/job-scheduler
   {:node (ig/ref :blaze.db.admin/node)
    :handlers {:blaze.job/prune (ig/ref :blaze.job/prune)}
    :clock (ig/ref :blaze.test/offset-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}

   :blaze.job/prune
   {:main-node (ig/ref :blaze.db.main/node)
    :admin-node (ig/ref :blaze.db.admin/node)
    :clock (ig/ref :blaze.test/offset-clock)
    :index-entries-per-step 10}

   :blaze.db.main/node
   {:tx-log (ig/ref :blaze.db.main/tx-log)
    :tx-cache (ig/ref :blaze.db.main/tx-cache)
    :indexer-executor (ig/ref :blaze.db.node.main/indexer-executor)
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
    :resource-store (ig/ref :blaze.db/resource-store)
    :kv-store (ig/ref :blaze.db.admin/index-kv-store)
    :resource-indexer (ig/ref :blaze.db.node.admin/resource-indexer)
    :search-param-registry (ig/ref :blaze.db/search-param-registry)
    :scheduler (ig/ref :blaze/scheduler)
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
    :executor (ig/ref ::rs-kv/executor)}

   [::kv/mem :blaze.db/resource-kv-store]
   {:column-families {}}

   ::rs-kv/executor {}

   :blaze.db/search-param-registry
   {:structure-definition-repo structure-definition-repo}

   :blaze/scheduler {}

   :blaze.test/fixed-clock {}

   :blaze.test/offset-clock
   {:clock (ig/ref :blaze.test/fixed-clock)
    :offset-seconds 11}

   :blaze.test/fixed-rng-fn {}})

(defmacro with-system-data
  [[binding-form config] txs & body]
  `(with-system [system# ~config]
     (run! #(deref (d/transact (:blaze.db.main/node system#) %)) ~txs)
     (let [~binding-form system#] ~@body)))

(def job
  {:fhir/type :fhir/Task
   :meta #fhir/Meta{:profile [#fhir/canonical"https://samply.github.io/blaze/fhir/StructureDefinition/PruneJob"]}
   :status #fhir/code"ready"
   :intent #fhir/code"order"
   :code #fhir/CodeableConcept
          {:coding
           [#fhir/Coding
             {:system #fhir/uri"https://samply.github.io/blaze/fhir/CodeSystem/JobType"
              :code #fhir/code"prune"
              :display "Prune the Database"}]}})

(def job-missing-t
  job)

(def job-42
  (assoc
   job
   :input
   [{:fhir/type :fhir.Task/input
     :type #fhir/CodeableConcept
            {:coding
             [#fhir/Coding
               {:system #fhir/uri"https://samply.github.io/blaze/fhir/CodeSystem/PruneJobParameter"
                :code #fhir/code"t"}]}
     :value #fhir/positiveInt 42}]))

(defn- output-value [job code]
  (job-util/output-value job "https://samply.github.io/blaze/fhir/CodeSystem/PruneJobOutput" code))

(defn- total-index-entries [job]
  (output-value job "total-index-entries"))

(defn- index-entries-processed [job]
  (output-value job "index-entries-processed"))

(defn- index-entries-deleted [job]
  (output-value job "index-entries-deleted"))

(defn- processing-duration [job]
  (output-value job "processing-duration"))

(defn- next-index [job]
  (output-value job "next-index"))

(defn- next-type [job]
  (output-value job "next-type"))

(defn- next-id [job]
  (output-value job "next-id"))

(defn- next-t [job]
  (output-value job "next-t"))

(defn- job-id [{{:keys [clock rng-fn]} :context}]
  (luid/luid clock (rng-fn)))

(defn gen-create-patient-tx-data [n]
  (mapv
   (fn [id]
     [:create {:fhir/type :fhir/Patient :id (format "%05d" id)}])
   (range n)))

(defn gen-patient-purge-tx-data [n]
  (mapv
   (fn [id]
     [:patient-purge (format "%05d" id)])
   (range n)))

(deftest simple-job-execution-test
  (testing "success"
    (testing "increment three times, once for each index"
      (with-system-data [{:blaze/keys [job-scheduler] :as system} config]
        [(gen-create-patient-tx-data 10)
         (gen-patient-purge-tx-data 5)]

        @(js/create-job job-scheduler job-42)

        (testing "the job is completed"
          (given @(jtu/pull-job system :completed)
            :fhir/type := :fhir/Task
            job-util/job-number := "1"
            jtu/combined-status := :completed
            total-index-entries := #fhir/unsignedInt 30
            index-entries-processed := #fhir/unsignedInt 30
            index-entries-deleted := #fhir/unsignedInt 15
            [processing-duration :value] :? pos?
            [processing-duration :unit] := #fhir/string"s"
            [processing-duration :system] := #fhir/uri"http://unitsofmeasure.org"
            [processing-duration :code] := #fhir/code"s"
            next-index := nil))

        (testing "job history"
          (given @(jtu/pull-job-history system)
            count := 5

            [0 jtu/combined-status] := :ready
            [1 jtu/combined-status] := :in-progress/started
            [2 jtu/combined-status] := :in-progress/incremented
            [3 jtu/combined-status] := :in-progress/incremented
            [4 jtu/combined-status] := :completed

            [0 total-index-entries] := nil
            [1 total-index-entries] := #fhir/unsignedInt 30
            [2 total-index-entries] := #fhir/unsignedInt 30
            [3 total-index-entries] := #fhir/unsignedInt 30
            [4 total-index-entries] := #fhir/unsignedInt 30

            [0 index-entries-processed] := nil
            [1 index-entries-processed] := #fhir/unsignedInt 0
            [2 index-entries-processed] := #fhir/unsignedInt 10
            [3 index-entries-processed] := #fhir/unsignedInt 20
            [4 index-entries-processed] := #fhir/unsignedInt 30

            [0 index-entries-deleted] := nil
            [1 index-entries-deleted] := #fhir/unsignedInt 0
            [2 index-entries-deleted] := #fhir/unsignedInt 5
            [3 index-entries-deleted] := #fhir/unsignedInt 10
            [4 index-entries-deleted] := #fhir/unsignedInt 15

            [0 next-index] := nil
            [1 next-index] := nil
            [2 next-index] := #fhir/code"type-as-of-index"
            [3 next-index] := #fhir/code"system-as-of-index"
            [4 next-index] := nil

            [0 next-type] := nil
            [1 next-type] := nil
            [2 next-type] := nil
            [3 next-type] := nil
            [4 next-type] := nil))))

    (testing "increment six times, twice for each index"
      (with-system-data [{:blaze/keys [job-scheduler] :as system} config]
        [(gen-create-patient-tx-data 20)
         (gen-patient-purge-tx-data 10)]

        @(js/create-job job-scheduler job-42)

        (testing "the job is completed"
          (given @(jtu/pull-job system :completed)
            :fhir/type := :fhir/Task
            job-util/job-number := "1"
            jtu/combined-status := :completed
            total-index-entries := #fhir/unsignedInt 60
            index-entries-processed := #fhir/unsignedInt 60
            index-entries-deleted := #fhir/unsignedInt 30
            [processing-duration :value] :? pos?
            [processing-duration :unit] := #fhir/string"s"
            [processing-duration :system] := #fhir/uri"http://unitsofmeasure.org"
            [processing-duration :code] := #fhir/code"s"
            next-index := nil))

        (testing "job history"
          (given @(jtu/pull-job-history system)
            count := 8

            [0 jtu/combined-status] := :ready
            [1 jtu/combined-status] := :in-progress/started
            [2 jtu/combined-status] := :in-progress/incremented
            [3 jtu/combined-status] := :in-progress/incremented
            [4 jtu/combined-status] := :in-progress/incremented
            [5 jtu/combined-status] := :in-progress/incremented
            [6 jtu/combined-status] := :in-progress/incremented
            [7 jtu/combined-status] := :completed

            [0 total-index-entries] := nil
            [1 total-index-entries] := #fhir/unsignedInt 60
            [2 total-index-entries] := #fhir/unsignedInt 60
            [3 total-index-entries] := #fhir/unsignedInt 60
            [4 total-index-entries] := #fhir/unsignedInt 60
            [5 total-index-entries] := #fhir/unsignedInt 60
            [6 total-index-entries] := #fhir/unsignedInt 60
            [7 total-index-entries] := #fhir/unsignedInt 60

            [0 index-entries-processed] := nil
            [1 index-entries-processed] := #fhir/unsignedInt 0
            [2 index-entries-processed] := #fhir/unsignedInt 10
            [3 index-entries-processed] := #fhir/unsignedInt 20
            [4 index-entries-processed] := #fhir/unsignedInt 30
            [5 index-entries-processed] := #fhir/unsignedInt 40
            [6 index-entries-processed] := #fhir/unsignedInt 50
            [7 index-entries-processed] := #fhir/unsignedInt 60

            [0 index-entries-deleted] := nil
            [1 index-entries-deleted] := #fhir/unsignedInt 0
            [2 index-entries-deleted] := #fhir/unsignedInt 10
            [3 index-entries-deleted] := #fhir/unsignedInt 10
            [4 index-entries-deleted] := #fhir/unsignedInt 20
            [5 index-entries-deleted] := #fhir/unsignedInt 20
            [6 index-entries-deleted] := #fhir/unsignedInt 30
            [7 index-entries-deleted] := #fhir/unsignedInt 30

            [0 next-index] := nil
            [1 next-index] := nil
            [2 next-index] := #fhir/code"resource-as-of-index"
            [3 next-index] := #fhir/code"type-as-of-index"
            [4 next-index] := #fhir/code"type-as-of-index"
            [5 next-index] := #fhir/code"system-as-of-index"
            [6 next-index] := #fhir/code"system-as-of-index"
            [7 next-index] := nil

            [0 next-type] := nil
            [1 next-type] := nil
            [2 next-type] := #fhir/code"Patient"
            [3 next-type] := nil
            [4 next-type] := #fhir/code"Patient"
            [5 next-type] := nil
            [6 next-type] := #fhir/code"Patient"
            [7 next-type] := nil

            [0 next-id] := nil
            [1 next-id] := nil
            [2 next-id] := #fhir/id"00010"
            [3 next-id] := nil
            [4 next-id] := #fhir/id"00010"
            [5 next-id] := nil
            [6 next-id] := #fhir/id"00010"
            [7 next-id] := nil

            [0 next-t] := nil
            [1 next-t] := nil
            [2 next-t] := #fhir/positiveInt 1
            [3 next-t] := nil
            [4 next-t] := #fhir/positiveInt 1
            [5 next-t] := nil
            [6 next-t] := #fhir/positiveInt 1
            [7 next-t] := nil)))))

  (testing "missing t"
    (with-system [{:blaze/keys [job-scheduler] :as system} config]

      @(js/create-job job-scheduler job-missing-t)

      (testing "the job has failed"
        (given @(jtu/pull-job system :failed)
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          jtu/combined-status := :failed
          job-util/error-msg := "Missing T.")))))

(defn- delayed-prune
  ([node n t]
   (Thread/sleep 100)
   (np/-prune node n t nil))
  ([node n t start]
   (Thread/sleep 100)
   (np/-prune node n t start)))

(deftest job-execution-with-pause-test
  (with-redefs [d/prune delayed-prune]
    (testing "resume from started state"
      (with-system-data [{:blaze/keys [job-scheduler] :as system} config]
        [(gen-create-patient-tx-data 10)
         (gen-patient-purge-tx-data 5)]

        @(js/create-job job-scheduler job-42)

        (given @(jtu/pull-job system :in-progress/started)
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          total-index-entries := #fhir/unsignedInt 30
          index-entries-processed := #fhir/unsignedInt 0
          index-entries-deleted := #fhir/unsignedInt 0)

        (given @(js/pause-job job-scheduler (job-id job-scheduler))
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          jtu/combined-status := :on-hold/paused)

        (Thread/sleep 200)

        (given @(js/resume-job job-scheduler (job-id job-scheduler))
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          jtu/combined-status := :in-progress/resumed)

        (testing "the job is completed"
          (given @(jtu/pull-job system :completed)
            :fhir/type := :fhir/Task
            job-util/job-number := "1"
            jtu/combined-status := :completed
            total-index-entries := #fhir/unsignedInt 30
            index-entries-processed := #fhir/unsignedInt 30
            index-entries-deleted := #fhir/unsignedInt 15
            [processing-duration :value] :? pos?
            [processing-duration :unit] := #fhir/string"s"
            [processing-duration :system] := #fhir/uri"http://unitsofmeasure.org"
            [processing-duration :code] := #fhir/code"s"))

        (testing "job history"
          (given @(jtu/pull-job-history system)
            count := 7

            [0 jtu/combined-status] := :ready
            [1 jtu/combined-status] := :in-progress/started
            [2 jtu/combined-status] := :on-hold/paused
            [3 jtu/combined-status] := :on-hold/paused
            [4 jtu/combined-status] := :in-progress/resumed
            [5 jtu/combined-status] := :in-progress/incremented
            [6 jtu/combined-status] := :completed

            [0 total-index-entries] := nil
            [1 total-index-entries] := #fhir/unsignedInt 30
            [2 total-index-entries] := #fhir/unsignedInt 30
            [3 total-index-entries] := #fhir/unsignedInt 30
            [4 total-index-entries] := #fhir/unsignedInt 30
            [5 total-index-entries] := #fhir/unsignedInt 30
            [6 total-index-entries] := #fhir/unsignedInt 30

            [0 index-entries-processed] := nil
            [1 index-entries-processed] := #fhir/unsignedInt 0
            [2 index-entries-processed] := #fhir/unsignedInt 0
            [3 index-entries-processed] := #fhir/unsignedInt 10
            [4 index-entries-processed] := #fhir/unsignedInt 10
            [5 index-entries-processed] := #fhir/unsignedInt 20
            [6 index-entries-processed] := #fhir/unsignedInt 30

            [0 index-entries-deleted] := nil
            [1 index-entries-deleted] := #fhir/unsignedInt 0
            [2 index-entries-deleted] := #fhir/unsignedInt 0
            [3 index-entries-deleted] := #fhir/unsignedInt 5
            [4 index-entries-deleted] := #fhir/unsignedInt 5
            [5 index-entries-deleted] := #fhir/unsignedInt 10
            [6 index-entries-deleted] := #fhir/unsignedInt 15)))

      (testing "increment six times, twice for each index"
        (with-system-data [{:blaze/keys [job-scheduler] :as system} config]
          [(gen-create-patient-tx-data 20)
           (gen-patient-purge-tx-data 10)]

          @(js/create-job job-scheduler job-42)

          (given @(jtu/pull-job system :in-progress/started)
            :fhir/type := :fhir/Task
            job-util/job-number := "1"
            total-index-entries := #fhir/unsignedInt 60
            index-entries-processed := #fhir/unsignedInt 0
            index-entries-deleted := #fhir/unsignedInt 0)

          (given @(js/pause-job job-scheduler (job-id job-scheduler))
            :fhir/type := :fhir/Task
            job-util/job-number := "1"
            jtu/combined-status := :on-hold/paused
            index-entries-processed := #fhir/unsignedInt 0
            index-entries-deleted := #fhir/unsignedInt 0)

          (Thread/sleep 200)

          (given @(js/resume-job job-scheduler (job-id job-scheduler))
            :fhir/type := :fhir/Task
            job-util/job-number := "1"
            jtu/combined-status := :in-progress/resumed
            index-entries-processed := #fhir/unsignedInt 10
            index-entries-deleted := #fhir/unsignedInt 10)

          (testing "the job is completed"
            (given @(jtu/pull-job system :completed)
              :fhir/type := :fhir/Task
              job-util/job-number := "1"
              jtu/combined-status := :completed
              total-index-entries := #fhir/unsignedInt 60
              index-entries-processed := #fhir/unsignedInt 60
              index-entries-deleted := #fhir/unsignedInt 30
              [processing-duration :value] :? pos?
              [processing-duration :unit] := #fhir/string"s"
              [processing-duration :system] := #fhir/uri"http://unitsofmeasure.org"
              [processing-duration :code] := #fhir/code"s"))

          (testing "job history"
            (given @(jtu/pull-job-history system)
              count := 10

              [0 jtu/combined-status] := :ready
              [1 jtu/combined-status] := :in-progress/started
              [2 jtu/combined-status] := :on-hold/paused
              [3 jtu/combined-status] := :on-hold/paused
              [4 jtu/combined-status] := :in-progress/resumed
              [5 jtu/combined-status] := :in-progress/incremented
              [6 jtu/combined-status] := :in-progress/incremented
              [7 jtu/combined-status] := :in-progress/incremented
              [8 jtu/combined-status] := :in-progress/incremented
              [9 jtu/combined-status] := :completed

              [0 total-index-entries] := nil
              [1 total-index-entries] := #fhir/unsignedInt 60
              [2 total-index-entries] := #fhir/unsignedInt 60
              [3 total-index-entries] := #fhir/unsignedInt 60
              [4 total-index-entries] := #fhir/unsignedInt 60
              [5 total-index-entries] := #fhir/unsignedInt 60
              [6 total-index-entries] := #fhir/unsignedInt 60
              [7 total-index-entries] := #fhir/unsignedInt 60
              [8 total-index-entries] := #fhir/unsignedInt 60
              [9 total-index-entries] := #fhir/unsignedInt 60

              [0 index-entries-processed] := nil
              [1 index-entries-processed] := #fhir/unsignedInt 0
              [2 index-entries-processed] := #fhir/unsignedInt 0
              [3 index-entries-processed] := #fhir/unsignedInt 10
              [4 index-entries-processed] := #fhir/unsignedInt 10
              [5 index-entries-processed] := #fhir/unsignedInt 20
              [6 index-entries-processed] := #fhir/unsignedInt 30
              [7 index-entries-processed] := #fhir/unsignedInt 40
              [8 index-entries-processed] := #fhir/unsignedInt 50
              [9 index-entries-processed] := #fhir/unsignedInt 60

              [0 index-entries-deleted] := nil
              [1 index-entries-deleted] := #fhir/unsignedInt 0
              [2 index-entries-deleted] := #fhir/unsignedInt 0
              [3 index-entries-deleted] := #fhir/unsignedInt 10
              [4 index-entries-deleted] := #fhir/unsignedInt 10
              [5 index-entries-deleted] := #fhir/unsignedInt 10
              [6 index-entries-deleted] := #fhir/unsignedInt 20
              [7 index-entries-deleted] := #fhir/unsignedInt 20
              [8 index-entries-deleted] := #fhir/unsignedInt 30
              [9 index-entries-deleted] := #fhir/unsignedInt 30)))))

    (testing "resume from incremented state"
      (with-system-data [{:blaze/keys [job-scheduler] :as system} config]
        [(gen-create-patient-tx-data 10)
         (gen-patient-purge-tx-data 5)]

        @(js/create-job job-scheduler job-42)

        (given @(jtu/pull-job system :in-progress/incremented)
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          total-index-entries := #fhir/unsignedInt 30
          index-entries-processed := #fhir/unsignedInt 10
          index-entries-deleted := #fhir/unsignedInt 5)

        (given @(js/pause-job job-scheduler (job-id job-scheduler))
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          jtu/combined-status := :on-hold/paused)

        (Thread/sleep 200)

        (given @(js/resume-job job-scheduler (job-id job-scheduler))
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          jtu/combined-status := :in-progress/resumed)

        (testing "the job is completed"
          (given @(jtu/pull-job system :completed)
            :fhir/type := :fhir/Task
            job-util/job-number := "1"
            jtu/combined-status := :completed
            total-index-entries := #fhir/unsignedInt 30
            index-entries-processed := #fhir/unsignedInt 30
            index-entries-deleted := #fhir/unsignedInt 15
            [processing-duration :value] :? pos?
            [processing-duration :unit] := #fhir/string"s"
            [processing-duration :system] := #fhir/uri"http://unitsofmeasure.org"
            [processing-duration :code] := #fhir/code"s"))

        (testing "job history"
          (given @(jtu/pull-job-history system)
            count := 7

            [0 jtu/combined-status] := :ready
            [1 jtu/combined-status] := :in-progress/started
            [2 jtu/combined-status] := :in-progress/incremented
            [3 jtu/combined-status] := :on-hold/paused
            [4 jtu/combined-status] := :on-hold/paused
            [5 jtu/combined-status] := :in-progress/resumed
            [6 jtu/combined-status] := :completed)))

      (testing "increment six times, twice for each index"
        (with-system-data [{:blaze/keys [job-scheduler] :as system} config]
          [(gen-create-patient-tx-data 20)
           (gen-patient-purge-tx-data 10)]

          @(js/create-job job-scheduler job-42)

          (given @(jtu/pull-job system :in-progress/incremented)
            :fhir/type := :fhir/Task
            job-util/job-number := "1"
            total-index-entries := #fhir/unsignedInt 60
            index-entries-processed := #fhir/unsignedInt 10
            index-entries-deleted := #fhir/unsignedInt 10)

          (given @(js/pause-job job-scheduler (job-id job-scheduler))
            :fhir/type := :fhir/Task
            job-util/job-number := "1"
            jtu/combined-status := :on-hold/paused)

          (Thread/sleep 200)

          (given @(js/resume-job job-scheduler (job-id job-scheduler))
            :fhir/type := :fhir/Task
            job-util/job-number := "1"
            jtu/combined-status := :in-progress/resumed)

          (testing "the job is completed"
            (given @(jtu/pull-job system :completed)
              :fhir/type := :fhir/Task
              job-util/job-number := "1"
              jtu/combined-status := :completed
              total-index-entries := #fhir/unsignedInt 60
              index-entries-processed := #fhir/unsignedInt 60
              index-entries-deleted := #fhir/unsignedInt 30
              [processing-duration :value] :? pos?
              [processing-duration :unit] := #fhir/string"s"
              [processing-duration :system] := #fhir/uri"http://unitsofmeasure.org"
              [processing-duration :code] := #fhir/code"s"))

          (testing "job history"
            (given @(jtu/pull-job-history system)
              count := 10

              [0 jtu/combined-status] := :ready
              [1 jtu/combined-status] := :in-progress/started
              [2 jtu/combined-status] := :in-progress/incremented
              [3 jtu/combined-status] := :on-hold/paused
              [4 jtu/combined-status] := :on-hold/paused
              [5 jtu/combined-status] := :in-progress/resumed
              [6 jtu/combined-status] := :in-progress/incremented
              [7 jtu/combined-status] := :in-progress/incremented
              [8 jtu/combined-status] := :in-progress/incremented
              [9 jtu/combined-status] := :completed

              [0 index-entries-processed] := nil
              [1 index-entries-processed] := #fhir/unsignedInt 0
              [2 index-entries-processed] := #fhir/unsignedInt 10
              [3 index-entries-processed] := #fhir/unsignedInt 10
              [4 index-entries-processed] := #fhir/unsignedInt 20
              [5 index-entries-processed] := #fhir/unsignedInt 20
              [6 index-entries-processed] := #fhir/unsignedInt 30
              [7 index-entries-processed] := #fhir/unsignedInt 40
              [8 index-entries-processed] := #fhir/unsignedInt 50
              [9 index-entries-processed] := #fhir/unsignedInt 60

              [0 next-index] := nil
              [1 next-index] := nil
              [2 next-index] := #fhir/code"resource-as-of-index"
              [3 next-index] := #fhir/code"resource-as-of-index"
              [4 next-index] := #fhir/code"type-as-of-index"
              [5 next-index] := #fhir/code"type-as-of-index"
              [6 next-index] := #fhir/code"type-as-of-index"
              [7 next-index] := #fhir/code"system-as-of-index"
              [8 next-index] := #fhir/code"system-as-of-index"
              [9 next-index] := nil

              [0 next-type] := nil
              [1 next-type] := nil
              [2 next-type] := #fhir/code"Patient"
              [3 next-type] := #fhir/code"Patient"
              [4 next-type] := nil
              [5 next-type] := nil
              [6 next-type] := #fhir/code"Patient"
              [7 next-type] := nil
              [8 next-type] := #fhir/code"Patient"
              [9 next-type] := nil)))))))
