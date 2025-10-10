(ns blaze.job.async-interaction-test
  (:require
   [blaze.async.comp :as ac]
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
   [blaze.fhir.spec.references-spec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.fhir.writing-context]
   [blaze.handler.fhir.util-spec]
   [blaze.job-scheduler :as js]
   [blaze.job.async-interaction :as job-async]
   [blaze.job.async-interaction-spec]
   [blaze.job.test-util :as jtu]
   [blaze.job.util :as job-util]
   [blaze.luid :as luid]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [integrant.core :as ig]
   [java-time.api :as time]
   [juxt.iota :refer [given]]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def config
  {:blaze/job-scheduler
   {:node (ig/ref :blaze.db.admin/node)
    :handlers {:blaze.job/async-interaction (ig/ref :blaze.job/async-interaction)}
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}

   :blaze.job/async-interaction
   {:main-node (ig/ref :blaze.db.main/node)
    :admin-node (ig/ref :blaze.db.admin/node)
    :batch-handler (ig/ref ::batch-handler)
    :db-sync-timeout 1000
    :blaze/base-url "base-url-104348"
    :context-path "/fhir"
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}

   ::batch-handler {}

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
   :blaze.test/fixed-rng-fn {}})

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.job/async-interaction nil}
      :key := :blaze.job/async-interaction
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.job/async-interaction {}}
      :key := :blaze.job/async-interaction
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :blaze/base-url))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :main-node))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :admin-node))
      [:cause-data ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :batch-handler))
      [:cause-data ::s/problems 4 :pred] := `(fn ~'[%] (contains? ~'% :db-sync-timeout))
      [:cause-data ::s/problems 5 :pred] := `(fn ~'[%] (contains? ~'% :context-path))))

  (testing "invalid base-url"
    (given-failed-system (assoc-in config [:blaze.job/async-interaction :blaze/base-url] ::invalid)
      :key := :blaze.job/async-interaction
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/base-url]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid main-node"
    (given-failed-system (assoc-in config [:blaze.job/async-interaction :main-node] ::invalid)
      :key := :blaze.job/async-interaction
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/node]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid admin-node"
    (given-failed-system (assoc-in config [:blaze.job/async-interaction :admin-node] ::invalid)
      :key := :blaze.job/async-interaction
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/node]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid batch-handler"
    (given-failed-system (assoc-in config [:blaze.job/async-interaction :batch-handler] ::invalid)
      :key := :blaze.job/async-interaction
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.rest-api/batch-handler]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid db-sync-timeout"
    (given-failed-system (assoc-in config [:blaze.job/async-interaction :db-sync-timeout] ::invalid)
      :key := :blaze.job/async-interaction
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.rest-api/db-sync-timeout]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid context-path"
    (given-failed-system (assoc-in config [:blaze.job/async-interaction :context-path] ::invalid)
      :key := :blaze.job/async-interaction
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/context-path]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(derive :blaze.db.main/node :blaze.db/node)
(derive :blaze.db.admin/node :blaze.db/node)

(defmethod ig/init-key ::batch-handler [_ _]
  (fn [{:blaze/keys [cancelled?]}]
    (Thread/sleep 100)
    (if-let [anom (cancelled?)]
      (ac/completed-future anom)
      (ac/completed-future (ring/response {:fhir/type :fhir/Observation})))))

(defn- processing-duration [job]
  (-> (job-util/output-value job job-async/output-uri "processing-duration")
      :value type/value))

(deftest simple-job-execution-test
  (testing "success"
    (with-system [{:blaze/keys [job-scheduler] :as system} config]

      @(js/create-job job-scheduler (job-async/job #fhir/dateTime "2024-05-30T10:26:00" "0" 0)
                      (job-async/request-bundle "0" "GET" "Observation"))

      (testing "the job is completed"
        (given @(jtu/pull-job system :completed)
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          jtu/combined-status := :completed
          :authoredOn := #fhir/dateTime "2024-05-30T10:26:00"
          job-async/response-bundle-ref := "Bundle/AAAAAAAAAAAAAAAA"
          processing-duration :? decimal?))

      (testing "the response bundle is stored"
        (given @(jtu/pull-other-resource system "Bundle" "AAAAAAAAAAAAAAAA")
          :fhir/type := :fhir/Bundle
          [:entry 0 :response :status] := #fhir/string "200"
          [:entry 0 :resource :fhir/type] := :fhir/Observation))

      (testing "job history"
        (given @(jtu/pull-job-history system)
          count := 3

          [0 jtu/combined-status] := :ready
          [1 jtu/combined-status] := :in-progress/started
          [2 jtu/combined-status] := :completed)))

    (testing "unknown FHIR type"
      (with-system [{:blaze/keys [job-scheduler] :as system} config]

        @(js/create-job job-scheduler (job-async/job #fhir/dateTime "2024-05-30T10:26:00" "0" 0)
                        (job-async/request-bundle "0" "GET" "Error"))

        (testing "the job is completed"
          (given @(jtu/pull-job system :completed)
            :fhir/type := :fhir/Task
            job-util/job-number := "1"
            jtu/combined-status := :completed
            :authoredOn := #fhir/dateTime "2024-05-30T10:26:00"
            job-async/response-bundle-ref := "Bundle/AAAAAAAAAAAAAAAA"
            processing-duration :? decimal?))

        (testing "the response bundle is stored"
          (given @(jtu/pull-other-resource system "Bundle" "AAAAAAAAAAAAAAAA")
            :fhir/type := :fhir/Bundle
            [:entry 0 :response :status] := #fhir/string "400"
            [:entry 0 :response :outcome :fhir/type] := :fhir/OperationOutcome
            [:entry 0 :response :outcome :issue 0 :diagnostics] := #fhir/string "Unknown type `Error` in bundle entry request URL `Error`."))

        (testing "job history"
          (given @(jtu/pull-job-history system)
            count := 3

            [0 jtu/combined-status] := :ready
            [1 jtu/combined-status] := :in-progress/started
            [2 jtu/combined-status] := :completed)))))

  (testing "missing request bundle reference"
    (with-system [{:blaze/keys [job-scheduler] :as system} config]
      @(js/create-job
        job-scheduler
        {:fhir/type :fhir/Task
         :status #fhir/code "ready"
         :code #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri "https://samply.github.io/blaze/fhir/CodeSystem/JobType"
                    :code #fhir/code "async-interaction"
                    :display #fhir/string "Asynchronous Interaction Request"}]}})

      (testing "the job is failed"
        (given @(jtu/pull-job system :failed)
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          jtu/combined-status := :failed
          job-util/error-msg := "Missing request bundle reference."))))

  (testing "missing database point in time"
    (with-system [{:blaze/keys [job-scheduler] :as system} config]
      @(js/create-job
        job-scheduler
        (-> (job-async/job #fhir/dateTime "2024-05-30T10:26:00" "0" 0)
            (update :input (partial take 1)))
        (job-async/request-bundle "0" "GET" "Error"))

      (testing "the job is failed"
        (given @(jtu/pull-job system :failed)
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          jtu/combined-status := :failed
          job-util/error-msg := "Missing database point in time.")))))

(defn- job-id [{{:keys [clock rng-fn]} :context}]
  (luid/luid clock (rng-fn)))

(deftest cancellation-test
  (with-system [{:blaze/keys [job-scheduler] :as system} config]

    @(js/create-job job-scheduler (job-async/job #fhir/dateTime "2024-05-30T10:26:00" "0" 0)
                    (job-async/request-bundle "0" "GET" "Observation"))

    @(jtu/pull-job system :in-progress/started)

    @(js/cancel-job job-scheduler (job-id job-scheduler))

    (testing "the job has finished cancellation"
      (given @(jtu/pull-job system :cancelled/finished)
        :fhir/type := :fhir/Task
        job-util/job-number := "1"
        jtu/combined-status := :cancelled/finished
        :authoredOn := #fhir/dateTime "2024-05-30T10:26:00"))

    (testing "job history"
      (given @(jtu/pull-job-history system)
        count := 4

        [0 jtu/combined-status] := :ready
        [1 jtu/combined-status] := :in-progress/started
        [2 jtu/combined-status] := :cancelled/requested
        [3 jtu/combined-status] := :cancelled/finished))))
