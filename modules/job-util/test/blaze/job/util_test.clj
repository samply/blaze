(ns blaze.job.util-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.api-stub :as api-stub :refer [with-db with-system-data]]
   [blaze.fhir.canonical :as canonical]
   [blaze.fhir.spec.type :as type]
   [blaze.job-scheduler :as-alias js]
   [blaze.job.test-util :as jtu]
   [blaze.job.util :as job-util]
   [blaze.job.util-spec]
   [blaze.test-util :as tu :refer [given-failed-future with-global-log-capture]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest job-number-test
  (doseq [system (canonical/urls "sid/JobNumber")]
    (is (= "174731"
           (job-util/job-number
            {:fhir/type :fhir/Task
             :identifier
             [(type/identifier {:system (type/uri system)
                                :value #fhir/string "174731"})]})))))

(deftest code-value-test
  (testing "matches by system value"
    (is (= "bar" (job-util/code-value "foo" (jtu/concept "foo" "bar"))))
    (is (nil? (job-util/code-value "foo" (jtu/concept "bar" "baz")))))

  (testing "accepts the legacy system when given the current canonical"
    (doseq [system (canonical/urls "CodeSystem/JobType")]
      (is (= "x" (job-util/code-value
                  (canonical/url "CodeSystem/JobType")
                  (jtu/concept system "x")))))))

(deftest type-codeable-concept-test
  (testing "carries both JobType systems with code and display, current first"
    (given (:coding (job-util/type-codeable-concept "compact" "Compact a Database Column Family"))
      [0 :system] := #fhir/uri "https://blaze-server.org/fhir/CodeSystem/JobType"
      [0 :code] := #fhir/code "compact"
      [0 :display] := #fhir/string "Compact a Database Column Family"
      [1 :system] := #fhir/uri "https://samply.github.io/blaze/fhir/CodeSystem/JobType"
      [1 :code] := #fhir/code "compact"
      [1 :display] := #fhir/string "Compact a Database Column Family")))

(deftest job-type-test
  (doseq [system (canonical/urls "CodeSystem/JobType")]
    (is (= :type-140532
           (job-util/job-type
            {:fhir/type :fhir/Task
             :code (jtu/concept system "type-140532")})))))

(deftest status-reason-test
  (doseq [system (canonical/urls "CodeSystem/JobStatusReason")]
    (is (= "reason-175220"
           (job-util/status-reason
            {:fhir/type :fhir/Task
             :statusReason (jtu/concept system "reason-175220")})))))

(deftest cancelled-sub-status-test
  (doseq [system (canonical/urls "CodeSystem/JobCancelledSubStatus")]
    (is (= "sub-status-161316"
           (job-util/cancelled-sub-status
            {:fhir/type :fhir/Task
             :businessStatus (jtu/concept system "sub-status-161316")})))))

(deftest status-reason-emits-both-codings-test
  (testing "the predefined status reasons carry both systems, current first"
    (given (:coding job-util/started-status-reason)
      [0 :system] := #fhir/uri "https://blaze-server.org/fhir/CodeSystem/JobStatusReason"
      [0 :code] := #fhir/code "started"
      [1 :system] := #fhir/uri "https://samply.github.io/blaze/fhir/CodeSystem/JobStatusReason"
      [1 :code] := #fhir/code "started")))

(deftest cancelled-sub-status-emits-both-codings-test
  (testing "the predefined sub statuses carry both systems, current first"
    (given (:coding job-util/cancellation-requested-sub-status)
      [0 :system] := #fhir/uri "https://blaze-server.org/fhir/CodeSystem/JobCancelledSubStatus"
      [0 :code] := #fhir/code "requested"
      [1 :system] := #fhir/uri "https://samply.github.io/blaze/fhir/CodeSystem/JobCancelledSubStatus"
      [1 :code] := #fhir/code "requested")))

(deftest task-output-emits-both-codings-test
  (testing "a Blaze output system yields both codings, current first"
    (given (:coding (:type (job-util/task-output
                            (canonical/url "CodeSystem/JobOutput") "error"
                            #fhir/string "msg")))
      [0 :system] := #fhir/uri "https://blaze-server.org/fhir/CodeSystem/JobOutput"
      [0 :code] := #fhir/code "error"
      [1 :system] := #fhir/uri "https://samply.github.io/blaze/fhir/CodeSystem/JobOutput"
      [1 :code] := #fhir/code "error")))

(deftest input-value-test
  (is (= (job-util/input-value
          {:fhir/type :fhir/Task
           :input
           [{:fhir/type :fhir.Task/input
             :type (jtu/concept "foo" "other")
             :value #fhir/string "other"}
            {:fhir/type :fhir.Task/input
             :type (jtu/concept "foo" "bar")
             :value #fhir/code "baz"}]}
          "foo" "bar")
         #fhir/code "baz")))

(deftest output-value-test
  (is (= (job-util/output-value
          {:fhir/type :fhir/Task
           :output
           [(job-util/task-output "foo" "other" #fhir/string "other")
            (job-util/task-output "foo" "bar" #fhir/code "baz")]}
          "foo" "bar")
         #fhir/code "baz"))

  (testing "with default output system"
    (is (= (job-util/output-value
            {:fhir/type :fhir/Task
             :output
             [(job-util/task-output "foo" "other" #fhir/string "other")
              (job-util/task-output "https://samply.github.io/blaze/fhir/CodeSystem/JobOutput" "bar" #fhir/code "baz")]}
            "bar")
           #fhir/code "baz"))))

(deftest outputs-test
  (is (= (job-util/outputs
          {:fhir/type :fhir/Task
           :output
           [(job-util/task-output "foo" "other" #fhir/string "other")
            (job-util/task-output "foo" "bar" #fhir/code "baz")
            (job-util/task-output "foo" "bar" #fhir/code "qux")]}
          "foo" "bar")
         [(job-util/task-output "foo" "bar" #fhir/code "baz")
          (job-util/task-output "foo" "bar" #fhir/code "qux")]))

  (testing "without matching outputs"
    (is (= (job-util/outputs {:fhir/type :fhir/Task} "foo" "bar") []))))

(deftest error-msg-test
  (is (= (job-util/error-msg
          {:fhir/type :fhir/Task
           :output
           [(job-util/task-output "https://samply.github.io/blaze/fhir/CodeSystem/JobOutput" "error" #fhir/string "msg-175657")]})
         "msg-175657")))

(deftest update-output-value-test
  (is (= (job-util/update-output-value
          {:fhir/type :fhir/Task
           :output
           [(job-util/task-output "foo" "other" #fhir/string "other")
            (job-util/task-output "foo" "bar" #fhir/integer 1)]}
          "foo" "bar"
          (fn [value x]
            (type/integer (+ (:value value) x)))
          1)
         {:fhir/type :fhir/Task
          :output
          [(job-util/task-output "foo" "other" #fhir/string "other")
           (job-util/task-output "foo" "bar" #fhir/integer 2)]})))

(deftest remove-output-test
  (is (= (job-util/remove-output
          {:fhir/type :fhir/Task
           :output
           [(job-util/task-output "foo" "other" #fhir/string "other")
            (job-util/task-output "foo" "bar" #fhir/integer 1)]}
          "foo" "bar")
         {:fhir/type :fhir/Task
          :output
          [(job-util/task-output "foo" "other" #fhir/string "other")]})))

(def ^:private ready-task
  {:fhir/type :fhir/Task :id "0" :status #fhir/code "ready"})

(def ^:private incremented-task
  {:fhir/type :fhir/Task
   :id "0"
   :status #fhir/code "in-progress"
   :statusReason job-util/incremented-status-reason})

(defn- start-job [job]
  (assoc job
         :status #fhir/code "in-progress"
         :statusReason job-util/started-status-reason))

(deftest pull-job-test
  (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
    [[[:put {:fhir/type :fhir/Task :id "0"}]]]

    (given @(job-util/pull-job node "0")
      :fhir/type := :fhir/Task
      :id := "0")))

(deftest update-job-test
  (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
    [[[:put ready-task]]]

    (let [job @(job-util/pull-job node "0")]

      (testing "start job"
        (let [job @(job-util/update-job node job start-job)]
          (given job
            :status := #fhir/code "in-progress")

          (testing "fail job"
            (given @(job-util/update-job node job job-util/fail-job (ba/fault "msg-181135"))
              :status := #fhir/code "failed"
              job-util/error-msg := "msg-181135"))))))

  (testing "lost updates are detected"
    (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
      [[[:put ready-task]]]

      (let [job @(job-util/pull-job node "0")]

        (testing "start job"
          (given @(job-util/update-job node job start-job)
            :status := #fhir/code "in-progress"))

        (testing "start job again fails"
          (given-failed-future (job-util/update-job node job start-job)
            ::anom/category := ::anom/conflict
            ::anom/message := "Precondition `W/\"1\"` failed on `Task/0`."
            job-util/job-update-failed? := true))

        (testing "fail job again fails"
          (given-failed-future (job-util/update-job node job job-util/fail-job (ba/fault "msg-181135"))
            ::anom/category := ::anom/conflict
            ::anom/message := "Precondition `W/\"1\"` failed on `Task/0`."
            job-util/job-update-failed? := true))))))

(deftest update-job-plus-test
  (testing "without other resources"
    (testing "with no argument"
      (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
        [[[:put ready-task]]]

        (let [job @(job-util/pull-job node "0")]

          (given @(job-util/update-job+ node job nil start-job)
            :status := #fhir/code "in-progress"))))

    (testing "with one argument"
      (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
        [[[:put ready-task]]]

        (let [job @(job-util/pull-job node "0")]

          (given @(job-util/update-job+ node job nil job-util/fail-job (ba/fault "msg-162452"))
            :status := #fhir/code "failed"
            job-util/error-msg := "msg-162452"))))

    (testing "with two arguments"
      (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
        [[[:put ready-task]]]

        (let [job @(job-util/pull-job node "0")]

          (given @(job-util/update-job+ node job nil (fn [job status reason] (assoc job :status (type/code status) :statusReason reason)) "in-progress" job-util/started-status-reason)
            :status := #fhir/code "in-progress"
            job-util/status-reason := "started")))))

  (testing "with one Bundle"
    (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
      [[[:put ready-task]]]

      (let [job @(job-util/pull-job node "0")]

        (given @(job-util/update-job+ node job [{:fhir/type :fhir/Bundle :id "0"}]
                                      job-util/fail-job (ba/fault "msg-181135"))
          :status := #fhir/code "failed"
          job-util/error := (ba/fault "msg-181135"))

        (with-db [db node]
          (is (some? (d/resource-handle db "Bundle" "0"))))))))

(defn- start-job* [{:keys [status] :as job}]
  (if (= "ready" (:value status))
    (start-job job)
    (ba/conflict "already started")))

(defn- add-unknown-bundle-reference [job]
  (job-util/add-output job "foo" "bar" #fhir/Reference {:reference #fhir/string "Bundle/unknown"}))

(deftest update-job-with-retry-test
  (testing "retry"
    (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
      [[[:put ready-task]]]

      (given @(job-util/update-job-with-retry node 1 "0" start-job*)
        [:meta :versionId] := #fhir/id "2"
        :status := #fhir/code "in-progress")

      (testing "fails because it's already started"
        (given-failed-future (job-util/update-job-with-retry node 1 "0" start-job*)
          ::anom/category := ::anom/conflict
          ::anom/message := "already started"
          job-util/job-update-failed? := true))))

  (testing "referential integrity problem"
    (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
      [[[:put incremented-task]]]

      (given-failed-future (job-util/update-job-with-retry node 1 "0" add-unknown-bundle-reference)
        ::anom/category := ::anom/conflict
        ::anom/message := "Referential integrity violated. Resource `Bundle/unknown` doesn't exist."))))

(def ^:private mask-anomaly vector)

(defn- start-job-with-retry [node id]
  (-> (job-util/update-job-with-retry node 5 id start-job*)
      (ac/exceptionally mask-anomaly)))

(defn- increment-unsigned-int [value n]
  (type/unsignedInt (+ (:value value) n)))

(defn- increment-count [job]
  (job-util/update-output-value job "my" "count" increment-unsigned-int 1))

(defn- increment-count-with-retry [node n id]
  (job-util/update-job-with-retry node n id increment-count))

(defn- get-count [job]
  (job-util/output-value job "my" "count"))

(deftest update-job-with-retry-concurrent-test
  (testing "single status change"
    (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
      [[[:put ready-task]]]

      (let [futures (repeatedly 5 #(start-job-with-retry node "0"))]
        @(ac/all-of futures)
        (let [{jobs true anomalies false} (group-by map? (map ac/join futures))]
          (is (= 1 (count jobs)))
          (is (= #fhir/code "in-progress" (:status (first jobs))))
          (is (every? (comp #{"already started"} ::anom/message) (map first anomalies)))))))

  (testing "multiple counter increments"
    (testing "are all sucessful"
      (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
        [[[:put (job-util/add-output incremented-task "my" "count" #fhir/unsignedInt 0)]]]

        (let [futures (repeatedly 5 #(increment-count-with-retry node 4 "0"))]
          @(ac/all-of futures)
          (let [jobs (map ac/join futures)]
            (is (= 5 (count jobs)))
            (is (= #{1 2 3 4 5} (into #{} (map (comp :value get-count)) jobs)))))))

    (testing "at least one fails"
      (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
        [[[:put (job-util/add-output incremented-task "my" "count" #fhir/unsignedInt 0)]]]

        (given-failed-future (ac/all-of (repeatedly 50 #(increment-count-with-retry node 2 "0")))
          ::anom/category := ::anom/conflict
          :http/status := 412)))))

(defn- set-state
  "Returns `job` in `state`, a keyword of the status of the job and the status
  reason, or the sub status of a cancelled job, as namespace, like
  `:in-progress/started`."
  [job state]
  (let [status (or (namespace state) (name state))
        reason (when (namespace state) (name state))]
    (cond-> (-> (assoc job :status (type/code status))
                (dissoc :statusReason :businessStatus))
      (and reason (= "cancelled" status))
      (assoc :businessStatus
             (jtu/concept (canonical/url "CodeSystem/JobCancelledSubStatus") reason))
      (and reason (not= "cancelled" status))
      (assoc :statusReason
             (jtu/concept (canonical/url "CodeSystem/JobStatusReason") reason)))))

(defn- state [{{status :value} :status :as job}]
  (if-let [reason (or (job-util/status-reason job)
                      (job-util/cancelled-sub-status job))]
    (keyword status reason)
    (keyword status)))

(def ^:private allowed-transitions
  [[:ready :in-progress/started]
   [:ready :cancelled/finished]
   [:ready :failed]
   [:in-progress/started :in-progress/incremented]
   [:in-progress/started :on-hold/paused]
   [:in-progress/started :on-hold/orderly-shutdown]
   [:in-progress/started :cancelled/requested]
   [:in-progress/started :completed]
   [:in-progress/started :failed]
   [:in-progress/incremented :in-progress/incremented]
   [:in-progress/incremented :on-hold/paused]
   [:in-progress/incremented :on-hold/orderly-shutdown]
   [:in-progress/incremented :cancelled/requested]
   [:in-progress/incremented :completed]
   [:in-progress/incremented :failed]
   [:in-progress/resumed :in-progress/incremented]
   [:in-progress/resumed :on-hold/paused]
   [:in-progress/resumed :on-hold/orderly-shutdown]
   [:in-progress/resumed :cancelled/requested]
   [:in-progress/resumed :completed]
   [:in-progress/resumed :failed]
   [:on-hold/paused :in-progress/resumed]
   [:on-hold/paused :cancelled/finished]
   [:on-hold/paused :failed]
   [:on-hold/orderly-shutdown :in-progress/resumed]
   [:on-hold/orderly-shutdown :cancelled/finished]
   [:on-hold/orderly-shutdown :failed]
   [:cancelled/requested :cancelled/finished]])

(def ^:private disallowed-transitions
  [[:ready :ready]
   [:ready :in-progress/resumed]
   [:ready :cancelled/requested]
   [:ready :completed]
   [:in-progress/started :in-progress/started]
   [:in-progress/resumed :in-progress/started]
   [:in-progress/resumed :in-progress/resumed]
   [:in-progress/started :cancelled/finished]
   [:on-hold/paused :in-progress/started]
   [:on-hold/paused :cancelled/requested]
   [:on-hold/paused :completed]
   [:cancelled/requested :cancelled/requested]
   [:cancelled/requested :completed]
   [:cancelled/requested :failed]
   [:cancelled/finished :cancelled/finished]
   [:cancelled/finished :failed]
   [:completed :failed]
   [:failed :completed]])

(defn- state-str [state]
  (if-let [status (namespace state)]
    (str status "/" (name state))
    (name state)))

(defn- transition-conflict-msg [from to]
  (format "Job `0` can't transition from `%s` to `%s`."
          (state-str from) (state-str to)))

(defn- transition-bug-msg [from to]
  (str (transition-conflict-msg from to)
       " This is a bug in Blaze. Please report it at https://github.com/samply/blaze/issues."))

(defn- count-logged
  "Calls `f` and returns the number of log entries whose first vararg equals
  `msg`, logged from any thread while `f` runs."
  [msg f]
  (let [n (atom 0)
        key (keyword "blaze.job.util-test" (name (gensym "count-")))]
    (log/swap-config!
     assoc-in [:appenders key]
     {:enabled? true
      :fn (fn [{:keys [vargs]}]
            (when (= msg (first vargs))
              (swap! n inc)))})
    (try
      (f)
      @n
      (finally
        (log/swap-config! update :appenders dissoc key)))))

(defn- start-job-after-concurrent-update
  "Returns a function for `update-job-with-retry` that updates the job
  concurrently on its first call, so that the update is retried, and starts the
  job on the retry, which isn't an allowed transition."
  [node]
  (let [calls (atom 0)]
    (fn [job]
      (if (= 1 (swap! calls inc))
        (do @(d/transact node [[:put job]])
            job)
        (start-job job)))))

(deftest update-job-transition-test
  (testing "allowed transitions"
    (doseq [[from to] allowed-transitions]
      (testing (str from " -> " to)
        (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
          [[[:put (set-state {:fhir/type :fhir/Task :id "0"} from)]]]

          (given @(job-util/update-job node @(job-util/pull-job node "0") set-state to)
            state := to)))))

  (testing "disallowed transitions"
    (doseq [[from to] disallowed-transitions]
      (testing (str from " -> " to)
        (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
          [[[:put (set-state {:fhir/type :fhir/Task :id "0"} from)]]]

          (with-global-log-capture [logged (transition-bug-msg from to)]
            (given-failed-future (job-util/update-job node @(job-util/pull-job node "0") set-state to)
              ::anom/category := ::anom/conflict
              ::anom/message := (transition-conflict-msg from to)
              :job/from := (state-str from)
              :job/to := (state-str to)
              ::js/action := :transition-job
              job-util/job-update-failed? := false)

            (testing "the transition conflict is logged as bug"
              (given (deref logged 1000 ::not-logged)
                :level := :error)))

          (testing "the job stays unchanged"
            (given @(job-util/pull-job node "0")
              state := from
              [:meta :versionId] := #fhir/id "1"))))))

  (testing "a job without status can't be updated"
    (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
      [[[:put {:fhir/type :fhir/Task :id "0"}]]]

      (given-failed-future (job-util/update-job node @(job-util/pull-job node "0") start-job)
        ::anom/category := ::anom/conflict
        ::anom/message := "Job `0` can't transition from `` to `in-progress/started`."
        :job/from := ""
        :job/to := "in-progress/started")))

  (testing "update-job+ checks the transition"
    (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
      [[[:put (set-state {:fhir/type :fhir/Task :id "0"} :in-progress/resumed)]]]

      (given-failed-future (job-util/update-job+ node @(job-util/pull-job node "0")
                                                 [{:fhir/type :fhir/Bundle :id "0"}]
                                                 start-job)
        ::anom/category := ::anom/conflict
        ::anom/message := (transition-conflict-msg :in-progress/resumed :in-progress/started)
        ::js/action := :transition-job
        job-util/job-update-failed? := false)

      (testing "the other resources aren't created"
        (with-db [db node]
          (is (nil? (d/resource-handle db "Bundle" "0")))))))

  (testing "update-job-with-retry checks the transition"
    (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
      [[[:put (set-state {:fhir/type :fhir/Task :id "0"} :in-progress/resumed)]]]

      (given-failed-future (job-util/update-job-with-retry node 5 "0" start-job)
        ::anom/category := ::anom/conflict
        ::anom/message := (transition-conflict-msg :in-progress/resumed :in-progress/started)
        ::js/action := :transition-job
        job-util/job-update-failed? := false)))

  (testing "update-job-with-retry logs a transition conflict after a retry only once"
    (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
      [[[:put (set-state {:fhir/type :fhir/Task :id "0"} :in-progress/incremented)]]]

      (let [msg (transition-bug-msg :in-progress/incremented :in-progress/started)
            n (count-logged
               msg
               #(given-failed-future
                 (job-util/update-job-with-retry
                  node 5 "0" (start-job-after-concurrent-update node))
                  ::anom/category := ::anom/conflict
                  ::js/action := :transition-job))]
        (is (= 1 n))))))

(deftest ended?-test
  (testing "ended jobs"
    (doseq [state [:completed :failed :cancelled/finished]]
      (testing state
        (is (true? (job-util/ended? (set-state {:fhir/type :fhir/Task} state)))))))

  (testing "jobs that haven't ended"
    (doseq [state [:ready :in-progress/started :in-progress/incremented
                   :in-progress/resumed :on-hold/paused :on-hold/orderly-shutdown
                   :cancelled/requested]]
      (testing state
        (is (false? (job-util/ended? (set-state {:fhir/type :fhir/Task} state))))))))

(deftest job-update-failed-test
  (are [anomaly] (false? (job-util/job-update-failed? anomaly))
    (ba/fault)
    (ba/conflict "foo")))

(deftest fail-job-test
  (testing "without message"
    (given (job-util/fail-job {:fhir/type :fhir/Task} (ba/fault))
      job-util/error-msg := "empty error message"))

  (testing "a running job fails"
    (given (job-util/fail-job (set-state {:fhir/type :fhir/Task} :in-progress/started)
                              (ba/fault "msg-162512"))
      state := :failed
      job-util/error := (ba/fault "msg-162512")))

  (testing "a job whose cancellation was requested gets its cancellation finished"
    (given (job-util/fail-job (set-state {:fhir/type :fhir/Task} :cancelled/requested)
                              (ba/incorrect "msg-162538"))
      state := :cancelled/finished
      job-util/error := (ba/incorrect "msg-162538"))))

(deftest finish-cancellation-test
  (given (job-util/finish-cancellation (set-state {:fhir/type :fhir/Task} :cancelled/requested))
    state := :cancelled/finished))

(deftest async-status-url-test
  (testing "with context path"
    (is (= "http://localhost:8080/fhir/__async-status/AAAAAAAAAAAAAAAA"
           (job-util/async-status-url
            {:context-path "/fhir"}
            {:blaze/base-url "http://localhost:8080"}
            {:fhir/type :fhir/Task :id "AAAAAAAAAAAAAAAA"}))))

  (testing "without context path"
    (is (= "http://localhost:8080/__async-status/AAAAAAAAAAAAAAAA"
           (job-util/async-status-url
            {}
            {:blaze/base-url "http://localhost:8080"}
            {:fhir/type :fhir/Task :id "AAAAAAAAAAAAAAAA"})))))

(deftest response-resource-test
  (testing "job types without an implementation have no response resource"
    (is (nil? (job-util/response-resource
               {:fhir/type :fhir/Task
                :code (job-util/type-codeable-concept "foo" "Foo")})))))

(deftest mark-running-test
  (testing "a job not requested to stop is marked as running"
    (is (= {"0" false} (job-util/mark-running {} "0"))))

  (testing "a stop request is kept"
    (are [reason] (= {"0" reason} (job-util/mark-running {"0" reason} "0"))
      :cancelled
      :paused
      :shutdown)))

(deftest stopped-on-hold?-test
  (testing "a job not requested to stop by being put on-hold"
    (are [running-jobs] (not (job-util/stopped-on-hold? running-jobs "0"))
      {}
      {"0" false}
      {"0" :cancelled}))

  (testing "a job requested to stop by being put on-hold"
    (are [reason] (job-util/stopped-on-hold? {"0" reason} "0")
      :paused
      :shutdown)))
