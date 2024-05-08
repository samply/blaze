(ns blaze.job-scheduler-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.api-spec]
   [blaze.db.kv :as kv]
   [blaze.db.kv.mem]
   [blaze.db.node :as node :refer [node?]]
   [blaze.db.resource-store :as rs]
   [blaze.db.resource-store.kv :as rs-kv]
   [blaze.db.search-param-registry]
   [blaze.db.tx-cache]
   [blaze.db.tx-log :as tx-log]
   [blaze.db.tx-log.local]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util :refer [given-failed-future structure-definition-repo]]
   [blaze.job-scheduler :as js]
   [blaze.job-scheduler-spec]
   [blaze.job-scheduler.job-util :as job-util]
   [blaze.log]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu :refer [given-thrown]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [java-time.api :as time]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [java.util Random]
   [java.util.concurrent TimeUnit]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-level! :trace)

(test/use-fixtures :each tu/fixture)

(derive :blaze.db.main/node :blaze.db/node)
(derive :blaze.db.admin/node :blaze.db/node)

(def config
  {:blaze/job-scheduler
   {:main-node (ig/ref :blaze.db.main/node)
    :admin-node (ig/ref :blaze.db.admin/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref ::incrementing-rng-fn)}

   :blaze.db.main/node
   {:tx-log (ig/ref :blaze.db.main/tx-log)
    :tx-cache (ig/ref :blaze.db.admin/tx-cache)
    :indexer-executor (ig/ref :blaze.db.node.main/indexer-executor)
    :resource-store (ig/ref :blaze.db/resource-store)
    :kv-store (ig/ref :blaze.db.main/index-kv-store)
    :resource-indexer (ig/ref :blaze.db.node.main/resource-indexer)
    :search-param-registry (ig/ref :blaze.db/search-param-registry)
    :poll-timeout (time/millis 10)}

   :blaze.db.admin/node
   {:tx-log (ig/ref :blaze.db.admin/tx-log)
    :tx-cache (ig/ref :blaze.db.admin/tx-cache)
    :indexer-executor (ig/ref :blaze.db.node.admin/indexer-executor)
    :resource-store (ig/ref :blaze.db/resource-store)
    :kv-store (ig/ref :blaze.db.admin/index-kv-store)
    :resource-indexer (ig/ref :blaze.db.node.admin/resource-indexer)
    :search-param-registry (ig/ref :blaze.db/search-param-registry)
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

   :blaze.test/fixed-clock {}

   ::incrementing-rng-fn {}})

(defmethod ig/init-key ::incrementing-rng-fn
  [_ _]
  (let [n (atom -1)]
    #(proxy [Random] []
       (nextLong [] (swap! n inc)))))

(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze/job-scheduler nil})
      :key := :blaze/job-scheduler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze/job-scheduler {}})
      :key := :blaze/job-scheduler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :main-node))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :admin-node))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))))

  (testing "invalid main node"
    (given-thrown (ig/init {:blaze/job-scheduler {:main-node ::invalid}})
      :key := :blaze/job-scheduler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :admin-node))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:cause-data ::s/problems 3 :pred] := `node?
      [:cause-data ::s/problems 3 :val] := ::invalid))

  (testing "invalid admin node"
    (given-thrown (ig/init {:blaze/job-scheduler {:admin-node ::invalid}})
      :key := :blaze/job-scheduler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :main-node))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:cause-data ::s/problems 3 :pred] := `node?
      [:cause-data ::s/problems 3 :val] := ::invalid))

  (testing "invalid clock"
    (given-thrown (ig/init {:blaze/job-scheduler {:clock ::invalid}})
      :key := :blaze/job-scheduler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :main-node))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :admin-node))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:cause-data ::s/problems 3 :pred] := `time/clock?
      [:cause-data ::s/problems 3 :val] := ::invalid))

  (testing "invalid rng-fn"
    (given-thrown (ig/init {:blaze/job-scheduler {:rng-fn ::invalid}})
      :key := :blaze/job-scheduler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :main-node))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :admin-node))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 3 :pred] := `fn?
      [:cause-data ::s/problems 3 :val] := ::invalid))

  (testing "success"
    (with-system [{:blaze/keys [job-scheduler]} config]
      (is (s/valid? :blaze/job-scheduler job-scheduler)))))

(defn- combined-status [{:keys [status] :as job}]
  (if-let [status-reason (job-util/status-reason job)]
    (keyword (type/value status) status-reason)
    (keyword (type/value status))))

(defn- start-job [job]
  (assoc
   job
   :status #fhir/code"in-progress"
   :statusReason job-util/started-status-reason))

(defn- finish-job [job]
  (-> (assoc job :status #fhir/code"completed")
      (dissoc :statusReason)))

(defn- async-delay [x]
  (let [future (ac/future)]
    (ac/complete-on-timeout! future x 150 TimeUnit/MILLISECONDS)
    future))

(defmethod js/on-start :test
  [{:keys [admin-node]} job]
  (-> (job-util/update-job admin-node job start-job)
      (ac/then-compose async-delay)
      (ac/then-compose #(job-util/update-job admin-node % finish-job))))

(defmethod js/on-resume :test
  [{:keys [admin-node]} job]
  (-> (async-delay job)
      (ac/then-compose #(job-util/update-job admin-node % finish-job))))

(defmethod js/on-start :start-only
  [{:keys [admin-node]} job]
  (-> (job-util/update-job admin-node job start-job)
      (ac/then-compose async-delay)
      (ac/then-compose #(job-util/update-job admin-node % finish-job))))

(defmethod js/on-start :error
  [_ _]
  (ac/completed-future (ba/fault "error-150651")))

(defmethod js/on-start :throws-error
  [_ _]
  (throw (Exception.)))

(defn- job-type [type]
  (type/map->CodeableConcept
   {:coding
    [(type/map->Coding
      {:system (type/uri job-util/type-url)
       :code (type/code type)})]}))

(defn- ready-job [type]
  {:fhir/type :fhir/Task
   :status #fhir/code"ready"
   :code (job-type type)})

(def ^:private job-id "AAAAAAAAAAAAAAAB")

(defn- pull-job* [node status]
  (-> (d/pull node (d/resource-handle (d/db node) "Task" job-id))
      (ac/then-compose-async
       (fn [job]
         (if (= status (combined-status job))
           (ac/completed-future job)
           (pull-job* node status)))
       (ac/delayed-executor 10 TimeUnit/MILLISECONDS))))

(defn- pull-job [node status]
  (-> (pull-job* node status)
      (ac/or-timeout! 10 TimeUnit/SECONDS)))

(deftest create-test
  (testing "test job"
    (with-system [{:blaze/keys [job-scheduler] :blaze.db.admin/keys [node]} config]

      (testing "the job is created as ready"
        (given @(js/create-job job-scheduler (ready-job "test"))
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          combined-status := :ready
          job-util/job-type := :test))

      (testing "the job is in-progress"
        (given @(pull-job node :in-progress/started)
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          combined-status := :in-progress/started
          job-util/job-type := :test))

      (testing "the job is completed"
        (given @(pull-job node :completed)
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          combined-status := :completed
          job-util/job-type := :test))))

  (testing "create a second job"
    (with-system [{:blaze/keys [job-scheduler]} config]

      @(js/create-job job-scheduler (ready-job "test"))

      (testing "the job number is 2"
        (given @(js/create-job job-scheduler (ready-job "test"))
          :fhir/type := :fhir/Task
          job-util/job-number := "2"
          combined-status := :ready
          job-util/job-type := :test))))

  (testing "job fails if there is no on-start implementation"
    (with-system [{:blaze/keys [job-scheduler] :blaze.db.admin/keys [node]} config]

      @(js/create-job job-scheduler (ready-job "unknown-142504"))

      (testing "the job has failed"
        (given @(pull-job node :failed)
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          combined-status := :failed
          job-util/job-type := :unknown-142504
          job-util/error-msg := "Failed to start because the implementation is missing."))))

  (testing "job fails because the execution errored"
    (with-system [{:blaze/keys [job-scheduler] :blaze.db.admin/keys [node]} config]

      @(js/create-job job-scheduler (ready-job "error"))

      (testing "the job has failed"
        (given @(pull-job node :failed)
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          combined-status := :failed
          job-util/job-type := :error
          job-util/error-msg := "error-150651"))))

  (testing "job fails because the execution throws an exception"
    (with-system [{:blaze/keys [job-scheduler] :blaze.db.admin/keys [node]} config]

      @(js/create-job job-scheduler (ready-job "throws-error"))

      (testing "the job has failed"
        (given @(pull-job node :failed)
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          combined-status := :failed
          job-util/job-type := :throws-error
          job-util/error-msg := "empty error message")))))

(deftest pause-test
  (testing "works while job is in-progress"
    (with-system [{:blaze/keys [job-scheduler] :blaze.db.admin/keys [node]} config]

      @(js/create-job job-scheduler (ready-job "test"))

      @(pull-job node :in-progress/started)

      (given @(js/pause-job job-scheduler job-id)
        :fhir/type := :fhir/Task
        job-util/job-number := "1"
        combined-status := :on-hold/paused
        job-util/job-type := :test)))

  (testing "pause is idempotent, so it can be called twice with the same output"
    (with-system [{:blaze/keys [job-scheduler] :blaze.db.admin/keys [node]} config]

      @(js/create-job job-scheduler (ready-job "test"))

      @(pull-job node :in-progress/started)

      (dotimes [_ 2]
        (given @(js/pause-job job-scheduler job-id)
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          combined-status := :on-hold/paused
          job-util/job-type := :test))))

  (testing "fails if job is already completed"
    (with-system [{:blaze/keys [job-scheduler] :blaze.db.admin/keys [node]} config]

      @(js/create-job job-scheduler (ready-job "test"))

      @(pull-job node :completed)

      (given-failed-future (js/pause-job job-scheduler job-id)
        ::anom/category := ::anom/conflict
        ::anom/message := (format "Can't pause job `%s` because it isn't in-progress. It's status is `completed`." job-id)))))

(deftest resume-test
  (testing "works while job is on-hold"
    (with-system [{:blaze/keys [job-scheduler] :blaze.db.admin/keys [node]} config]

      @(js/create-job job-scheduler (ready-job "test"))

      @(pull-job node :in-progress/started)

      @(js/pause-job job-scheduler job-id)

      (given @(js/resume-job job-scheduler job-id)
        :fhir/type := :fhir/Task
        job-util/job-number := "1"
        combined-status := :in-progress/resumed
        job-util/job-type := :test)

      (testing "the job is completed"
        (given @(pull-job node :completed)
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          combined-status := :completed
          job-util/job-type := :test))))

  (testing "fails if job is already completed"
    (with-system [{:blaze/keys [job-scheduler] :blaze.db.admin/keys [node]} config]

      @(js/create-job job-scheduler (ready-job "test"))

      @(pull-job node :completed)

      (given-failed-future (js/resume-job job-scheduler job-id)
        ::anom/category := ::anom/conflict
        ::anom/message := (format "Can't resume job `%s` because it isn't on-hold. It's status is `completed`." job-id))))

  (testing "job fails if there is no on-resume implementation"
    (with-system [{:blaze/keys [job-scheduler] :blaze.db.admin/keys [node]} config]

      @(js/create-job job-scheduler (ready-job "start-only"))

      @(pull-job node :in-progress/started)

      @(js/pause-job job-scheduler job-id)

      (given @(js/resume-job job-scheduler job-id)
        :fhir/type := :fhir/Task
        job-util/job-number := "1"
        combined-status := :in-progress/resumed
        job-util/job-type := :start-only)

      (testing "the job has failed"
        (given @(pull-job node :failed)
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          combined-status := :failed
          job-util/job-type := :start-only
          job-util/error-msg := "Failed to resume because the implementation is missing.")))))

(deftest shutdown-test
  (with-system [{:blaze/keys [job-scheduler] :blaze.db.admin/keys [node] :as system} config]

    @(js/create-job job-scheduler (ready-job "test"))

    @(pull-job node :in-progress/started)

    (ig/halt! system [:blaze/job-scheduler])

    (testing "there are no running jobs left"
      (is (empty? @(:running-jobs job-scheduler))))

    (testing "the job is on-hold"
      (given @(pull-job node :on-hold/orderly-shutdown)
        :fhir/type := :fhir/Task
        job-util/job-number := "1"
        combined-status := :on-hold/orderly-shutdown
        job-util/job-type := :test))))
