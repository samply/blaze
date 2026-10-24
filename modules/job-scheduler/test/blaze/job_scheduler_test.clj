(ns blaze.job-scheduler-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.db.api-spec]
   [blaze.db.api-stub :refer [with-db]]
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
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.spec]
   [blaze.terminology-service :as-alias ts]
   [blaze.terminology-service-spec]
   [blaze.terminology-service.not-available]
   [blaze.test-util :as tu :refer [given-failed-future]]
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

(defn- running-jobs
  "Returns the running jobs of `job-scheduler`, a map of job id to the future
  of its execution."
  [{:keys [state]}]
  (:running-jobs @state))

(defn- await-running
  "Waits until the job with `id` is in the running jobs of `job-scheduler` if
  `running?` is true, or isn't in them otherwise.

  Returns true if that happens within 10 seconds, or false otherwise."
  [job-scheduler id running?]
  (loop [n 1000]
    (cond
      (= running? (contains? (running-jobs job-scheduler) id)) true
      (zero? n) false
      :else (do (Thread/sleep 10) (recur (dec n))))))

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
        (ac/completed-future job))
      (-on-pause [_ _])
      (-on-shutdown [_ _]))))

(defn- long-running-job-future
  "Returns a future that is meant to be completed when `job` is signalled to
  shut down.

  Completes with `job` after 10 seconds otherwise, so that a test in which the
  job is never signalled fails instead of hanging."
  [job]
  (doto (ac/future)
    (ac/complete-on-timeout! job 10 TimeUnit/SECONDS)))

(defn- after-start-gate
  "Returns a future that will complete with `job` after `start-gate` was
  delivered."
  [start-gate job]
  (ac/supply-async #(do @start-gate job)))

(defmethod ig/init-key :blaze.job/long-running
  [_ {:keys [admin-node running start-gate resume-signal pause-signal
             ignore-pause cancel-signal shutdown-signal]}]
  (reify p/JobHandler
    (-on-start [_ {:keys [id] :as job}]
      (let [future (long-running-job-future job)]
        (swap! running assoc id future)
        (-> (if start-gate
              (after-start-gate start-gate job)
              (ac/completed-future job))
            (ac/then-compose #(job-util/update-job admin-node % start-job))
            (ac/then-compose (fn [_] future)))))
    (-on-resume [_ job]
      (deliver resume-signal job)
      (ac/completed-future job))
    (-on-pause [_ {:keys [id] :as job}]
      (deliver pause-signal job)
      (when-not ignore-pause
        (ac/complete! (get @running id) job)))
    (-on-cancel [_ job]
      (deliver cancel-signal job)
      (ac/completed-future job))
    (-on-shutdown [_ {:keys [id] :as job}]
      (deliver shutdown-signal job)
      (ac/complete! (get @running id) job))))

(defmethod ig/init-key :blaze.job/no-op
  [_ {:keys [called-signal]}]
  (reify p/JobHandler
    (-on-start [_ job]
      (deliver called-signal job)
      (ac/completed-future job))
    (-on-resume [_ job]
      (deliver called-signal job)
      (ac/completed-future job))
    (-on-cancel [_ job]
      (deliver called-signal job)
      (ac/completed-future job))
    (-on-shutdown [_ _])))

(defmethod ig/init-key :blaze.job/blocking-start
  [_ {:keys [start-gate]}]
  (reify p/JobHandler
    (-on-start [_ job]
      @start-gate
      (ac/completed-future job))
    (-on-shutdown [_ _])))

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
    :mode :internal}

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

  (testing "missing clock"
    (given-failed-system (update config :blaze/job-scheduler dissoc :clock)
      :key := :blaze/job-scheduler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :clock))))

  (testing "missing rng-fn"
    (given-failed-system (update config :blaze/job-scheduler dissoc :rng-fn)
      :key := :blaze/job-scheduler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))))

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
        (given @(js/create-job job-scheduler (ready-job "test"))
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          jtu/combined-status := :ready
          job-util/job-type := :test
          ;; both JobNumber identifiers, current first
          [:identifier 0 :use] := #fhir/code "official"
          [:identifier 0 :system] := #fhir/uri "https://blaze-server.org/fhir/sid/JobNumber"
          [:identifier 0 :value] := #fhir/string "1"
          [:identifier 1 :use] := #fhir/code "official"
          [:identifier 1 :system] := #fhir/uri "https://samply.github.io/blaze/fhir/sid/JobNumber"
          [:identifier 1 :value] := #fhir/string "1"))

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
          (with-db [db node]
            (given @(d/pull node (d/resource-handle db "Bundle" bundle-id))
              :fhir/type := :fhir/Bundle
              :type := #fhir/code "batch")))))))

(deftest create-job-concurrently-test
  (testing "all jobs are created with unique job numbers"
    (with-system [{:blaze/keys [job-scheduler]} config]

      (let [futures (mapv (fn [_] (js/create-job job-scheduler (ready-job "test")))
                          (range 10))]

        (is (= (range 1 11)
               (sort (map (comp parse-long job-util/job-number deref) futures))))))))

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

      (given @(js/cancel-job job-scheduler job-id)
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

      (given @(js/pause-job job-scheduler job-id)
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

      (testing "the paused execution ends"
        (is (await-running job-scheduler job-id false)))

      (given @(js/resume-job job-scheduler job-id)
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
      (is (empty? (running-jobs job-scheduler))))

    (testing "the job is on-hold"
      (given @(jtu/pull-job system job-id :on-hold/orderly-shutdown)
        :fhir/type := :fhir/Task
        job-util/job-number := "1"
        jtu/combined-status := :on-hold/orderly-shutdown
        job-util/job-type := :test))))

(defn- long-running-config [handler-config]
  (-> (assoc-in config [:blaze/job-scheduler :handlers :blaze.job/long-running]
                (ig/ref :blaze.job/long-running))
      (assoc :blaze.job/long-running
             (merge {:admin-node (ig/ref :blaze.db.admin/node)
                     :running (atom {})
                     :resume-signal (promise)
                     :pause-signal (promise)
                     :cancel-signal (promise)
                     :shutdown-signal (promise)}
                    handler-config))))

(deftest shutdown-signals-running-jobs-test
  (let [shutdown-signal (promise)]
    (with-system [{:blaze/keys [job-scheduler] :as system}
                  (long-running-config {:shutdown-signal shutdown-signal})]

      @(js/create-job job-scheduler (ready-job "long-running"))

      @(jtu/pull-job system job-id :in-progress/started)

      (ig/halt! system [:blaze/job-scheduler])

      (testing "the job handler was signalled with the on-hold job"
        (given (deref shutdown-signal 0 ::not-signalled)
          :fhir/type := :fhir/Task
          :id := job-id
          jtu/combined-status := :on-hold/orderly-shutdown))

      (testing "there are no running jobs left"
        (is (empty? (running-jobs job-scheduler))))

      (testing "the job stays on-hold"
        (given @(jtu/pull-job-history system job-id)
          count := 3
          [0 jtu/combined-status] := :ready
          [1 jtu/combined-status] := :in-progress/started
          [2 jtu/combined-status] := :on-hold/orderly-shutdown)))))

(defn- pull-job-signalling-ready
  "Returns a function like `pull-job` that delivers a job pulled from the
  current database to `ready-signal` if it is ready."
  [pull-job ready-signal]
  (fn
    ([node id]
     (do-sync [job (pull-job node id)]
       (when (= :ready (jtu/combined-status job))
         (deliver ready-signal job))
       job))
    ([node db id]
     (pull-job node db id))))

(deftest shutdown-signals-starting-jobs-test
  (let [start-gate (promise)
        shutdown-signal (promise)]
    (with-system [{:blaze/keys [job-scheduler] :as system}
                  (long-running-config {:start-gate start-gate
                                        :shutdown-signal shutdown-signal})]

      @(js/create-job job-scheduler (ready-job "long-running"))

      (testing "the job is running but still ready"
        (is (await-running job-scheduler job-id true))
        (given @(jtu/pull-job system job-id :ready)
          jtu/combined-status := :ready))

      (let [pulled-ready (promise)]
        (with-redefs [job-util/pull-job (pull-job-signalling-ready
                                         job-util/pull-job pulled-ready)]
          (let [halted (future (ig/halt! system [:blaze/job-scheduler]))]

            (testing "the shutdown sees the job as ready"
              (given (deref pulled-ready 10000 ::timeout)
                jtu/combined-status := :ready))

            (deliver start-gate ::open)
            @halted)))

      (testing "the job handler was signalled with the on-hold job"
        (given (deref shutdown-signal 0 ::not-signalled)
          :fhir/type := :fhir/Task
          :id := job-id
          jtu/combined-status := :on-hold/orderly-shutdown))

      (testing "there are no running jobs left"
        (is (empty? (running-jobs job-scheduler))))

      (testing "the job stays on-hold"
        (given @(jtu/pull-job-history system job-id)
          count := 3
          [0 jtu/combined-status] := :ready
          [1 jtu/combined-status] := :in-progress/started
          [2 jtu/combined-status] := :on-hold/orderly-shutdown)))))

(deftest error-in-on-next-handler-test
  (let [called (promise)]
    (with-redefs [js/on-start (fn [_ _] (deliver called ::called) (throw (Exception.)))]
      (with-system [{:blaze/keys [job-scheduler]} config]

        @(js/create-job job-scheduler (ready-job "test"))

        ;; the throwing on-start makes the TaskSubscriber fail
        (is (= ::called (deref called 10000 ::timeout)))))))

(defn- no-op-config [called-signal]
  (-> (assoc-in config [:blaze/job-scheduler :handlers :blaze.job/no-op]
                (ig/ref :blaze.job/no-op))
      (assoc :blaze.job/no-op {:called-signal called-signal})))

(defn- halt-job-scheduler
  "Halts the job scheduler of `system`.

  Returns ::halted, or ::timeout if halting doesn't return within 10 seconds."
  [system]
  (-> (future (ig/halt! system [:blaze/job-scheduler]) ::halted)
      (deref 10000 ::timeout)))

(deftest job-completing-immediately-test
  (testing "start"
    (let [called-signal (promise)]
      (with-system [{:blaze/keys [job-scheduler] :as system}
                    (no-op-config called-signal)]

        @(js/create-job job-scheduler (ready-job "no-op"))

        (deref called-signal 10000 ::timeout)

        (testing "the job isn't running anymore"
          (is (await-running job-scheduler job-id false)))

        (testing "halting the job scheduler returns"
          (is (= ::halted (halt-job-scheduler system)))))))

  (testing "resume"
    (let [called-signal (promise)]
      (with-system [{:blaze/keys [job-scheduler] :as system}
                    (no-op-config called-signal)]

        @(js/create-job job-scheduler (on-hold-job "no-op"))
        @(js/resume-job job-scheduler job-id)

        (deref called-signal 10000 ::timeout)

        (testing "the job isn't running anymore"
          (is (await-running job-scheduler job-id false)))

        (testing "halting the job scheduler returns"
          (is (= ::halted (halt-job-scheduler system)))))))

  (testing "cancel"
    (let [called-signal (promise)]
      (with-system [{:blaze/keys [job-scheduler] :as system}
                    (no-op-config called-signal)]

        @(js/create-job job-scheduler (on-hold-job "no-op"))
        @(js/cancel-job job-scheduler job-id)

        (deref called-signal 10000 ::timeout)

        (testing "the job isn't running"
          (is (not (contains? (running-jobs job-scheduler) job-id))))

        (testing "halting the job scheduler returns"
          (is (= ::halted (halt-job-scheduler system))))))))

(deftest cancellation-request-of-running-job-test
  (let [running (atom {})
        cancel-signal (promise)]
    (with-system [{:blaze/keys [job-scheduler] :as system}
                  (long-running-config {:running running
                                        :cancel-signal cancel-signal})]

      @(js/create-job job-scheduler (ready-job "long-running"))

      @(jtu/pull-job system job-id :in-progress/started)

      @(js/cancel-job job-scheduler job-id)

      (deref cancel-signal 10000 ::timeout)

      (testing "the job is still running"
        (is (contains? (running-jobs job-scheduler) job-id)))

      (let [halted (future (ig/halt! system [:blaze/job-scheduler]) ::halted)]

        (testing "halting the job scheduler waits for the job"
          (is (= ::timeout (deref halted 500 ::timeout))))

        (ac/complete! (get @running job-id) @cancel-signal)

        (testing "halting the job scheduler returns after the job ended"
          (is (= ::halted (deref halted 10000 ::timeout))))))))

(deftest shutdown-signals-paused-running-jobs-test
  (let [shutdown-signal (promise)]
    (with-system [{:blaze/keys [job-scheduler] :as system}
                  (long-running-config {:shutdown-signal shutdown-signal
                                        :ignore-pause true})]

      @(js/create-job job-scheduler (ready-job "long-running"))

      @(jtu/pull-job system job-id :in-progress/started)

      @(js/pause-job job-scheduler job-id)

      (testing "the paused job is still running"
        (is (contains? (running-jobs job-scheduler) job-id)))

      (ig/halt! system [:blaze/job-scheduler])

      (testing "the job handler was signalled with the paused job"
        (given (deref shutdown-signal 0 ::not-signalled)
          :fhir/type := :fhir/Task
          :id := job-id
          jtu/combined-status := :on-hold/paused))

      (testing "the job stays paused"
        (given @(jtu/pull-job-history system job-id)
          count := 3
          [0 jtu/combined-status] := :ready
          [1 jtu/combined-status] := :in-progress/started
          [2 jtu/combined-status] := :on-hold/paused)))))

(deftest no-jobs-started-during-shutdown-test
  (testing "a job becoming ready isn't started"
    (let [called-signal (promise)]
      (with-system [{:blaze/keys [job-scheduler] :as system}
                    (no-op-config called-signal)]

        (ig/halt! system [:blaze/job-scheduler])

        @(js/create-job job-scheduler (ready-job "no-op"))

        (testing "the job handler isn't called"
          (is (= ::not-called (deref called-signal 1000 ::not-called)))))))

  (testing "a resumed job isn't resumed"
    (let [called-signal (promise)]
      (with-system [{:blaze/keys [job-scheduler] :as system}
                    (no-op-config called-signal)]

        @(js/create-job job-scheduler (on-hold-job "no-op"))

        (ig/halt! system [:blaze/job-scheduler])

        @(js/resume-job job-scheduler job-id)

        (testing "the job handler isn't called"
          (is (= ::not-called (deref called-signal 1000 ::not-called)))))))

  (testing "a cancellation is still requested"
    (let [called-signal (promise)]
      (with-system [{:blaze/keys [job-scheduler] :as system}
                    (no-op-config called-signal)]

        @(js/create-job job-scheduler (on-hold-job "no-op"))

        (ig/halt! system [:blaze/job-scheduler])

        @(js/cancel-job job-scheduler job-id)

        (testing "the job handler is called"
          (given (deref called-signal 10000 ::not-called)
            jtu/combined-status := :cancelled/requested))))))

(defn- blocking-start-config [start-gate]
  (-> (assoc-in config [:blaze/job-scheduler :handlers :blaze.job/blocking-start]
                (ig/ref :blaze.job/blocking-start))
      (assoc :blaze.job/blocking-start {:start-gate start-gate})))

(deftest job-running-while-its-handler-starts-it-test
  (let [start-gate (promise)]
    (with-system [{:blaze/keys [job-scheduler]}
                  (blocking-start-config start-gate)]

      @(js/create-job job-scheduler (ready-job "blocking-start"))

      (testing "the job is running while -on-start is called"
        (is (await-running job-scheduler job-id true)))

      (deliver start-gate ::open)

      (testing "the job isn't running anymore after it ended"
        (is (await-running job-scheduler job-id false))))))

(deftest resume-running-job-test
  (let [running (atom {})
        resume-signal (promise)]
    (with-system [{:blaze/keys [job-scheduler] :as system}
                  (long-running-config {:running running
                                        :resume-signal resume-signal
                                        :ignore-pause true})]

      @(js/create-job job-scheduler (ready-job "long-running"))

      @(jtu/pull-job system job-id :in-progress/started)

      @(js/pause-job job-scheduler job-id)

      (testing "a paused job that is still running can't be resumed"
        (given-failed-future (js/resume-job job-scheduler job-id)
          ::anom/category := ::anom/conflict
          ::anom/message := (format "Can't resume job `%s` because its previous execution is still running." job-id)))

      (ac/complete! (get @running job-id) {})

      (is (await-running job-scheduler job-id false))

      (testing "the job can be resumed after its execution ended"
        (given @(js/resume-job job-scheduler job-id)
          jtu/combined-status := :in-progress/resumed)

        (given (deref resume-signal 10000 ::not-called)
          jtu/combined-status := :in-progress/resumed)))))

(defn- resume-directly
  "Resumes `job` by updating it directly on `admin-node`, bypassing the job
  scheduler."
  [admin-node job]
  (job-util/update-job
   admin-node job
   #(assoc %
           :status #fhir/code "in-progress"
           :statusReason job-util/resumed-status-reason)))

(deftest running-job-is-not-executed-twice-test
  (let [running (atom {})
        resume-signal (promise)]
    (with-system [{:blaze/keys [job-scheduler] :blaze.db.admin/keys [node]
                   :as system}
                  (long-running-config {:running running
                                        :resume-signal resume-signal})]

      @(js/create-job job-scheduler (ready-job "long-running"))

      @(resume-directly node @(jtu/pull-job system job-id :in-progress/started))

      (testing "the job handler isn't called to resume the running job"
        (is (= ::not-called (deref resume-signal 1000 ::not-called))))

      (testing "the first execution is still tracked"
        (is (contains? (running-jobs job-scheduler) job-id)))

      (ac/complete! (get @running job-id) {})

      (testing "the job isn't running anymore after the first execution ended"
        (is (await-running job-scheduler job-id false))))))

(deftest pause-signals-running-job-test
  (let [pause-signal (promise)]
    (with-system [{:blaze/keys [job-scheduler] :as system}
                  (long-running-config {:pause-signal pause-signal})]

      @(js/create-job job-scheduler (ready-job "long-running"))

      @(jtu/pull-job system job-id :in-progress/started)

      @(js/pause-job job-scheduler job-id)

      (testing "the job handler was signalled with the paused job"
        (given (deref pause-signal 10000 ::not-signalled)
          :fhir/type := :fhir/Task
          :id := job-id
          jtu/combined-status := :on-hold/paused))

      (testing "the execution of the job ends"
        (is (await-running job-scheduler job-id false)))

      (testing "the job can be resumed"
        (given @(js/resume-job job-scheduler job-id)
          jtu/combined-status := :in-progress/resumed)))))

(deftest shutdown-with-job-ending-ready-test
  (let [start-gate (promise)]
    (with-system [{:blaze/keys [job-scheduler] :as system}
                  (blocking-start-config start-gate)]

      @(js/create-job job-scheduler (ready-job "blocking-start"))

      (is (await-running job-scheduler job-id true))

      (let [pulled-ready (promise)]
        (with-redefs [job-util/pull-job (pull-job-signalling-ready
                                         job-util/pull-job pulled-ready)]
          (let [halted (future (ig/halt! system [:blaze/job-scheduler]) ::halted)]

            (testing "the shutdown sees the job as ready"
              (given (deref pulled-ready 10000 ::timeout)
                jtu/combined-status := :ready))

            (deliver start-gate ::open)

            (testing "halting the job scheduler returns after the job ended"
              (is (= ::halted (deref halted 10000 ::timeout)))))))

      (testing "the job stays ready"
        (given (last @(jtu/pull-job-history system job-id))
          jtu/combined-status := :ready)))))

(defn- update-job-failing-hold-once
  "Returns a function like `update-job` that fails the first hold on shutdown
  with `anomaly`, after calling `on-failure` with the job."
  [update-job anomaly on-failure]
  (let [failed? (atom false)]
    (fn
      ([node job f]
       (update-job node job f))
      ([node job f x]
       (if (and (= job-util/orderly-shut-down-status-reason x)
                (compare-and-set! failed? false true))
         (do (on-failure job)
             (ac/completed-future anomaly))
         (update-job node job f x))))))

(deftest shutdown-with-failing-hold-test
  (testing "a hold failing because of a concurrent update is retried"
    (with-system [{:blaze/keys [job-scheduler] :as system}
                  (long-running-config {})]

      @(js/create-job job-scheduler (ready-job "long-running"))

      @(jtu/pull-job system job-id :in-progress/started)

      (with-redefs [job-util/update-job
                    (update-job-failing-hold-once
                     job-util/update-job
                     (ba/conflict "conflict-174612" ::js/action :update-job)
                     identity)]
        (ig/halt! system [:blaze/job-scheduler]))

      (testing "the job is on-hold"
        (given (last @(jtu/pull-job-history system job-id))
          jtu/combined-status := :on-hold/orderly-shutdown))))

  (testing "a hold failing otherwise is given up"
    (let [running (atom {})]
      (with-system [{:blaze/keys [job-scheduler] :as system}
                    (long-running-config {:running running})]

        @(js/create-job job-scheduler (ready-job "long-running"))

        @(jtu/pull-job system job-id :in-progress/started)

        (with-redefs [job-util/update-job
                      (update-job-failing-hold-once
                       job-util/update-job
                       (ba/fault "fault-174631")
                       #(ac/complete! (get @running (:id %)) %))]
          (ig/halt! system [:blaze/job-scheduler]))

        (testing "the job isn't put on-hold"
          (given (last @(jtu/pull-job-history system job-id))
            jtu/combined-status := :in-progress/started))))))
