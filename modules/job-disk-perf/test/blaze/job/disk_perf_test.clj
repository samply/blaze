(ns blaze.job.disk-perf-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.api-spec]
   [blaze.db.kv :as kv]
   [blaze.db.kv.mem]
   [blaze.db.kv.spec]
   [blaze.db.node :as node]
   [blaze.db.resource-store :as rs]
   [blaze.db.resource-store.kv :as rs-kv]
   [blaze.db.search-param-registry]
   [blaze.db.search-param-registry-spec]
   [blaze.db.tx-cache]
   [blaze.db.tx-log :as tx-log]
   [blaze.db.tx-log.local]
   [blaze.fhir.canonical :as canonical]
   [blaze.fhir.parsing-context]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.fhir.writing-context]
   [blaze.job-scheduler :as js]
   [blaze.job.disk-perf :as job-disk-perf]
   [blaze.job.disk-perf-spec]
   [blaze.job.test-util :as jtu]
   [blaze.job.util :as job-util]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.terminology-service :as-alias ts]
   [blaze.terminology-service-spec]
   [blaze.terminology-service.not-available]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [integrant.core :as ig]
   [java-time.api :as time]
   [juxt.iota :refer [given]])
  (:import
   [java.nio.file Files]
   [java.nio.file.attribute FileAttribute]
   [java.util.concurrent TimeUnit]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn- tmp-dir []
  (str (Files/createTempDirectory "blaze-disk-perf-job-test" (make-array FileAttribute 0))))

(defn- config [dir]
  {:blaze/job-scheduler
   {:node (ig/ref :blaze.db.admin/node)
    :handlers {:blaze.job/disk-perf (ig/ref :blaze.job/disk-perf)}
    :clock (ig/ref :blaze.test/offset-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}

   :blaze.job/disk-perf
   {:dirs {"index" dir}
    :admin-node (ig/ref :blaze.db.admin/node)
    :executor (ig/ref :blaze.job.disk-perf/executor)
    :block-size 16384
    :clock (ig/ref :blaze.test/offset-clock)}

   :blaze.job.disk-perf/executor {}

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

   [::tx-log/local :blaze.db.admin/tx-log]
   {:kv-store (ig/ref :blaze.db.admin/transaction-kv-store)
    :clock (ig/ref :blaze.test/fixed-clock)}

   [::kv/mem :blaze.db.admin/transaction-kv-store]
   {:column-families {}}

   [:blaze.db/tx-cache :blaze.db.admin/tx-cache]
   {:kv-store (ig/ref :blaze.db.admin/index-kv-store)}

   [::node/indexer-executor :blaze.db.node.admin/indexer-executor]
   {}

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

   [::node/resource-indexer :blaze.db.node.admin/resource-indexer]
   {:kv-store (ig/ref :blaze.db.admin/index-kv-store)
    :resource-store (ig/ref :blaze.db/resource-store)
    :search-param-registry (ig/ref :blaze.db/search-param-registry)
    :executor (ig/ref :blaze.db.node.resource-indexer.admin/executor)}

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

   :blaze.test/offset-clock
   {:clock (ig/ref :blaze.test/fixed-clock)
    :offset-seconds 11}

   :blaze.test/fixed-rng-fn {}})

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.job/disk-perf nil}
      :key := :blaze.job/disk-perf
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.job/disk-perf {}}
      :key := :blaze.job/disk-perf
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :dirs))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :admin-node))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :executor))
      [:cause-data ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :block-size))
      [:cause-data ::s/problems 4 :pred] := `(fn ~'[%] (contains? ~'% :clock))))

  (testing "invalid dirs"
    (given-failed-system (assoc-in (config (tmp-dir)) [:blaze.job/disk-perf :dirs] ::invalid)
      :key := :blaze.job/disk-perf
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.job.disk-perf/dirs]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(derive :blaze.db.admin/node :blaze.db/node)

(def ^:private tiny-params
  "Parameters resulting in a benchmark run of well under a second."
  {:file-size 0.00390625M
   :phase-duration 0.2M
   :max-concurrency 2})

(deftest job-test
  (testing "with all parameters"
    (let [job (job-disk-perf/job (time/offset-date-time)
                                 {:database "index"
                                  :file-size 4M
                                  :phase-duration 30M
                                  :max-concurrency 8})]
      (testing "meta.profile carries only the current canonical"
        (given (:profile (:meta job))
          count := 1
          [0] := #fhir/canonical "https://blaze-server.org/fhir/StructureDefinition/DiskPerfJob"))

      (testing "code carries both JobType systems with display, current first"
        (given (:coding (:code job))
          count := 2
          [0 :system] := #fhir/uri "https://blaze-server.org/fhir/CodeSystem/JobType"
          [0 :code] := #fhir/code "disk-perf"
          [0 :display] := #fhir/string "Measure Disk Performance"
          [1 :system] := #fhir/uri "https://samply.github.io/blaze/fhir/CodeSystem/JobType"
          [1 :code] := #fhir/code "disk-perf"
          [1 :display] := #fhir/string "Measure Disk Performance"))

      (testing "all four inputs are present"
        (given (:input job)
          count := 4
          [0 :value] := #fhir/code "index"
          [1 :value :value :value] := 4M
          [1 :value :code] := #fhir/code "GiBy"
          [2 :value :value :value] := 30M
          [2 :value :code] := #fhir/code "s"
          [3 :value] := #fhir/positiveInt 8))))

  (testing "without parameters"
    (let [job (job-disk-perf/job (time/offset-date-time) {})]
      (is (nil? (:input job))))))

(deftest params-spec-test
  (testing "the parameter bounds are valid"
    (are [params] (s/valid? ::job-disk-perf/params params)
      {:file-size 0.0009765625M}
      {:file-size 64M}
      {:phase-duration 0.2M}
      {:phase-duration 300M}
      {:max-concurrency 1}
      {:max-concurrency 1024}))

  (testing "out-of-range parameters are invalid"
    (are [params] (not (s/valid? ::job-disk-perf/params params))
      {:file-size 0.0009M}
      {:file-size 100M}
      {:phase-duration 0M}
      {:phase-duration 400M}
      {:max-concurrency 0}
      {:max-concurrency 1025})))

(defn- output-value [job code]
  (job-util/output-value job (canonical/url "CodeSystem/DiskPerfJobOutput") code))

(def ^:private concurrency-extension-url
  (canonical/url "StructureDefinition/disk-perf-concurrency"))

(defn- outputs [job code]
  (job-util/outputs job (canonical/url "CodeSystem/DiskPerfJobOutput") code))

(deftest simple-job-execution-test
  (testing "success"
    (with-system [{:blaze/keys [job-scheduler] :as system} (config (tmp-dir))]

      @(js/create-job job-scheduler (job-disk-perf/job (time/offset-date-time) tiny-params))

      (testing "the job is completed"
        (let [job @(jtu/pull-job system :completed)]
          (given job
            :fhir/type := :fhir/Task
            job-util/job-number := "1"
            jtu/combined-status := :completed
            [#(output-value % "score") :value] :? #(and (decimal? %) (<= 0M % 100M))
            [#(output-value % "rating")] :? #{#fhir/code "excellent" #fhir/code "good"
                                              #fhir/code "acceptable" #fhir/code "insufficient"}
            [#(output-value % "seq-write-throughput") :value :value] :? pos?
            [#(output-value % "seq-write-throughput") :code] := #fhir/code "By/s"
            [#(outputs % "read-iops") count] := 2
            [#(outputs % "read-iops") 0 :extension 0 :url] := concurrency-extension-url
            [#(outputs % "read-iops") 0 :extension 0 :value] := #fhir/positiveInt 1
            [#(outputs % "read-iops") 0 :value :value :value] :? pos?
            [#(outputs % "read-iops") 0 :value :code] := #fhir/code "/s"
            [#(outputs % "read-iops") 1 :extension 0 :value] := #fhir/positiveInt 2
            [#(outputs % "read-iops") 1 :value :value :value] :? pos?
            [#(outputs % "read-throughput") count] := 2
            [#(outputs % "read-throughput") 0 :value :value :value] :? pos?
            [#(outputs % "read-latency-p50") count] := 2
            [#(outputs % "read-latency-p50") 0 :extension 0 :value] := #fhir/positiveInt 1
            [#(outputs % "read-latency-p50") 0 :value :value :value] :? pos?
            [#(outputs % "read-latency-p50") 0 :value :code] := #fhir/code "us"
            [#(outputs % "read-latency-p95") count] := 2
            [#(outputs % "read-latency-p99") count] := 2
            [#(outputs % "read-latency-max") count] := 2
            [#(output-value % "fsync-rate") :value :value] :? pos?
            [#(output-value % "fsync-latency-p50") :value :value] :? pos?
            [#(output-value % "fsync-latency-p95") :value :value] :? pos?
            [#(output-value % "fsync-latency-p99") :value :value] :? pos?
            [#(output-value % "direct-io")] :? #(contains? #{#fhir/boolean true #fhir/boolean false} %)
            [#(output-value % "processing-duration") :value :value] :? #(and (decimal? %) (pos? %))
            [#(output-value % "processing-duration") :code] := #fhir/code "s")

          (testing "the phase outputs are removed"
            (is (nil? (output-value job "current-phase")))
            (is (nil? (output-value job "phase-progress"))))

          (testing "the response resource is a Parameters resource with the results"
            (given (job-util/response-resource job)
              :fhir/type := :fhir/Parameters
              [:parameter count] := 11
              [:parameter 0 :name] := #fhir/string "seq-write-throughput"
              [:parameter 0 :value :code] := #fhir/code "By/s"
              [:parameter 0 :value :value :value] :? pos?
              [:parameter 1 :name] := #fhir/string "rand-read"
              [:parameter 1 :part 0 :name] := #fhir/string "concurrency"
              [:parameter 1 :part 0 :value] := #fhir/positiveInt 1
              [:parameter 1 :part 1 :name] := #fhir/string "iops"
              [:parameter 1 :part 1 :value :code] := #fhir/code "/s"
              [:parameter 1 :part 1 :value :value :value] :? pos?
              [:parameter 1 :part 2 :name] := #fhir/string "throughput"
              [:parameter 1 :part 2 :value :code] := #fhir/code "By/s"
              [:parameter 1 :part 3 :name] := #fhir/string "latency-p50"
              [:parameter 1 :part 3 :value :code] := #fhir/code "us"
              [:parameter 1 :part 4 :name] := #fhir/string "latency-p95"
              [:parameter 1 :part 5 :name] := #fhir/string "latency-p99"
              [:parameter 1 :part 6 :name] := #fhir/string "latency-max"
              [:parameter 2 :name] := #fhir/string "rand-read"
              [:parameter 2 :part 0 :name] := #fhir/string "concurrency"
              [:parameter 2 :part 0 :value] := #fhir/positiveInt 2
              [:parameter 2 :part 1 :value :value :value] :? pos?
              [:parameter 3 :name] := #fhir/string "fsync-rate"
              [:parameter 4 :name] := #fhir/string "fsync-latency-p50"
              [:parameter 5 :name] := #fhir/string "fsync-latency-p95"
              [:parameter 6 :name] := #fhir/string "fsync-latency-p99"
              [:parameter 7 :name] := #fhir/string "direct-io"
              [:parameter 8 :name] := #fhir/string "score"
              [:parameter 8 :value :value] :? #(and (decimal? %) (<= 0M % 100M))
              [:parameter 9 :name] := #fhir/string "rating"
              [:parameter 10 :name] := #fhir/string "processing-duration"
              [:parameter 10 :value :code] := #fhir/code "s"))))

      (testing "job history starts with ready and started and ends with completed"
        (let [history @(jtu/pull-job-history system)]
          (given history
            [0 jtu/combined-status] := :ready
            [1 jtu/combined-status] := :in-progress/started)
          (is (= :completed (jtu/combined-status (last history))))))))

  (testing "with default parameters for max concurrency"
    (with-system [{:blaze/keys [job-scheduler] :as system} (config (tmp-dir))]

      @(js/create-job job-scheduler (job-disk-perf/job (time/offset-date-time)
                                                       (dissoc tiny-params :max-concurrency)))

      (testing "the job is completed with a sweep up to the default of 32"
        (given @(jtu/pull-job system :completed)
          jtu/combined-status := :completed
          [#(outputs % "read-iops") count] := 6
          [#(outputs % "read-iops") 0 :extension 0 :value] := #fhir/positiveInt 1
          [#(outputs % "read-iops") 5 :extension 0 :value] := #fhir/positiveInt 32
          [#(outputs % "read-iops") 5 :value :value :value] :? pos?))))

  (testing "no local storage directory"
    (with-system [{:blaze/keys [job-scheduler] :as system}
                  (assoc-in (config (tmp-dir)) [:blaze.job/disk-perf :dirs] {})]

      @(js/create-job job-scheduler (job-disk-perf/job (time/offset-date-time) tiny-params))

      (testing "the job has failed"
        (given @(jtu/pull-job system :failed)
          jtu/combined-status := :failed
          job-util/error-msg := "No local storage directory available for the `index` database."))))

  (testing "unknown database"
    (with-system [{:blaze/keys [job-scheduler] :as system} (config (tmp-dir))]

      @(js/create-job job-scheduler (job-disk-perf/job (time/offset-date-time)
                                                       (assoc tiny-params :database "resource")))

      (testing "the job has failed"
        (given @(jtu/pull-job system :failed)
          jtu/combined-status := :failed
          job-util/error-msg := "No local storage directory available for the `resource` database."))))

  ;; the out-of-range jobs below can't be built via job-disk-perf/job because
  ;; its params spec rejects them, so a valid job is built first and the input
  ;; value replaced afterwards, like a client bypassing validation could
  ;; submit it via the $disk-perf operation
  (testing "invalid file size"
    (with-system [{:blaze/keys [job-scheduler] :as system} (config (tmp-dir))]

      @(js/create-job job-scheduler
                      (-> (job-disk-perf/job (time/offset-date-time) tiny-params)
                          (assoc-in [:input 0 :value :value] #fhir/decimal 100M)))

      (testing "the job has failed"
        (given @(jtu/pull-job system :failed)
          jtu/combined-status := :failed
          job-util/error-msg := "Invalid `file-size` parameter 100 GiB. The file size has to be between 1 MiB and 64 GiB."))))

  (testing "invalid phase duration"
    (with-system [{:blaze/keys [job-scheduler] :as system} (config (tmp-dir))]

      @(js/create-job job-scheduler
                      (-> (job-disk-perf/job (time/offset-date-time) tiny-params)
                          (assoc-in [:input 1 :value :value] #fhir/decimal 0M)))

      (testing "the job has failed"
        (given @(jtu/pull-job system :failed)
          jtu/combined-status := :failed
          job-util/error-msg := "Invalid `phase-duration` parameter 0 s. The phase duration has to be between 0 and 300 seconds."))))

  (testing "invalid max concurrency"
    (with-system [{:blaze/keys [job-scheduler] :as system} (config (tmp-dir))]

      @(js/create-job job-scheduler
                      (-> (job-disk-perf/job (time/offset-date-time) tiny-params)
                          (assoc-in [:input 2 :value] #fhir/positiveInt 1025)))

      (testing "the job has failed"
        (given @(jtu/pull-job system :failed)
          jtu/combined-status := :failed
          job-util/error-msg := "Invalid `max-concurrency` parameter 1025. The maximum concurrency has to be between 1 and 1024.")))))

(defn- pull-job-in-phase*
  [{:blaze.db.admin/keys [node] :as system} id phase]
  (-> (d/pull node (d/resource-handle (d/db node) "Task" id))
      (ac/then-compose-async
       (fn [job]
         (if (= phase (:value (output-value job "current-phase")))
           (ac/completed-future job)
           (pull-job-in-phase* system id phase)))
       (ac/delayed-executor 10 TimeUnit/MILLISECONDS))))

(defn- pull-job-in-phase
  "Waits until the job with `id` is in `phase` or 10 seconds are eclipsed.

  Pausing a job right after the first progress update races the burst of
  progress updates at the start of the benchmark, because pausing doesn't
  retry on concurrent update conflicts. Once the rand-read phase is reached,
  the updates settle to one per 10 % of the sweep, so a pause doesn't race a
  concurrent update."
  [system id phase]
  (-> (pull-job-in-phase* system id phase)
      (ac/or-timeout! 10 TimeUnit/SECONDS)))

(deftest pause-resume-test
  (with-system [{:blaze/keys [job-scheduler] :as system} (config (tmp-dir))]

    (let [{:keys [id]} @(js/create-job
                         job-scheduler
                         (job-disk-perf/job (time/offset-date-time)
                                            (assoc tiny-params :phase-duration 10M)))]

      (testing "the job is running"
        (given @(jtu/pull-job system :in-progress/incremented)
          jtu/combined-status := :in-progress/incremented))

      @(pull-job-in-phase system id "rand-read")

      (testing "the job can be paused"
        (given @(js/pause-job job-scheduler id)
          jtu/combined-status := :on-hold/paused))

      (testing "resuming restarts the benchmark"
        (given @(js/resume-job job-scheduler id)
          jtu/combined-status := :in-progress/resumed))

      @(js/cancel-job job-scheduler id)

      (testing "the job ends up cancelled with finished sub status"
        (given @(jtu/pull-job system :cancelled/finished)
          jtu/combined-status := :cancelled/finished)))))

(deftest cancellation-test
  (with-system [{:blaze/keys [job-scheduler] :as system} (config (tmp-dir))]

    (let [{:keys [id]} @(js/create-job
                         job-scheduler
                         (job-disk-perf/job (time/offset-date-time)
                                            (assoc tiny-params :phase-duration 10M)))]

      (testing "the job is running"
        (given @(jtu/pull-job system :in-progress/incremented)
          jtu/combined-status := :in-progress/incremented))

      @(js/cancel-job job-scheduler id)

      (testing "the job ends up cancelled with finished sub status"
        (given @(jtu/pull-job system :cancelled/finished)
          jtu/combined-status := :cancelled/finished)))))
