(ns blaze.job-scheduler-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.api-spec]
   [blaze.db.kv :as kv]
   [blaze.db.kv.mem]
   [blaze.db.node :as node]
   [blaze.db.resource-store :as rs]
   [blaze.db.resource-store.kv :as rs-kv]
   [blaze.db.search-param-registry]
   [blaze.db.search-param-registry-spec]
   [blaze.db.spec]
   [blaze.db.tx-cache]
   [blaze.db.tx-log :as tx-log]
   [blaze.db.tx-log.local]
   [blaze.fhir.parsing-context]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.fhir.writing-context]
   [blaze.job-scheduler :as js]
   [blaze.job-scheduler-spec]
   [blaze.job-scheduler.protocols :as p]
   [blaze.job.test-util :as jtu]
   [blaze.job.util :as job-util]
   [blaze.module.test-util :as mtu :refer [given-failed-future given-failed-system with-system]]
   [blaze.spec]
   [blaze.terminology-service :as-alias ts]
   [blaze.terminology-service.not-available]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [java-time.api :as time]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [java.util.concurrent TimeUnit]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(derive :blaze.db.main/node :blaze.db/node)
(derive :blaze.db.admin/node :blaze.db/node)

(defn- start-job [job]
  (assoc
   job
   :status #fhir/code "in-progress"
   :statusReason job-util/started-status-reason))

(defn- finish-job [job]
  (-> (assoc job :status #fhir/code "completed")
      (dissoc :statusReason)))

(defn- async-delay [x]
  (let [future (ac/future)]
    (ac/complete-on-timeout! future x 150 TimeUnit/MILLISECONDS)
    future))

(defn- finish-cancellation [job]
  (assoc job :businessStatus job-util/cancellation-finished-sub-status))

(defmethod ig/init-key :blaze.job/test
  [_ {:keys [admin-node]}]
  (let [delays (atom {})]
    (reify p/JobHandler
      (-on-start [_ job]
        (-> (job-util/update-job admin-node job start-job)
            (ac/then-compose
             (fn [{:keys [id] :as job}]
               (let [delay (async-delay job)]
                 (swap! delays assoc id delay)
                 (-> delay
                     (ac/then-compose
                      #(job-util/update-job admin-node % finish-job))
                     (ac/exceptionally-compose
                      (fn [e]
                        (if (ba/interrupted? e)
                          (-> (job-util/pull-job admin-node id)
                              (ac/then-compose
                               #(job-util/update-job admin-node % finish-cancellation)))
                          e)))))))))
      (-on-resume [_ job]
        (-> (async-delay job)
            (ac/then-compose #(job-util/update-job admin-node % finish-job))))
      (-on-cancel [_ job]
        (ac/cancel! (get @delays (:id job)))
        (ac/completed-future job)))))

(defmethod ig/init-key :blaze.job/error
  [_ _]
  (reify p/JobHandler
    (-on-start [_ _]
      (ac/completed-future (ba/fault "error-150651")))))

(defmethod ig/init-key :blaze.job/throws-error
  [_ _]
  (reify p/JobHandler
    (-on-start [_ _]
      (throw (Exception.)))))

(def config
  {:blaze/job-scheduler
   {:node (ig/ref :blaze.db.admin/node)
    :handlers
    {:blaze.job/test (ig/ref :blaze.job/test)
     :blaze.job/error (ig/ref :blaze.job/error)
     :blaze.job/throws-error (ig/ref :blaze.job/throws-error)}
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/incrementing-rng-fn)}

   :blaze.job/test
   {:admin-node (ig/ref :blaze.db.admin/node)}

   :blaze.job/error {}
   :blaze.job/throws-error {}

   :blaze.db.main/node
   {:tx-log (ig/ref :blaze.db.main/tx-log)
    :tx-cache (ig/ref :blaze.db.admin/tx-cache)
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
   {:structure-definition-repo structure-definition-repo
    :terminology-service (ig/ref ::ts/not-available)}

   ::ts/not-available {}

   [:blaze.fhir/parsing-context :blaze.fhir.parsing-context/resource-store]
   {:structure-definition-repo structure-definition-repo
    :fail-on-unknown-property false
    :include-summary-only true
    :use-regex false}

   :blaze.fhir/writing-context
   {:structure-definition-repo structure-definition-repo}

   :blaze/scheduler {}

   :blaze.test/fixed-clock {}

   :blaze.test/incrementing-rng-fn {}})

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze/job-scheduler nil}
      :key := :blaze/job-scheduler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze/job-scheduler {}}
      :key := :blaze/job-scheduler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))))

  (testing "invalid node"
    (given-failed-system (assoc-in config [:blaze/job-scheduler :node] ::invalid)
      :key := :blaze/job-scheduler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/node]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid clock"
    (given-failed-system (assoc-in config [:blaze/job-scheduler :clock] ::invalid)
      :key := :blaze/job-scheduler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/clock]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid rng-fn"
    (given-failed-system (assoc-in config [:blaze/job-scheduler :rng-fn] ::invalid)
      :key := :blaze/job-scheduler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/rng-fn]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "success"
    (with-system [{:blaze/keys [job-scheduler]} config]
      (is (s/valid? :blaze/job-scheduler job-scheduler)))))

(defn- job-type [type]
  (type/codeable-concept
   {:coding
    [(type/coding
      {:system (type/uri job-util/type-url)
       :code (type/code type)})]}))

(defn- ready-job [type]
  {:fhir/type :fhir/Task
   :status #fhir/code "ready"
   :code (job-type type)})

(defn bundle-referencing-job [bundle-id]
  {:fhir/type :fhir/Task
   :status #fhir/code "ready"
   :code (job-type "async-interaction")
   :input
   [{:fhir/type :fhir.Task/input
     :type #fhir/CodeableConcept
            {:coding
             [#fhir/Coding
               {:system #fhir/uri "https://samply.github.io/blaze/fhir/CodeSystem/AsyncInteractionJobParameter"
                :code #fhir/code "bundle"}]}
     :value (type/reference {:reference (type/string (str "Bundle/" bundle-id))})}]})

(defn bundle [id]
  {:fhir/type :fhir/Bundle
   :id id
   :type #fhir/code "batch"})

(def ^:private job-id "AAAAAAAAAAAAAAAB")

(defn- bundle-input [job]
  (job-util/input-value job "https://samply.github.io/blaze/fhir/CodeSystem/AsyncInteractionJobParameter" "bundle"))

(deftest create-job-test
  (testing "test job"
    (with-system [{:blaze/keys [job-scheduler] :as system} config]

      (testing "the job is created as ready"
        (given @(mtu/assoc-thread-name (js/create-job job-scheduler (ready-job "test")))
          [meta :thread-name] :? mtu/common-pool-thread?
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          jtu/combined-status := :ready
          job-util/job-type := :test))

      (testing "the job is in-progress"
        (given @(jtu/pull-job system job-id :in-progress/started)
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          jtu/combined-status := :in-progress/started
          job-util/job-type := :test))

      (testing "the job is completed"
        (given @(jtu/pull-job system job-id :completed)
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          jtu/combined-status := :completed
          job-util/job-type := :test))

      (testing "job history"
        (given @(jtu/pull-job-history system job-id)
          count := 3

          [0 jtu/combined-status] := :ready
          [1 jtu/combined-status] := :in-progress/started
          [2 jtu/combined-status] := :completed))))

  (testing "create a second job"
    (with-system [{:blaze/keys [job-scheduler]} config]

      @(js/create-job job-scheduler (ready-job "test"))

      (testing "the job number is 2"
        (given @(js/create-job job-scheduler (ready-job "test"))
          :fhir/type := :fhir/Task
          job-util/job-number := "2"
          jtu/combined-status := :ready
          job-util/job-type := :test))))

  (testing "job fails if the job handler isn't found"
    (with-system [{:blaze/keys [job-scheduler] :as system} config]

      @(js/create-job job-scheduler (ready-job "unknown-142504"))

      (testing "the job has failed"
        (given @(jtu/pull-job system job-id :failed)
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          jtu/combined-status := :failed
          job-util/job-type := :unknown-142504
          job-util/error-msg := "Failed to start because the implementation is missing."))))

  (testing "job fails because the execution errored"
    (with-system [{:blaze/keys [job-scheduler] :as system} config]

      @(js/create-job job-scheduler (ready-job "error"))

      (testing "the job has failed"
        (given @(jtu/pull-job system job-id :failed)
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          jtu/combined-status := :failed
          job-util/job-type := :error
          job-util/error-msg := "error-150651"))))

  (testing "job fails because the execution throws an exception"
    (with-system [{:blaze/keys [job-scheduler] :as system} config]

      @(js/create-job job-scheduler (ready-job "throws-error"))

      (testing "the job has failed"
        (given @(jtu/pull-job system job-id :failed)
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          jtu/combined-status := :failed
          job-util/job-type := :throws-error
          job-util/error-msg := "empty error message"))))

  (testing "job with referenced resource"
    (with-system [{:blaze/keys [job-scheduler] :blaze.db.admin/keys [node] :as system} config]

      (let [bundle-id "115607"]
        @(js/create-job job-scheduler (bundle-referencing-job bundle-id)
                        (bundle bundle-id))

        (testing "the job is ready"
          (given @(jtu/pull-job system job-id :ready)
            :fhir/type := :fhir/Task
            job-util/job-number := "1"
            jtu/combined-status := :ready
            bundle-input := (type/reference {:reference (type/string (str "Bundle/" bundle-id))})))

        (testing "the bundle is created"
          (given @(d/pull node (d/resource-handle (d/db node) "Bundle" bundle-id))
            :fhir/type := :fhir/Bundle
            :type := #fhir/code "batch"))))))

(defn- in-progress-job [type]
  {:fhir/type :fhir/Task
   :status #fhir/code "in-progress"
   :statusReason job-util/started-status-reason
   :code (job-type type)})

(defn- completed-job [type]
  {:fhir/type :fhir/Task
   :status #fhir/code "completed"
   :code (job-type type)})

(defn- failed-job [type]
  {:fhir/type :fhir/Task
   :status #fhir/code "failed"
   :code (job-type type)})

(defn- cancellation-requested-job [type]
  {:fhir/type :fhir/Task
   :status #fhir/code "cancelled"
   :businessStatus job-util/cancellation-requested-sub-status
   :code (job-type type)})

(defn- cancellation-finished-job [type]
  {:fhir/type :fhir/Task
   :status #fhir/code "cancelled"
   :businessStatus job-util/cancellation-finished-sub-status
   :code (job-type type)})

(deftest cancel-job-test
  (testing "works while job is in-progress"
    (with-system [{:blaze/keys [job-scheduler] :as system} config]

      @(js/create-job job-scheduler (ready-job "test"))

      @(jtu/pull-job system job-id :in-progress/started)

      (given @(mtu/assoc-thread-name (js/cancel-job job-scheduler job-id))
        [meta :thread-name] :? mtu/common-pool-thread?
        :fhir/type := :fhir/Task
        job-util/job-number := "1"
        jtu/combined-status := :cancelled/requested
        job-util/job-type := :test)

      @(jtu/pull-job system job-id :cancelled/finished)

      (testing "job history"
        (given @(jtu/pull-job-history system job-id)
          count := 4

          [0 jtu/combined-status] := :ready
          [1 jtu/combined-status] := :in-progress/started
          [2 jtu/combined-status] := :cancelled/requested
          [3 jtu/combined-status] := :cancelled/finished))))

  (testing "fails if job is already completed"
    (with-system [{:blaze/keys [job-scheduler]} config]

      @(js/create-job job-scheduler (completed-job "test"))

      (given-failed-future (js/cancel-job job-scheduler job-id)
        ::anom/category := ::anom/conflict
        ::anom/message := (format "Can't cancel job `%s` because it's status is `completed`." job-id))))

  (testing "fails if job is already failed"
    (with-system [{:blaze/keys [job-scheduler]} config]

      @(js/create-job job-scheduler (failed-job "test"))

      (given-failed-future (js/cancel-job job-scheduler job-id)
        ::anom/category := ::anom/conflict
        ::anom/message := (format "Can't cancel job `%s` because it's status is `failed`." job-id))))

  (testing "fails if job has already a requested cancellation"
    (with-system [{:blaze/keys [job-scheduler]} config]

      @(js/create-job job-scheduler (cancellation-requested-job "test"))

      (given-failed-future (js/cancel-job job-scheduler job-id)
        ::anom/category := ::anom/conflict
        ::anom/message := (format "Can't cancel job `%s` because it's status is `cancelled`." job-id))))

  (testing "fails if job has already a finished cancellation"
    (with-system [{:blaze/keys [job-scheduler]} config]

      @(js/create-job job-scheduler (cancellation-finished-job "test"))

      (given-failed-future (js/cancel-job job-scheduler job-id)
        ::anom/category := ::anom/conflict
        ::anom/message := (format "Can't cancel job `%s` because it's status is `cancelled`." job-id))))

  (testing "job fails if the job handler isn't found"
    (with-system [{:blaze/keys [job-scheduler] :as system} config]

      @(js/create-job job-scheduler (in-progress-job "unknown-141028"))

      (given @(js/cancel-job job-scheduler job-id)
        :fhir/type := :fhir/Task
        job-util/job-number := "1"
        jtu/combined-status := :cancelled/requested
        job-util/job-type := :unknown-141028)

      (testing "the job has failed"
        (given @(jtu/pull-job system job-id :failed)
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          jtu/combined-status := :failed
          job-util/job-type := :unknown-141028
          job-util/error-msg := "Failed to cancel because the implementation is missing.")))))

(deftest pause-job-test
  (testing "works while job is in-progress"
    (with-system [{:blaze/keys [job-scheduler] :as system} config]

      @(js/create-job job-scheduler (ready-job "test"))

      @(jtu/pull-job system job-id :in-progress/started)

      (given @(mtu/assoc-thread-name (js/pause-job job-scheduler job-id))
        [meta :thread-name] :? mtu/common-pool-thread?
        :fhir/type := :fhir/Task
        job-util/job-number := "1"
        jtu/combined-status := :on-hold/paused
        job-util/job-type := :test)))

  (testing "pause is idempotent, so it can be called twice with the same output"
    (with-system [{:blaze/keys [job-scheduler] :as system} config]

      @(js/create-job job-scheduler (ready-job "test"))

      @(jtu/pull-job system job-id :in-progress/started)

      (dotimes [_ 2]
        (given @(js/pause-job job-scheduler job-id)
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          jtu/combined-status := :on-hold/paused
          job-util/job-type := :test))))

  (testing "fails if job is already completed"
    (with-system [{:blaze/keys [job-scheduler] :as system} config]

      @(js/create-job job-scheduler (ready-job "test"))

      @(jtu/pull-job system job-id :completed)

      (given-failed-future (js/pause-job job-scheduler job-id)
        ::anom/category := ::anom/conflict
        ::anom/message := (format "Can't pause job `%s` because it isn't in-progress. It's status is `completed`." job-id)))))

(defn- on-hold-job [type]
  {:fhir/type :fhir/Task
   :status #fhir/code "on-hold"
   :code (job-type type)})

(deftest resume-job-test
  (testing "works while job is on-hold"
    (with-system [{:blaze/keys [job-scheduler] :as system} config]

      @(js/create-job job-scheduler (ready-job "test"))

      @(jtu/pull-job system job-id :in-progress/started)

      @(js/pause-job job-scheduler job-id)

      (given @(mtu/assoc-thread-name (js/resume-job job-scheduler job-id))
        [meta :thread-name] :? mtu/common-pool-thread?
        :fhir/type := :fhir/Task
        job-util/job-number := "1"
        jtu/combined-status := :in-progress/resumed
        job-util/job-type := :test)

      (testing "the job is completed"
        (given @(jtu/pull-job system job-id :completed)
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          jtu/combined-status := :completed
          job-util/job-type := :test))))

  (testing "fails if job is already completed"
    (with-system [{:blaze/keys [job-scheduler] :as system} config]

      @(js/create-job job-scheduler (ready-job "test"))

      @(jtu/pull-job system job-id :completed)

      (given-failed-future (js/resume-job job-scheduler job-id)
        ::anom/category := ::anom/conflict
        ::anom/message := (format "Can't resume job `%s` because it isn't on-hold. It's status is `completed`." job-id))))

  (testing "job fails if the job handler isn't found"
    (with-system [{:blaze/keys [job-scheduler] :as system} config]

      @(js/create-job job-scheduler (on-hold-job "unknown-105857"))

      (given @(js/resume-job job-scheduler job-id)
        :fhir/type := :fhir/Task
        job-util/job-number := "1"
        jtu/combined-status := :in-progress/resumed
        job-util/job-type := :unknown-105857)

      (testing "the job has failed"
        (given @(jtu/pull-job system job-id :failed)
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          jtu/combined-status := :failed
          job-util/job-type := :unknown-105857
          job-util/error-msg := "Failed to resume because the implementation is missing.")))))

(deftest shutdown-test
  (with-system [{:blaze/keys [job-scheduler] :as system} config]

    @(js/create-job job-scheduler (ready-job "test"))

    @(jtu/pull-job system job-id :in-progress/started)

    (ig/halt! system [:blaze/job-scheduler])

    (testing "there are no running jobs left"
      (is (empty? @(:running-jobs job-scheduler))))

    (testing "the job is on-hold"
      (given @(jtu/pull-job system job-id :on-hold/orderly-shutdown)
        :fhir/type := :fhir/Task
        job-util/job-number := "1"
        jtu/combined-status := :on-hold/orderly-shutdown
        job-util/job-type := :test))))

(deftest error-in-on-next-handler-test
  (with-redefs [js/on-start (fn [_ _] (throw (Exception.)))]
    (with-system [{:blaze/keys [job-scheduler]} config]

      @(js/create-job job-scheduler (ready-job "test"))

      ;; we only wait here to be sure that on-start is called and the TaskSubscriber fails
      (is (nil? (Thread/sleep 100))))))
