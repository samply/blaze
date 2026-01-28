(ns blaze.job.compact-test
  (:require
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
   [blaze.fhir.parsing-context]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.fhir.writing-context]
   [blaze.job-scheduler :as js]
   [blaze.job.compact :as job-compact]
   [blaze.job.compact-spec]
   [blaze.job.test-util :as jtu]
   [blaze.job.util :as job-util]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.terminology-service :as-alias ts]
   [blaze.terminology-service.not-available]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [integrant.core :as ig]
   [java-time.api :as time]
   [juxt.iota :refer [given]]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(def config
  {:blaze/job-scheduler
   {:node (ig/ref :blaze.db.admin/node)
    :handlers {:blaze.job/compact (ig/ref :blaze.job/compact)}
    :clock (ig/ref :blaze.test/offset-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}

   :blaze.job/compact
   {:index-db (ig/ref :blaze.db.main/index-kv-store)
    :admin-node (ig/ref :blaze.db.admin/node)
    :clock (ig/ref :blaze.test/offset-clock)}

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
    (given-failed-system {:blaze.job/compact nil}
      :key := :blaze.job/compact
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.job/compact {}}
      :key := :blaze.job/compact
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :index-db))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :admin-node))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :clock))))

  (testing "invalid index-db"
    (given-failed-system (assoc-in config [:blaze.job/compact :index-db] ::invalid)
      :key := :blaze.job/compact
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/kv-store]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(derive :blaze.db.admin/node :blaze.db/node)

(def job-missing-database
  {:fhir/type :fhir/Task
   :meta #fhir/Meta{:profile [#fhir/canonical "https://samply.github.io/blaze/fhir/StructureDefinition/CompactJob"]}
   :status #fhir/code "ready"
   :intent #fhir/code "order"
   :code
   #fhir/CodeableConcept
    {:coding
     [#fhir/Coding
       {:system #fhir/uri "https://samply.github.io/blaze/fhir/CodeSystem/JobType"
        :code #fhir/code "compact"
        :display #fhir/string "Compact a Database Column Family"}]}})

(def job-missing-column-family
  (assoc
   job-missing-database
   :input
   [{:fhir/type :fhir.Task/input
     :type #fhir/CodeableConcept
            {:coding
             [#fhir/Coding
               {:system #fhir/uri "https://samply.github.io/blaze/fhir/CodeSystem/CompactJobParameter"
                :code #fhir/code "database"}]}
     :value #fhir/code "index"}]))

(defn- output-value [job code]
  (job-util/output-value job "https://samply.github.io/blaze/fhir/CodeSystem/CompactJobOutput" code))

(defn- processing-duration [job]
  (output-value job "processing-duration"))

(deftest simple-job-execution-test
  (testing "success"
    (testing "increment three times, once for each index"
      (with-system [{:blaze/keys [job-scheduler] :as system} config]

        @(js/create-job job-scheduler (job-compact/job (time/offset-date-time) "index" "resource-as-of-index"))

        (testing "the job is completed"
          (given @(jtu/pull-job system :completed)
            :fhir/type := :fhir/Task
            job-util/job-number := "1"
            jtu/combined-status := :completed
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
            [2 jtu/combined-status] := :completed)))))

  (testing "missing database"
    (with-system [{:blaze/keys [job-scheduler] :as system} config]

      @(js/create-job job-scheduler job-missing-database)

      (testing "the job has failed"
        (given @(jtu/pull-job system :failed)
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          jtu/combined-status := :failed
          job-util/error-msg := "Missing `database` parameter."))))

  (testing "missing column-family"
    (with-system [{:blaze/keys [job-scheduler] :as system} config]

      @(js/create-job job-scheduler job-missing-column-family)

      (testing "the job has failed"
        (given @(jtu/pull-job system :failed)
          :fhir/type := :fhir/Task
          job-util/job-number := "1"
          jtu/combined-status := :failed
          job-util/error-msg := "Missing `column-family` parameter.")))))
