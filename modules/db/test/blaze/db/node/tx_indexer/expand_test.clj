(ns blaze.db.node.tx-indexer.expand-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.search-param-value-resource :as sp-vr]
   [blaze.db.impl.search-param.util :as u]
   [blaze.db.kv.mem]
   [blaze.db.kv.mem-spec]
   [blaze.db.node]
   [blaze.db.node.tx-indexer.expand :as expand]
   [blaze.db.node.tx-indexer.expand-spec]
   [blaze.db.node.tx-indexer.util :as tx-u]
   [blaze.db.search-param-registry]
   [blaze.db.test-util :refer [concurrent-calls? config internal-label
                               transact-internal with-system-data]]
   [blaze.db.tx-cache]
   [blaze.db.tx-log.local]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.hash-spec]
   [blaze.fhir.spec.spec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util.graph-definition :as g-tu]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu :refer [given-failed-future]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [java.util.concurrent ForkJoinPool ForkJoinWorkerThread TimeUnit]))

(set! *warn-on-reflection* true)

(st/instrument)
(log/set-min-level! :trace)
(tu/set-default-locale-english!)                            ; important for the thousands separator in 10,000

(test/use-fixtures :each tu/fixture)

(def patient-0 {:fhir/type :fhir/Patient :id "0"})

(defn- expand-tx-cmds
  "Expands `tx-cmds` against `node` with the stats and the pool the node itself
  keeps."
  [{:keys [stats tx-indexer-pool] :as node} tx-cmds]
  (d/with-db node #(expand/expand-tx-cmds {:db-before % :stats @stats
                                           :pool tx-indexer-pool}
                                          tx-cmds)))

(deftest expand-tx-cmds-conditional-create-test
  (testing "conflict"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :birthDate #fhir/date #system/date "2020"}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :birthDate #fhir/date #system/date "2020"}]]]

      (given-failed-future
       (expand-tx-cmds
        node
        [{:op "create" :type "Patient" :id "foo"
          :hash (hash/generate patient-0)
          :if-none-exist [["birthdate" "2020"]]}])
        ::anom/category := ::anom/conflict
        ::anom/message := "Conditional create of a Patient with query `birthdate=2020` failed because at least the two matches `Patient/0/_history/1` and `Patient/1/_history/1` were found."
        :http/status := 412)))
  (testing "match"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "2"
               :identifier [#fhir/Identifier{:value #fhir/string "120426"}]}]]]

      (given @(expand-tx-cmds
               node
               [{:op "create" :type "Patient" :id "0"
                 :hash (hash/generate patient-0)
                 :if-none-exist [["identifier" "120426"]]}])
        count := 1
        [0 :op] := "hold"
        [0 :type] := "Patient"
        [0 :id] := "2"
        [0 :if-none-exist] := [["identifier" "120426"]]))))

(deftest expand-tx-cmds-conditional-delete-test
  (testing "success"
    (testing "no match"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0"
                 :identifier [#fhir/Identifier{:value #fhir/string "120426"}]}]]]

        (is (empty? @(expand-tx-cmds
                      node
                      [{:op "conditional-delete" :type "Patient"
                        :clauses [["identifier" "foo"]]}])))))

    (testing "one patient match"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0"
                 :identifier [#fhir/Identifier{:value #fhir/string "120426"}]}]]]

        (given @(expand-tx-cmds
                 node
                 [{:op "conditional-delete" :type "Patient"
                   :clauses [["identifier" "120426"]]}])
          count := 1
          [0 :op] := "delete"
          [0 :type] := "Patient"
          [0 :id] := "0")))

    (testing "two patient matches"
      (testing "is forbidden by default"
        (with-system-data [{:blaze.db/keys [node]} config]
          [(vec (for [id ["0" "1"]]
                  [:create {:fhir/type :fhir/Patient :id id
                            :identifier [#fhir/Identifier{:value #fhir/string "120426"}]}]))]

          (given-failed-future
           (expand-tx-cmds node
                           [{:op "conditional-delete" :type "Patient"
                             :clauses [["identifier" "120426"]]}])
            ::anom/category := ::anom/conflict
            ::anom/message := "Conditional delete of one single Patient with query `identifier=120426` failed because at least the two matches `Patient/0/_history/1` and `Patient/1/_history/1` were found."
            :http/status := 412)))

      (testing "fails on more then 10,000 matches"
        (with-system-data [{:blaze.db/keys [node]} config]
          [(vec (for [id (range 10001)]
                  [:create {:fhir/type :fhir/Patient :id (str id)
                            :identifier [#fhir/Identifier{:value #fhir/string "120426"}]}]))]

          (given-failed-future
           (expand-tx-cmds node
                           [{:op "conditional-delete" :type "Patient"
                             :clauses [["identifier" "120426"]]
                             :allow-multiple true}])
            ::anom/category := ::anom/conflict
            ::anom/message := "Conditional delete of Patients with query `identifier=120426` failed because more than 10,000 matches were found."
            :fhir/issue "too-costly")))

      (testing "works if allowed"
        (with-system-data [{:blaze.db/keys [node]} config]
          [(vec (for [id ["0" "1"]]
                  [:create {:fhir/type :fhir/Patient :id id
                            :identifier [#fhir/Identifier{:value #fhir/string  "120426"}]}]))]

          (given @(expand-tx-cmds node
                                  [{:op "conditional-delete" :type "Patient"
                                    :clauses [["identifier" "120426"]]
                                    :allow-multiple true}])
            count := 2
            [0 :op] := "delete"
            [0 :type] := "Patient"
            [0 :id] := "0"
            [1 :op] := "delete"
            [1 :type] := "Patient"
            [1 :id] := "1"))))))

(deftest expand-tx-cmds-patient-purge-test
  (testing "empty database"
    (with-system [{:blaze.db/keys [node]} config]
      (is (empty? @(expand-tx-cmds node [{:op "patient-purge" :id "0"}])))))

  (testing "patient only"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]]]

      (given @(expand-tx-cmds node [{:op "patient-purge" :id "0"}])
        count := 1
        [0 :op] := "purge"
        [0 :type] := "Patient"
        [0 :id] := "0"
        [0 :check-refs] := false)

      (given @(expand-tx-cmds node [{:op "patient-purge" :id "0" :check-refs true}])
        count := 1
        [0 :op] := "purge"
        [0 :type] := "Patient"
        [0 :id] := "0"
        [0 :check-refs] := true)))

  (testing "patient with one observation"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]
        [:create {:fhir/type :fhir/Observation :id "0"
                  :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

      (given @(expand-tx-cmds node [{:op "patient-purge" :id "0"}])
        count := 2
        [0 :op] := "purge"
        [0 :type] := "Patient"
        [0 :id] := "0"
        [1 :op] := "purge"
        [1 :type] := "Observation"
        [1 :id] := "0"))))

(deftest expand-tx-cmds-patient-purge-purged-rev-include-params-test
  (with-system-data [{:blaze.db/keys [node]} config]
    [[[:create {:fhir/type :fhir/Patient :id "0"}]
      [:create {:fhir/type :fhir/Observation :id "0"
                :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
      [:create {:fhir/type :fhir/Encounter :id "0"
                :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

    (testing "the purge of the patient checking references carries the
              rev-include params of the links of the types with resources and
              of their twins, through which the transaction purges every
              resource referencing the patient"
      (given @(expand-tx-cmds node [{:op "patient-purge" :id "0" :check-refs true}])
        [0 :type] := "Patient"
        [0 :purged-rev-include-params]
        := #{(tx-u/rev-include-param "Observation" "subject")
             (tx-u/rev-include-param "Observation" "patient")
             (tx-u/rev-include-param "Observation" "performer")
             (tx-u/rev-include-param "Encounter" "patient")
             (tx-u/rev-include-param "Encounter" "subject")}
        [1 :purged-rev-include-params] := nil
        [2 :purged-rev-include-params] := nil))

    (testing "the purge of the patient not checking references carries none"
      (given @(expand-tx-cmds node [{:op "patient-purge" :id "0"}])
        [0 :type] := "Patient"
        [0 :purged-rev-include-params] := nil))))

(defn- read-link-tids
  "Returns the set of the tids whose links the expansion of `tx-cmds` against
  `node` reads."
  [node tx-cmds]
  (let [tids (atom #{})
        index-handles-full-value sp-vr/index-handles-full-value]
    (with-redefs [sp-vr/index-handles-full-value
                  (fn [snapshot c-hash tid & args]
                    (swap! tids conj tid)
                    (apply index-handles-full-value snapshot c-hash tid args))]
      @(expand-tx-cmds node tx-cmds))
    @tids))

(deftest expand-tx-cmds-patient-purge-empty-types-test
  (testing "the links of types without resources aren't read, because no
            resource of them can reference the patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]
        [:create {:fhir/type :fhir/Observation :id "0"
                  :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

      (is (= #{(codec/tid "Observation")}
             (read-link-tids node [{:op "patient-purge" :id "0"}]))))))

(defn- ids-after-patient
  "Returns the set of ids of the purge commands following the one of the
  patient, which are in no particular order."
  [[_ & cmds]]
  (into #{} (map :id) cmds))

(def ^:private purge-graph-def
  (-> (g-tu/graph-def
       :nodes [(g-tu/node "patient" "Patient")
               (g-tu/node "observation" "Observation")]
       :link [(g-tu/link "patient" "observation" :params "subject={ref}")])
      (assoc :url #fhir/uri "http://example.com/purge"
             :version #fhir/string "1"
             :meta (type/meta {:security [internal-label]}))))

(deftest expand-tx-cmds-patient-purge-graph-test
  (testing "only the observation with the patient as subject is purged"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]
        [:create {:fhir/type :fhir/Patient :id "1"}]
        [:create {:fhir/type :fhir/Observation :id "0"
                  :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
        [:create {:fhir/type :fhir/Observation :id "1"
                  :subject #fhir/Reference{:reference #fhir/string "Patient/1"}
                  :performer [#fhir/Reference{:reference #fhir/string "Patient/0"}]}]]]

      (transact-internal node [[:create purge-graph-def]])

      (given @(expand-tx-cmds node [{:op "patient-purge" :id "0"
                                     :graph "http://example.com/purge|1"
                                     :check-refs true}])
        count := 2
        [0] := {:op "purge" :type "Patient" :id "0" :check-refs true
                :purged-rev-include-params
                #{(tx-u/rev-include-param "Observation" "subject")
                  (tx-u/rev-include-param "Observation" "patient")}}
        [1] := {:op "purge" :type "Observation" :id "0" :check-refs true})

      (testing "the compartment selects both observations"
        (given @(expand-tx-cmds node [{:op "patient-purge" :id "0"}])
          count := 3
          [0 :type] := "Patient"
          ids-after-patient := #{"0" "1"})))))

(def ^:private subject-and-performer-graph-def
  (-> (g-tu/graph-def
       :nodes [(g-tu/node "patient" "Patient")
               (g-tu/node "observation" "Observation")]
       :link [(g-tu/link "patient" "observation" :params "subject={ref}")
              (g-tu/link "patient" "observation" :params "performer={ref}")])
      (assoc :url #fhir/uri "http://example.com/purge"
             :version #fhir/string "2"
             :meta (type/meta {:security [internal-label]}))))

(deftest expand-tx-cmds-patient-purge-several-links-test
  (testing "a resource referencing the patient through several links is
            selected only once"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]
        [:create {:fhir/type :fhir/Observation :id "0"
                  :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
                  :performer [#fhir/Reference{:reference #fhir/string "Patient/0"}]}]]]

      (transact-internal node [[:create subject-and-performer-graph-def]])

      (testing "by the compartment"
        (given @(expand-tx-cmds node [{:op "patient-purge" :id "0"}])
          count := 2
          [1] := {:op "purge" :type "Observation" :id "0" :check-refs false}))

      (testing "by a GraphDefinition with several links to the same type"
        (given @(expand-tx-cmds node [{:op "patient-purge" :id "0"
                                       :graph "http://example.com/purge|2"}])
          count := 2
          [1] := {:op "purge" :type "Observation" :id "0" :check-refs false})))))

(def ^:private delayed-executor
  (ac/delayed-executor 100 TimeUnit/MILLISECONDS))

(defn- slow-pull
  "Returns a function like `pull` whose futures complete on a thread of the
  common ForkJoinPool after a delay, like the pull of a resource that isn't
  cached.

  The delay lets the caller attach its continuations before the future
  completes, so that they run on the completing thread and not on the calling
  one."
  [pull]
  (fn [db resource-handle]
    (-> (pull db resource-handle)
        (ac/then-apply-async identity delayed-executor))))

(defn- worker-of?
  "Returns true if `thread` is a worker of `pool`."
  [pool thread]
  (and (instance? ForkJoinWorkerThread thread)
       (identical? pool (.getPool ^ForkJoinWorkerThread thread))))

(deftest expand-tx-cmds-patient-purge-slow-graph-test
  (testing "a GraphDefinition pulled asynchronously"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]
        [:create {:fhir/type :fhir/Observation :id "0"
                  :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

      (transact-internal node [[:create purge-graph-def]])

      (let [threads (atom #{})
            pull d/pull
            index-handles-full-value sp-vr/index-handles-full-value]
        (with-redefs [d/pull (slow-pull pull)
                      sp-vr/index-handles-full-value
                      (fn [& args]
                        (swap! threads conj (Thread/currentThread))
                        (apply index-handles-full-value args))]
          (let [future (expand-tx-cmds node [{:op "patient-purge" :id "0"
                                              :graph "http://example.com/purge|1"}])]

            (testing "completes the expansion before it returns, so that the
                      verification following it runs on the calling thread"
              (is (ac/done? future)))

            (given @future
              count := 2
              [1] := {:op "purge" :type "Observation" :id "0" :check-refs false})))

        (testing "selects the resources on the pool of the node and not on the
                  thread completing the pull, a thread of the common
                  ForkJoinPool"
          (is (some (partial worker-of? (:tx-indexer-pool node)) @threads))
          (is (not-any? (partial worker-of? (ForkJoinPool/commonPool)) @threads)))))))

(deftest expand-tx-cmds-patient-purge-parallel-links-test
  (testing "the index handles of the links are read in parallel, so that the
            expansion isn't limited by reading them one link after another"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]
        [:create {:fhir/type :fhir/Observation :id "0"
                  :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
        [:create {:fhir/type :fhir/Encounter :id "0"
                  :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

      (is (concurrent-calls?
           #'sp-vr/index-handles-full-value (constantly true)
           #(deref (expand-tx-cmds node [{:op "patient-purge" :id "0"}])))))))

(def ^:private num-large-compartment-observations
  "A number of Observations whose resource handles are resolved in several
  parts in parallel."
  2000)

(deftest expand-tx-cmds-patient-purge-large-compartment-test
  (with-system-data [{:blaze.db/keys [node]} config]
    [(into
      [[:create {:fhir/type :fhir/Patient :id "0"}]]
      (map
       (fn [i]
         [:create {:fhir/type :fhir/Observation :id (str i)
                   :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]))
      (range num-large-compartment-observations))]

    (testing "all observations are selected"
      (given @(expand-tx-cmds node [{:op "patient-purge" :id "0"}])
        count := 2001
        [0 :type] := "Patient"
        ids-after-patient := (set (map str (range num-large-compartment-observations)))))

    (testing "all observations are selected by a GraphDefinition with one link
              per type"
      (transact-internal node [[:create purge-graph-def]])

      (given @(expand-tx-cmds node [{:op "patient-purge" :id "0"
                                     :graph "http://example.com/purge|1"}])
        count := 2001
        [0 :type] := "Patient"
        ids-after-patient := (set (map str (range num-large-compartment-observations)))))

    (testing "the resource handles are resolved in parallel"
      (is (concurrent-calls?
           #'u/resource-handle-xf (constantly true)
           #(deref (expand-tx-cmds node [{:op "patient-purge" :id "0"}])))))))

(deftest expand-tx-cmds-patient-purge-small-links-test
  (testing "the resource handles of links that fit into one part each, but not
            all together, are resolved in parallel"
    (with-system-data [{:blaze.db/keys [node]} config]
      [(-> [[:create {:fhir/type :fhir/Patient :id "0"}]]
           (into
            (map
             (fn [i]
               [:create {:fhir/type :fhir/Observation :id (str i)
                         :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]))
            (range 300))
           (into
            (map
             (fn [i]
               [:create {:fhir/type :fhir/Encounter :id (str i)
                         :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]))
            (range 300)))]

      (let [cmds (atom nil)]
        (is (concurrent-calls?
             #'u/resource-handle-xf (constantly true)
             #(reset! cmds @(expand-tx-cmds node [{:op "patient-purge" :id "0"}]))))
        (given @cmds
          count := 601
          [0 :type] := "Patient")
        (is (= 300 (count (filter (comp #{"Observation"} :type) @cmds))))
        (is (= 300 (count (filter (comp #{"Encounter"} :type) @cmds))))))))
