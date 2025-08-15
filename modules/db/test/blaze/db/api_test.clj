(ns blaze.db.api-test
  "Main high-level test of all database API functions."
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.anomaly-spec]
   [blaze.async.comp :as ac]
   [blaze.async.comp-spec]
   [blaze.async.flow :as flow]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.db.api-spec]
   [blaze.db.impl.db-spec]
   [blaze.db.impl.index.resource-search-param-value-test-util :as r-sp-v-tu]
   [blaze.db.kv.mem-spec]
   [blaze.db.node :as node]
   [blaze.db.node-spec]
   [blaze.db.node.resource-indexer :as resource-indexer]
   [blaze.db.resource-store :as rs]
   [blaze.db.search-param-registry]
   [blaze.db.test-util :refer [config with-system-data]]
   [blaze.db.tx-log :as tx-log]
   [blaze.db.tx-log-spec]
   [blaze.db.tx-log.local-spec]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.generators :as fg]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.spec.type.system :as system]
   [blaze.module.test-util :as mtu :refer [given-failed-future with-system]]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [java-time.api :as time]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [blaze.db.impl.batch_db PatientTypeQuery]
   [com.google.common.base CaseFormat]
   [java.time Instant]
   [java.util.concurrent TimeUnit]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)
(tu/set-default-locale-english!)                            ; important for the thousands separator in 10,000

(test/use-fixtures :each tu/fixture)

(def ^:private delayed-executor
  (ac/delayed-executor 1 TimeUnit/SECONDS))

(defmethod ig/init-key ::slow-resource-store [_ {:keys [resource-store]}]
  (reify
    rs/ResourceStore
    (-get [_ key]
      (-> (rs/get resource-store key)
          (ac/then-apply-async identity delayed-executor)))
    (-multi-get [_ keys]
      (-> (rs/multi-get resource-store keys)
          (ac/then-apply-async identity delayed-executor)))
    (-put [_ entries]
      (-> (rs/put! resource-store entries)
          (ac/then-apply-async identity delayed-executor)))))

(def ^:private slow-resource-store-system
  (merge-with
   merge
   config
   {:blaze.db/node
    {:resource-store (ig/ref ::slow-resource-store)}
    ::node/resource-indexer
    {:resource-store (ig/ref ::slow-resource-store)}
    ::slow-resource-store
    {:resource-store (ig/ref ::rs/kv)}}))

(defmethod ig/init-key ::resource-store-failing-on-put [_ _]
  (reify
    rs/ResourceStore
    (-put [_ _]
      (ac/completed-future {::anom/category ::anom/fault}))))

(def resource-store-failing-on-put-system
  (merge-with
   merge
   config
   {:blaze.db/node
    {:resource-store (ig/ref ::resource-store-failing-on-put)}
    ::node/resource-indexer
    {:resource-store (ig/ref ::resource-store-failing-on-put)}
    ::resource-store-failing-on-put {}}))

(defmacro with-open-db
  "Runs `body` once in a normal database and then again in a batch database."
  [[db node] & body]
  `(do
     (let [~db (d/db ~node)]
       ~@body)
     (with-open [~db (d/new-batch-db (d/db ~node))]
       ~@body)))

(deftest node-test
  (with-system [{:blaze.db/keys [node]} config]
    (is (identical? node (d/node (d/db node))))))

(deftest sync-test
  (doseq [config [config (assoc-in config [:blaze.db/node :storage] :distributed)]]
    (testing "on already available database value"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:create {:fhir/type :fhir/Patient :id "0"}]]]

        (is (= 1 (d/basis-t @(d/sync node 1))))))

    (testing "on currently unavailable database value"
      (with-system [{:blaze.db/keys [node]} config]
        (let [future (mtu/assoc-thread-name (d/sync node 1))]
          @(d/transact node [[:create {:fhir/type :fhir/Patient :id "0"}]])

          (given @future
            d/basis-t := 1
            [meta :thread-name] :? mtu/common-pool-thread?))))

    (testing "errored transactions are ignored"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:create {:fhir/type :fhir/Observation :id "0"}]]]

        @(-> (d/transact node [[:create
                                {:fhir/type :fhir/Observation :id "1"
                                 :subject #fhir/Reference{:reference "Patient/0"}}]])
             (ac/exceptionally (constantly nil)))

        (is (= 1 (d/basis-t @(d/sync node))))))

    (testing "on database value being available after two transactions"
      (with-system [{:blaze.db/keys [node]} config]
        (let [future (d/sync node 2)]
          @(d/transact node [[:create {:fhir/type :fhir/Patient :id "0"}]])
          @(d/transact node [[:create {:fhir/type :fhir/Patient :id "1"}]])

          (is (= 2 (d/basis-t @future)))))

      (testing "without t"
        (with-system-data [{:blaze.db/keys [node]} config]
          [[[:create {:fhir/type :fhir/Patient :id "0"}]]
           [[:create {:fhir/type :fhir/Patient :id "1"}]]]

          (is (= 2 (d/basis-t @(d/sync node)))))))

    (testing "cancelling"
      (with-system [{:blaze.db/keys [node]} config]
        (let [future (d/sync node 2)]
          (ac/cancel! future)
          @(d/transact node [[:create {:fhir/type :fhir/Patient :id "0"}]])
          (is (ac/canceled? future)))))))

(defn- create-tx-op [resource-gen]
  (gen/fmap (partial vector :create) resource-gen))

(defn- unique-ids? [tx-ops]
  (= (count tx-ops) (count (into #{} (map (comp :id second)) tx-ops))))

(defn- create-tx [resource-gen max-ops]
  (gen/such-that unique-ids? (gen/vector (create-tx-op resource-gen) 1 max-ops)))

(defn- kebab->pascal [s]
  (.to CaseFormat/LOWER_HYPHEN CaseFormat/UPPER_CAMEL s))

(def mixed-resource-gen
  (let [observation-gen
        (gen/vector
         (fg/observation
          :id (gen/fmap str gen/uuid)
          :subject (fg/reference :reference (gen/return "Patient/0"))
          :performer (gen/fmap vector (fg/reference :reference (gen/return "Practitioner/0"))))
         0 10)
        encounter-gen
        (gen/vector
         (fg/encounter
          :id (gen/fmap str gen/uuid)
          :subject (fg/reference :reference (gen/return "Patient/0")))
         0 10)
        procedure-gen
        (gen/vector
         (fg/procedure
          :id (gen/fmap str gen/uuid)
          :subject (fg/reference :reference (gen/return "Patient/0")))
         0 10)
        medication-administration-gen
        (gen/vector
         (fg/medication-administration
          :id (gen/fmap str gen/uuid)
          :medication (fg/reference :reference (gen/return "Medication/0"))
          :subject (fg/reference :reference (gen/return "Patient/0")))
         0 10)]
    (gen/let [observations observation-gen
              encounters encounter-gen
              procedures procedure-gen
              medication-administrations medication-administration-gen]
      (concat observations encounters procedures medication-administrations))))

(defn- pull-resource [db type id]
  (d/pull db (d/resource-handle db type id)))

(deftest transact-create-test
  (testing "one Patient"
    (with-system [{:blaze.db/keys [node]} config]
      (given @(mtu/assoc-thread-name (d/transact node [[:create {:fhir/type :fhir/Patient :id "0"}]]))
        [meta :thread-name] :? mtu/common-pool-thread?)

      (given @(pull-resource (d/db node) "Patient" "0")
        :fhir/type := :fhir/Patient
        :id := "0"
        [:meta :versionId] := #fhir/id"1"
        [meta :blaze.db/op] := :create)))

  (testing "one Patient with one Observation"
    (with-system-data [{:blaze.db/keys [node]} config]
      ;; create ops are purposely disordered in order to test the
      ;; reference dependency ordering algorithm
      [[[:create
         {:fhir/type :fhir/Observation :id "0"
          :subject #fhir/Reference{:reference "Patient/0"}}]
        [:create {:fhir/type :fhir/Patient :id "0"}]]]

      (given @(pull-resource (d/db node) "Patient" "0")
        :fhir/type := :fhir/Patient
        :id := "0"
        [:meta :versionId] := #fhir/id"1"
        [meta :blaze.db/op] := :create)

      (given @(pull-resource (d/db node) "Observation" "0")
        :fhir/type := :fhir/Observation
        :id := "0"
        [:subject :reference] := "Patient/0"
        [:meta :versionId] := #fhir/id"1"
        [meta :blaze.db/op] := :create)))

  (testing "a resource can't be created again with the same id"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]]]

      (given-failed-future (d/transact node [[:create {:fhir/type :fhir/Patient :id "0"}]])
        ::anom/category := ::anom/conflict
        ::anom/message := "Resource `Patient/0` already exists in the database with t = 1 and can't be created again.")))

  (testing "generated data"
    (doseq [gen `[fg/patient fg/observation fg/encounter fg/procedure
                  fg/allergy-intolerance fg/diagnostic-report fg/library]]
      (satisfies-prop 20
        (prop/for-all [tx-ops (create-tx ((resolve gen)) 20)]
          (with-system-data [{:blaze.db/keys [node]} config]
            [tx-ops]

            (= (count tx-ops)
               (count @(d/pull-many node (d/type-list (d/db node) (kebab->pascal (name gen))))))))))))

(deftest transact-conditional-create-test
  (testing "on empty database"
    (with-system [{:blaze.db/keys [node]} config]
      (let [db @(d/transact node [[:create
                                   {:fhir/type :fhir/Patient :id "0"}
                                   [["identifier" "111033"]]]])]
        (testing "the Patient was created"
          (given @(pull-resource db "Patient" "0")
            :fhir/type := :fhir/Patient
            :id := "0"
            [:meta :versionId] := #fhir/id"1"
            [meta :blaze.db/op] := :create)))))

  (testing "on non-matching Patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :identifier [#fhir/Identifier{:value #fhir/string"094808"}]}]]]

      (let [db @(d/transact node [[:create
                                   {:fhir/type :fhir/Patient :id "1"}
                                   [["identifier" "111033"]]]])]
        (testing "the Patient was created"
          (given @(pull-resource db "Patient" "1")
            :fhir/type := :fhir/Patient
            :id := "1"
            [:meta :versionId] := #fhir/id"2"
            [meta :blaze.db/op] := :create)))))

  (testing "on matching Patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :identifier [#fhir/Identifier{:value #fhir/string"111033"}]}]]]

      (let [db @(d/transact node [[:create
                                   {:fhir/type :fhir/Patient :id "1"}
                                   [["identifier" "111033"]]]])]
        (testing "no new patient is created"
          (is (= 1 (d/type-total db "Patient")))))))

  (testing "on multiple matching Patients"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :birthDate #fhir/date"2020"}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :birthDate #fhir/date"2020"}]]]

      (testing "causes a transaction abort with conflict"
        (given-failed-future
         (d/transact
          node
          [[:create
            {:fhir/type :fhir/Patient :id "1"}
            [["birthdate" "2020"]]]])
          ::anom/category := ::anom/conflict
          ::anom/message := "Conditional create of a Patient with query `birthdate=2020` failed because at least the two matches `Patient/0/_history/1` and `Patient/1/_history/1` were found."))))

  (testing "on deleting the matching Patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :identifier [#fhir/Identifier{:value #fhir/string"153229"}]}]]]

      (testing "causes a transaction abort with conflict"
        (given-failed-future
         (d/transact
          node
          [[:create
            {:fhir/type :fhir/Patient :id "foo"}
            [["identifier" "153229"]]]
           [:delete "Patient" "0"]])
          ::anom/category := ::anom/conflict
          ::anom/message := "Duplicate transaction commands `delete Patient/0` and `create Patient?identifier=153229 (resolved to id 0)`."))))

  (testing "failing query"
    (with-system [{:blaze.db/keys [node]} config]
      (given-failed-future
       (d/transact
        node
        [[:create
          {:fhir/type :fhir/Patient :id "0"}
          [["foo" "bar"]]]])
        ::anom/category := ::anom/incorrect
        ::anom/message := "Conditional create of a Patient with query `foo=bar` failed. Cause: The search-param with code `foo` and type `Patient` was not found."))))

(def ^:private read-only-tag
  #fhir/Coding
   {:system #fhir/uri"https://samply.github.io/blaze/fhir/CodeSystem/AccessControl"
    :code #fhir/code"read-only"})

(deftest transact-put-test
  (testing "one Patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (testing "the Patient was created"
        (given @(pull-resource (d/db node) "Patient" "0")
          :fhir/type := :fhir/Patient
          :id := "0"
          [:meta :versionId] := #fhir/id"1"
          [meta :blaze.db/op] := :put))))

  (testing "one Patient with an Extension on birthDate"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :birthDate
               #fhir/date
                {:extension [#fhir/Extension{:url "foo" :value #fhir/code"bar"}]
                 :value "2022"}}]]]

      (testing "the Patient was created"
        (given @(pull-resource (d/db node) "Patient" "0")
          :fhir/type := :fhir/Patient
          :id := "0"
          [:meta :versionId] := #fhir/id"1"
          [meta :blaze.db/op] := :put
          [:birthDate :extension 0 :url] := "foo"
          [:birthDate :extension 0 :value] := #fhir/code"bar"
          [:birthDate :value] := #system/date"2022"))))

  (testing "one Patient with one Observation"
    (with-system-data [{:blaze.db/keys [node]} config]
      ;; the create ops are purposely disordered in order to test the
      ;; reference dependency ordering algorithm
      [[[:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}}]
        [:put {:fhir/type :fhir/Patient :id "0"}]]]

      (testing "the Patient was created"
        (given @(pull-resource (d/db node) "Patient" "0")
          :fhir/type := :fhir/Patient
          :id := "0"
          [:meta :versionId] := #fhir/id"1"
          [meta :blaze.db/op] := :put))

      (testing "the Observation was created"
        (given @(pull-resource (d/db node) "Observation" "0")
          :fhir/type := :fhir/Observation
          :id := "0"
          [:meta :versionId] := #fhir/id"1"
          [:subject :reference] := "Patient/0"
          [meta :blaze.db/op] := :put))))

  (testing "updating one Patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0" :gender #fhir/code"male"}]]
       [[:put {:fhir/type :fhir/Patient :id "0" :gender #fhir/code"female"}]]]

      (given @(pull-resource (d/db node) "Patient" "0")
        :fhir/type := :fhir/Patient
        :id := "0"
        [:meta :versionId] := #fhir/id"2"
        :gender := #fhir/code"female"
        [meta :blaze.db/op] := :put))

    (testing "with if-none-match"
      (testing "of any"
        (with-system-data [{:blaze.db/keys [node]} config]
          [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

          (given-failed-future
           (d/transact
            node
            [[:put {:fhir/type :fhir/Patient :id "0"} [:if-none-match :any]]])
            ::anom/category := ::anom/conflict
            ::anom/message := "Resource `Patient/0` already exists.")))

      (testing "of 1"
        (with-system-data [{:blaze.db/keys [node]} config]
          [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

          (given-failed-future
           (d/transact
            node
            [[:put {:fhir/type :fhir/Patient :id "0"} [:if-none-match 1]]])
            ::anom/category := ::anom/conflict
            ::anom/message := "Resource `Patient/0` with version 1 already exists."))))

    (testing "with identical content"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:create {:fhir/type :fhir/Patient :id "0" :gender #fhir/code"female"}]]]

        (let [db @(d/transact node [[:put {:fhir/type :fhir/Patient :id "0" :gender #fhir/code"female"}]])]

          (testing "versionId is still 1"
            (given @(pull-resource db "Patient" "0")
              :fhir/type := :fhir/Patient
              :id := "0"
              [:meta :versionId] := #fhir/id"1"
              :gender := #fhir/code"female"
              [meta :blaze.db/op] := :create))

          (testing "instance history contains only one entry"
            (is (= 1 (d/total-num-of-instance-changes db "Patient" "0")))
            (is (= 1 (count (d/instance-history db "Patient" "0")))))

          (testing "type history contains only one entry"
            (is (= 1 (d/total-num-of-type-changes db "Patient")))
            (is (= 1 (count (d/type-history db "Patient")))))

          (testing "system history contains only one entry"
            (is (= 1 (d/total-num-of-system-changes db)))
            (is (= 1 (count (d/system-history db)))))))))

  (testing "Diamond Reference Dependencies"
    (with-system-data [{:blaze.db/keys [node]} config]
      ;; the create ops are purposely disordered in order to test the
      ;; reference dependency ordering algorithm
      [[[:put {:fhir/type :fhir/List
               :id "0"
               :entry
               [{:fhir/type :fhir.List/entry
                 :item #fhir/Reference{:reference "Observation/0"}}
                {:fhir/type :fhir.List/entry
                 :item #fhir/Reference{:reference "Observation/1"}}]}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :subject #fhir/Reference{:reference "Patient/0"}}]
        [:put {:fhir/type :fhir/Patient :id "0"}]]]

      (given @(pull-resource (d/db node) "Patient" "0")
        :fhir/type := :fhir/Patient
        :id := "0"
        [:meta :versionId] := #fhir/id"1"
        [meta :blaze.db/op] := :put)

      (given @(pull-resource (d/db node) "Observation" "0")
        :fhir/type := :fhir/Observation
        :id := "0"
        [:meta :versionId] := #fhir/id"1"
        [:subject :reference] := "Patient/0"
        [meta :blaze.db/op] := :put)

      (given @(pull-resource (d/db node) "Observation" "1")
        :fhir/type := :fhir/Observation
        :id := "1"
        [:meta :versionId] := #fhir/id"1"
        [:subject :reference] := "Patient/0"
        [meta :blaze.db/op] := :put)

      (given @(pull-resource (d/db node) "List" "0")
        :fhir/type := :fhir/List
        :id := "0"
        [:meta :versionId] := #fhir/id"1"
        [:entry 0 :item :reference] := "Observation/0"
        [:entry 1 :item :reference] := "Observation/1"
        [meta :blaze.db/op] := :put)))

  (testing "read-only resources can't be updated"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"
                  :meta (type/map->Meta {:tag [read-only-tag]})}]]]

      (given-failed-future (d/transact node [[:put {:fhir/type :fhir/Patient :id "0"
                                                    :meta (type/map->Meta {:tag [read-only-tag]})
                                                    :gender #fhir/code"female"}]])
        ::anom/category := ::anom/conflict
        ::anom/message := "Can't update the read-only resource `Patient/0`."))))

(deftest transact-delete-test
  (testing "on empty database"
    (with-system [{:blaze.db/keys [node]} config]
      (let [db @(d/transact node [[:delete "Patient" "0"]])]
        (testing "the patient is deleted"
          (given (d/resource-handle db "Patient" "0")
            :op := :delete)))

      (testing "doing a second delete"
        (let [db @(d/transact node [[:delete "Patient" "0"]])]
          (testing "the patient is still deleted and still only has one change"
            (given (d/resource-handle db "Patient" "0")
              :op := :delete
              :num-changes := 1))))))

  (testing "patient with an observation referencing it"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]
        [:create {:fhir/type :fhir/Observation :id "0"
                  :subject #fhir/Reference{:reference "Patient/0"}}]]]

      (testing "deleting only the patient fails"
        (given-failed-future
         (d/transact node [[:delete "Patient" "0"]])
          ::anom/category := ::anom/conflict
          ::anom/message := "Referential integrity violated. Resource `Patient/0` should be deleted but is referenced from `Observation/0`."))

      (testing "deleting the patient and the observation in one transaction succeeds"
        (let [db @(d/transact node [[:delete "Patient" "0"] [:delete "Observation" "0"]])]
          (given (d/resource-handle db "Patient" "0")
            :op := :delete
            :num-changes := 2)
          (given (d/resource-handle db "Observation" "0")
            :op := :delete
            :num-changes := 2)))))

  (testing "referential integrity checks are not affected by hash collisions"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create
         {:fhir/type :fhir/Encounter
          :id "A597AEF8855D993D92B063CC69194E33A57F07760034D054150054BCDABFE332"}]
        [:create
         {:fhir/type :fhir/Encounter
          :id "1577C74A3D24F812C29FC372B5B0D9D325AA86A05A541598BF3734247EEAAE5C"}]
        [:create
         {:fhir/type :fhir/Observation :id "0"
          :encounter #fhir/Reference{:reference "Encounter/1577C74A3D24F812C29FC372B5B0D9D325AA86A05A541598BF3734247EEAAE5C"}}]]]

      (testing "deleting the unreferenced encounter succeeds"
        (let [db @(d/transact node [[:delete "Encounter" "A597AEF8855D993D92B063CC69194E33A57F07760034D054150054BCDABFE332"]])]
          (given (d/resource-handle db "Encounter" "A597AEF8855D993D92B063CC69194E33A57F07760034D054150054BCDABFE332")
            :op := :delete
            :num-changes := 2)))))

  (testing "encounter with a condition referencing it"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Encounter :id "0"}]
        [:create {:fhir/type :fhir/Condition :id "0"
                  :encounter #fhir/Reference{:reference "Encounter/0"}}]]]

      (testing "deleting only the encounter fails"
        (given-failed-future
         (d/transact node [[:delete "Encounter" "0"]])
          ::anom/category := ::anom/conflict
          ::anom/message := "Referential integrity violated. Resource `Encounter/0` should be deleted but is referenced from `Condition/0`."))

      (testing "deleting the encounter and the condition in the same transaction succeeds"
        (let [db @(d/transact node [[:delete "Encounter" "0"] [:delete "Condition" "0"]])]
          (given (d/resource-handle db "Encounter" "0")
            :op := :delete
            :num-changes := 2)
          (given (d/resource-handle db "Condition" "0")
            :op := :delete
            :num-changes := 2)))))

  (testing "a patient with several resources referencing it"
    (satisfies-prop 10
      (prop/for-all [resources mixed-resource-gen]
        (with-system-data [{:blaze.db/keys [node]} config]
          [(into [[:put {:fhir/type :fhir/Patient :id "0"}]
                  [:put {:fhir/type :fhir/Medication :id "0"}]
                  [:put {:fhir/type :fhir/Practitioner :id "0"}]]
                 (map (partial vector :create))
                 resources)]
          (let [make-delete-command (fn [{id :id type :fhir/type}] [:delete (name type) id])
                delete-list (into [[:delete "Patient" "0"]] (map make-delete-command) resources)
                db @(d/transact node delete-list)]
            (for [[_ type id] delete-list]
              (given (d/resource-handle db type id)
                :op := :delete
                :num-changes := 2)))))))

  (testing "patient with an observation referencing it without enforcing referential integrity"
    (with-system-data [{:blaze.db/keys [node]} (assoc-in config [:blaze.db/node :enforce-referential-integrity] false)]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]
        [:create {:fhir/type :fhir/Observation :id "0"
                  :subject #fhir/Reference{:reference "Patient/0"}}]]]
      (let [db @(d/transact node [[:delete "Patient" "0"]])]
        (given (d/resource-handle db "Patient" "0")
          :op := :delete
          :num-changes := 2)
        (given (d/resource-handle db "Observation" "0")
          :op := :create
          :num-changes := 1))))

  (testing "read-only resources are not deletable"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"
                  :meta (type/map->Meta {:tag [read-only-tag]})}]]]

      (given-failed-future (d/transact node [[:delete "Patient" "0"]])
        ::anom/category := ::anom/conflict
        ::anom/message := "Can't delete the read-only resource `Patient/0`."))))

(deftest transact-conditional-delete-test
  (testing "one matching patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"
                  :identifier [#fhir/Identifier{:value #fhir/string"181205"}]}]
        [:create {:fhir/type :fhir/Patient :id "1"
                  :identifier [#fhir/Identifier{:value #fhir/string"164453"}]}]]]

      (let [db @(d/transact node [[:conditional-delete "Patient"
                                   [["identifier" "181205"]]]])]
        (testing "the patient is deleted"
          (given (d/resource-handle db "Patient" "0")
            :op := :delete
            :num-changes := 2))

        (testing "the other patient still exists"
          (given (d/resource-handle db "Patient" "1")
            :op := :create
            :num-changes := 1)))))

  (testing "no match"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"
                  :identifier [#fhir/Identifier{:value #fhir/string"181205"}]}]]]

      (let [db @(d/transact node [[:conditional-delete "Patient"
                                   [["identifier" "foo"]]]])]
        (testing "the patient isn't deleted"
          (given (d/resource-handle db "Patient" "0")
            :op := :create
            :num-changes := 1)))))

  (testing "two matching patients"
    (testing "is forbidden by default"
      (with-system-data [{:blaze.db/keys [node]} config]
        [(vec (for [id ["0" "1"]]
                [:create {:fhir/type :fhir/Patient :id id
                          :identifier [#fhir/Identifier{:value #fhir/string"181205"}]}]))]

        (testing "with query"
          (given-failed-future (d/transact node [[:conditional-delete "Patient" [["identifier" "181205"]]]])
            ::anom/category := ::anom/conflict
            ::anom/message := "Conditional delete of one single Patient with query `identifier=181205` failed because at least the two matches `Patient/0/_history/1` and `Patient/1/_history/1` were found."
            :http/status := 412))

        (testing "without query (matching all patients)"
          (given-failed-future (d/transact node [[:conditional-delete "Patient"]])
            ::anom/category := ::anom/conflict
            ::anom/message := "Conditional delete of one single Patient without a query failed because at least the two matches `Patient/0/_history/1` and `Patient/1/_history/1` were found."
            :http/status := 412))))

    (testing "works if allowed"
      (testing "with query"
        (with-system-data [{:blaze.db/keys [node]} (assoc-in config [:blaze.db/node :allow-multiple-delete] true)]
          [(vec (for [id ["0" "1"]]
                  [:create {:fhir/type :fhir/Patient :id id
                            :identifier [#fhir/Identifier{:value #fhir/string"181205"}]}]))
           [[:create {:fhir/type :fhir/Patient :id "2"
                      :identifier [#fhir/Identifier{:value #fhir/string"164453"}]}]]]

          (let [db @(d/transact node [[:conditional-delete "Patient"
                                       [["identifier" "181205"]]]])]
            (testing "both patients are deleted"
              (doseq [id ["0" "1"]]
                (given (d/resource-handle db "Patient" id)
                  :op := :delete
                  :num-changes := 2)))

            (testing "the third patient still exists"
              (given (d/resource-handle db "Patient" "2")
                :op := :create
                :num-changes := 1)))))))

  (testing "three patients"
    (with-system-data [{:blaze.db/keys [node]} (assoc-in config [:blaze.db/node :allow-multiple-delete] true)]
      [(vec (for [id ["0" "1" "2"]]
              [:create {:fhir/type :fhir/Patient :id id}]))]

      (let [db @(d/transact node [[:conditional-delete "Patient"]])]
        (testing "all patients are deleted"
          (doseq [id ["0" "1" "2"]]
            (given (d/resource-handle db "Patient" id)
              :op := :delete
              :num-changes := 2))))))

  (testing "patient with an observation referencing it"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"
                  :identifier [#fhir/Identifier{:value #fhir/string"181205"}]}]
        [:create {:fhir/type :fhir/Observation :id "0"
                  :subject #fhir/Reference{:reference "Patient/0"}}]]]

      (testing "deleting only the patient fails"
        (given-failed-future
         (d/transact node [[:conditional-delete "Patient" [["identifier" "181205"]]]])
          ::anom/category := ::anom/conflict
          ::anom/message := "Referential integrity violated. Resource `Patient/0` should be deleted but is referenced from `Observation/0`."))

      (testing "deleting the patient and the observation in one transaction succeeds"
        (let [db @(d/transact node [[:conditional-delete "Patient"
                                     [["identifier" "181205"]]]
                                    [:delete "Observation" "0"]])]
          (given (d/resource-handle db "Patient" "0")
            :op := :delete
            :num-changes := 2)
          (given (d/resource-handle db "Observation" "0")
            :op := :delete
            :num-changes := 2)))))

  (testing "two patients with observations referencing them"
    (with-system-data [{:blaze.db/keys [node]} (assoc-in config [:blaze.db/node :allow-multiple-delete] true)]
      (vec (for [id ["0" "1"]]
             [[:create {:fhir/type :fhir/Patient :id id
                        :identifier [#fhir/Identifier{:value #fhir/string"181205"}]}]
              [:create {:fhir/type :fhir/Observation :id id
                        :subject (type/map->Reference {:reference (str "Patient/" id)})}]]))

      (testing "deleting only the patients fails"
        (given-failed-future
         (d/transact node [[:conditional-delete "Patient" [["identifier" "181205"]]]])
          ::anom/category := ::anom/conflict
          ::anom/message := "Referential integrity violated. Resource `Patient/0` should be deleted but is referenced from `Observation/0`."))

      (testing "deleting the patients and the observations in one transaction succeeds"
        (let [db @(d/transact node [[:conditional-delete "Patient"
                                     [["identifier" "181205"]]]
                                    [:delete "Observation" "0"]
                                    [:delete "Observation" "1"]])]
          (doseq [id ["0" "1"]]
            (given (d/resource-handle db "Patient" id)
              :op := :delete
              :num-changes := 2))
          (doseq [id ["0" "1"]]
            (given (d/resource-handle db "Observation" id)
              :op := :delete
              :num-changes := 2))))))

  (testing "Encounter and Condition referencing each other"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Encounter :id "0"
               :diagnosis
               [{:fhir/type :fhir.Encounter/diagnosis
                 :condition #fhir/Reference{:reference "Condition/0"}}]}]
        [:put {:fhir/type :fhir/Condition :id "0"
               :encounter #fhir/Reference{:reference "Encounter/0"}}]]]

      (testing "deleting both types in one transaction succeeds"
        (let [db @(d/transact node [[:conditional-delete "Encounter"]
                                    [:conditional-delete "Condition"]])]
          (given (d/resource-handle db "Encounter" "0")
            :op := :delete
            :num-changes := 2)
          (given (d/resource-handle db "Condition" "0")
            :op := :delete
            :num-changes := 2)))))

  (testing "on updating the matching Patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :identifier [#fhir/Identifier{:value #fhir/string"140151"}]}]]]

      (testing "causes a transaction abort with conflict"
        (given-failed-future
         (d/transact
          node
          [[:conditional-delete "Patient" [["identifier" "140151"]]]
           [:put {:fhir/type :fhir/Patient :id "0"}]])
          ::anom/category := ::anom/conflict
          ::anom/message := "Duplicate transaction commands `put Patient/0` and `delete Patient/0`."))))

  (testing "failing query"
    (with-system [{:blaze.db/keys [node]} config]
      (given-failed-future (d/transact node [[:conditional-delete "Patient" [["foo" "bar"]]]])
        ::anom/category := ::anom/incorrect
        ::anom/message := "Conditional delete of Patients with query `foo=bar` failed. Cause: The search-param with code `foo` and type `Patient` was not found."))))

(deftest transact-conditional-delete-too-many-test
  (log/set-min-level! :info)
  (st/unstrument)
  (testing "works up to 10,000 matches"
    (testing "with query"
      (with-system-data [{:blaze.db/keys [node]} (assoc-in config [:blaze.db/node :allow-multiple-delete] true)]
        [(vec (for [id (range 10000)]
                [:create {:fhir/type :fhir/Patient :id (str id)
                          :identifier [#fhir/Identifier{:value #fhir/string"181205"}]}]))]

        (let [db @(d/transact node [[:conditional-delete "Patient"
                                     [["identifier" "181205"]]]])]
          (testing "all patients are deleted"
            (is (zero? (d/type-total db "Patient")))))))

    (testing "without query (matching all patients)"
      (with-system-data [{:blaze.db/keys [node]} (assoc-in config [:blaze.db/node :allow-multiple-delete] true)]
        [(vec (for [id (range 10000)]
                [:create {:fhir/type :fhir/Patient :id (str id)}]))]

        (let [db @(d/transact node [[:conditional-delete "Patient"]])]
          (testing "all patients are deleted"
            (is (zero? (d/type-total db "Patient"))))))))

  (testing "fails on more then 10,000 matches"
    (testing "with query"
      (with-system-data [{:blaze.db/keys [node]} (assoc-in config [:blaze.db/node :allow-multiple-delete] true)]
        [(vec (for [id (range 10001)]
                [:create {:fhir/type :fhir/Patient :id (str id)
                          :identifier [#fhir/Identifier{:value #fhir/string"181205"}]}]))]

        (given-failed-future (d/transact node [[:conditional-delete "Patient" [["identifier" "181205"]]]])
          ::anom/category := ::anom/conflict
          ::anom/message := "Conditional delete of Patients with query `identifier=181205` failed because more than 10,000 matches were found."
          :fhir/issue := "too-costly")))

    (testing "without query (matching all patients)"
      (with-system-data [{:blaze.db/keys [node]} (assoc-in config [:blaze.db/node :allow-multiple-delete] true)]
        [(vec (for [id (range 10001)]
                [:create {:fhir/type :fhir/Patient :id (str id)
                          :identifier [#fhir/Identifier{:value #fhir/string"181205"}]}]))]

        (given-failed-future (d/transact node [[:conditional-delete "Patient"]])
          ::anom/category := ::anom/conflict
          ::anom/message := "Conditional delete of all Patients failed because more than 10,000 matches were found."
          :fhir/issue := "too-costly")))))

(defn- pull-instance-history
  ([db type id]
   (d/pull-many db (d/instance-history db type id)))
  ([db type id start-t]
   (d/pull-many db (d/instance-history db type id start-t))))

(deftest transact-delete-history-test
  (testing "one patient with one version"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]]]

      (let [db-before (d/db node)
            db-after @(d/transact node [[:delete-history "Patient" "0"]])]

        (testing "nothing changed"
          (testing "the patient is the same"
            (is (= (d/resource-handle db-before "Patient" "0")
                   (d/resource-handle db-after "Patient" "0"))))

          (testing "the instance histories are the same"
            (is (= (d/total-num-of-instance-changes db-before "Patient" "0")
                   (d/total-num-of-instance-changes db-after "Patient" "0")))
            (is (= (vec (d/instance-history db-before "Patient" "0"))
                   (vec (d/instance-history db-after "Patient" "0")))))

          (testing "the type histories are the same"
            (is (= (d/total-num-of-type-changes db-before "Patient")
                   (d/total-num-of-type-changes db-after "Patient")))
            (is (= (vec (d/type-history db-before "Patient"))
                   (vec (d/type-history db-after "Patient")))))

          (testing "the system histories are the same"
            (is (= (d/total-num-of-system-changes db-before)
                   (d/total-num-of-system-changes db-after)))
            (is (= (vec (d/system-history db-before))
                   (vec (d/system-history db-after)))))))))

  (testing "one patient with two versions"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0" :active false}]]
       [[:put {:fhir/type :fhir/Patient :id "0" :active true}]]]

      (let [db-before (d/db node)
            db-after @(d/transact node [[:delete-history "Patient" "0"]])]

        (testing "the patient"
          (let [patient @(pull-resource db-after "Patient" "0")]

            (testing "is active"
              (given patient
                :active := true))

            (testing "the instance history contains only that patient"
              (is (= 1 (d/total-num-of-instance-changes db-after "Patient" "0")))
              (is (= [patient] @(pull-instance-history db-after "Patient" "0"))))))

        (testing "the type history contains only one entry"
          (is (= 1 (d/total-num-of-type-changes db-after "Patient")))
          (given @(d/pull-many node (d/type-history db-after "Patient"))
            count := 1
            [0 :active] := true))

        (testing "the system history contains only one entry"
          (is (= 1 (d/total-num-of-system-changes db-after)))
          (given @(d/pull-many node (d/system-history db-after))
            count := 1
            [0 :active] := true))

        (testing "the instance history of db-before still contains two entries"
          (given @(pull-instance-history db-before "Patient" "0")
            count := 2
            [0 :active] := true
            [1 :active] := false))

        (testing "the type history of db-before still contains two entries"
          (is (= 2 (d/total-num-of-type-changes db-before "Patient")))
          (given @(d/pull-many node (d/type-history db-before "Patient"))
            count := 2
            [0 :active] := true
            [1 :active] := false))

        (testing "the system history of db-before still contains two entries"
          (is (= 2 (d/total-num-of-system-changes db-before)))
          (given @(d/pull-many node (d/system-history db-before))
            count := 2
            [0 :active] := true
            [1 :active] := false)))))

  (testing "two patients, one with one version and the other with two versions"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]
        [:create {:fhir/type :fhir/Patient :id "1" :active false}]]
       [[:put {:fhir/type :fhir/Patient :id "1" :active true}]]]

      (let [db-after @(d/transact node [[:delete-history "Patient" "0"]
                                        [:delete-history "Patient" "1"]])]

        (testing "the patient 1"
          (let [patient @(pull-resource db-after "Patient" "1")]

            (testing "is active"
              (given patient
                :active := true))

            (testing "the instance history contains only that patient"
              (is (= 1 (d/total-num-of-instance-changes db-after "Patient" "1")))
              (is (= [patient] @(pull-instance-history db-after "Patient" "1"))))))

        (testing "the type history contains two entries"
          (is (= 2 (d/total-num-of-type-changes db-after "Patient")))
          (given @(d/pull-many node (d/type-history db-after "Patient"))
            count := 2
            [0 :active] := true
            [1 :id] := "0"))

        (testing "the system history contains two entries"
          (is (= 2 (d/total-num-of-system-changes db-after)))
          (given @(d/pull-many node (d/system-history db-after))
            count := 2
            [0 :active] := true
            [1 :id] := "0")))))

  (testing "one deleted patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]]
       [[:delete "Patient" "0"]]]

      (let [db-before (d/db node)
            db-after @(d/transact node [[:delete-history "Patient" "0"]])]

        (testing "the patient is still deleted"
          (given (d/resource-handle db-after "Patient" "0")
            :op := :delete))

        (testing "the instance history contains only one entry"
          (given (vec (d/instance-history db-after "Patient" "0"))
            count := 1
            [0 :op] := :delete))

        (testing "the type history contains only one entry"
          (is (= 1 (d/total-num-of-type-changes db-after "Patient")))
          (given (vec (d/type-history db-after "Patient"))
            count := 1
            [0 :op] := :delete))

        (testing "the system history contains only one entry"
          (is (= 1 (d/total-num-of-system-changes db-after)))
          (given (vec (d/system-history db-after))
            count := 1
            [0 :op] := :delete))

        (testing "the instance history of db-before still contains two entries"
          (given (vec (d/instance-history db-before "Patient" "0"))
            count := 2
            [0 :op] := :delete
            [1 :op] := :create))

        (testing "the type history of db-before still contains two entries"
          (is (= 2 (d/total-num-of-type-changes db-before "Patient")))
          (given (vec (d/type-history db-before "Patient"))
            count := 2
            [0 :op] := :delete
            [1 :op] := :create))

        (testing "the system history of db-before still contains two entries"
          (is (= 2 (d/total-num-of-system-changes db-before)))
          (given (vec (d/system-history db-before))
            count := 2
            [0 :op] := :delete
            [1 :op] := :create)))))

  (testing "adding a new version on top of a deleted history"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0" :active false}]]
       [[:put {:fhir/type :fhir/Patient :id "0" :active true}]]
       [[:delete-history "Patient" "0"]]]

      (let [db-before (d/db node)
            db-after @(d/transact node [[:put {:fhir/type :fhir/Patient :id "0"
                                               :gender #fhir/code"male"}]])]

        (testing "the patient is male"
          (given @(pull-resource db-after "Patient" "0")
            :gender := #fhir/code"male"))

        (testing "the instance history contains two entries"
          (given @(pull-instance-history db-after "Patient" "0")
            count := 2
            [0 :gender] := #fhir/code"male"
            [1 :active] := true))

        (testing "the type history contains two entries"
          (is (= 2 (d/total-num-of-type-changes db-after "Patient")))
          (given @(d/pull-many node (d/type-history db-after "Patient"))
            count := 2
            [0 :gender] := #fhir/code"male"
            [1 :active] := true))

        (testing "the system history contains two entries"
          (is (= 2 (d/total-num-of-system-changes db-after)))
          (given @(d/pull-many node (d/system-history db-after))
            count := 2
            [0 :gender] := #fhir/code"male"
            [1 :active] := true))

        (testing "the instance history of db-before still contains only one entry"
          (given @(pull-instance-history db-before "Patient" "0")
            count := 1
            [0 :active] := true))

        (testing "the type history of db-before still contains only one entry"
          (is (= 1 (d/total-num-of-type-changes db-before "Patient")))
          (given @(d/pull-many node (d/type-history db-before "Patient"))
            count := 1
            [0 :active] := true))

        (testing "the system history of db-before still contains only one entry"
          (is (= 1 (d/total-num-of-system-changes db-before)))
          (given @(d/pull-many node (d/system-history db-before))
            count := 1
            [0 :active] := true))))))

(deftest transact-delete-history-too-many-test
  (log/set-min-level! :info)
  (st/unstrument)
  (testing "works with up to 100,000 history entries"
    (with-system-data [{:blaze.db/keys [node]} config]
      (vec (for [_ (range 50000)
                 active [true false]]
             [[:put {:fhir/type :fhir/Patient :id "0" :active active}]]))

      (let [db-before (d/db node)
            db-after @(d/transact node [[:delete-history "Patient" "0"]])]

        (testing "the patient"
          (let [patient @(pull-resource db-after "Patient" "0")]

            (testing "is not active"
              (given patient
                :active := false))

            (testing "the instance history contains only that patient"
              (is (= [patient] @(pull-instance-history db-after "Patient" "0"))))))

        (testing "the instance history of db-before still contains 100,000 entries"
          (is (= 100000 (count (d/instance-history db-before "Patient" "0"))))))))

  (testing "fails on more then 100,000 history entries"
    (with-system-data [{:blaze.db/keys [node]} config]
      (into
       [[[:put {:fhir/type :fhir/Patient :id "0" :active false}]]]
       (for [_ (range 50000)
             active [true false]]
         [[:put {:fhir/type :fhir/Patient :id "0" :active active}]]))

      (given-failed-future (d/transact node [[:delete-history "Patient" "0"]])
        ::anom/category := ::anom/conflict
        ::anom/message := "Deleting the history of `Patient/0` failed because there are more than 100,000 history entries."
        :fhir/issue := "too-costly"))))

(deftest transact-patient-purge-test
  (testing "one created patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]]]

      (let [db-after @(d/transact node [[:patient-purge "0"]])]

        (testing "the patient is gone"
          (is (nil? (d/resource-handle db-after "Patient" "0")))
          (is (zero? (d/total-num-of-instance-changes db-after "Patient" "0")))
          (is (zero? (d/total-num-of-type-changes db-after "Patient")))
          (is (zero? (d/total-num-of-system-changes db-after)))
          (is (coll/empty? (d/instance-history db-after "Patient" "0")))
          (is (coll/empty? (d/type-history db-after "Patient")))
          (is (coll/empty? (d/system-history db-after)))
          (is (coll/empty? (d/changes db-after)))))

      (testing "purging it again"
        (let [db-after @(d/transact node [[:patient-purge "0"]])]

          (testing "the patient is still gone"
            (is (nil? (d/resource-handle db-after "Patient" "0")))
            (is (zero? (d/total-num-of-instance-changes db-after "Patient" "0")))
            (is (zero? (d/total-num-of-type-changes db-after "Patient")))
            (is (zero? (d/total-num-of-system-changes db-after)))
            (is (coll/empty? (d/instance-history db-after "Patient" "0")))
            (is (coll/empty? (d/type-history db-after "Patient")))
            (is (coll/empty? (d/system-history db-after)))
            (is (coll/empty? (d/changes db-after)))))))

    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]]]

      (testing "purging a non-exiting patient"
        (let [db-after @(d/transact node [[:patient-purge "1"]])]

          (testing "the existing patient is fine"
            (given (d/resource-handle db-after "Patient" "0")
              :id := "0"
              :op := :create))

          (testing "the purged patient doesn't exist"
            (is (nil? (d/resource-handle db-after "Patient" "1"))))))))

  (testing "one deleted patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]]
       [[:delete "Patient" "0"]]]

      (let [db-after @(d/transact node [[:patient-purge "0"]])]

        (testing "the patient is gone"
          (is (nil? (d/resource-handle db-after "Patient" "0")))
          (is (zero? (d/total-num-of-instance-changes db-after "Patient" "0")))
          (is (zero? (d/total-num-of-type-changes db-after "Patient")))
          (is (zero? (d/total-num-of-system-changes db-after)))
          (is (coll/empty? (d/instance-history db-after "Patient" "0")))
          (is (coll/empty? (d/type-history db-after "Patient")))
          (is (coll/empty? (d/system-history db-after)))
          (is (coll/empty? (d/changes db-after)))))))

  (testing "two patients"
    (testing "purging both"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:create {:fhir/type :fhir/Patient :id "0"}]
          [:create {:fhir/type :fhir/Patient :id "1"}]]]

        (let [db-after @(d/transact node [[:patient-purge "0"]
                                          [:patient-purge "1"]])]

          (testing "both patients are gone"
            (is (nil? (d/resource-handle db-after "Patient" "0")))
            (is (nil? (d/resource-handle db-after "Patient" "1")))
            (is (zero? (d/total-num-of-instance-changes db-after "Patient" "0")))
            (is (zero? (d/total-num-of-instance-changes db-after "Patient" "1")))
            (is (zero? (d/total-num-of-type-changes db-after "Patient")))
            (is (zero? (d/total-num-of-system-changes db-after)))
            (is (coll/empty? (d/instance-history db-after "Patient" "0")))
            (is (coll/empty? (d/instance-history db-after "Patient" "1")))
            (is (coll/empty? (d/type-history db-after "Patient")))
            (is (coll/empty? (d/system-history db-after)))
            (is (coll/empty? (d/changes db-after)))))))

    (testing "purging only one"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:create {:fhir/type :fhir/Patient :id "0"}]
          [:create {:fhir/type :fhir/Patient :id "1"}]]]

        (let [db-after @(d/transact node [[:patient-purge "0"]])]

          (testing "only the first patient is gone"
            (is (nil? (d/resource-handle db-after "Patient" "0")))
            (is (= "1" (:id (d/resource-handle db-after "Patient" "1"))))
            (is (zero? (d/total-num-of-instance-changes db-after "Patient" "0")))
            (is (= 1 (d/total-num-of-instance-changes db-after "Patient" "1")))
            (is (= 1 (d/total-num-of-type-changes db-after "Patient")))
            (is (= 1 (d/total-num-of-system-changes db-after)))
            (is (coll/empty? (d/instance-history db-after "Patient" "0")))
            (is (= 1 (count (d/instance-history db-after "Patient" "1"))))
            (is (= 1 (count (d/type-history db-after "Patient"))))
            (is (= 1 (count (d/system-history db-after))))
            (is (coll/empty? (d/changes db-after))))))))

  (testing "three patients created in different transactions"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]]
       [[:create {:fhir/type :fhir/Patient :id "1"}]]
       [[:create {:fhir/type :fhir/Patient :id "2"}]]]

      (testing "purging the middle one"
        (let [db-after @(d/transact node [[:patient-purge "1"]])]

          (testing "the type history contains the two remaining patients"
            (is (= 2 (d/total-num-of-type-changes db-after "Patient")))
            (given @(d/pull-many node (d/type-history db-after "Patient"))
              count := 2
              [0 :id] := "2"
              [1 :id] := "0"))

          (testing "the system history contains the two remaining patients"
            (is (= 2 (d/total-num-of-system-changes db-after)))
            (given @(d/pull-many node (d/system-history db-after))
              count := 2
              [0 :id] := "2"
              [1 :id] := "0"))))))

  (testing "one patient with one observation"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]
        [:create {:fhir/type :fhir/Observation :id "0"
                  :subject #fhir/Reference{:reference "Patient/0"}}]]]

      (let [db-after @(d/transact node [[:patient-purge "0"]])]

        (testing "the patient and the observation are gone"
          (is (nil? (d/resource-handle db-after "Patient" "0")))
          (is (nil? (d/resource-handle db-after "Observation" "0")))
          (is (zero? (d/total-num-of-instance-changes db-after "Patient" "0")))
          (is (zero? (d/total-num-of-instance-changes db-after "Observation" "0")))
          (is (zero? (d/total-num-of-type-changes db-after "Patient")))
          (is (zero? (d/total-num-of-type-changes db-after "Observation")))
          (is (zero? (d/total-num-of-system-changes db-after)))
          (is (coll/empty? (d/instance-history db-after "Patient" "0")))
          (is (coll/empty? (d/instance-history db-after "Observation" "0")))
          (is (coll/empty? (d/type-history db-after "Patient")))
          (is (coll/empty? (d/type-history db-after "Observation")))
          (is (coll/empty? (d/system-history db-after)))
          (is (coll/empty? (d/changes db-after)))))))

  (testing "one patient with one observation and one encounter"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]
        [:create {:fhir/type :fhir/Encounter :id "0"
                  :subject #fhir/Reference{:reference "Patient/0"}}]
        [:create {:fhir/type :fhir/Observation :id "0"
                  :subject #fhir/Reference{:reference "Patient/0"}
                  :encounter #fhir/Reference{:reference "Encounter/0"}}]]]

      (let [db-after @(d/transact node [[:patient-purge "0"]])]

        (testing "the patient, the observation and the encounter are gone"
          (is (nil? (d/resource-handle db-after "Patient" "0")))
          (is (nil? (d/resource-handle db-after "Encounter" "0")))
          (is (nil? (d/resource-handle db-after "Observation" "0")))
          (is (zero? (d/total-num-of-instance-changes db-after "Patient" "0")))
          (is (zero? (d/total-num-of-instance-changes db-after "Encounter" "0")))
          (is (zero? (d/total-num-of-instance-changes db-after "Observation" "0")))
          (is (zero? (d/total-num-of-type-changes db-after "Patient")))
          (is (zero? (d/total-num-of-type-changes db-after "Encounter")))
          (is (zero? (d/total-num-of-type-changes db-after "Observation")))
          (is (zero? (d/total-num-of-system-changes db-after)))
          (is (coll/empty? (d/instance-history db-after "Patient" "0")))
          (is (coll/empty? (d/instance-history db-after "Encounter" "0")))
          (is (coll/empty? (d/instance-history db-after "Observation" "0")))
          (is (coll/empty? (d/type-history db-after "Patient")))
          (is (coll/empty? (d/type-history db-after "Encounter")))
          (is (coll/empty? (d/type-history db-after "Observation")))
          (is (coll/empty? (d/system-history db-after)))
          (is (coll/empty? (d/changes db-after)))))))

  (testing "one patient with one MedicationAdministration and Medication"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Medication :id "0"}]
        [:put {:fhir/type :fhir/MedicationAdministration :id "0"
               :medication #fhir/Reference{:reference "Medication/0"}
               :subject #fhir/Reference{:reference "Patient/0"}}]]]

      (let [db-after @(d/transact node [[:patient-purge "0"]])]

        (testing "the Medication stays"
          (is (nil? (d/resource-handle db-after "Patient" "0")))
          (is (nil? (d/resource-handle db-after "MedicationAdministration" "0")))
          (is (= "0" (:id (d/resource-handle db-after "Medication" "0"))))
          (is (zero? (d/total-num-of-instance-changes db-after "Patient" "0")))
          (is (zero? (d/total-num-of-instance-changes db-after "MedicationAdministration" "0")))
          (is (= 1 (d/total-num-of-instance-changes db-after "Medication" "0")))
          (is (zero? (d/total-num-of-type-changes db-after "Patient")))
          (is (zero? (d/total-num-of-type-changes db-after "MedicationAdministration")))
          (is (= 1 (d/total-num-of-type-changes db-after "Medication")))
          (is (= 1 (d/total-num-of-system-changes db-after)))
          (is (coll/empty? (d/instance-history db-after "Patient" "0")))
          (is (coll/empty? (d/instance-history db-after "MedicationAdministration" "0")))
          (is (= 1 (count (d/instance-history db-after "Medication" "0"))))
          (is (coll/empty? (d/type-history db-after "Patient")))
          (is (coll/empty? (d/type-history db-after "MedicationAdministration")))
          (is (= 1 (count (d/type-history db-after "Medication"))))
          (is (= 1 (count (d/system-history db-after))))
          (is (coll/empty? (d/changes db-after)))))))

  (testing "purge one patient and delete another in the same transaction"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]]
       [[:create {:fhir/type :fhir/Patient :id "1"}]]
       [[:put {:fhir/type :fhir/Patient :id "0" :active true}]]]

      (let [db-after @(d/transact node [[:patient-purge "0"]
                                        [:delete "Patient" "1"]])]

        (testing "both patients are gone"
          (is (coll/empty? (d/type-list db-after "Patient"))))

        (testing "patient history contains two entries from patient 1"
          (is (= 2 (d/total-num-of-type-changes db-after "Patient")))
          (is (= ["1" "1"] (mapv :id (d/type-history db-after "Patient")))))

        (testing "system history contains two entries from patient 1"
          (is (= 2 (d/total-num-of-system-changes db-after)))
          (is (= ["1" "1"] (mapv :id (d/system-history db-after))))))))

  (testing "purge, create and purge a patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]]
       [[:patient-purge "0"]]]

      (let [db-after @(d/transact node [[:create {:fhir/type :fhir/Patient :id "0"}]])]

        (testing "the patient exists"
          (is (= ["0"] (mapv :id (d/type-list db-after "Patient")))))

        (testing "instance history contains one entry"
          (is (= 1 (d/total-num-of-instance-changes db-after "Patient" "0")))
          (is (= ["0"] (mapv :id (d/instance-history db-after "Patient" "0")))))

        (testing "type history contains one entry"
          (is (= 1 (d/total-num-of-type-changes db-after "Patient")))
          (is (= ["0"] (mapv :id (d/type-history db-after "Patient")))))

        (testing "system history contains one entry"
          (is (= 1 (d/total-num-of-system-changes db-after)))
          (is (= ["0"] (mapv :id (d/system-history db-after))))))))

  (testing "one patient linked by another patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :link [{:fhir/type :fhir.Patient/link
                       :other #fhir/Reference{:reference "Patient/0"}
                       :type #fhir/code"seealso"}]}]]]

      (given-failed-future (d/transact node [[:patient-purge "0"]])
        ::anom/category := ::anom/conflict
        ::anom/message := "Referential integrity violated. Resource `Patient/0` should be deleted but is referenced from `Patient/1`.")))

  (testing "one patient linked by another patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]
        [:create {:fhir/type :fhir/Patient :id "1"}]
        [:create {:fhir/type :fhir/Encounter :id "0"
                  :subject #fhir/Reference{:reference "Patient/0"}}]
        [:create {:fhir/type :fhir/Observation :id "1"
                  :subject #fhir/Reference{:reference "Patient/1"}
                  :encounter #fhir/Reference{:reference "Encounter/0"}}]]]

      (given-failed-future (d/transact node [[:patient-purge "0"]])
        ::anom/category := ::anom/conflict
        ::anom/message := "Referential integrity violated. Resource `Encounter/0` should be deleted but is referenced from `Observation/1`.")))

  (testing "purging an creating the same patient in one transaction fails"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]]]

      (given-failed-future (d/transact node [[:patient-purge "0"]
                                             [:create {:fhir/type :fhir/Patient :id "0"}]])
        ::anom/category := ::anom/conflict
        ::anom/message := "Duplicate transaction commands `create Patient/0` and `purge Patient/0`."))))

(deftest transact-patient-purge-too-many-test
  (log/set-min-level! :info)
  (st/unstrument)
  (testing "works with up to 100,000 history entries"
    (with-system-data [{:blaze.db/keys [node]} config]
      (vec (for [_ (range 50000)
                 active [true false]]
             [[:put {:fhir/type :fhir/Patient :id "0" :active active}]]))

      (let [db-before (d/db node)
            db-after @(d/transact node [[:patient-purge "0"]])]

        (testing "the patient is gone"
          (is (nil? (d/resource-handle db-after "Patient" "0")))
          (is (zero? (d/total-num-of-instance-changes db-after "Patient" "0")))
          (is (zero? (d/total-num-of-type-changes db-after "Patient")))
          (is (zero? (d/total-num-of-system-changes db-after)))
          (is (coll/empty? (d/instance-history db-after "Patient" "0")))
          (is (coll/empty? (d/type-history db-after "Patient")))
          (is (coll/empty? (d/system-history db-after)))
          (is (coll/empty? (d/changes db-after))))

        (testing "the instance history of db-before still contains 100,000 entries"
          (is (= 100000 (count (d/instance-history db-before "Patient" "0"))))))))

  (testing "fails on more then 100,000 history entries"
    (with-system-data [{:blaze.db/keys [node]} config]
      (into
       [[[:put {:fhir/type :fhir/Patient :id "0" :active false}]]]
       (for [_ (range 50000)
             active [true false]]
         [[:put {:fhir/type :fhir/Patient :id "0" :active active}]]))

      (given-failed-future (d/transact node [[:patient-purge "0"]])
        ::anom/category := ::anom/conflict
        ::anom/message := "Deleting the history of `Patient/0` failed because there are more than 100,000 history entries."
        :fhir/issue := "too-costly"))))

(deftest transact-test
  (testing "a transaction with duplicate resources fails"
    (testing "two puts"
      (with-system [{:blaze.db/keys [node]} config]
        (given-failed-future
         (d/transact
          node
          [[:put {:fhir/type :fhir/Patient :id "0"}]
           [:put {:fhir/type :fhir/Patient :id "0"}]])
          ::anom/category := ::anom/incorrect
          ::anom/message := "Duplicate resource `Patient/0`.")))

    (testing "one put and one delete"
      (with-system [{:blaze.db/keys [node]} config]
        (given-failed-future
         (d/transact
          node
          [[:put {:fhir/type :fhir/Patient :id "0"}]
           [:delete "Patient" "0"]])
          ::anom/category := ::anom/incorrect
          ::anom/message := "Duplicate resource `Patient/0`."))))

  (testing "failed transactions don't leave behind any inspectable data"
    (with-system [{:blaze.db/keys [node]} config]
      (testing "creating an active patient successfully"
        @(d/transact
          node
          [[:create {:fhir/type :fhir/Patient :id "0" :active true}]]))

      (testing "updating that patient to active=false in a failing transaction"
        (given-failed-future
         (d/transact
          node
          [[:put {:fhir/type :fhir/Patient :id "0" :active false}]
           [:create {:fhir/type :fhir/Observation :id "0"
                     :subject #fhir/Reference{:reference "Patient/1"}}]])
          ::anom/category := ::anom/conflict))

      (testing "creating a second patient in order to add a successful transaction on top"
        @(d/transact node [[:create {:fhir/type :fhir/Patient :id "1"}]]))

      (with-open-db [db node]
        (testing "the second patient is found in `db`"
          (given (d/resource-handle db "Patient" "1")
            :id := "1"))

        (testing "the first patient is still active"
          (given @(pull-resource db "Patient" "0")
            :id := "0"
            :active := true)))))

  (testing "a transaction violating referential integrity fails"
    (testing "creating an Observation were the subject doesn't exist"
      (testing "create"
        (with-system [{:blaze.db/keys [node]} config]
          (given-failed-future
           (d/transact
            node
            [[:create
              {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}}]])
            ::anom/category := ::anom/conflict
            ::anom/message := "Referential integrity violated. Resource `Patient/0` doesn't exist.")))

      (testing "put"
        (with-system [{:blaze.db/keys [node]} config]
          (given-failed-future
           (d/transact
            node
            [[:put
              {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}}]])
            ::anom/category := ::anom/conflict
            ::anom/message := "Referential integrity violated. Resource `Patient/0` doesn't exist."))))

    (testing "creating a List where the entry item will be deleted in the same transaction"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:create {:fhir/type :fhir/Observation :id "0"}]
          [:create {:fhir/type :fhir/Observation :id "1"}]]]

        (given-failed-future
         (d/transact
          node
          [[:create
            {:fhir/type :fhir/List :id "0"
             :entry
             [{:fhir/type :fhir.List/entry
               :item #fhir/Reference{:reference "Observation/0"}}
              {:fhir/type :fhir.List/entry
               :item #fhir/Reference{:reference "Observation/1"}}]}]
           [:delete "Observation" "1"]])
          ::anom/category := ::anom/conflict
          ::anom/message := "Referential integrity violated. Resource `Observation/1` should be deleted but is referenced from `List/0`."))))

  (testing "not enforcing referential integrity"
    (testing "creating an Observation where the subject doesn't exist"
      (testing "create"
        (with-system-data [{:blaze.db/keys [node]} (assoc-in config [:blaze.db/node :enforce-referential-integrity] false)]
          [[[:create
             {:fhir/type :fhir/Observation :id "0"
              :subject #fhir/Reference{:reference "Patient/0"}}]]]

          (given @(pull-resource (d/db node) "Observation" "0")
            :fhir/type := :fhir/Observation
            :id := "0"
            [:meta :versionId] := #fhir/id"1"
            [:subject :reference] := "Patient/0"
            [meta :blaze.db/op] := :create)))))

  (testing "creating 1000 transactions in parallel"
    (with-system [{:blaze.db/keys [node]} slow-resource-store-system]
      (let [db-futures
            (mapv
             #(-> (d/transact node [[:create {:fhir/type :fhir/Patient :id (str %)}]])
                  (ac/then-apply :db-after))
             (range 1000))]

        (testing "wait for all transactions finishing"
          @(ac/all-of db-futures))

        (testing "since we created a patient in every transaction"
          (testing "the number of patients equals the t of the database"
            (let [db (d/db node)]
              (is
               (every?
                true?
                (map
                 #(= % (d/type-total (d/as-of db %) "Patient"))
                 (range 1000))))))))))

  (testing "with failing resource storage"
    (testing "on put"
      (with-system [{:blaze.db/keys [node]} resource-store-failing-on-put-system]
        (given-failed-future
         (d/transact node [[:put {:fhir/type :fhir/Patient :id "0"}]])
          ::anom/category := ::anom/fault))))

  (testing "with failing resource indexer"
    (with-redefs
     [resource-indexer/index-resources
      (fn [_ _]
        (ac/failed-future (ex-info "" (ba/fault "" ::x ::y))))]
      (with-system [{:blaze.db/keys [node]} config]
        (given-failed-future
         (d/transact node [[:put {:fhir/type :fhir/Patient :id "0"}]])
          ::anom/category := ::anom/fault
          ::x ::y)))))

(deftest subscription-publisher-test
  (testing "with one task"
    (with-system [{:blaze.db/keys [node]} config]

      (let [publisher (d/changed-resources-publisher node "Task")
            processor (flow/take 1)
            future (flow/collect processor)]
        (flow/subscribe! publisher processor)

        @(d/transact node [[:create {:fhir/type :fhir/Task :id "0"}]])

        (given @future
          count := 1
          [0 count] := 1
          [0 0 :fhir/type] := :fhir/Task
          [0 0 :id] := "0"))))

  (testing "with two tasks in one transaction"
    (with-system [{:blaze.db/keys [node]} config]

      (let [publisher (d/changed-resources-publisher node "Task")
            processor (flow/take 1)
            future (flow/collect processor)]
        (flow/subscribe! publisher processor)

        @(d/transact node [[:create {:fhir/type :fhir/Task :id "0"}]
                           [:create {:fhir/type :fhir/Task :id "1"}]])

        (given @future
          count := 1
          [0 count] := 2
          [0 0 :fhir/type] := :fhir/Task
          [0 0 :id] := "0"
          [0 1 :fhir/type] := :fhir/Task
          [0 1 :id] := "1"))))

  (testing "with two tasks in two transaction"
    (with-system [{:blaze.db/keys [node]} config]

      (let [publisher (d/changed-resources-publisher node "Task")
            processor (flow/take 2)
            future (flow/collect processor)]
        (flow/subscribe! publisher processor)

        @(d/transact node [[:create {:fhir/type :fhir/Task :id "0"}]])
        @(d/transact node [[:create {:fhir/type :fhir/Task :id "1"}]])

        (given @future
          count := 2
          [0 count] := 1
          [0 0 :fhir/type] := :fhir/Task
          [0 0 :id] := "0"
          [1 count] := 1
          [1 0 :fhir/type] := :fhir/Task
          [1 0 :id] := "1"))))

  (testing "with one task after one failing transaction"
    (with-system [{:blaze.db/keys [node]} config]

      (let [publisher (d/changed-resources-publisher node "Task")
            processor (flow/take 1)
            future (flow/collect processor)]
        (flow/subscribe! publisher processor)

        ;; failing transaction is not published
        (given-failed-future (d/transact node [[:put {:fhir/type :fhir/Task :id "0"}
                                                [:if-match 2]]])
          ::anom/category := ::anom/conflict)

        @(d/transact node [[:create {:fhir/type :fhir/Task :id "0"}]])

        (given @future
          count := 1
          [0 count] := 1
          [0 0 :fhir/type] := :fhir/Task
          [0 0 :id] := "0")))))

(deftest as-of-test
  (with-system-data [{:blaze.db/keys [node]} config]
    [[[:put {:fhir/type :fhir/Patient :id "0"}]]
     [[:put {:fhir/type :fhir/Patient :id "1"}]]]

    (let [db (d/db node)]

      (testing "the newest t is 2"
        (is (= 2 (d/basis-t db))))

      (testing "the effective t of a DB as of 1 is 1"
        (is (= 1 (d/t (d/as-of db 1)))))

      (testing "the as-of-t of a DB as of 1 is 1"
        (is (= 1 (d/as-of-t (d/as-of db 1))))))))

(deftest t-test
  (with-system-data [{:blaze.db/keys [node]} config]
    [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

    (is (= 1 (d/t (d/db node))))))

(deftest tx-test
  (with-system-data [{:blaze.db/keys [node]} config]
    [[[:put {:fhir/type :fhir/Patient :id "id-142136"}]]]

    (with-open-db [db node]
      (given (d/tx db (d/basis-t db))
        :blaze.db.tx/instant := Instant/EPOCH))))

;; ---- Instance-Level Functions ----------------------------------------------

(deftest resource-handle-test
  (testing "a new node does not contain a resource"
    (with-system [{:blaze.db/keys [node]} config]
      (is (nil? (d/resource-handle (d/db node) "Patient" "foo")))))

  (testing "a resource handle"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]]]

      (let [resource-handle (d/resource-handle (d/db node) "Patient" "0")]

        (testing "is not deleted"
          (is (not (d/deleted? resource-handle))))

        (testing "is actually one"
          (is (d/resource-handle? resource-handle))))))

  (testing "doesn't find a resource handle mit prefix of it's id"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "00"}]]]

      (is (nil? (d/resource-handle (d/db node) "Patient" "0")))))

  (testing "a node contains a resource after a create transaction"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]]]

      (testing "pull"
        (given @(pull-resource (d/db node) "Patient" "0")
          :fhir/type := :fhir/Patient
          :id := "0"
          [:meta :versionId] := #fhir/id"1"
          [meta :blaze.db/tx :blaze.db/t] := 1
          [meta :blaze.db/num-changes] := 1))

      (testing "pull-content"
        (given @(d/pull-content node (d/resource-handle (d/db node) "Patient" "0"))
          :fhir/type := :fhir/Patient
          :id := "0"
          :meta := nil))

      (testing "number of changes is 1"
        (is (= 1 (:num-changes (d/resource-handle (d/db node) "Patient" "0")))))))

  (testing "a node contains a resource after a put transaction"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (given @(pull-resource (d/db node) "Patient" "0")
        :fhir/type := :fhir/Patient
        :id := "0"
        [:meta :versionId] := #fhir/id"1"
        [meta :blaze.db/tx :blaze.db/t] := 1
        [meta :blaze.db/num-changes] := 1)))

  (testing "a deleted resource"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:delete "Patient" "0"]]]

      (testing "is not deleted"
        (is (d/deleted? (d/resource-handle (d/db node) "Patient" "0"))))

      (testing "is flagged"
        (given @(pull-resource (d/db node) "Patient" "0")
          :fhir/type := :fhir/Patient
          :id := "0"
          [:meta :versionId] := #fhir/id"2"
          [meta :blaze.db/op] := :delete
          [meta :blaze.db/tx :blaze.db/t] := 2)))))

;; ---- Type-Level Functions --------------------------------------------------

(deftest type-list-and-total-test
  (testing "a new node has no patients"
    (with-system [{:blaze.db/keys [node]} config]
      (is (coll/empty? (d/type-list (d/db node) "Patient")))
      (is (zero? (d/type-total (d/db node) "Patient")))))

  (testing "a node with one patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (testing "has one list entry"
        (is (= 1 (d/type-total (d/db node) "Patient"))))

      (testing "contains that patient"
        (given @(d/pull-many node (d/type-list (d/db node) "Patient"))
          count := 1
          [0 :fhir/type] := :fhir/Patient
          [0 :id] := "0"
          [0 :meta fhir-spec/fhir-type] := :fhir/Meta
          [0 :meta :versionId] := #fhir/id"1"
          [0 :meta :lastUpdated] := Instant/EPOCH))))

  (testing "a node with one deleted patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:delete "Patient" "0"]]]

      (testing "doesn't contain it in the list"
        (is (coll/empty? (d/type-list (d/db node) "Patient")))
        (is (zero? (d/type-total (d/db node) "Patient"))))))

  (testing "a node with one recreated patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:delete "Patient" "0"]]
       [[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (testing "has one list entry"
        (is (= 1 (count (d/type-list (d/db node) "Patient"))))
        (is (= 1 (d/type-total (d/db node) "Patient"))))))

  (testing "a node with two patients in two transactions"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:put {:fhir/type :fhir/Patient :id "1"}]]]

      (testing "has two list entries"
        (is (= 2 (d/type-total (d/db node) "Patient"))))

      (testing "contains both patients in id order"
        (given @(d/pull-many node (d/type-list (d/db node) "Patient"))
          count := 2
          [0 :fhir/type] := :fhir/Patient
          [0 :id] := "0"
          [0 :meta :versionId] := #fhir/id"1"
          [1 :fhir/type] := :fhir/Patient
          [1 :id] := "1"
          [1 :meta :versionId] := #fhir/id"2"))

      (testing "it is possible to start with the second patient"
        (given @(d/pull-many node (d/type-list (d/db node) "Patient" "1"))
          count := 1
          [0 :fhir/type] := :fhir/Patient
          [0 :id] := "1"
          [0 :meta :versionId] := #fhir/id"2"))

      (testing "overshooting the start-id returns an empty collection"
        (is (coll/empty? (d/type-list (d/db node) "Patient" "2"))))))

  (testing "a node with two patients in one transaction"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "1"}]]]

      (testing "has two list entries"
        (is (= 2 (d/type-total (d/db node) "Patient"))))

      (testing "contains both patients in id order"
        (given @(d/pull-many node (d/type-list (d/db node) "Patient"))
          count := 2
          [0 :fhir/type] := :fhir/Patient
          [0 :id] := "0"
          [0 :meta :versionId] := #fhir/id"1"
          [1 :fhir/type] := :fhir/Patient
          [1 :id] := "1"
          [1 :meta :versionId] := #fhir/id"1"))

      (testing "it is possible to start with the second patient"
        (given @(d/pull-many node (d/type-list (d/db node) "Patient" "1"))
          count := 1
          [0 :fhir/type] := :fhir/Patient
          [0 :id] := "1"
          [0 :meta :versionId] := #fhir/id"1"))

      (testing "overshooting the start-id returns an empty collection"
        (is (coll/empty? (d/type-list (d/db node) "Patient" "2"))))))

  (testing "a node with one updated patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0" :active false}]]
       [[:put {:fhir/type :fhir/Patient :id "0" :active true}]]]

      (testing "has one list entry"
        (is (= 1 (d/type-total (d/db node) "Patient"))))

      (testing "contains the updated patient"
        (given @(d/pull-many node (d/type-list (d/db node) "Patient"))
          count := 1
          [0 :fhir/type] := :fhir/Patient
          [0 :id] := "0"
          [0 :active] := true
          [0 :meta :versionId] := #fhir/id"2"))))

  (testing "a node with resources of different types"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:put {:fhir/type :fhir/Observation :id "0"}]]]

      (testing "has one patient list entry"
        (is (= 1 (count (d/type-list (d/db node) "Patient"))))
        (is (= 1 (d/type-total (d/db node) "Patient"))))

      (testing "has one observation list entry"
        (is (= 1 (count (d/type-list (d/db node) "Observation"))))
        (is (= 1 (d/type-total (d/db node) "Observation"))))))

  (testing "the database is immutable"
    (testing "while updating a patient"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0" :active false}]]]

        (let [db (d/db node)]
          @(d/transact node [[:put {:fhir/type :fhir/Patient :id "0" :active true}]])

          (testing "the original database"
            (testing "has still only one list entry"
              (is (= 1 (d/type-total db "Patient"))))

            (testing "contains still the original patient"
              (given @(d/pull-many node (d/type-list db "Patient"))
                count := 1
                [0 :fhir/type] := :fhir/Patient
                [0 :id] := "0"
                [0 :active] := false
                [0 :meta :versionId] := #fhir/id"1"))))))

    (testing "while adding another patient"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

        (let [db (d/db node)]
          @(d/transact node [[:put {:fhir/type :fhir/Patient :id "1"}]])

          (testing "the original database"
            (testing "has still only one patient"
              (is (= 1 (d/type-total db "Patient"))))

            (testing "contains still only the first patient"
              (given @(d/pull-many node (d/type-list db "Patient"))
                count := 1
                [0 :fhir/type] := :fhir/Patient
                [0 :id] := "0"
                [0 :meta :versionId] := #fhir/id"1")))))))

  (testing "resources will be returned in lexical id order"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "00"}]]]

      (given @(d/pull-many node (d/type-list (d/db node) "Patient"))
        count := 2
        [0 :id] := "0"
        [1 :id] := "00")))

  (testing "type lists can be processed twice"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "1"}]]]

      (let [coll (d/type-list (d/db node) "Patient")]
        (is (= "0" (:id (coll/first coll))))
        (is (= ["0" "1"] (mapv :id coll)))))))

(defn- pull-type-query
  ([node type clauses]
   (when-ok [handles (d/type-query (d/db node) type clauses)]
     @(d/pull-many node handles)))
  ([node type clauses start-id]
   (when-ok [handles (d/type-query (d/db node) type clauses start-id)]
     @(d/pull-many node handles))))

(defn- count-type-query [node type clauses]
  (when-ok [query (d/compile-type-query node type clauses)]
    @(d/count-query (d/db node) query)))

(defn- explain-type-query [node type clauses]
  (when-ok [query (d/compile-type-query node type clauses)]
    (d/explain-query (d/db node) query)))

(defmacro given-type-query
  "Combines `pull-type-query` with `count-type-query`. Assumes that the first
  line of assertions is `count := x` because it takes the count from it."
  [node type clauses & [_count-sym _eq-sym count :as body]]
  `(do (given (pull-type-query ~node ~type ~clauses)
         ~@body)
       (is (= ~count (count-type-query ~node ~type ~clauses)))))

(def system-clock-config
  (assoc-in config [::tx-log/local :clock] (ig/ref :blaze.test/system-clock)))

(deftest type-query-test
  (with-system [{:blaze.db/keys [node]} config]
    (testing "a new node has no patients"
      (is (coll/empty? (d/type-query (d/db node) "Patient" [["gender" "male"]])))
      (is (coll/empty? (d/type-query (d/db node) "Patient" [["gender" "male"]] "0"))))

    (testing "sort clauses are only allowed at first position"
      (given (d/type-query (d/db node) "Patient" [["gender" "male"]
                                                  [:sort "_lastUpdated" :desc]])
        ::anom/category := ::anom/incorrect
        ::anom/message := "Sort clauses are only allowed at first position."))

    (testing "unknown search-param in sort clause"
      (given (d/type-query (d/db node) "Patient" [[:sort "foo" :desc]])
        ::anom/category := ::anom/incorrect
        ::anom/message := "Unknown search-param `foo` in sort clause.")))

  (testing "a node with one patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0" :active true}]]]

      (testing "the patient can be found"
        (given-type-query node "Patient" [["active" "true"]]
          count := 1
          [0 :fhir/type] := :fhir/Patient
          [0 :id] := "0"))

      (testing "an unknown search-param errors"
        (given (d/type-query (d/db node) "Patient" [["foo" "bar"]
                                                    ["active" "true"]])
          ::anom/category := ::anom/not-found
          ::anom/message := "The search-param with code `foo` and type `Patient` was not found.")

        (testing "with start id"
          (given (d/type-query (d/db node) "Patient" [["foo" "bar"]
                                                      ["active" "true"]]
                               "0")
            ::anom/category := ::anom/not-found
            ::anom/message := "The search-param with code `foo` and type `Patient` was not found.")))))

  (testing "a node with two patients in one transaction"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0" :active true}]
        [:put {:fhir/type :fhir/Patient :id "1" :active false}]]]

      (testing "only the active patient will be found"
        (given-type-query node "Patient" [["active" "true"]]
          count := 1
          [0 :fhir/type] := :fhir/Patient
          [0 :id] := "0"))

      (testing "only the non-active patient will be found"
        (given-type-query node "Patient" [["active" "false"]]
          count := 1
          [0 :fhir/type] := :fhir/Patient
          [0 :id] := "1"))

      (testing "both patients will be found"
        (given-type-query node "Patient" [["active" "true" "false"]]
          count := 2
          [0 :fhir/type] := :fhir/Patient
          [0 :id] := "0"
          [1 :fhir/type] := :fhir/Patient
          [1 :id] := "1"))))

  (testing "search by token"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0" :active true}]
        [:put {:fhir/type :fhir/Patient :id "1" :active false}]
        [:put {:fhir/type :fhir/Patient :id "2" :active true}]]
       [[:delete "Patient" "2"]]]

      (testing "the deleted patient isn't returned"
        (given-type-query node "Patient" [["active" "true"]]
          count := 1
          [0 :fhir/type] := :fhir/Patient
          [0 :id] := "0")))

    (testing "works with variable length ids"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Patient :id "1" :active true}]
          [:put {:fhir/type :fhir/Patient :id "10" :active true}]]]

        (given-type-query node "Patient" [["active" "true"]]
          count := 2
          [0 :fhir/type] := :fhir/Patient
          [0 :id] := "1"
          [1 :fhir/type] := :fhir/Patient
          [1 :id] := "10"))))

  (testing "does not find the deleted active patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0" :active true}]
        [:put {:fhir/type :fhir/Patient :id "1" :active true}]]
       [[:delete "Patient" "1"]]]

      (given-type-query node "Patient" [["active" "true"]]
        count := 1
        [0 :fhir/type] := :fhir/Patient
        [0 :id] := "0")))

  (testing "does not find the updated patient that is no longer active"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0" :active true}]
        [:put {:fhir/type :fhir/Patient :id "1" :active true}]]
       [[:put {:fhir/type :fhir/Patient :id "1" :active false}]]]

      (given-type-query node "Patient" [["active" "true"]]
        count := 1
        [0 :fhir/type] := :fhir/Patient
        [0 :id] := "0")))

  ;; TODO: fix this https://github.com/samply/blaze/issues/904
  #_(testing "sorting by _lastUpdated returns only the newest version of the patient"
      (with-system-data [{:blaze.db/keys [node]} (with-system-clock config)]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]]
         [[:put {:fhir/type :fhir/Patient :id "1"}]]]

        ;; we have to sleep more than one second here because dates are index only with second resolution
        (Thread/sleep 2000)
        @(d/transact node [[:put {:fhir/type :fhir/Patient :id "0"}]])

        (doseq [dir [:asc :desc]]
          (given-type-query node "Patient" [[:sort "_lastUpdated" dir]]
            count := 2
            [0 :fhir/type] := :fhir/Patient
            [0 :id] := "0"
            [0 :active] := false))))

  (testing "a node with three patients in one transaction"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0" :active true}]
        [:put {:fhir/type :fhir/Patient :id "1" :active false}]
        [:put {:fhir/type :fhir/Patient :id "2" :active true}]]]

      (testing "two active patients will be found"
        (given-type-query node "Patient" [["active" "true"]]
          count := 2
          [0 :fhir/type] := :fhir/Patient
          [0 :id] := "0"
          [1 :fhir/type] := :fhir/Patient
          [1 :id] := "2"))

      (testing "it is possible to start with the second patient"
        (given (pull-type-query node "Patient" [["active" "true"]] "2")
          count := 1
          [0 :fhir/type] := :fhir/Patient
          [0 :id] := "2"))))

  (testing "special case of _lastUpdated date search parameter"
    (testing "inequality searches do return every resource only once"
      (with-system-data [{:blaze.db/keys [node]} system-clock-config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]]
         [[:put {:fhir/type :fhir/Patient :id "1"}]]]

        ;; we have to sleep more than one second here because dates are index only with second resolution
        (Thread/sleep 2000)
        @(d/transact node [[:put {:fhir/type :fhir/Patient :id "0"}]])

        (given-type-query node "Patient" [["_lastUpdated" "ge2000-01-01"]]
          count := 2
          [0 :id] := "0"
          [1 :id] := "1")

        (given-type-query node "Patient" [["_lastUpdated" "lt3000-01-01"]]
          count := 2
          [0 :id] := "0"
          [1 :id] := "1"))))

  (testing "Patient phonetic"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :name [#fhir/HumanName{:family "Doe" :given ["John"]}]}]]]

      (testing "family"
        (given-type-query node "Patient" [["phonetic" "Day"]]
          count := 1
          [0 :id] := "0")

        (testing "with matcher"
          (let [matcher (d/compile-type-matcher node "Patient" [["phonetic" "Day"]])
                db (d/db node)
                xform (d/matcher-transducer db matcher)]
            (given (into [] xform (d/type-list db "Patient"))
              count := 1
              [0 :id] := "0"))))

      (testing "given"
        (given-type-query node "Patient" [["phonetic" "Jane"]]
          count := 1
          [0 :id] := "0"))))

  (testing "Patient with very long name"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :active true
               :name [(type/human-name {:family (apply str (repeat 1000 "a"))})]}]]]

      (testing "as first clause"
        (given (pull-type-query node "Patient" [["family" (apply str (repeat 1000 "a"))]])
          count := 1
          [0 :id] := "0"))

      (testing "as second clause"
        (given (pull-type-query node "Patient" [["active" "true"]
                                                ["family" (apply str (repeat 1000 "a"))]])
          count := 1
          [0 :id] := "0"))))

  (testing "Patient with random name"
    (satisfies-prop 100
      (prop/for-all [name fg/string-value]
        (with-system-data [{:blaze.db/keys [node]} config]
          [[[:put {:fhir/type :fhir/Patient :id "0"
                   :active true
                   :name [(type/human-name {:family name})]}]]]

          (testing "as first clause"
            (given-type-query node "Patient" [["family" name]]
              count := 1
              [0 :id] := "0"))

          (testing "as second clause"
            (given-type-query node "Patient" [["active" "true"]
                                              ["family" name]]
              count := 1
              [0 :id] := "0"))))))

  (testing "Patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "id-0"
               :meta #fhir/Meta{:profile [#fhir/canonical"http://example.com/profile-uri-145024"]}
               :identifier [#fhir/Identifier{:value #fhir/string"0"}]
               :active false
               :gender #fhir/code"male"
               :birthDate #fhir/date"2020-02-08"
               :deceased true
               :address
               [#fhir/Address{:line ["Philipp-Rosenthal-Straße 27"]
                              :city "Leipzig"}]
               :name [#fhir/HumanName{:family "Müller"}]}]
        [:put {:fhir/type :fhir/Patient :id "id-1"
               :active true
               :gender #fhir/code"female"
               :birthDate #fhir/date"2020-02"
               :address [#fhir/Address{:city "Berlin"}]
               :telecom
               [{:fhir/type :fhir/ContactPoint
                 :system #fhir/code"email"
                 :value #fhir/string"foo@bar.baz"}
                {:fhir/type :fhir/ContactPoint
                 :system #fhir/code"phone"
                 :value #fhir/string"0815"}]}]
        [:put {:fhir/type :fhir/Patient :id "id-2"
               :active false
               :gender #fhir/code"female"
               :birthDate #fhir/date"2020"
               :deceased #fhir/dateTime"2020-03"
               :address
               [#fhir/Address{:line ["Liebigstraße 20a"]
                              :city "Leipzig"}]
               :name [#fhir/HumanName{:family "Schmidt"}]}]
        [:put {:fhir/type :fhir/Patient :id "id-3"
               :birthDate #fhir/date"2019"}]
        [:put {:fhir/type :fhir/Patient :id "id-4"
               :birthDate #fhir/date"2021"}]
        [:put {:fhir/type :fhir/Patient :id "id-5"}]]
       [[:delete "Patient" "id-5"]]]

      (testing "_id"
        (given-type-query node "Patient" [["_id" "id-1"]]
          count := 1
          [0 :id] := "id-1"))

      (testing "_lastUpdated"
        (testing "all resources are created at EPOCH"
          (given-type-query node "Patient" [["_lastUpdated" "1970-01-01"]]
            count := 5))

        (testing "no resource is created after EPOCH"
          (given-type-query node "Patient" [["_lastUpdated" "gt1970-01-02"]]
            count := 0)))

      (testing "_profile"
        (given-type-query node "Patient" [["_profile" "http://example.com/profile-uri-145024"]]
          count := 1
          [0 :id] := "id-0"))

      (testing "active"
        (given-type-query node "Patient" [["active" "true"]]
          count := 1
          [0 :id] := "id-1"))

      (testing "gender and active"
        (given-type-query node "Patient" [["gender" "female"]
                                          ["active" "true" "false"]]
          count := 2
          [0 :id] := "id-1"
          [1 :id] := "id-2"))

      (testing "address with line"
        (testing "in first position"
          (given-type-query node "Patient" [["address" "Liebigstraße"]]
            count := 1
            [0 :id] := "id-2"))

        (testing "in second position"
          (given-type-query node "Patient" [["gender" "female"]
                                            ["address" "Liebigstraße"]]
            count := 1
            [0 :id] := "id-2")))

      (testing "address with city"
        (testing "full result"
          (given-type-query node "Patient" [["address" "Leipzig"]]
            count := 2
            [0 :id] := "id-0"
            [1 :id] := "id-2"))

        (testing "it is possible to start with the second patient"
          (given (pull-type-query node "Patient" [["address" "Leipzig"]] "id-2")
            count := 1
            [0 :id] := "id-2")))

      (testing "address-city full"
        (given-type-query node "Patient" [["address-city" "Leipzig"]]
          count := 2
          [0 :id] := "id-0"
          [1 :id] := "id-2"))

      (testing "address-city prefix"
        (testing "full result"
          (given-type-query node "Patient" [["address-city" "Leip"]]
            count := 2
            [0 :id] := "id-0"
            [1 :id] := "id-2"))

        (testing "it is possible to start with the second patient"
          (given (pull-type-query node "Patient" [["address-city" "Leip"]] "id-2")
            count := 1
            [0 :id] := "id-2")))

      (testing "address-city and family prefix"
        (given-type-query node "Patient" [["address-city" "Leip"]
                                          ["family" "Sch"]]
          count := 1
          [0 :id] := "id-2"))

      (testing "address-city and gender"
        (given-type-query node "Patient" [["address-city" "Leipzig"]
                                          ["gender" "female"]]
          count := 1
          [0 :id] := "id-2"))

      (testing "gender and address-city with multiple values"
        (given-type-query node "Patient" [["gender" "female"]
                                          ["address-city" "Leipzig" "Berlin"]]
          count := 2
          [0 :id] := "id-1"
          [1 :id] := "id-2"))

      (testing "birthdate"
        (testing "without a prefix (same as eq)"
          (testing "with day precision"
            (testing "fully containing three patients"
              (given-type-query node "Patient" [["birthdate" "2020"]]
                count := 3
                [0 :id] := "id-2"
                [1 :id] := "id-1"
                [2 :id] := "id-0")

              (testing "it is possible to start with the second patient"
                (given (pull-type-query node "Patient" [["birthdate" "2020"]] "id-1")
                  count := 2
                  [0 :id] := "id-1"
                  [1 :id] := "id-0"))

              (testing "it is possible to start with the third patient"
                (given (pull-type-query node "Patient" [["birthdate" "2020"]] "id-0")
                  count := 1
                  [0 :id] := "id-0")))

            (testing "fully containing two patients"
              (given-type-query node "Patient" [["birthdate" "2020-02"]]
                count := 2
                [0 :id] := "id-1"
                [1 :id] := "id-0")

              (testing "it is possible to start with the second patient"
                (given (pull-type-query node "Patient" [["birthdate" "2020-02"]] "id-0")
                  count := 1
                  [0 :id] := "id-0")))

            (testing "fully containing one patient"
              (given-type-query node "Patient" [["birthdate" "2020-02-08"]]
                count := 1
                [0 :id] := "id-0"))

            (testing "fully containing no patient"
              (given-type-query node "Patient" [["birthdate" "2020-02-09"]]
                count := 0))))

        (testing "with `eq` prefix"
          (given-type-query node "Patient" [["birthdate" "eq2020"]]
            count := 3
            [0 :id] := "id-2"
            [1 :id] := "id-1"
            [2 :id] := "id-0"))

        (testing "with ge prefix"
          (testing "with day precision"
            (testing "overlapping four patients"
              (testing "starting at the most specific birthdate"
                (given-type-query node "Patient" [["birthdate" "ge2020-02-08"]]
                  count := 4
                  [0 :id] := "id-2"
                  [1 :id] := "id-1"
                  [2 :id] := "id-0"
                  [3 :id] := "id-4")

                (testing "it is possible to start with the second patient"
                  (given (pull-type-query node "Patient" [["birthdate" "ge2020-02-08"]] "id-1")
                    count := 3
                    [0 :id] := "id-1"
                    [1 :id] := "id-0"
                    [2 :id] := "id-4"))

                (testing "it is possible to start with the third patient"
                  (given (pull-type-query node "Patient" [["birthdate" "ge2020-02-08"]] "id-0")
                    count := 2
                    [0 :id] := "id-0"
                    [1 :id] := "id-4"))

                (testing "it is possible to start with the fourth patient"
                  (given (pull-type-query node "Patient" [["birthdate" "ge2020-02-08"]] "id-4")
                    count := 1
                    [0 :id] := "id-4")))

              (testing "starting before the most specific birthdate"
                (given-type-query node "Patient" [["birthdate" "ge2020-02-07"]]
                  count := 4
                  [0 :id] := "id-2"
                  [1 :id] := "id-1"
                  [2 :id] := "id-0"
                  [3 :id] := "id-4")))

            (testing "overlapping three patients"
              (testing "starting after the most specific birthdate"
                (given-type-query node "Patient" [["birthdate" "ge2020-02-09"]]
                  count := 3
                  [0 :id] := "id-2"
                  [1 :id] := "id-1"
                  [2 :id] := "id-4"))

              (testing "starting at the last day of 2020-02"
                (given-type-query node "Patient" [["birthdate" "ge2020-02-29"]]
                  count := 3
                  [0 :id] := "id-2"
                  [1 :id] := "id-1"
                  [2 :id] := "id-4")))

            (testing "overlapping two patients"
              (testing "starting at the first day of 2020-03"
                (given-type-query node "Patient" [["birthdate" "ge2020-03-01"]]
                  count := 2
                  [0 :id] := "id-2"
                  [1 :id] := "id-4"))

              (testing "starting at the last day of 2020"
                (given-type-query node "Patient" [["birthdate" "ge2020-12-31"]]
                  count := 2
                  [0 :id] := "id-2"
                  [1 :id] := "id-4")))

            (testing "overlapping one patient"
              (testing "starting at the first day of 2021"
                (given-type-query node "Patient" [["birthdate" "ge2021-01-01"]]
                  count := 1
                  [0 :id] := "id-4")))

            (testing "overlapping no patient"
              (testing "starting at the first day of 2022"
                (given-type-query node "Patient" [["birthdate" "ge2022-01-01"]]
                  count := 0)))))

        (testing "with gt prefix"
          (testing "with day precision"
            (testing "overlapping three patients"
              (testing "starting at the most specific birthdate"
                (given-type-query node "Patient" [["birthdate" "gt2020-02-08"]]
                  count := 3
                  [0 :id] := "id-2"
                  [1 :id] := "id-1"
                  [2 :id] := "id-4")

                (testing "it is possible to start with the second patient"
                  (given (pull-type-query node "Patient" [["birthdate" "gt2020-02-08"]] "id-1")
                    count := 2
                    [0 :id] := "id-1"
                    [1 :id] := "id-4"))

                (testing "it is possible to start with the third patient"
                  (given (pull-type-query node "Patient" [["birthdate" "gt2020-02-08"]] "id-4")
                    count := 1
                    [0 :id] := "id-4")))

              (testing "starting before the most specific birthdate"
                (given-type-query node "Patient" [["birthdate" "gt2020-02-07"]]
                  count := 4
                  [0 :id] := "id-2"
                  [1 :id] := "id-1"
                  [2 :id] := "id-0"
                  [3 :id] := "id-4")))

            (testing "overlapping three patients"
              (testing "starting after the most specific birthdate"
                (given-type-query node "Patient" [["birthdate" "gt2020-02-09"]]
                  count := 3
                  [0 :id] := "id-2"
                  [1 :id] := "id-1"
                  [2 :id] := "id-4"))

              (testing "starting at the last day of 2020-02"
                (given-type-query node "Patient" [["birthdate" "gt2020-02-29"]]
                  count := 2
                  [0 :id] := "id-2"
                  [1 :id] := "id-4")))

            (testing "overlapping two patients"
              (testing "starting at the first day of 2020-03"
                (given-type-query node "Patient" [["birthdate" "gt2020-03-01"]]
                  count := 2
                  [0 :id] := "id-2"
                  [1 :id] := "id-4"))

              (testing "starting at the last day of 2020"
                (given-type-query node "Patient" [["birthdate" "gt2020-12-31"]]
                  count := 1
                  [0 :id] := "id-4")))

            (testing "overlapping one patient"
              (testing "starting at the first day of 2021"
                (given-type-query node "Patient" [["birthdate" "gt2021-01-01"]]
                  count := 1
                  [0 :id] := "id-4")))

            (testing "overlapping no patient"
              (testing "starting at the first day of 2022"
                (given-type-query node "Patient" [["birthdate" "gt2022-01-01"]]
                  count := 0)))))

        (testing "with lt prefix"
          (testing "with day precision"
            (testing "overlapping three patients"
              (testing "starting at the most specific birthdate"
                (given-type-query node "Patient" [["birthdate" "lt2020-02-08"]]
                  count := 3
                  [0 :id] := "id-1"
                  [1 :id] := "id-2"
                  [2 :id] := "id-3")

                (testing "it is possible to start with the second patient"
                  (given (pull-type-query node "Patient" [["birthdate" "lt2020-02-08"]] "id-2")
                    count := 2
                    [0 :id] := "id-2"
                    [1 :id] := "id-3"))

                (testing "it is possible to start with the third patient"
                  (given (pull-type-query node "Patient" [["birthdate" "lt2020-02-08"]] "id-3")
                    count := 1
                    [0 :id] := "id-3")))

              (testing "starting after the most specific birthdate"
                (given-type-query node "Patient" [["birthdate" "lt2020-02-09"]]
                  count := 4
                  [0 :id] := "id-0"
                  [1 :id] := "id-1"
                  [2 :id] := "id-2"
                  [3 :id] := "id-3")))))

        (testing "with le prefix"
          (testing "with day precision"
            (testing "overlapping four patients"
              (testing "starting at the most specific birthdate"
                (given-type-query node "Patient" [["birthdate" "le2020-02-08"]]
                  count := 4
                  [0 :id] := "id-3"
                  [1 :id] := "id-2"
                  [2 :id] := "id-1"
                  [3 :id] := "id-0")

                (testing "it is possible to start with the second patient"
                  (given (pull-type-query node "Patient" [["birthdate" "le2020-02-08"]] "id-2")
                    count := 3
                    [0 :id] := "id-2"
                    [1 :id] := "id-1"
                    [2 :id] := "id-0"))

                (testing "it is possible to start with the third patient"
                  (given (pull-type-query node "Patient" [["birthdate" "le2020-02-08"]] "id-1")
                    count := 2
                    [0 :id] := "id-1"
                    [1 :id] := "id-0"))

                (testing "it is possible to start with the fourth patient"
                  (given (pull-type-query node "Patient" [["birthdate" "le2020-02-08"]] "id-0")
                    count := 1
                    [0 :id] := "id-0")))

              (testing "starting after the most specific birthdate"
                (given-type-query node "Patient" [["birthdate" "le2020-02-09"]]
                  count := 4
                  [0 :id] := "id-3"
                  [1 :id] := "id-2"
                  [2 :id] := "id-1"
                  [3 :id] := "id-0")))

            (testing "overlapping three patients"
              (testing "starting before the most specific birthdate"
                (given-type-query node "Patient" [["birthdate" "le2020-02-07"]]
                  count := 3
                  [0 :id] := "id-3"
                  [1 :id] := "id-2"
                  [2 :id] := "id-1"))

              (testing "starting at the first day of 2020-02"
                (given-type-query node "Patient" [["birthdate" "le2020-02-01"]]
                  count := 3
                  [0 :id] := "id-3"
                  [1 :id] := "id-2"
                  [2 :id] := "id-1")))

            (testing "overlapping two patients"
              (testing "starting at the last day of 2020-01"
                (given-type-query node "Patient" [["birthdate" "le2020-01-31"]]
                  count := 2
                  [0 :id] := "id-3"
                  [1 :id] := "id-2"))

              (testing "starting at the first day of 2020"
                (given-type-query node "Patient" [["birthdate" "le2020-01-01"]]
                  count := 2
                  [0 :id] := "id-3"
                  [1 :id] := "id-2")))

            (testing "overlapping one patient"
              (testing "starting at the last day of 2019"
                (given-type-query node "Patient" [["birthdate" "le2019-12-31"]]
                  count := 1
                  [0 :id] := "id-3")))

            (testing "overlapping no patient"
              (testing "starting at the last day of 2018"
                (given-type-query node "Patient" [["birthdate" "le2018-12-31"]]
                  count := 0)))))

        (testing "with ap prefix"
          (testing "with day precision"
            (testing "overlapping three patients"
              (given-type-query node "Patient" [["birthdate" "ap2020-02-08"]]
                count := 3
                [0 :id] := "id-2"
                [1 :id] := "id-1"
                [2 :id] := "id-0")

              (testing "it is possible to start with the second patient"
                (given (pull-type-query node "Patient" [["birthdate" "ap2020-02-08"]] "id-1")
                  count := 2
                  [0 :id] := "id-1"
                  [1 :id] := "id-0"))

              (testing "it is possible to start with the third patient"
                (given (pull-type-query node "Patient" [["birthdate" "ap2020-02-08"]] "id-0")
                  count := 1
                  [0 :id] := "id-0")))

            (testing "overlapping two patients"
              (doseq [date ["ap2020-02-07" "ap2020-02-09"]]
                (given-type-query node "Patient" [["birthdate" date]]
                  count := 2
                  [0 :id] := "id-2"
                  [1 :id] := "id-1"))

              (testing "it is possible to start with the second patient"
                (doseq [date ["ap2020-02-07" "ap2020-02-09"]]
                  (given (pull-type-query node "Patient" [["birthdate" date]] "id-1")
                    count := 1
                    [0 :id] := "id-1"))))

            (testing "overlapping one patient"
              (doseq [date ["ap2020-01-31" "ap2020-03-01"]]
                (given-type-query node "Patient" [["birthdate" date]]
                  count := 1
                  [0 :id] := "id-2")))

            (testing "overlapping no patient"
              (doseq [date ["ap2018-12-31" "ap2022-01-01"]]
                (given-type-query node "Patient" [["birthdate" date]]
                  count := 0))))

          (testing "with month precision"
            (testing "overlapping three patients"
              (given-type-query node "Patient" [["birthdate" "ap2020-02"]]
                count := 3
                [0 :id] := "id-2"
                [1 :id] := "id-1"
                [2 :id] := "id-0")

              (testing "it is possible to start with the second patient"
                (given (pull-type-query node "Patient" [["birthdate" "ap2020-02"]] "id-1")
                  count := 2
                  [0 :id] := "id-1"
                  [1 :id] := "id-0"))

              (testing "it is possible to start with the third patient"
                (given (pull-type-query node "Patient" [["birthdate" "ap2020-02"]] "id-0")
                  count := 1
                  [0 :id] := "id-0")))

            (testing "overlapping one patient"
              (given-type-query node "Patient" [["birthdate" "ap2020-03"]]
                count := 1
                [0 :id] := "id-2"))

            (testing "overlapping no patient"
              (doseq [date ["ap2018-12" "ap2022-01"]]
                (given-type-query node "Patient" [["birthdate" date]]
                  count := 0))))

          (testing "with year precision"
            (testing "overlapping three patients"
              (given-type-query node "Patient" [["birthdate" "ap2020"]]
                count := 3
                [0 :id] := "id-2"
                [1 :id] := "id-1"
                [2 :id] := "id-0")

              (testing "it is possible to start with the second patient"
                (given (pull-type-query node "Patient" [["birthdate" "ap2020"]] "id-1")
                  count := 2
                  [0 :id] := "id-1"
                  [1 :id] := "id-0"))

              (testing "it is possible to start with the third patient"
                (given (pull-type-query node "Patient" [["birthdate" "ap2020"]] "id-0")
                  count := 1
                  [0 :id] := "id-0")))

            (testing "overlapping no patient"
              (doseq [date ["ap2018" "ap2022"]]
                (given-type-query node "Patient" [["birthdate" date]]
                  count := 0))))))

      (testing "gender and birthdate"
        (given-type-query node "Patient" [["gender" "male" "female"]
                                          ["birthdate" "2020-02"]]
          count := 2
          [0 :id] := "id-0"
          [1 :id] := "id-1"))

      (testing "gender and birthdate with multiple values"
        (given-type-query node "Patient" [["gender" "male" "female"]
                                          ["birthdate" "2020-02-09" "2020"]]
          count := 3
          [0 :id] := "id-0"
          [1 :id] := "id-1"
          [2 :id] := "id-2"))

      (testing "gender and birthdate with prefix"
        (testing "with ge prefix"
          (given-type-query node "Patient" [["gender" "male" "female"]
                                            ["birthdate" "ge2020"]]
            count := 3
            [0 :id] := "id-0"
            [1 :id] := "id-1"
            [2 :id] := "id-2")

          (given-type-query node "Patient" [["gender" "male" "female"]
                                            ["birthdate" "ge2020-02-07"]]
            count := 3
            [0 :id] := "id-0"
            [1 :id] := "id-1"
            [2 :id] := "id-2"))

        (testing "with gt prefix"
          (given-type-query node "Patient" [["gender" "male" "female"]
                                            ["birthdate" "gt2020"]]
            count := 0)

          (given-type-query node "Patient" [["gender" "male" "female"]
                                            ["birthdate" "gt2020-02-07"]]
            count := 3
            [0 :id] := "id-0"
            [1 :id] := "id-1"
            [2 :id] := "id-2"))

        (testing "with le prefix"
          (given-type-query node "Patient" [["gender" "male" "female"]
                                            ["birthdate" "le2020"]]
            count := 3
            [0 :id] := "id-0"
            [1 :id] := "id-1"
            [2 :id] := "id-2")

          (given-type-query node "Patient" [["gender" "male" "female"]
                                            ["birthdate" "le2020-02"]]
            count := 3
            [0 :id] := "id-0"
            [1 :id] := "id-1"
            [2 :id] := "id-2")

          (given-type-query node "Patient" [["gender" "male" "female"]
                                            ["birthdate" "le2021"]]
            count := 3
            [0 :id] := "id-0"
            [1 :id] := "id-1"
            [2 :id] := "id-2"))

        (testing "with lt prefix"
          (given-type-query node "Patient" [["gender" "male" "female"]
                                            ["birthdate" "lt2020"]]
            count := 0)

          (given-type-query node "Patient" [["gender" "male" "female"]
                                            ["birthdate" "lt2020-02"]]
            count := 1
            [0 :id] := "id-2")

          (given-type-query node "Patient" [["gender" "male" "female"]
                                            ["birthdate" "lt2021"]]
            count := 3
            [0 :id] := "id-0"
            [1 :id] := "id-1"
            [2 :id] := "id-2")))

      (testing "deceased"
        (given-type-query node "Patient" [["deceased" "true"]]
          count := 2
          [0 :id] := "id-0"
          [1 :id] := "id-2"))

      (testing "email"
        (given-type-query node "Patient" [["email" "foo@bar.baz"]]
          count := 1
          [0 :id] := "id-1"))

      (testing "family lower-case"
        (given-type-query node "Patient" [["family" "schmidt"]]
          count := 1
          [0 :id] := "id-2"))

      (testing "gender"
        (given-type-query node "Patient" [["gender" "male"]]
          count := 1
          [0 :id] := "id-0"))

      (testing "identifier"
        (given-type-query node "Patient" [["identifier" "0"]]
          count := 1
          [0 :id] := "id-0"))

      (testing "telecom"
        (given-type-query node "Patient" [["telecom" "0815"]]
          count := 1
          [0 :id] := "id-1"))))

  (testing "Practitioner"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Practitioner
               :id "id-0"
               :name
               [#fhir/HumanName
                 {:family "Müller"
                  :given ["Hans" "Martin"]}]}]]]

      (testing "name"
        (testing "using family"
          (given-type-query node "Practitioner" [["name" "müller"]]
            count := 1
            [0 :id] := "id-0"))

        (testing "using first given"
          (given-type-query node "Practitioner" [["name" "hans"]]
            count := 1
            [0 :id] := "id-0"))

        (testing "using second given"
          (given-type-query node "Practitioner" [["name" "martin"]]
            count := 1
            [0 :id] := "id-0")))))

  (testing "Specimen"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Specimen
               :id "id-0"
               :type
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri"https://fhir.bbmri.de/CodeSystem/SampleMaterialType"
                    :code #fhir/code"dna"}]}
               :collection
               {:fhir/type :fhir.Specimen/collection
                :bodySite
                #fhir/CodeableConcept
                 {:coding
                  [#fhir/Coding
                    {:system #fhir/uri"urn:oid:2.16.840.1.113883.6.43.1"
                     :code #fhir/code"C77.4"}]}}}]]]

      (testing "bodysite"
        (testing "using system|code"
          (given-type-query node "Specimen" [["bodysite" "urn:oid:2.16.840.1.113883.6.43.1|C77.4"]]
            count := 1
            [0 :id] := "id-0"))

        (testing "using code"
          (given-type-query node "Specimen" [["bodysite" "C77.4"]]
            count := 1
            [0 :id] := "id-0"))

        (testing "using system|"
          (given-type-query node "Specimen" [["bodysite" "urn:oid:2.16.840.1.113883.6.43.1|"]]
            count := 1
            [0 :id] := "id-0")))

      (testing "type"
        (given-type-query node "Specimen" [["type" "https://fhir.bbmri.de/CodeSystem/SampleMaterialType|dna"]]
          count := 1
          [0 :id] := "id-0"))

      (testing "bodysite and type"
        (testing "using system|code"
          (given-type-query node "Specimen" [["bodysite" "urn:oid:2.16.840.1.113883.6.43.1|C77.4"]
                                             ["type" "https://fhir.bbmri.de/CodeSystem/SampleMaterialType|dna"]]
            count := 1
            [0 :id] := "id-0"))

        (testing "using code"
          (given-type-query node "Specimen" [["bodysite" "urn:oid:2.16.840.1.113883.6.43.1|C77.4"]
                                             ["type" "dna"]]
            count := 1
            [0 :id] := "id-0"))

        (testing "using system|"
          (given-type-query node "Specimen" [["bodysite" "urn:oid:2.16.840.1.113883.6.43.1|C77.4"]
                                             ["type" "https://fhir.bbmri.de/CodeSystem/SampleMaterialType|"]]
            count := 1
            [0 :id] := "id-0"))

        (testing "does not match"
          (testing "using system|code"
            (given-type-query node "Specimen" [["bodysite" "urn:oid:2.16.840.1.113883.6.43.1|C77.4"]
                                               ["type" "https://fhir.bbmri.de/CodeSystem/SampleMaterialType|urine"]]
              count := 0))))))

  (testing "ActivityDefinition"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/ActivityDefinition
               :id "id-0"
               :url #fhir/uri"url-111619"
               :description #fhir/markdown"desc-121208"}]
        [:put {:fhir/type :fhir/ActivityDefinition
               :id "id-1"
               :url #fhir/uri"url-111721"}]]]

      (testing "url"
        (given-type-query node "ActivityDefinition" [["url" "url-111619"]]
          count := 1
          [0 :id] := "id-0"))

      (testing "description"
        (given-type-query node "ActivityDefinition" [["description" "desc-121208"]]
          count := 1
          [0 :id] := "id-0"))))

  (testing "CodeSystem"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/CodeSystem
               :id "id-0"
               :version "version-122443"}]
        [:put {:fhir/type :fhir/CodeSystem
               :id "id-1"
               :version "version-122456"}]]]

      (testing "version"
        (given-type-query node "CodeSystem" [["version" "version-122443"]]
          count := 1
          [0 :id] := "id-0"))))

  (testing "MedicationKnowledge"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/MedicationKnowledge :id "0"
               :monitoringProgram
               [{:fhir/type :fhir.MedicationKnowledge/monitoringProgram
                 :name "name-123124"}]}]
        [:put {:fhir/type :fhir/MedicationKnowledge :id "1"}]]]

      (testing "monitoring-program-name"
        (given-type-query node "MedicationKnowledge" [["monitoring-program-name" "name-123124"]]
          count := 1
          [0 :id] := "0"))))

  (testing "DocumentReference"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/DocumentReference :id "0"
               :content
               [{:fhir/type :fhir.DocumentReference/content
                 :attachment #fhir/Attachment{:url #fhir/url"url-164344"}}]}]
        [:put {:fhir/type :fhir/DocumentReference :id "1"}]]]

      (testing "location"
        (given-type-query node "DocumentReference" [["location" "url-164344"]]
          count := 1
          [0 :id] := "0"))))

  (testing "Medication"
    (testing "with one ingredient reference"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Medication :id "0"
                 :ingredient
                 [{:fhir/type :fhir.Medication/ingredient
                   :item #fhir/Reference{:reference "Substance/0"}}]}]
          [:put {:fhir/type :fhir/Substance :id "0"}]
          [:put {:fhir/type :fhir/Medication :id "1"}]]]

        (testing "ingredient"
          (given-type-query node "Medication" [["ingredient" "Substance/0"]]
            count := 1
            [0 :id] := "0"))))

    (testing "with two ingredient references"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Medication :id "0"
                 :ingredient
                 [{:fhir/type :fhir.Medication/ingredient
                   :item #fhir/Reference{:reference "Substance/0"}}
                  {:fhir/type :fhir.Medication/ingredient
                   :item #fhir/Reference{:reference "Substance/1"}}]}]
          [:put {:fhir/type :fhir/Substance :id "0"}]
          [:put {:fhir/type :fhir/Substance :id "1"}]
          [:put {:fhir/type :fhir/Medication :id "1"}]]]

        (testing "ingredient"
          (given-type-query node "Medication" [["ingredient" "Substance/0"]]
            count := 1
            [0 :id] := "0")))))

  (testing "Observation"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Observation :id "id-0"
               :status #fhir/code"final"
               :value
               #fhir/Quantity
                {:value 23.42M
                 :unit #fhir/string"kg/m²"
                 :code #fhir/code"kg/m2"
                 :system #fhir/uri"http://unitsofmeasure.org"}}]
        [:put {:fhir/type :fhir/Observation :id "id-1"
               :status #fhir/code"final"
               :value
               #fhir/Quantity
                {:value 23.42M
                 :unit #fhir/string"kg/m²"
                 :code #fhir/code"kg/m2"
                 :system #fhir/uri"http://unitsofmeasure.org"}}]]]

      (testing "full result"
        (let [clauses [["value-quantity" "23.42"]]]
          (given (pull-type-query node "Observation" clauses)
            count := 2
            [0 :id] := "id-0"
            [1 :id] := "id-1")))

      (testing "it is possible to start with the second observation"
        (let [clauses [["value-quantity" "23.42"]]]
          (given (pull-type-query node "Observation" clauses "id-1")
            count := 1
            [0 :id] := "id-1")))))

  (testing "quantity search doesn't overshoot into other types"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Observation :id "id-0"
               :value
               #fhir/Quantity
                {:value 0M
                 :unit #fhir/string"m"
                 :code #fhir/code"m"
                 :system #fhir/uri"http://unitsofmeasure.org"}}]
        [:put {:fhir/type :fhir/TestScript
               :id "id-0"
               :useContext
               [{:fhir/type :fhir/UsageContext
                 :value
                 #fhir/Quantity
                  {:value 0M
                   :unit #fhir/string"m"
                   :code #fhir/code"m"
                   :system #fhir/uri"http://unitsofmeasure.org"}}]}]]]

      (testing "ResourceSearchParamValue index looks like it should"
        (is (= (r-sp-v-tu/decode-index-entries
                (:kv-store node)
                :type :id :hash-prefix :code :v-hash)
               [["Observation" "id-0" #blaze/hash-prefix"36A9F36D"
                 "value-quantity" #blaze/byte-string"0000000080"]
                ["Observation" "id-0" #blaze/hash-prefix"36A9F36D"
                 "value-quantity" #blaze/byte-string"5C38E45A80"]
                ["Observation" "id-0" #blaze/hash-prefix"36A9F36D"
                 "value-quantity" #blaze/byte-string"9B780D9180"]
                ["Observation" "id-0" #blaze/hash-prefix"36A9F36D"
                 "combo-value-quantity" #blaze/byte-string"0000000080"]
                ["Observation" "id-0" #blaze/hash-prefix"36A9F36D"
                 "combo-value-quantity" #blaze/byte-string"5C38E45A80"]
                ["Observation" "id-0" #blaze/hash-prefix"36A9F36D"
                 "combo-value-quantity" #blaze/byte-string"9B780D9180"]
                ["Observation" "id-0" #blaze/hash-prefix"36A9F36D"
                 "_lastUpdated" #blaze/byte-string"80008001"]
                ["TestScript" "id-0" #blaze/hash-prefix"51E67D28"
                 "context-quantity" #blaze/byte-string"0000000080"]
                ["TestScript" "id-0" #blaze/hash-prefix"51E67D28"
                 "context-quantity" #blaze/byte-string"5C38E45A80"]
                ["TestScript" "id-0" #blaze/hash-prefix"51E67D28"
                 "context-quantity" #blaze/byte-string"9B780D9180"]
                ["TestScript" "id-0" #blaze/hash-prefix"51E67D28"
                 "_lastUpdated" #blaze/byte-string"80008001"]])))

      (testing "TestScript would be found"
        (let [clauses [["context-quantity" "0"]]]
          (given (pull-type-query node "TestScript" clauses)
            count := 1
            [0 :id] := "id-0")))

      (testing "greater equal"
        (let [clauses [["value-quantity" "ge0"]]]
          (given (pull-type-query node "Observation" clauses)
            count := 1
            [0 :id] := "id-0")))))

  (testing "Date order"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient
               :id "id-0"
               :birthDate #fhir/date"1900"}]
        [:put {:fhir/type :fhir/Patient
               :id "id-1"
               :birthDate #fhir/date"1960"}]
        [:put {:fhir/type :fhir/Patient
               :id "id-2"
               :birthDate #fhir/date"1970"}]
        [:put {:fhir/type :fhir/Patient
               :id "id-3"
               :birthDate #fhir/date"1980"}]
        [:put {:fhir/type :fhir/Patient
               :id "id-4"
               :birthDate #fhir/date"2020"}]
        [:put {:fhir/type :fhir/Patient
               :id "id-5"
               :birthDate #fhir/date"2100"}]]]

      (given-type-query node "Patient" [["birthdate" "ge1900"]]
        count := 6
        [0 :id] := "id-0"
        [1 :id] := "id-1"
        [2 :id] := "id-2"
        [3 :id] := "id-3"
        [4 :id] := "id-4"
        [5 :id] := "id-5")))

  (testing "type number"
    (testing "Decimal"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/RiskAssessment
                 :id "id-0"
                 :method
                 #fhir/CodeableConcept
                  {:coding
                   [#fhir/Coding
                     {:system #fhir/uri"system-164844"
                      :code #fhir/code"code-164847"}]}
                 :prediction
                 [{:fhir/type :fhir.RiskAssessment/prediction
                   :probability 0.9M}]}]
          [:put {:fhir/type :fhir/RiskAssessment
                 :id "id-1"
                 :status #fhir/code"final"
                 :prediction
                 [{:fhir/type :fhir.RiskAssessment/prediction
                   :probability 0.1M}]}]
          [:put {:fhir/type :fhir/RiskAssessment
                 :id "id-2"
                 :method
                 #fhir/CodeableConcept
                  {:coding
                   [#fhir/Coding
                     {:system #fhir/uri"system-164844"
                      :code #fhir/code"code-164847"}]}
                 :prediction
                 [{:fhir/type :fhir.RiskAssessment/prediction
                   :probability 0.5M}]}]]]

        (given-type-query node "RiskAssessment" [["probability" "ge0.5"]]
          count := 2
          [0 :id] := "id-2"
          [1 :id] := "id-0")

        (testing "it is possible to start with the second risk assessment"
          (given (pull-type-query node "RiskAssessment" [["probability" "ge0.5"]] "id-0")
            count := 1
            [0 :id] := "id-0"))

        (testing "as second clause"
          (given-type-query node "RiskAssessment" [["method" "code-164847"]
                                                   ["probability" "ge0.5"]]
            count := 2
            [0 :id] := "id-0"
            [1 :id] := "id-2")

          (testing "it is possible to start with the second risk assessment"
            (given (pull-type-query node "RiskAssessment" [["method" "code-164847"]
                                                           ["probability" "ge0.5"]]
                                    "id-2")
              count := 1
              [0 :id] := "id-2")))

        (testing "with matcher"
          (let [matcher (d/compile-type-matcher node "RiskAssessment" [["probability" "ge0.5"]])
                db (d/db node)
                xform (d/matcher-transducer db matcher)]
            (given (into [] xform (d/type-list db "RiskAssessment"))
              count := 2
              [0 :id] := "id-0"
              [1 :id] := "id-2")))))

    (testing "Integer"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/MolecularSequence
                 :id "id-0"
                 :variant
                 [{:fhir/type :fhir.MolecularSequence/variant
                   :start #fhir/integer 1}]}]
          [:put {:fhir/type :fhir/MolecularSequence
                 :id "id-1"
                 :variant
                 [{:fhir/type :fhir.MolecularSequence/variant
                   :start #fhir/integer 2}]}]]]

        (given-type-query node "MolecularSequence" [["variant-start" "1"]]
          count := 1
          [0 :id] := "id-0")

        (given-type-query node "MolecularSequence" [["variant-start" "2"]]
          count := 1
          [0 :id] := "id-1")))))

(deftest type-query-id-test
  (testing "search by _id"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0" :active true}]
        [:put {:fhir/type :fhir/Patient :id "1" :active false}]
        [:put {:fhir/type :fhir/Patient :id "2"}]]
       [[:delete "Patient" "2"]]]

      (doseq [id ["0" "1"]]
        (given-type-query node "Patient" [["_id" id]]
          count := 1
          [0 :fhir/type] := :fhir/Patient
          [0 :id] := id)

        (testing "it is possible to start with that id"
          (given (pull-type-query node "Patient" [["_id" id]] id)
            count := 1
            [0 :fhir/type] := :fhir/Patient
            [0 :id] := id))

        (testing "will find nothing if started with another id"
          (is (coll/empty? (d/type-query (d/db node) "Patient" [["_id" id]] "2")))))

      (testing "doesn't find the deleted resource"
        (is (coll/empty? (d/type-query (d/db node) "Patient" [["_id" "2"]])))
        (is (coll/empty? (d/type-query (d/db node) "Patient" [["_id" "2"]] "2")))
        (is (zero? (count-type-query node "Patient" [["_id" "2"]]))))

      (testing "finds nothing with id not in database"
        (is (coll/empty? (d/type-query (d/db node) "Patient" [["_id" "3"]])))
        (is (coll/empty? (d/type-query (d/db node) "Patient" [["_id" "3"]] "3")))
        (is (zero? (count-type-query node "Patient" [["_id" "3"]]))))

      (testing "finds more than one patient"
        (given-type-query node "Patient" [["_id" "0" "1"]]
          count := 2
          [0 :fhir/type] := :fhir/Patient
          [0 :id] := "0"
          [1 :fhir/type] := :fhir/Patient
          [1 :id] := "1"))

      (testing "as second clause"
        (let [clauses [["active" "true"] ["_id" "0"]]]
          (given-type-query node "Patient" clauses
            count := 1
            [0 :fhir/type] := :fhir/Patient
            [0 :id] := "0")

          (given (explain-type-query node "Patient" clauses)
            :scan-type := :ordered
            [:scan-clauses count] := 1
            [:scan-clauses 0 :code] := "_id"
            [:seek-clauses count] := 1
            [:seek-clauses 0 :code] := "active"))

        (is (zero? (count-type-query node "Patient" [["active" "true"] ["_id" "1"]])))))))

(deftest type-query-sort-test
  (testing "sorting by _id"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "1"}]]]

      (testing "ascending"
        (let [clauses [[:sort "_id" :asc]]]
          (given-type-query node "Patient" clauses
            count := 2
            [0 :fhir/type] := :fhir/Patient
            [0 :id] := "0"
            [1 :fhir/type] := :fhir/Patient
            [1 :id] := "1")

          (given (explain-type-query node "Patient" clauses)
            :scan-type := :ordered
            [:scan-clauses count] := 1
            [:scan-clauses 0 :code] := "_id"
            [:seek-clauses count] := 0)

          (testing "it is possible to start with the second patient"
            (given (pull-type-query node "Patient" clauses "1")
              count := 1
              [0 :fhir/type] := :fhir/Patient
              [0 :id] := "1"))))

      (testing "descending"
        (given (d/type-query (d/db node) "Patient" [[:sort "_id" :desc]])
          ::anom/category := ::anom/unsupported
          ::anom/message := "Unsupported sort direction `desc` for search param `_id`."))))

  (testing "a node with two patients in two transactions"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:put {:fhir/type :fhir/Patient :id "1"}]]]

      (testing "the oldest patient comes first"
        (let [clauses [[:sort "_lastUpdated" :asc]]]
          (given-type-query node "Patient" clauses
            count := 2
            [0 :fhir/type] := :fhir/Patient
            [0 :id] := "0"
            [1 :id] := "1")

          (given (explain-type-query node "Patient" clauses)
            :scan-type := :ordered
            [:scan-clauses count] := 1
            [:scan-clauses 0 :code] := "_lastUpdated"
            [:seek-clauses count] := 0)))

      (testing "the newest patient comes first"
        (let [clauses [[:sort "_lastUpdated" :desc]]]
          (given-type-query node "Patient" clauses
            count := 2
            [0 :id] := "1"
            [1 :id] := "0")

          (given (explain-type-query node "Patient" clauses)
            :scan-type := :ordered
            [:scan-clauses count] := 1
            [:scan-clauses 0 :code] := "_lastUpdated"
            [:seek-clauses count] := 0)))))

  (testing "a node with three patients in three transactions"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:put {:fhir/type :fhir/Patient :id "1"}]]
       [[:put {:fhir/type :fhir/Patient :id "2"}]]]

      (testing "the oldest patient comes first"
        (let [clauses [[:sort "_lastUpdated" :asc]]]
          (given-type-query node "Patient" clauses
            count := 3
            [0 :id] := "0"
            [1 :id] := "1"
            [2 :id] := "2")

          (testing "it is possible to start with the second patient"
            (given (pull-type-query node "Patient" clauses "1")
              count := 2
              [0 :id] := "1"
              [1 :id] := "2"))))

      (testing "the newest patient comes first"
        (let [clauses [[:sort "_lastUpdated" :desc]]]
          (given-type-query node "Patient" clauses
            count := 3
            [0 :id] := "2"
            [1 :id] := "1"
            [2 :id] := "0")

          (testing "it is possible to start with the second patient"
            (given (pull-type-query node "Patient" clauses "1")
              count := 2
              [0 :id] := "1"
              [1 :id] := "0"))))))

  (testing "sorting works together with token search"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0" :active true}]
        [:put {:fhir/type :fhir/Patient :id "1" :active false}]
        [:put {:fhir/type :fhir/Patient :id "2" :active true}]
        [:put {:fhir/type :fhir/Patient :id "3" :active true}]]
       [[:delete "Patient" "3"]]]

      (let [clauses [[:sort "_lastUpdated" :asc] ["active" "true"]]]
        (given-type-query node "Patient" clauses
          count := 2
          [0 :id] := "0"
          [1 :id] := "2")

        (testing "it is possible to start with the second patient"
          (given (pull-type-query node "Patient" clauses "2")
            count := 1
            [0 :id] := "2"))))))

(deftest ^:slow type-query-sort-property-test
  (log/set-min-level! :warn)

  (testing "sorting by _id"
    (testing "random id's"
      (satisfies-prop 100
        (prop/for-all [ids (gen/set (s/gen :blaze.resource/id) {:min-elements 1 :max-elements 100})]
          (with-system-data [{:blaze.db/keys [node]} config]
            [(mapv #(vector :create {:fhir/type :fhir/Patient :id %}) ids)]

            (= (sort ids) (mapv :id (pull-type-query node "Patient" [[:sort "_id" :asc]])))))))))

(deftest type-query-version-test
  (testing "only the latest version matches"
    (testing "token search param"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0" :active true}]]
         [[:put {:fhir/type :fhir/Patient :id "0" :active false}]]
         [[:put {:fhir/type :fhir/Patient :id "0" :active true
                 :birthDate #fhir/date"2020"}]]]

        (given-type-query node "Patient" [["active" "true"]]
          count := 1
          [0 :fhir/type] := :fhir/Patient
          [0 :id] := "0"
          [0 :birthDate] := #fhir/date"2020")))

    (testing "date search param"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0" :active true
                 :birthDate #fhir/date"2025"}]]
         [[:put {:fhir/type :fhir/Patient :id "0" :active true
                 :birthDate #fhir/date"2024"}]]
         [[:put {:fhir/type :fhir/Patient :id "0" :active true
                 :birthDate #fhir/date"2025"
                 :gender #fhir/code"female"}]]]

        (doseq [clauses [[["birthdate" "2025"]]
                         [["active" "true"]
                          ["birthdate" "2025"]]]]
          (given (pull-type-query node "Patient" clauses)
            count := 1
            [0 :fhir/type] := :fhir/Patient
            [0 :id] := "0"
            [0 :gender] := #fhir/code"female")))))

  (testing "the latest version doesn't match"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0" :active true}]]
       [[:put {:fhir/type :fhir/Patient :id "0" :active false}]]
       [[:put {:fhir/type :fhir/Patient :id "0" :active true
               :birthDate #fhir/date"2020"}]]
       [[:put {:fhir/type :fhir/Patient :id "0" :active false}]]]

      (given-type-query node "Patient" [["active" "true"]]
        count := 0))))

(deftest type-query-search-param-list-test
  (testing "Special Search Parameter _list"
    (testing "a node with two patients, one observation and one list in one transaction"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Patient :id "1"}]
          [:put {:fhir/type :fhir/Observation :id "0"}]
          [:put {:fhir/type :fhir/List :id "0"
                 :entry
                 [{:fhir/type :fhir.List/entry
                   :item #fhir/Reference {:reference "Patient/0"}}
                  {:fhir/type :fhir.List/entry
                   :item #fhir/Reference {:reference "Observation/0"}}]}]]]

        (testing "returns only the patient referenced in the list"
          (given-type-query node "Patient" [["_list" "0"]]
            count := 1
            [0 :fhir/type] := :fhir/Patient
            [0 :id] := "0"))

        (testing "returns only the observation referenced in the list"
          (given-type-query node "Observation" [["_list" "0"]]
            count := 1
            [0 :fhir/type] := :fhir/Observation
            [0 :id] := "0"))))

    (testing "a node with four patients and one list in one transaction"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Patient :id "1"}]
          [:put {:fhir/type :fhir/Patient :id "2"}]
          [:put {:fhir/type :fhir/Patient :id "3"}]
          [:put {:fhir/type :fhir/List :id "0"
                 :entry
                 [{:fhir/type :fhir.List/entry
                   :item #fhir/Reference {:reference "Patient/0"}}
                  {:fhir/type :fhir.List/entry
                   :item #fhir/Reference {:reference "Patient/2"}}
                  {:fhir/type :fhir.List/entry
                   :item #fhir/Reference {:reference "Patient/3"}}]}]]]

        (testing "it is possible to start with the second patient"
          (given (pull-type-query node "Patient" [["_list" "0"]] "2")
            count := 2
            [0 :id] := "2"
            [1 :id] := "3"))))

    (testing "doesn't return the deleted patient"
      (with-system-data [{:blaze.db/keys [node]} (assoc-in config [:blaze.db/node :enforce-referential-integrity] false)]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Patient :id "1"}]
          [:put {:fhir/type :fhir/Patient :id "2"}]
          [:put {:fhir/type :fhir/Patient :id "3"}]
          [:put {:fhir/type :fhir/List :id "0"
                 :entry
                 [{:fhir/type :fhir.List/entry
                   :item #fhir/Reference {:reference "Patient/0"}}
                  {:fhir/type :fhir.List/entry
                   :item #fhir/Reference {:reference "Patient/2"}}
                  {:fhir/type :fhir.List/entry
                   :item #fhir/Reference {:reference "Patient/3"}}]}]]
         [[:delete "Patient" "2"]]]

        (given-type-query node "Patient" [["_list" "0"]]
          count := 2
          [0 :id] := "0"
          [1 :id] := "3")))

    (testing "a deleted list"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/List :id "0"
                 :entry
                 [{:fhir/type :fhir.List/entry
                   :item #fhir/Reference {:reference "Patient/0"}}]}]]
         [[:delete "List" "0"]]]

        (testing "doesn't reference anything"
          (given-type-query node "Patient" [["_list" "0"]]
            count := 0)

          (testing "it is possible to start with some patient"
            (given (pull-type-query node "Patient" [["_list" "0"]] "0")
              count := 0)))))))

(deftest type-query-has-test
  (testing "Special Search Parameter _has"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :active true}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :active false}]
        [:put {:fhir/type :fhir/Patient :id "2"
               :active false}]
        [:put {:fhir/type :fhir/Patient :id "3"
               :active false}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri"http://loinc.org"
                    :code #fhir/code"8480-6"}]}
               :value
               #fhir/Quantity
                {:value 130M
                 :code #fhir/code"mm[Hg]"
                 :system #fhir/uri"http://unitsofmeasure.org"}}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :subject #fhir/Reference{:reference "Patient/0"}
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri"http://loinc.org"
                    :code #fhir/code"8480-6"}]}
               :value
               #fhir/Quantity
                {:value 150M
                 :code #fhir/code"mm[Hg]"
                 :system #fhir/uri"http://unitsofmeasure.org"}}]
        [:put {:fhir/type :fhir/Observation :id "2"
               :subject #fhir/Reference{:reference "Patient/1"}
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri"http://loinc.org"
                    :code #fhir/code"8480-6"}]}
               :value
               #fhir/Quantity
                {:value 100M
                 :code #fhir/code"mm[Hg]"
                 :system #fhir/uri"http://unitsofmeasure.org"}}]
        [:put {:fhir/type :fhir/Observation :id "3"
               :subject #fhir/Reference{:reference "Patient/3"}
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri"http://loinc.org"
                    :code #fhir/code"8480-6"}]}
               :value
               #fhir/Quantity
                {:value 10M
                 :code #fhir/code"mm[Hg]"
                 :system #fhir/uri"http://unitsofmeasure.org"}}]]]

      (testing "select the Patient with >= 130 mm[Hg]"
        (given-type-query node "Patient" [["_has:Observation:patient:code-value-quantity" "8480-6$ge130"]]
          count := 1
          [0 :id] := "0"))

      (testing "select the Patient with 100 mm[Hg]"
        (let [clauses [["_has:Observation:patient:code-value-quantity" "8480-6$100"]]]
          (given (pull-type-query node "Patient" clauses)
            count := 1
            [0 :id] := "1")))

      (testing "select all Patients"
        (let [clauses [["_has:Observation:patient:code-value-quantity" "8480-6$ge100"]]]
          (given (pull-type-query node "Patient" clauses)
            count := 2
            [0 :id] := "0"
            [1 :id] := "1"))

        (testing "it is possible to start with the second patient"
          (let [clauses [["_has:Observation:patient:code-value-quantity" "8480-6$ge100"]]]
            (given (pull-type-query node "Patient" clauses "1")
              count := 1
              [0 :id] := "1"))))

      (testing "as second clause"
        (testing "select the non-active Patient with = 10 mm[Hg]"
          (let [clauses [["active" "false"]
                         ["_has:Observation:patient:code-value-quantity" "8480-6$10"]]]
            (given (pull-type-query node "Patient" clauses)
              count := 1
              [0 :id] := "3")))))

    (testing "errors"
      (testing "missing modifier"
        (with-system [{:blaze.db/keys [node]} config]
          (given (d/type-query (d/db node) "Patient" [["_has" ""]])
            ::anom/category := ::anom/incorrect
            ::anom/message := "Missing modifier of _has search param.")))

      (testing "missing type"
        (with-system [{:blaze.db/keys [node]} config]
          (given (d/type-query (d/db node) "Patient" [["_has:" ""]])
            ::anom/category := ::anom/incorrect
            ::anom/message := "Missing type in _has search param `_has:`.")))

      (testing "missing chaining search param"
        (with-system [{:blaze.db/keys [node]} config]
          (given (d/type-query (d/db node) "Patient" [["_has:foo" ""]])
            ::anom/category := ::anom/incorrect
            ::anom/message := "Missing chaining search param in _has search param `_has:foo`.")))

      (testing "missing search param"
        (with-system [{:blaze.db/keys [node]} config]
          (given (d/type-query (d/db node) "Patient" [["_has:foo:bar" ""]])
            ::anom/category := ::anom/incorrect
            ::anom/message := "Missing search param in _has search param `_has:foo:bar`.")))

      (testing "main search param not found"
        (with-system [{:blaze.db/keys [node]} config]
          (given (d/type-query (d/db node) "Patient" [["_has:Observation:patient:foo" ""]])
            ::anom/category := ::anom/not-found
            ::anom/message := "The search-param with code `foo` and type `Observation` was not found.")))

      (testing "chain search param not found"
        (with-system [{:blaze.db/keys [node]} config]
          (given (d/type-query (d/db node) "Patient" [["_has:Observation:foo:code" ""]])
            ::anom/category := ::anom/not-found
            ::anom/message := "The search-param with code `foo` and type `Observation` was not found."))))))

(deftest type-query-condition-test
  (with-system-data [{:blaze.db/keys [node]} config]
    [[[:put {:fhir/type :fhir/Patient
             :id "id-0"}]
      [:put {:fhir/type :fhir/Condition :id "id-0"
             :code
             #fhir/CodeableConcept
              {:coding
               [#fhir/Coding
                 {:system #fhir/uri"http://fhir.de/CodeSystem/dimdi/icd-10-gm"
                  :code #fhir/code"C71.4"}]}
             :subject #fhir/Reference{:reference "Patient/id-0"}
             :onset
             {:fhir/type :fhir/Age
              :value 63M}}]
      [:put {:fhir/type :fhir/Condition :id "id-1"}]]]

    (testing "patient"
      (given-type-query node "Condition" [["patient" "id-0"]]
        count := 1
        [0 :id] := "id-0"))

    (testing "code"
      (testing "duplicate values have no effect (#293)"
        (given-type-query node "Condition" [["code" "C71.4" "C71.4"]]
          count := 1
          [0 :id] := "id-0")))

    (testing "onset-age"
      (given-type-query node "Condition" [["onset-age" "63"]]
        count := 1
        [0 :id] := "id-0")))

  (with-system-data [{:blaze.db/keys [node]} config]
    [[[:put {:fhir/type :fhir/Condition :id "0"
             :code
             #fhir/CodeableConcept
              {:coding
               [#fhir/Coding
                 {:code #fhir/code"0"}]}}]
      [:put {:fhir/type :fhir/Condition :id "3"
             :code
             #fhir/CodeableConcept
              {:coding
               [#fhir/Coding
                 {:code #fhir/code"0"}]}}]
      [:put {:fhir/type :fhir/Condition :id "4"
             :code
             #fhir/CodeableConcept
              {:coding
               [#fhir/Coding
                 {:code #fhir/code"0"}]}}]
      [:put {:fhir/type :fhir/Condition :id "1"
             :code
             #fhir/CodeableConcept
              {:coding
               [#fhir/Coding
                 {:code #fhir/code"1"}]}}]
      [:put {:fhir/type :fhir/Condition :id "2"
             :code
             #fhir/CodeableConcept
              {:coding
               [#fhir/Coding
                 {:code #fhir/code"1"}]}}]]]

    (testing "code"
      (given-type-query node "Condition" [["code" "0" "1"]]
        count := 5
        [0 :id] := "0"
        [1 :id] := "1"
        [2 :id] := "2"
        [3 :id] := "3"
        [4 :id] := "4")

      (testing "it is possible to start with the second condition"
        (given (pull-type-query node "Condition" [["code" "0" "1"]] "1")
          count := 4
          [0 :id] := "1"
          [1 :id] := "2"
          [2 :id] := "3"
          [3 :id] := "4"))

      (testing "it is possible to start with the third condition"
        (given (pull-type-query node "Condition" [["code" "0" "1"]] "2")
          count := 3
          [0 :id] := "2"
          [1 :id] := "3"
          [2 :id] := "4"))))

  (with-system-data [{:blaze.db/keys [node]} config]
    [[[:put {:fhir/type :fhir/Condition :id "0"
             :code
             #fhir/CodeableConcept
              {:coding
               [#fhir/Coding{:code #fhir/code"foo"}
                #fhir/Coding{:code #fhir/code"bar"}]}
             :subject #fhir/Reference{:reference "Patient/0"}}]
      [:put {:fhir/type :fhir/Condition :id "1"
             :subject #fhir/Reference{:reference "Patient/1"}}]
      [:put {:fhir/type :fhir/Patient :id "0"
             :gender #fhir/code"female"}]
      [:put {:fhir/type :fhir/Patient :id "1"
             :gender #fhir/code"male"}]]]

    (testing "duplicates are removed"
      (given-type-query node "Condition" [["code" "foo" "bar"]]
        count := 1
        [0 :id] := "0"))

    (testing "forward chaining to Patient"
      (given-type-query node "Condition" [["patient.gender" "male"]]
        count := 1
        [0 :fhir/type] := :fhir/Condition
        [0 :id] := "1"))))

(deftest type-query-measure-report-test
  (with-system-data [{:blaze.db/keys [node]} config]
    [[[:put {:fhir/type :fhir/MeasureReport
             :id "id-144132"
             :measure #fhir/canonical"http://example.com/fhir/Measure/181106"}]]]

    (testing "measure"
      (let [clauses [["measure" "http://example.com/fhir/Measure/181106"]]]
        (given (pull-type-query node "MeasureReport" clauses)
          count := 1
          [0 :id] := "id-144132")))))

(deftest type-query-observation-test
  (with-system-data [{:blaze.db/keys [node]} config]
    [[[:put {:fhir/type :fhir/Observation :id "id-0"
             :meta #fhir/Meta{:profile [#fhir/canonical"http://example.com/profile-uri-091902"]}
             :status #fhir/code"final"
             :effective
             #fhir/Period
              {:start #fhir/dateTime"2021-02-23T15:12:45+01:00"
               :end #fhir/dateTime"2021-02-23T16:00:00+01:00"}
             :value
             #fhir/Quantity
              {:value 0M
               :unit #fhir/string"kg/m²"
               :code #fhir/code"kg/m2"
               :system #fhir/uri"http://unitsofmeasure.org"}}]
      [:put {:fhir/type :fhir/Observation :id "id-1"
             :meta #fhir/Meta{:profile [#fhir/canonical"http://example.com/profile-uri-091902|1.1.0"]}
             :status #fhir/code"final"
             :effective #fhir/dateTime"2021-02-25"
             :value
             #fhir/Quantity
              {:value 1M
               :unit #fhir/string"kg/m²"
               :code #fhir/code"kg/m2"
               :system #fhir/uri"http://unitsofmeasure.org"}}]
      [:put {:fhir/type :fhir/Observation :id "id-2"
             :meta #fhir/Meta{:profile [#fhir/canonical"http://example.com/profile-uri-091902|2.4.7"]}
             :status #fhir/code"final"
             :value
             #fhir/Quantity
              {:value 2.11M
               :unit #fhir/string"kg/m²"
               :code #fhir/code"kg/m2"
               :system #fhir/uri"http://unitsofmeasure.org"}}]
      [:put {:fhir/type :fhir/Observation :id "id-3"
             :meta #fhir/Meta{:profile [#fhir/canonical"http://example.com/profile-uri-091902|2.3.9"]}
             :status #fhir/code"final"
             :value
             #fhir/Quantity
              {:value 3M
               :unit #fhir/string"kg/m²"
               :code #fhir/code"kg/m2"
               :system #fhir/uri"http://unitsofmeasure.org"}}]]]

    (testing "_profile"
      (testing "full URL and version matching without modifier"
        (given-type-query node "Observation" [["_profile" "http://example.com/profile-uri-091902"]]
          count := 1
          [0 :id] := "id-0")
        (given-type-query node "Observation" [["_profile" "http://example.com/profile-uri-091902|2.4.7"]]
          count := 1
          [0 :id] := "id-2"))

      (testing "below with URL only"
        (let [clauses [["_profile:below" "http://example.com/profile-uri-091902"]]]
          (given-type-query node "Observation" clauses
            count := 4
            [0 :id] := "id-0"
            [1 :id] := "id-1"
            [2 :id] := "id-2"
            [3 :id] := "id-3")

          (given (explain-type-query node "Observation" clauses)
            :scan-type := :ordered
            [:scan-clauses count] := 1
            [:scan-clauses 0 :code] := "_profile"
            [:scan-clauses 0 :modifier] := "below"
            [:seek-clauses count] := 0)

          (testing "it is possible to start with the second observation"
            (given (pull-type-query node "Observation" clauses "id-1")
              count := 3
              [0 :id] := "id-1"
              [1 :id] := "id-2"
              [2 :id] := "id-3"))

          (testing "it is possible to start with the third observation"
            (given (pull-type-query node "Observation" clauses "id-2")
              count := 2
              [0 :id] := "id-2"
              [1 :id] := "id-3"))))

      (testing "below with URL and major version 1"
        (given-type-query node "Observation" [["_profile:below" "http://example.com/profile-uri-091902|1"]]
          count := 1
          [0 :id] := "id-1"))

      (testing "below with URL and major version 2"
        (given-type-query node "Observation" [["_profile:below" "http://example.com/profile-uri-091902|2"]]
          count := 2
          [0 :id] := "id-2"
          [1 :id] := "id-3")

        (testing "it is possible to start with the second observation"
          (given (pull-type-query node "Observation" [["_profile:below" "http://example.com/profile-uri-091902|2"]] "id-2")
            count := 2
            [0 :id] := "id-2"
            [1 :id] := "id-3")))

      (testing "below with URL and minor version 2.4"
        (given-type-query node "Observation" [["_profile:below" "http://example.com/profile-uri-091902|2.4"]]
          count := 1
          [0 :id] := "id-2")))

    (testing "date"
      (testing "with year precision"
        (given-type-query node "Observation" [["date" "2021"]]
          count := 2
          [0 :id] := "id-0"
          [1 :id] := "id-1"))

      (testing "with day precision"
        (testing "before the period"
          (given-type-query node "Observation" [["date" "2021-02-22"]]
            count := 0))

        (testing "within the period"
          (given-type-query node "Observation" [["date" "2021-02-23"]]
            count := 1
            [0 :id] := "id-0"))

        (testing "after the period"
          (given-type-query node "Observation" [["date" "2021-02-24"]]
            count := 0)))

      (testing "with second precision"
        (testing "before the start of the period"
          (given-type-query node "Observation" [["date" "ap2021-02-23T15:12:44+01:00"]]
            count := 0))

        (testing "at the start of the period"
          (given-type-query node "Observation" [["date" "ap2021-02-23T15:12:45+01:00"]]
            count := 1
            [0 :id] := "id-0"))

        (testing "within the period"
          (doseq [date ["ap2021-02-23T15:12:46+01:00"
                        "ap2021-02-23T15:30:00+01:00"
                        "ap2021-02-23T15:59:59+01:00"]]
            (given-type-query node "Observation" [["date" date]]
              count := 1
              [0 :id] := "id-0")))

        (testing "at the end of the period"
          (given-type-query node "Observation" [["date" "ap2021-02-23T16:00:00+01:00"]]
            count := 1
            [0 :id] := "id-0"))

        (testing "after the end of the period"
          (given-type-query node "Observation" [["date" "ap2021-02-23T16:00:01+01:00"]]
            count := 0))))

    (testing "value-quantity"
      (testing "without unit"
        (given-type-query node "Observation" [["value-quantity" "2.11"]]
          count := 1
          [0 :id] := "id-2"))

      (testing "with minimal unit"
        (let [clauses [["value-quantity" "2.11|kg/m2"]]]
          (given (pull-type-query node "Observation" clauses)
            count := 1
            [0 :id] := "id-2")))

      (testing "with human unit"
        (let [clauses [["value-quantity" "2.11|kg/m²"]]]
          (given (pull-type-query node "Observation" clauses)
            count := 1
            [0 :id] := "id-2")))

      (testing "with full unit"
        (let [clauses [["value-quantity" "2.11|http://unitsofmeasure.org|kg/m2"]]]
          (given (pull-type-query node "Observation" clauses)
            count := 1
            [0 :id] := "id-2")))

      (testing "with lesser precision"
        (let [clauses [["value-quantity" "2.1"]]]
          (given (pull-type-query node "Observation" clauses)
            count := 1
            [0 :id] := "id-2")))

      (testing "with even lesser precision"
        (let [clauses [["value-quantity" "2"]]]
          (given (pull-type-query node "Observation" clauses)
            count := 1
            [0 :id] := "id-2")))

      (testing "with two values"
        (let [clauses [["value-quantity" "0" "3"]]]
          (given (pull-type-query node "Observation" clauses)
            count := 2
            [0 :id] := "id-0"
            [1 :id] := "id-3")))

      (testing "with prefix"
        (testing "not equal"
          (given (d/type-query (d/db node) "Observation" [["value-quantity" "ne2.11"]])
            ::anom/category := ::anom/unsupported
            ::anom/message := "Unsupported prefix `ne` in search parameter `value-quantity`."))

        (testing "greater than"
          (let [clauses [["value-quantity" "gt2.11"]]]
            (given (pull-type-query node "Observation" clauses)
              count := 1
              [0 :id] := "id-3"))

          (testing "with lesser precision"
            (let [clauses [["value-quantity" "gt2.1"]]]
              (given (pull-type-query node "Observation" clauses)
                count := 2
                [0 :id] := "id-2"
                [1 :id] := "id-3"))

            (testing "it is possible to start with the second observation"
              (let [clauses [["value-quantity" "gt2.1"]]]
                (given (pull-type-query node "Observation" clauses "id-3")
                  count := 1
                  [0 :id] := "id-3"))))

          (testing "with even lesser precision"
            (let [clauses [["value-quantity" "gt2"]]]
              (given (pull-type-query node "Observation" clauses)
                count := 2
                [0 :id] := "id-2"
                [1 :id] := "id-3"))))

        (testing "less than"
          (let [clauses [["value-quantity" "lt2.11"]]]
            (given (pull-type-query node "Observation" clauses)
              count := 2
              [0 :id] := "id-1"
              [1 :id] := "id-0"))

          (testing "it is possible to start with the second observation"
            (let [clauses [["value-quantity" "lt2.11"]]]
              (given (pull-type-query node "Observation" clauses "id-0")
                count := 1
                [0 :id] := "id-0")))

          (testing "with lesser precision"
            (let [clauses [["value-quantity" "lt2.1"]]]
              (given (pull-type-query node "Observation" clauses)
                count := 2
                [0 :id] := "id-1"
                [1 :id] := "id-0")))

          (testing "with even lesser precision"
            (let [clauses [["value-quantity" "lt2"]]]
              (given (pull-type-query node "Observation" clauses)
                count := 2
                [0 :id] := "id-1"
                [1 :id] := "id-0"))))

        (testing "greater equal"
          (let [clauses [["value-quantity" "ge2.11"]]]
            (given (pull-type-query node "Observation" clauses)
              count := 2
              [0 :id] := "id-2"
              [1 :id] := "id-3"))

          (testing "it is possible to start with the second observation"
            (let [clauses [["value-quantity" "ge2.11"]]]
              (given (pull-type-query node "Observation" clauses "id-3")
                count := 1
                [0 :id] := "id-3")))

          (testing "with lesser precision"
            (let [clauses [["value-quantity" "ge2.1"]]]
              (given (pull-type-query node "Observation" clauses)
                count := 2
                [0 :id] := "id-2"
                [1 :id] := "id-3")))

          (testing "with even lesser precision"
            (let [clauses [["value-quantity" "ge2"]]]
              (given (pull-type-query node "Observation" clauses)
                count := 2
                [0 :id] := "id-2"
                [1 :id] := "id-3"))))

        (testing "less equal"
          (let [clauses [["value-quantity" "le2.11"]]]
            (given (pull-type-query node "Observation" clauses)
              count := 3
              [0 :id] := "id-2"
              [1 :id] := "id-1"
              [2 :id] := "id-0"))

          (testing "it is possible to start with the second observation"
            (let [clauses [["value-quantity" "le2.11"]]]
              (given (pull-type-query node "Observation" clauses "id-1")
                count := 2
                [0 :id] := "id-1"
                [1 :id] := "id-0")))

          (testing "with lesser precision"
            (let [clauses [["value-quantity" "le2.1"]]]
              (given (pull-type-query node "Observation" clauses)
                count := 2
                [0 :id] := "id-1"
                [1 :id] := "id-0")))

          (testing "with even lesser precision"
            (let [clauses [["value-quantity" "le2"]]]
              (given (pull-type-query node "Observation" clauses)
                count := 2
                [0 :id] := "id-1"
                [1 :id] := "id-0"))))

        (testing "starts after"
          (given (d/type-query (d/db node) "Observation" [["value-quantity" "sa2.11"]])
            ::anom/category := ::anom/unsupported
            ::anom/message := "Unsupported prefix `sa` in search parameter `value-quantity`."))

        (testing "ends before"
          (given (d/type-query (d/db node) "Observation" [["value-quantity" "eb2.11"]])
            ::anom/category := ::anom/unsupported
            ::anom/message := "Unsupported prefix `eb` in search parameter `value-quantity`."))

        (testing "approximately"
          (given (d/type-query (d/db node) "Observation" [["value-quantity" "ap2.11"]])
            ::anom/category := ::anom/unsupported
            ::anom/message := "Unsupported prefix `ap` in search parameter `value-quantity`.")))

      (testing "with more than one value"
        (let [clauses [["value-quantity" "2.11|kg/m2" "1|kg/m2"]]]
          (given (pull-type-query node "Observation" clauses)
            count := 2
            [0 :id] := "id-2"
            [1 :id] := "id-1")))

      (testing "with invalid decimal value"
        (given (d/type-query (d/db node) "Observation" [["value-quantity" "a"]])
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid decimal value `a` in search parameter `value-quantity`.")))

    (testing "status and value-quantity"
      (let [clauses [["status" "final"] ["value-quantity" "2.11|kg/m2"]]]
        (given (pull-type-query node "Observation" clauses)
          count := 1
          [0 :id] := "id-2"))

      (testing "with lesser precision"
        (let [clauses [["status" "final"] ["value-quantity" "2.1|kg/m2"]]]
          (given (pull-type-query node "Observation" clauses)
            count := 1
            [0 :id] := "id-2")))

      (testing "with two values"
        (let [clauses [["status" "final"] ["value-quantity" "0" "3"]]]
          (given (pull-type-query node "Observation" clauses)
            count := 2
            [0 :id] := "id-0"
            [1 :id] := "id-3")))

      (testing "with prefix"
        (testing "not equal"
          (given (let [clauses [["status" "final"] ["value-quantity" "ne2.11|kg/m2"]]]
                   (d/type-query (d/db node) "Observation" clauses))
            ::anom/category := ::anom/unsupported
            ::anom/message := "Unsupported prefix `ne` in search parameter `value-quantity`."))

        (testing "greater than"
          (let [clauses [["status" "final"] ["value-quantity" "gt2.11|kg/m2"]]]
            (given (pull-type-query node "Observation" clauses)
              count := 1
              [0 :id] := "id-3"))

          (testing "with lesser precision"
            (let [clauses [["status" "final"] ["value-quantity" "gt2.1|kg/m2"]]]
              (given (pull-type-query node "Observation" clauses)
                count := 2
                [0 :id] := "id-2"
                [1 :id] := "id-3"))))

        (testing "less than"
          (let [clauses [["status" "final"] ["value-quantity" "lt2.11|kg/m2"]]]
            (given (pull-type-query node "Observation" clauses)
              count := 2
              [0 :id] := "id-0"
              [1 :id] := "id-1")))

        (testing "greater equal"
          (given-type-query node "Observation" [["status" "final"]
                                                ["value-quantity" "ge2.11|kg/m2"]]
            count := 2
            [0 :id] := "id-2"
            [1 :id] := "id-3"))

        (testing "less equal"
          (let [clauses [["status" "final"] ["value-quantity" "le2.11|kg/m2"]]]
            (given (pull-type-query node "Observation" clauses)
              count := 3
              [0 :id] := "id-0"
              [1 :id] := "id-1"
              [2 :id] := "id-2"))

          (testing "with lesser precision"
            (let [clauses [["status" "final"] ["value-quantity" "le2.1|kg/m2"]]]
              (given (pull-type-query node "Observation" clauses)
                count := 2
                [0 :id] := "id-0"
                [1 :id] := "id-1"))))

        (testing "starts after"
          (given (let [clauses [["status" "final"] ["value-quantity" "sa2.11|kg/m2"]]]
                   (d/type-query (d/db node) "Observation" clauses))
            ::anom/category := ::anom/unsupported
            ::anom/message := "Unsupported prefix `sa` in search parameter `value-quantity`."))

        (testing "ends before"
          (given (let [clauses [["status" "final"] ["value-quantity" "eb2.11|kg/m2"]]]
                   (d/type-query (d/db node) "Observation" clauses))
            ::anom/category := ::anom/unsupported
            ::anom/message := "Unsupported prefix `eb` in search parameter `value-quantity`."))

        (testing "approximately"
          (given (let [clauses [["status" "final"] ["value-quantity" "ap2.11|kg/m2"]]]
                   (d/type-query (d/db node) "Observation" clauses))
            ::anom/category := ::anom/unsupported
            ::anom/message := "Unsupported prefix `ap` in search parameter `value-quantity`.")))

      (testing "with more than one value"
        (let [clauses [["status" "final"] ["value-quantity" "2.11|kg/m2" "1|kg/m2"]]]
          (given (pull-type-query node "Observation" clauses)
            count := 2
            [0 :id] := "id-1"
            [1 :id] := "id-2"))))

    (testing "value-quantity and status"
      (given-type-query node "Observation" [["value-quantity" "2.11|kg/m2"] ["status" "final"]]
        count := 1
        [0 :id] := "id-2"))

    (testing "status and sort by _lastUpdated"
      (given-type-query node "Observation" [[:sort "_lastUpdated" :asc] ["status" "final"]]
        count := 4))

    (testing "three clauses"
      (given-type-query node "Observation" [["_profile:below" "http://example.com/profile-uri-091902"]
                                            ["status" "final"]
                                            ["date" "2021"]]
        count := 2
        [0 :id] := "id-0"
        [1 :id] := "id-1"))))

(deftest type-query-observation-code-value-quantity-test
  (with-system-data [{:blaze.db/keys [node]} config]
    [[[:put {:fhir/type :fhir/Observation :id "id-0"
             :status #fhir/code"final"
             :code
             #fhir/CodeableConcept
              {:coding
               [#fhir/Coding
                 {:system #fhir/uri"http://loinc.org"
                  :code #fhir/code"8480-6"}]}
             :value
             #fhir/Quantity
              {:value 130M
               :code #fhir/code"mm[Hg]"
               :system #fhir/uri"http://unitsofmeasure.org"}}]
      [:put {:fhir/type :fhir/Observation :id "id-1"
             :status #fhir/code"final"
             :code
             #fhir/CodeableConcept
              {:coding
               [#fhir/Coding
                 {:system #fhir/uri"http://loinc.org"
                  :code #fhir/code"8480-6"}]}
             :value
             #fhir/Quantity
              {:value 150M
               :code #fhir/code"mm[Hg]"
               :system #fhir/uri"http://unitsofmeasure.org"}}]
      [:put {:fhir/type :fhir/Observation :id "id-2"
             :status #fhir/code"final"
             :code
             #fhir/CodeableConcept
              {:coding
               [#fhir/Coding
                 {:system #fhir/uri"http://loinc.org"
                  :code #fhir/code"8462-4"}]}
             :value
             #fhir/Quantity
              {:value 90M
               :code #fhir/code"mm[Hg]"
               :system #fhir/uri"http://unitsofmeasure.org"}}]
      [:put {:fhir/type :fhir/Observation :id "id-3"
             :status #fhir/code"final"
             :code
             #fhir/CodeableConcept
              {:coding
               [#fhir/Coding
                 {:system #fhir/uri"http://loinc.org"
                  :code #fhir/code"8462-4"}]}
             :value
             #fhir/Quantity
              {:value 70M
               :code #fhir/code"mm[Hg]"
               :system #fhir/uri"http://unitsofmeasure.org"}}]]]

    (testing "missing second value part"
      (given (d/type-query (d/db node) "Observation" [["code-value-quantity" "8480-6"]])
        ::anom/category := ::anom/incorrect
        ::anom/message := "Miss the second part is composite search value `8480-6`."))

    (testing "as first clause"
      (given-type-query node "Observation" [["code-value-quantity" "8480-6$ge140"]]
        count := 1
        [0 :id] := "id-1")

      (given-type-query node "Observation" [["code-value-quantity" "http://loinc.org|8462-4$ge90|mm[Hg]"]]
        count := 1
        [0 :id] := "id-2"))

    (testing "as second clause"
      (given-type-query node "Observation" [["status" "final"]
                                            ["code-value-quantity" "http://loinc.org|8480-6$ge140|mm[Hg]"]]
        count := 1
        [0 :id] := "id-1"))

    (testing "with matcher"
      (let [matcher (d/compile-type-matcher node "Observation" [["code-value-quantity" "8480-6$ge140"]])
            db (d/db node)
            xform (d/matcher-transducer db matcher)]
        (given (into [] xform (d/type-list db "Observation"))
          count := 1
          [0 :id] := "id-1")))

    (testing "with individual code and value-quantity clauses"
      (given-type-query node "Observation" [["code" "http://loinc.org|8480-6"]
                                            ["value-quantity" "ge140|mm[Hg]"]]
        count := 1
        [0 :id] := "id-1"))

    (testing "resulting on more than one observation"
      (testing "as first clause"
        (testing "code as system|code"
          (testing "value as value|unit"
            (let [clauses [["code-value-quantity" "http://loinc.org|8480-6$ge130|mm[Hg]"]]]
              (given (pull-type-query node "Observation" clauses)
                count := 2
                [0 :id] := "id-0"
                [1 :id] := "id-1")))
          (testing "value as value"
            (let [clauses [["code-value-quantity" "http://loinc.org|8480-6$ge130"]]]
              (given (pull-type-query node "Observation" clauses)
                count := 2
                [0 :id] := "id-0"
                [1 :id] := "id-1"))))
        (testing "code as code"
          (testing "value as value|unit"
            (let [clauses [["code-value-quantity" "8480-6$ge130|mm[Hg]"]]]
              (given (pull-type-query node "Observation" clauses)
                count := 2
                [0 :id] := "id-0"
                [1 :id] := "id-1")))
          (testing "value as value"
            (let [clauses [["code-value-quantity" "8480-6$ge130"]]]
              (given (pull-type-query node "Observation" clauses)
                count := 2
                [0 :id] := "id-0"
                [1 :id] := "id-1"))))

        (testing "it is possible to start with the second observation"
          (testing "code as system|code"
            (testing "value as value|unit"
              (let [clauses [["code-value-quantity" "http://loinc.org|8480-6$ge130|mm[Hg]"]]]
                (given (pull-type-query node "Observation" clauses "id-1")
                  count := 1
                  [0 :id] := "id-1")))
            (testing "value as value"
              (let [clauses [["code-value-quantity" "http://loinc.org|8480-6$ge130"]]]
                (given (pull-type-query node "Observation" clauses "id-1")
                  count := 1
                  [0 :id] := "id-1"))))
          (testing "code as code"
            (testing "value as value|unit"
              (let [clauses [["code-value-quantity" "8480-6$ge130|mm[Hg]"]]]
                (given (pull-type-query node "Observation" clauses "id-1")
                  count := 1
                  [0 :id] := "id-1")))
            (testing "value as value"
              (let [clauses [["code-value-quantity" "8480-6$ge130"]]]
                (given (pull-type-query node "Observation" clauses "id-1")
                  count := 1
                  [0 :id] := "id-1"))))))

      (testing "as second clause"
        (testing "code as system|code"
          (testing "value as value|unit"
            (let [clauses [["status" "final"]
                           ["code-value-quantity" "http://loinc.org|8480-6$ge130|mm[Hg]"]]]
              (given (pull-type-query node "Observation" clauses)
                count := 2
                [0 :id] := "id-0"
                [1 :id] := "id-1")))
          (testing "value as value"
            (let [clauses [["status" "final"]
                           ["code-value-quantity" "http://loinc.org|8480-6$ge130"]]]
              (given (pull-type-query node "Observation" clauses)
                count := 2
                [0 :id] := "id-0"
                [1 :id] := "id-1"))))
        (testing "code as code"
          (testing "value as value|unit"
            (let [clauses [["status" "final"]
                           ["code-value-quantity" "8480-6$ge130|mm[Hg]"]]]
              (given (pull-type-query node "Observation" clauses)
                count := 2
                [0 :id] := "id-0"
                [1 :id] := "id-1")))
          (testing "value as value"
            (let [clauses [["status" "final"]
                           ["code-value-quantity" "8480-6$ge130"]]]
              (given (pull-type-query node "Observation" clauses)
                count := 2
                [0 :id] := "id-0"
                [1 :id] := "id-1"))))

        (testing "it is possible to start with the second observation"
          (testing "code as system|code"
            (testing "value as value|unit"
              (let [clauses [["status" "final"]
                             ["code-value-quantity" "http://loinc.org|8480-6$ge130|mm[Hg]"]]]
                (given (pull-type-query node "Observation" clauses "id-1")
                  count := 1
                  [0 :id] := "id-1")))
            (testing "value as value"
              (let [clauses [["status" "final"]
                             ["code-value-quantity" "http://loinc.org|8480-6$ge130"]]]
                (given (pull-type-query node "Observation" clauses "id-1")
                  count := 1
                  [0 :id] := "id-1"))))
          (testing "code as code"
            (testing "value as value|unit"
              (let [clauses [["status" "final"]
                             ["code-value-quantity" "8480-6$ge130|mm[Hg]"]]]
                (given (pull-type-query node "Observation" clauses "id-1")
                  count := 1
                  [0 :id] := "id-1")))
            (testing "value as value"
              (let [clauses [["status" "final"]
                             ["code-value-quantity" "8480-6$ge130"]]]
                (given (pull-type-query node "Observation" clauses "id-1")
                  count := 1
                  [0 :id] := "id-1"))))))

      (testing "with individual code and value-quantity clauses"
        (testing "code as system|code"
          (testing "value as value|unit"
            (let [clauses [["code" "http://loinc.org|8480-6"]
                           ["value-quantity" "ge130|mm[Hg]"]]]
              (given (pull-type-query node "Observation" clauses)
                count := 2
                [0 :id] := "id-0"
                [1 :id] := "id-1")))
          (testing "value as value"
            (let [clauses [["code" "http://loinc.org|8480-6"]
                           ["value-quantity" "ge130"]]]
              (given (pull-type-query node "Observation" clauses)
                count := 2
                [0 :id] := "id-0"
                [1 :id] := "id-1"))))
        (testing "code as code"
          (testing "value as value|unit"
            (let [clauses [["code" "8480-6"]
                           ["value-quantity" "ge130|mm[Hg]"]]]
              (given (pull-type-query node "Observation" clauses)
                count := 2
                [0 :id] := "id-0"
                [1 :id] := "id-1")))
          (testing "value as value"
            (let [clauses [["code" "8480-6"]
                           ["value-quantity" "ge130"]]]
              (given (pull-type-query node "Observation" clauses)
                count := 2
                [0 :id] := "id-0"
                [1 :id] := "id-1"))))

        (testing "it is possible to start with the second observation"
          (testing "code as system|code"
            (testing "value as value|unit"
              (let [clauses [["code" "http://loinc.org|8480-6"]
                             ["value-quantity" "ge130|mm[Hg]"]]]
                (given (pull-type-query node "Observation" clauses "id-1")
                  count := 1
                  [0 :id] := "id-1")))
            (testing "value as value"
              (let [clauses [["code" "http://loinc.org|8480-6"]
                             ["value-quantity" "ge130"]]]
                (given (pull-type-query node "Observation" clauses "id-1")
                  count := 1
                  [0 :id] := "id-1"))))
          (testing "code as code"
            (testing "value as value|unit"
              (let [clauses [["code" "8480-6"]
                             ["value-quantity" "ge130|mm[Hg]"]]]
                (given (pull-type-query node "Observation" clauses "id-1")
                  count := 1
                  [0 :id] := "id-1")))
            (testing "value as value"
              (let [clauses [["code" "8480-6"]
                             ["value-quantity" "ge130"]]]
                (given (pull-type-query node "Observation" clauses "id-1")
                  count := 1
                  [0 :id] := "id-1")))))))))

(deftest type-query-observation-code-value-concept-test
  (with-system-data [{:blaze.db/keys [node]} config]
    [[[:put {:fhir/type :fhir/Observation :id "id-0"
             :status #fhir/code"final"
             :code
             #fhir/CodeableConcept
              {:coding
               [#fhir/Coding
                 {:system #fhir/uri"http://loinc.org"
                  :code #fhir/code"94564-2"
                  :display #fhir/string"SARS-CoV-2 (COVID-19) IgM Ab [Presence]"}]}
             :value
             #fhir/CodeableConcept
              {:coding
               [#fhir/Coding
                 {:system #fhir/uri"http://snomed.info/sct"
                  :code #fhir/code"260373001"
                  :display #fhir/string"Detected (qualifier value)"}]}}]
      [:put {:fhir/type :fhir/Observation :id "id-1"
             :status #fhir/code"final"
             :code
             #fhir/CodeableConcept
              {:coding
               [#fhir/Coding
                 {:system #fhir/uri"http://loinc.org"
                  :code #fhir/code"94564-2"
                  :display #fhir/string"SARS-CoV-2 (COVID-19) IgM Ab [Presence]"}]}
             :value
             #fhir/CodeableConcept
              {:coding
               [#fhir/Coding
                 {:system #fhir/uri"http://snomed.info/sct"
                  :code #fhir/code"260415000"
                  :display #fhir/string"Not detected (qualifier value)"}]}}]
      [:put {:fhir/type :fhir/Observation :id "id-2"
             :status #fhir/code"final"
             :code
             #fhir/CodeableConcept
              {:coding
               [#fhir/Coding
                 {:system #fhir/uri"http://loinc.org"
                  :code #fhir/code"94564-2"
                  :display #fhir/string"SARS-CoV-2 (COVID-19) IgM Ab [Presence]"}]}
             :value
             #fhir/CodeableConcept
              {:coding
               [#fhir/Coding
                 {:system #fhir/uri"http://snomed.info/sct"
                  :code #fhir/code"260373001"
                  :display #fhir/string"Detected (qualifier value)"}]}}]]]

    (testing "missing second value part"
      (given (d/type-query (d/db node) "Observation" [["code-value-concept" "http://loinc.org|94564-2"]])
        ::anom/category := ::anom/incorrect
        ::anom/message := "Miss the second part is composite search value `http://loinc.org|94564-2`."))

    (testing "as first clause"
      (testing "code as system|code"
        (testing "value as system|code"
          (given-type-query node "Observation" [["code-value-concept" "http://loinc.org|94564-2$http://snomed.info/sct|260373001"]]
            count := 2
            [0 :id] := "id-0"
            [1 :id] := "id-2"))

        (testing "value as code"
          (given-type-query node "Observation" [["code-value-concept" "http://loinc.org|94564-2$260373001"]]
            count := 2
            [0 :id] := "id-0"
            [1 :id] := "id-2")))

      (testing "code as code"
        (testing "value as system|code"
          (given-type-query node "Observation" [["code-value-concept" "94564-2$http://snomed.info/sct|260373001"]]
            count := 2
            [0 :id] := "id-0"
            [1 :id] := "id-2"))

        (testing "value as code"
          (given-type-query node "Observation" [["code-value-concept" "94564-2$260373001"]]
            count := 2
            [0 :id] := "id-0"
            [1 :id] := "id-2")))

      (testing "it is possible to start with the second observation"
        (testing "code as system|code"
          (testing "value as system|code"
            (given (pull-type-query node "Observation" [["code-value-concept" "http://loinc.org|94564-2$http://snomed.info/sct|260373001"]] "id-2")
              count := 1
              [0 :id] := "id-2"))

          (testing "value as code"
            (given (pull-type-query node "Observation" [["code-value-concept" "http://loinc.org|94564-2$260373001"]] "id-2")
              count := 1
              [0 :id] := "id-2")))

        (testing "code as code"
          (testing "value as system|code"
            (given (pull-type-query node "Observation" [["code-value-concept" "94564-2$http://snomed.info/sct|260373001"]] "id-2")
              count := 1
              [0 :id] := "id-2"))

          (testing "value as code"
            (given (pull-type-query node "Observation" [["code-value-concept" "94564-2$260373001"]] "id-2")
              count := 1
              [0 :id] := "id-2")))))

    (testing "as second clause"
      (testing "code as system|code"
        (testing "value as system|code"
          (given-type-query node "Observation" [["status" "final"]
                                                ["code-value-concept" "http://loinc.org|94564-2$http://snomed.info/sct|260373001"]]
            count := 2
            [0 :id] := "id-0"
            [1 :id] := "id-2"))

        (testing "value as code"
          (given-type-query node "Observation" [["status" "final"]
                                                ["code-value-concept" "http://loinc.org|94564-2$260373001"]]
            count := 2
            [0 :id] := "id-0"
            [1 :id] := "id-2")))

      (testing "code as code"
        (testing "value as system|code"
          (given-type-query node "Observation" [["status" "final"]
                                                ["code-value-concept" "94564-2$http://snomed.info/sct|260373001"]]
            count := 2
            [0 :id] := "id-0"
            [1 :id] := "id-2"))

        (testing "value as code"
          (given-type-query node "Observation" [["status" "final"]
                                                ["code-value-concept" "94564-2$260373001"]]
            count := 2
            [0 :id] := "id-0"
            [1 :id] := "id-2"))))

    (testing "with matcher"
      (let [matcher (d/compile-type-matcher node "Observation" [["code-value-concept" "94564-2$260373001"]])
            db (d/db node)
            xform (d/matcher-transducer db matcher)]
        (given (into [] xform (d/type-list db "Observation"))
          count := 2
          [0 :id] := "id-0"
          [1 :id] := "id-2")))))

(deftest type-query-observation-code-subject-test
  (with-system-data [{:blaze.db/keys [node]} config]
    [[[:put {:fhir/type :fhir/Patient :id "0"}]
      [:put {:fhir/type :fhir/Patient :id "1"}]
      [:put {:fhir/type :fhir/Group :id "0"}]
      [:put {:fhir/type :fhir/Group :id "1"}]
      [:put {:fhir/type :fhir/Observation :id "0"
             :category
             [#fhir/CodeableConcept
               {:coding
                [#fhir/Coding
                  {:system #fhir/uri"system-182245"
                   :code #fhir/code"code-182257"}]}]
             :code
             #fhir/CodeableConcept
              {:coding
               [#fhir/Coding
                 {:system #fhir/uri"http://loinc.org"
                  :code #fhir/code"94564-2"}]}
             :subject #fhir/Reference{:reference "Patient/0"}}]
      [:put {:fhir/type :fhir/Observation :id "1"
             :category
             [#fhir/CodeableConcept
               {:coding
                [#fhir/Coding
                  {:system #fhir/uri"system-182245"
                   :code #fhir/code"code-182257"}]}]
             :code
             #fhir/CodeableConcept
              {:coding
               [#fhir/Coding
                 {:system #fhir/uri"http://loinc.org"
                  :code #fhir/code"8480-6"}]}
             :subject #fhir/Reference{:reference "Patient/0"}}]
      [:put {:fhir/type :fhir/Observation :id "2"
             :category
             [#fhir/CodeableConcept
               {:coding
                [#fhir/Coding
                  {:system #fhir/uri"system-182245"
                   :code #fhir/code"code-182257"}]}]
             :code
             #fhir/CodeableConcept
              {:coding
               [#fhir/Coding
                 {:system #fhir/uri"http://loinc.org"
                  :code #fhir/code"94564-2"}]}
             :subject #fhir/Reference{:reference "Patient/1"}}]
      [:put {:fhir/type :fhir/Observation :id "3"
             :category
             [#fhir/CodeableConcept
               {:coding
                [#fhir/Coding
                  {:system #fhir/uri"system-182245"
                   :code #fhir/code"code-182257"}]}]
             :code
             #fhir/CodeableConcept
              {:coding
               [#fhir/Coding
                 {:system #fhir/uri"http://loinc.org"
                  :code #fhir/code"94564-2"}]}
             :subject #fhir/Reference{:reference "Group/0"}}]
      [:put {:fhir/type :fhir/Observation :id "4"
             :category
             [#fhir/CodeableConcept
               {:coding
                [#fhir/Coding
                  {:system #fhir/uri"system-182245"
                   :code #fhir/code"code-182257"}]}]
             :code
             #fhir/CodeableConcept
              {:coding
               [#fhir/Coding
                 {:system #fhir/uri"http://loinc.org"
                  :code #fhir/code"94564-2"}]}
             :subject #fhir/Reference{:reference "Group/1"}}]]]

    (doseq [code ["http://loinc.org|94564-2" "94564-2"]]
      (testing "as first clause"
        (testing "with one patient"
          (doseq [subject-clause [["subject" "Patient/0"]
                                  ["patient" "Patient/0"]
                                  ["patient" "0"]]
                  clauses [[subject-clause ["code" code]]]]
            (given-type-query node "Observation" clauses
              count := 1
              [0 :id] := "0")

            (when (= "http://loinc.org|94564-2" code)
              (given (explain-type-query node "Observation" clauses)
                :query-type := :compartment
                :scan-type := :ordered
                [:scan-clauses count] := 1
                [:scan-clauses 0 :code] := "code"
                [:scan-clauses 0 :values] := ["http://loinc.org|94564-2"]
                [:seek-clauses count] := 0))

            (when (= "94564-2" code)
              (given (explain-type-query node "Observation" clauses)
                :query-type := :compartment
                :scan-type := nil
                :scan-clauses := nil
                [:seek-clauses count] := 1
                [:seek-clauses 0 :code] := "code"
                [:seek-clauses 0 :values] := ["94564-2"]))))

        (testing "with one group"
          (given-type-query node "Observation" [["subject" "Group/0"] ["code" code]]
            count := 1
            [0 :id] := "3"))

        (testing "with two patients"
          (doseq [subject-clause [["subject" "Patient/0" "Patient/1"]
                                  ["patient" "Patient/0" "Patient/1"]
                                  ["patient" "Patient/0" "1"]
                                  ["patient" "0" "Patient/1"]
                                  ["patient" "0" "1"]]]
            (doseq [clauses [[subject-clause
                              ["code" code]]
                             [subject-clause
                              ["code" code]
                              ["category" "system-182245|code-182257"]]]]
              (given-type-query node "Observation" clauses
                count := 2
                [0 :id] := "0"
                [1 :id] := "2")

              (testing "it is possible to start with the second observation"
                (given (pull-type-query node "Observation" clauses "2")
                  count := 1
                  [0 :id] := "2")))))

        (testing "with two groups"
          (let [clauses [["subject" "Group/0" "Group/1"] ["code" code]]]
            (given-type-query node "Observation" clauses
              count := 2
              [0 :id] := "3"
              [1 :id] := "4")

            (testing "it is possible to start with the second observation"
              (given (pull-type-query node "Observation" clauses "4")
                count := 1
                [0 :id] := "4")))))

      (testing "as second clause"
        (testing "with one patient"
          (doseq [subject-clause [["subject" "Patient/0"]
                                  ["patient" "Patient/0"]
                                  ["patient" "0"]]]
            (given-type-query node "Observation" [["code" code] subject-clause]
              count := 1
              [0 :id] := "0")))

        (testing "with one group"
          (let [clauses [["code" code] ["subject" "Group/0"]]]
            (given-type-query node "Observation" clauses
              count := 1
              [0 :id] := "3")))

        (testing "with two patients"
          (doseq [subject-clause [["subject" "Patient/0" "Patient/1"]
                                  ["patient" "Patient/0" "Patient/1"]
                                  ["patient" "Patient/0" "1"]
                                  ["patient" "0" "Patient/1"]
                                  ["patient" "0" "1"]]]
            (let [clauses [["code" code] subject-clause]]
              (given-type-query node "Observation" clauses
                count := 2
                [0 :id] := "0"
                [1 :id] := "2")

              (testing "it is possible to start with the second observation"
                (given (pull-type-query node "Observation" clauses "2")
                  count := 1
                  [0 :id] := "2")))))

        (testing "with two groups"
          (let [clauses [["code" code] ["subject" "Group/0" "Group/1"]]]
            (given-type-query node "Observation" clauses
              count := 2
              [0 :id] := "3"
              [1 :id] := "4")

            (testing "it is possible to start with the second observation"
              (given (pull-type-query node "Observation" clauses "4")
                count := 1
                [0 :id] := "4")))))

      (testing "with both subject and patient parameters"
        (testing "with one patient"
          (doseq [ref ["Patient/0" "0"]
                  clauses [[["code" code]
                            ["subject" "Patient/0"]
                            ["patient" ref]]
                           [["code" code]
                            ["patient" ref]
                            ["subject" "Patient/0"]]]]
            (given-type-query node "Observation" clauses
              count := 1
              [0 :id] := "0")))

        (testing "with two patients"
          (let [clauses [["code" code]
                         ["subject" "Patient/0" "Patient/1"]
                         ["patient" "Patient/0" "Patient/1"]]]
            (given-type-query node "Observation" clauses
              count := 2
              [0 :id] := "0"
              [1 :id] := "2"))))))

  (testing "start-id works"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "1"}]
        [:put {:fhir/type :fhir/Observation :id "3"
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri"http://loinc.org"
                    :code #fhir/code"94564-2"}]}
               :subject #fhir/Reference{:reference "Patient/0"}}]
        [:put {:fhir/type :fhir/Observation :id "2"
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri"http://loinc.org"
                    :code #fhir/code"8480-6"}]}
               :subject #fhir/Reference{:reference "Patient/0"}}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri"http://loinc.org"
                    :code #fhir/code"94564-2"}]}
               :subject #fhir/Reference{:reference "Patient/1"}}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri"http://loinc.org"
                    :code #fhir/code"8480-6"}]}
               :subject #fhir/Reference{:reference "Patient/1"}}]]]

      (let [clauses [["patient" "0" "1"]
                     ["code" "http://loinc.org|94564-2" "http://loinc.org|8480-6"]]]
        (given-type-query node "Observation" clauses
          count := 4
          [0 :id] := "2"
          [1 :id] := "3"
          [2 :id] := "0"
          [3 :id] := "1")

        (testing "it is possible to start with the second observation"
          (given (pull-type-query node "Observation" clauses "3")
            count := 3
            [0 :id] := "3"
            [1 :id] := "0"
            [2 :id] := "1"))

        (testing "it is possible to start with the third observation"
          (given (pull-type-query node "Observation" clauses "0")
            count := 2
            [0 :id] := "0"
            [1 :id] := "1"))))))

(deftest type-query-procedure-code-subject-test
  (with-system-data [{:blaze.db/keys [node]} config]
    [[[:put {:fhir/type :fhir/Patient :id "0"}]
      [:put {:fhir/type :fhir/Patient :id "1"}]
      [:put {:fhir/type :fhir/Group :id "0"}]
      [:put {:fhir/type :fhir/Group :id "1"}]
      [:put {:fhir/type :fhir/Procedure :id "0"
             :code
             #fhir/CodeableConcept
              {:coding
               [#fhir/Coding
                 {:system #fhir/uri"http://snomed.info/sct"
                  :code #fhir/code"243141005"
                  :display #fhir/string"Mechanically assisted spontaneous ventilation (regime/therapy)"}]}
             :subject #fhir/Reference{:reference "Patient/0"}}]
      [:put {:fhir/type :fhir/Procedure :id "1"
             :code
             #fhir/CodeableConcept
              {:coding
               [#fhir/Coding
                 {:system #fhir/uri"http://snomed.info/sct"
                  :code #fhir/code"243141005"
                  :display #fhir/string"Mechanically assisted spontaneous ventilation (regime/therapy)"}]}
             :subject #fhir/Reference{:reference "Patient/1"}}]
      [:put {:fhir/type :fhir/Procedure :id "2"
             :code
             #fhir/CodeableConcept
              {:coding
               [#fhir/Coding
                 {:system #fhir/uri"http://snomed.info/sct"
                  :code #fhir/code"243141005"
                  :display #fhir/string"Mechanically assisted spontaneous ventilation (regime/therapy)"}]}
             :subject #fhir/Reference{:reference "Group/0"}}]
      [:put {:fhir/type :fhir/Procedure :id "3"
             :code
             #fhir/CodeableConcept
              {:coding
               [#fhir/Coding
                 {:system #fhir/uri"http://snomed.info/sct"
                  :code #fhir/code"243141005"
                  :display #fhir/string"Mechanically assisted spontaneous ventilation (regime/therapy)"}]}
             :subject #fhir/Reference{:reference "Group/1"}}]]]

    (doseq [code ["http://snomed.info/sct|243141005" "243141005"]]
      (testing "as first clause"
        (testing "with one patient"
          (doseq [subject-clause [["subject" "Patient/0"]
                                  ["patient" "Patient/0"]
                                  ["patient" "0"]]]
            (let [clauses [subject-clause ["code" code]]]
              (given (pull-type-query node "Procedure" clauses)
                count := 1
                [0 :id] := "0")

              (testing "count query"
                (is (= 1 (count-type-query node "Procedure" clauses)))))))

        (testing "with one group"
          (let [clauses [["subject" "Group/0"] ["code" code]]]
            (given (pull-type-query node "Procedure" clauses)
              count := 1
              [0 :id] := "2")

            (testing "count query"
              (is (= 1 (count-type-query node "Procedure" clauses))))))

        (testing "with two patients"
          (doseq [subject-clause [["subject" "Patient/0" "Patient/1"]
                                  ["patient" "Patient/0" "Patient/1"]
                                  ["patient" "Patient/0" "1"]
                                  ["patient" "0" "Patient/1"]
                                  ["patient" "0" "1"]]]
            (let [clauses [subject-clause ["code" code]]]
              (given (pull-type-query node "Procedure" clauses)
                count := 2
                [0 :id] := "0"
                [1 :id] := "1")

              (testing "it is possible to start with the second Procedure"
                (given (pull-type-query node "Procedure" clauses "1")
                  count := 1
                  [0 :id] := "1"))

              (testing "count query"
                (is (= 2 (count-type-query node "Procedure" clauses)))))))

        (testing "with two groups"
          (let [clauses [["subject" "Group/0" "Group/1"] ["code" code]]]
            (given (pull-type-query node "Procedure" clauses)
              count := 2
              [0 :id] := "2"
              [1 :id] := "3")

            (testing "it is possible to start with the second Procedure"
              (given (pull-type-query node "Procedure" clauses "3")
                count := 1
                [0 :id] := "3"))

            (testing "count query"
              (is (= 2 (count-type-query node "Procedure" clauses)))))))

      (testing "as second clause"
        (testing "with one patient"
          (doseq [subject-clause [["subject" "Patient/0"]
                                  ["patient" "Patient/0"]
                                  ["patient" "0"]]]
            (let [clauses [["code" code] subject-clause]]
              (given (pull-type-query node "Procedure" clauses)
                count := 1
                [0 :id] := "0")

              (testing "count query"
                (is (= 1 (count-type-query node "Procedure" clauses)))))))

        (testing "with one group"
          (let [clauses [["code" code] ["subject" "Group/0"]]]
            (given (pull-type-query node "Procedure" clauses)
              count := 1
              [0 :id] := "2")

            (testing "count query"
              (is (= 1 (count-type-query node "Procedure" clauses))))))

        (testing "with two patients"
          (doseq [subject-clause [["subject" "Patient/0" "Patient/1"]
                                  ["patient" "Patient/0" "Patient/1"]
                                  ["patient" "Patient/0" "1"]
                                  ["patient" "0" "Patient/1"]
                                  ["patient" "0" "1"]]]
            (let [clauses [["code" code] subject-clause]]
              (given (pull-type-query node "Procedure" clauses)
                count := 2
                [0 :id] := "0"
                [1 :id] := "1")

              (testing "it is possible to start with the second Procedure"
                (given (pull-type-query node "Procedure" clauses "1")
                  count := 1
                  [0 :id] := "1"))

              (testing "count query"
                (is (= 2 (count-type-query node "Procedure" clauses)))))))

        (testing "with two groups"
          (let [clauses [["code" code] ["subject" "Group/0" "Group/1"]]]
            (given (pull-type-query node "Procedure" clauses)
              count := 2
              [0 :id] := "2"
              [1 :id] := "3")

            (testing "it is possible to start with the second Procedure"
              (given (pull-type-query node "Procedure" clauses "3")
                count := 1
                [0 :id] := "3"))

            (testing "count query"
              (is (= 2 (count-type-query node "Procedure" clauses)))))))

      (testing "with both subject and patient parameters"
        (testing "with one patient"
          (doseq [ref ["Patient/0" "0"]
                  clauses [[["code" code]
                            ["subject" "Patient/0"]
                            ["patient" ref]]
                           [["code" code]
                            ["patient" ref]
                            ["subject" "Patient/0"]]]]
            (given (pull-type-query node "Procedure" clauses)
              count := 1
              [0 :id] := "0")

            (testing "count query"
              (is (= 1 (count-type-query node "Procedure" clauses))))))

        (testing "with two patients"
          (let [clauses [["code" code]
                         ["subject" "Patient/0" "Patient/1"]
                         ["patient" "Patient/0" "Patient/1"]]]
            (given (pull-type-query node "Procedure" clauses)
              count := 2
              [0 :id] := "0"
              [1 :id] := "1")

            (testing "count query"
              (is (= 2 (count-type-query node "Procedure" clauses))))))))))

(deftest type-query-observation-date-subject-test
  (with-system-data [{:blaze.db/keys [node]} config]
    [[[:put {:fhir/type :fhir/Patient :id "0"}]
      [:put {:fhir/type :fhir/Patient :id "1"}]
      [:put {:fhir/type :fhir/Observation :id "0"
             :effective #fhir/dateTime"1990-06-14T12:24:48Z"
             :subject #fhir/Reference{:reference "Patient/0"}}]
      [:put {:fhir/type :fhir/Observation :id "1"
             :effective #fhir/dateTime"1990-06-14T12:24:48Z"
             :subject #fhir/Reference{:reference "Patient/1"}}]]]

    (testing "as first clause"
      (testing "with one patient"
        (doseq [subject ["Patient/0" "0"]]
          (let [clauses [["subject" subject]
                         ["date" "1990-06-14T12:24:48Z"]]]
            (given (pull-type-query node "Observation" clauses)
              count := 1
              [0 :id] := "0")

            (testing "count query"
              (is (= 1 (count-type-query node "Observation" clauses)))))))

      (testing "with two patients"
        (doseq [subjects [["Patient/0" "Patient/1"] ["0" "1"]]]
          (let [clauses [(into ["subject"] subjects)
                         ["date" "1990-06-14T12:24:48Z"]]]
            (given (pull-type-query node "Observation" clauses)
              count := 2
              [0 :id] := "0"
              [1 :id] := "1")

            (testing "it is possible to start with the second observation"
              (given (pull-type-query node "Observation" clauses "1")
                count := 1
                [0 :id] := "1"))

            (testing "count query"
              (is (= 2 (count-type-query node "Observation" clauses))))))))

    (testing "as second clause"
      (testing "with one patient"
        (doseq [subject ["Patient/0" "0"]]
          (let [clauses [["date" "1990-06-14T12:24:48Z"]
                         ["subject" subject]]]
            (given (pull-type-query node "Observation" clauses)
              count := 1
              [0 :id] := "0")

            (testing "count query"
              (is (= 1 (count-type-query node "Observation" clauses)))))))

      (testing "with two patients"
        (doseq [subjects [["Patient/0" "Patient/1"] ["0" "1"]]]
          (let [clauses [["date" "1990-06-14T12:24:48Z"]
                         (into ["subject"] subjects)]]
            (given (pull-type-query node "Observation" clauses)
              count := 2
              [0 :id] := "0"
              [1 :id] := "1")

            (testing "count query"
              (is (= 2 (count-type-query node "Observation" clauses))))))))))

(deftest type-query-list-test
  (testing "item"
    (testing "with no modifier"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Patient :id "1"}]]
         [[:put {:fhir/type :fhir/List
                 :id "id-150545"
                 :entry
                 [{:fhir/type :fhir.List/entry
                   :item
                   #fhir/Reference
                    {:reference "Patient/0"}}]}]
          [:put {:fhir/type :fhir/List
                 :id "id-143814"
                 :entry
                 [{:fhir/type :fhir.List/entry
                   :item
                   #fhir/Reference
                    {:reference "Patient/1"}}]}]]]

        (given-type-query node "List" [["item" "Patient/1"]]
          count := 1
          [0 :id] := "id-143814")))

    (testing "with identifier modifier"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/List
                 :id "id-123058"
                 :entry
                 [{:fhir/type :fhir.List/entry
                   :item
                   #fhir/Reference
                    {:identifier
                     #fhir/Identifier
                      {:system #fhir/uri"system-122917"
                       :value #fhir/string"value-122931"}}}]}]
          [:put {:fhir/type :fhir/List
                 :id "id-143814"
                 :entry
                 [{:fhir/type :fhir.List/entry
                   :item
                   #fhir/Reference
                    {:identifier
                     #fhir/Identifier
                      {:system #fhir/uri"system-122917"
                       :value "value-143818"}}}]}]]]

        (let [clauses [["item:identifier" "system-122917|value-122931"]]]
          (given (pull-type-query node "List" clauses)
            count := 1
            [0 :id] := "id-123058")))))

  (testing "code and item"
    (testing "with identifier modifier"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/List
                 :id "id-123058"
                 :code
                 #fhir/CodeableConcept
                  {:coding
                   [#fhir/Coding
                     {:system #fhir/uri"system-152812"
                      :code #fhir/code"code-152819"}]}
                 :entry
                 [{:fhir/type :fhir.List/entry
                   :item
                   #fhir/Reference
                    {:identifier
                     #fhir/Identifier
                      {:system #fhir/uri"system-122917"
                       :value "value-122931"}}}]}]
          [:put {:fhir/type :fhir/List
                 :id "id-143814"
                 :code
                 #fhir/CodeableConcept
                  {:coding
                   [#fhir/Coding
                     {:system #fhir/uri"system-152812"
                      :code #fhir/code"code-152819"}]}
                 :entry
                 [{:fhir/type :fhir.List/entry
                   :item
                   #fhir/Reference
                    {:identifier
                     #fhir/Identifier
                      {:system #fhir/uri"system-122917"
                       :value "value-143818"}}}]}]]]

        (let [clauses [["code" "system-152812|code-152819"]
                       ["item:identifier" "system-122917|value-143818"]]]
          (given (pull-type-query node "List" clauses)
            count := 1
            [0 :id] := "id-143814"))))))

(deftest type-query-encounter-test
  (testing "duplicates are removed"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Encounter
               :id "0"
               :diagnosis
               [{:fhir/type :fhir.Encounter/diagnosis
                 :condition #fhir/Reference{:reference "Condition/0"}}
                {:fhir/type :fhir.Encounter/diagnosis
                 :condition #fhir/Reference{:reference "Condition/1"}}]}]
        [:put {:fhir/type :fhir/Encounter
               :id "1"
               :diagnosis
               [{:fhir/type :fhir.Encounter/diagnosis
                 :condition #fhir/Reference{:reference "Condition/1"}}
                {:fhir/type :fhir.Encounter/diagnosis
                 :condition #fhir/Reference{:reference "Condition/2"}}]}]
        [:put {:fhir/type :fhir/Condition :id "0"}]
        [:put {:fhir/type :fhir/Condition :id "1"}]
        [:put {:fhir/type :fhir/Condition :id "2"}]]]

      (testing "on pulling all resource handles"
        (given-type-query node "Encounter" [["diagnosis" "Condition/0" "Condition/1" "Condition/2"]]
          count := 2
          [0 :id] := "0"
          [1 :id] := "1")

        (testing "count query"
          (is (= 2 (count-type-query node "Encounter" [["diagnosis" "Condition/0" "Condition/1" "Condition/2"]])))))

      (testing "on pulling the second page"
        (given (pull-type-query node "Encounter" [["diagnosis" "Condition/0" "Condition/1" "Condition/2"]] "1")
          count := 1
          [0 :id] := "1")))))

(deftest type-query-multiple-clauses-test
  (testing "with two token search params"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Observation :id "0"
               :status #fhir/code"final"
               :code #fhir/CodeableConcept
                      {:coding
                       [#fhir/Coding
                         {:system #fhir/uri"http://loinc.org"
                          :code #fhir/code"94564-2"}]}}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :status #fhir/code"preliminary"
               :code #fhir/CodeableConcept
                      {:coding
                       [#fhir/Coding
                         {:system #fhir/uri"http://loinc.org"
                          :code #fhir/code"94564-2"}]}}]
        [:put {:fhir/type :fhir/Observation :id "2"
               :status #fhir/code"final"
               :code #fhir/CodeableConcept
                      {:coding
                       [#fhir/Coding
                         {:system #fhir/uri"http://loinc.org"
                          :code #fhir/code"8462-4"}]}}]
        [:put {:fhir/type :fhir/Observation :id "3"
               :status #fhir/code"preliminary"
               :code #fhir/CodeableConcept
                      {:coding
                       [#fhir/Coding
                         {:system #fhir/uri"http://loinc.org"
                          :code #fhir/code"8462-4"}]}}]]]

      (let [clauses [["status" "final"] ["code" "94564-2"]]]
        (given-type-query node "Observation" clauses
          count := 1
          [0 :id] := "0")

        (given (explain-type-query node "Observation" clauses)
          :scan-type := :ordered
          [:scan-clauses count] := 2
          [:scan-clauses 0 :code] := "status"
          [:scan-clauses 1 :code] := "code"))

      (given-type-query node "Observation" [["status" "preliminary"] ["code" "94564-2"]]
        count := 1
        [0 :id] := "1")

      (given-type-query node "Observation" [["status" "final"] ["code" "8462-4"]]
        count := 1
        [0 :id] := "2")

      (given-type-query node "Observation" [["status" "preliminary"] ["code" "8462-4"]]
        count := 1
        [0 :id] := "3")

      (testing "first clause with multiple values"
        (given-type-query node "Observation" [["status" "final" "preliminary"] ["code" "94564-2"]]
          count := 2
          [0 :id] := "0"
          [1 :id] := "1")

        (testing "it is possible to start with the second observation"
          (given (pull-type-query node "Observation" [["status" "final" "preliminary"] ["code" "94564-2"]] "1")
            count := 1
            [0 :id] := "1")))

      (given-type-query node "Observation" [["status" "final"] ["code" "94564-2" "8462-4"]]
        count := 2
        [0 :id] := "0"
        [1 :id] := "2")

      (given-type-query node "Observation" [["status" "final" "preliminary"] ["code" "94564-2" "8462-4"]]
        count := 4
        [0 :id] := "0"
        [1 :id] := "1"
        [2 :id] := "2"
        [3 :id] := "3")))

  (testing "with one token and one date search param"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Observation :id "0"
               :status #fhir/code"final"
               :effective #fhir/dateTime"2025"}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :status #fhir/code"preliminary"
               :effective #fhir/dateTime"2025"}]
        [:put {:fhir/type :fhir/Observation :id "2"
               :status #fhir/code"final"
               :effective #fhir/dateTime"2026"}]
        [:put {:fhir/type :fhir/Observation :id "3"
               :status #fhir/code"preliminary"
               :effective #fhir/dateTime"2026"}]]]

      (let [clauses [["status" "final"] ["date" "2025"]]]
        (given-type-query node "Observation" clauses
          count := 1
          [0 :id] := "0")

        (given (explain-type-query node "Observation" clauses)
          :scan-type := :ordered
          [:scan-clauses count] := 1
          [:scan-clauses 0 :code] := "status"
          [:seek-clauses count] := 1
          [:seek-clauses 0 :code] := "date"))

      (given-type-query node "Observation" [["status" "preliminary"] ["date" "2025"]]
        count := 1
        [0 :id] := "1")

      (given-type-query node "Observation" [["status" "final"] ["date" "2026"]]
        count := 1
        [0 :id] := "2")

      (given-type-query node "Observation" [["status" "preliminary"] ["date" "2026"]]
        count := 1
        [0 :id] := "3")

      (given-type-query node "Observation" [["status" "final" "preliminary"] ["date" "2025"]]
        count := 2
        [0 :id] := "0"
        [1 :id] := "1")

      (given-type-query node "Observation" [["status" "final"] ["date" "2025" "2026"]]
        count := 2
        [0 :id] := "0"
        [1 :id] := "2")

      (given-type-query node "Observation" [["status" "final" "preliminary"] ["date" "2025" "2026"]]
        count := 4
        [0 :id] := "0"
        [1 :id] := "1"
        [2 :id] := "2"
        [3 :id] := "3"))))

(deftest type-query-date-equal-test
  (testing "with second precision"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Observation :id "0"
               :status #fhir/code"final"
               :effective #fhir/dateTime"1990-06-14T12:24:47Z"}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :status #fhir/code"final"
               :effective #fhir/dateTime"1990-06-14T12:24:48Z"}]
        [:put {:fhir/type :fhir/Observation :id "2"
               :status #fhir/code"final"
               :effective #fhir/dateTime"1990-06-14T12:24:48Z"}]
        [:put {:fhir/type :fhir/Observation :id "3"
               :status #fhir/code"final"
               :effective #fhir/dateTime"1990-06-14T12:24:48Z"}]
        [:put {:fhir/type :fhir/Observation :id "4"
               :status #fhir/code"final"
               :effective #fhir/dateTime"1990-06-14T12:24:49Z"}]]]

      (let [clauses [["date" "1990-06-14T12:24:48Z"]]]
        (given-type-query node "Observation" clauses
          count := 3
          [0 :id] := "1"
          [1 :id] := "2"
          [2 :id] := "3")

        (given (explain-type-query node "Observation" clauses)
          :scan-type := :unordered
          [:scan-clauses count] := 1
          [:scan-clauses 0 :code] := "date"
          [:seek-clauses count] := 0)

        (testing "it is possible to start with the second observation"
          (given (pull-type-query node "Observation" clauses "2")
            count := 2
            [0 :id] := "2"
            [1 :id] := "3")))

      (testing "as second clause"
        (let [clauses [["status" "final"] ["date" "1990-06-14T12:24:48Z"]]]
          (given-type-query node "Observation" clauses
            count := 3
            [0 :id] := "1"
            [1 :id] := "2"
            [2 :id] := "3")

          (given (explain-type-query node "Observation" clauses)
            :scan-type := :ordered
            [:scan-clauses count] := 1
            [:scan-clauses 0 :code] := "status"
            [:seek-clauses count] := 1
            [:seek-clauses 0 :code] := "date")

          (testing "it is possible to start with the second observation"
            (given (pull-type-query node "Observation" clauses "2")
              count := 2
              [0 :id] := "2"
              [1 :id] := "3")))))))

(deftest type-query-date-not-equal-test
  (testing "with second precision"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Observation :id "0"
               :status #fhir/code"final"
               :effective #fhir/dateTime"1990-06-14T12:24:47Z"}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :status #fhir/code"final"
               :effective #fhir/dateTime"1990-06-14T12:24:48Z"}]
        [:put {:fhir/type :fhir/Observation :id "2"
               :status #fhir/code"final"
               :effective #fhir/dateTime"1990-06-14T12:24:48Z"}]
        [:put {:fhir/type :fhir/Observation :id "3"
               :status #fhir/code"final"
               :effective #fhir/dateTime"1990-06-14T12:24:48Z"}]
        [:put {:fhir/type :fhir/Observation :id "4"
               :status #fhir/code"final"
               :effective #fhir/dateTime"1990-06-14T12:24:49Z"}]]]

      (given-type-query node "Observation" [["date" "ne1990-06-14T12:24:48Z"]]
        count := 2
        [0 :id] := "0"
        [1 :id] := "4")

      (testing "it is possible to start with the second observation"
        (given (pull-type-query node "Observation" [["date" "ne1990-06-14T12:24:48Z"]] "4")
          count := 1
          [0 :id] := "4"))

      (testing "as second clause"
        (given-type-query node "Observation" [["status" "final"] ["date" "ne1990-06-14T12:24:48Z"]]
          count := 2
          [0 :id] := "0"
          [1 :id] := "4")

        (testing "it is possible to start with the second observation"
          (given (pull-type-query node "Observation" [["status" "final"]
                                                      ["date" "ne1990-06-14T12:24:48Z"]]
                                  "4")
            count := 1
            [0 :id] := "4"))))))

(deftest type-query-date-greater-than-test
  (testing "year precision"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :birthDate #fhir/date"1990"}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :birthDate #fhir/date"1991"}]
        [:put {:fhir/type :fhir/Patient :id "2"
               :birthDate #fhir/date"1992"}]]]

      (given-type-query node "Patient" [["birthdate" "gt1990"]]
        count := 2
        [0 :id] := "1"
        [1 :id] := "2")

      (testing "it is possible to start with the second patient"
        (given (pull-type-query node "Patient" [["birthdate" "gt1990"]] "2")
          count := 1
          [0 :id] := "2"))))

  (testing "day precision"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :birthDate #fhir/date"2022-12-14"}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :birthDate #fhir/date"2022-12-15"}]]]

      (given-type-query node "Patient" [["birthdate" "gt2022-12-14"]]
        count := 1
        [0 :id] := "1")))

  (testing "as second clause"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :gender #fhir/code"male"
               :birthDate #fhir/date"2022-12-14"}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :gender #fhir/code"male"
               :birthDate #fhir/date"2022-12-15"}]]]

      (given-type-query node "Patient" [["gender" "male"]
                                        ["birthdate" "gt2022-12-14"]]
        count := 1
        [0 :id] := "1"))))

(deftest type-query-date-less-than-test
  (testing "year precision"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :birthDate #fhir/date"1970"}]]
       [[:put {:fhir/type :fhir/Patient :id "0"
               :birthDate #fhir/date"1990"}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :birthDate #fhir/date"1989"}]
        [:put {:fhir/type :fhir/Patient :id "2"
               :birthDate #fhir/date"1988"}]]]

      (doseq [clauses [[["birthdate" "lt1990"]]
                       [["birthdate" "lt1990" "le1989"]]]]
        (given-type-query node "Patient" clauses
          count := 2
          [0 :id] := "1"
          [1 :id] := "2")

        (testing "it is possible to start with the second patient"
          (given (pull-type-query node "Patient" clauses "2")
            count := 1
            [0 :id] := "2")))))

  (testing "day precision"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :birthDate #fhir/date"2022-12-14"}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :birthDate #fhir/date"2022-12-13"}]]]

      (given-type-query node "Patient" [["birthdate" "lt2022-12-14"]]
        count := 1
        [0 :id] := "1")))

  (testing "as second clause"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :gender #fhir/code"male"
               :birthDate #fhir/date"2022-12-14"}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :gender #fhir/code"male"
               :birthDate #fhir/date"2022-12-13"}]]]

      (given-type-query node "Patient" [["gender" "male"]
                                        ["birthdate" "lt2022-12-14"]]
        count := 1
        [0 :id] := "1"))))

(deftest type-query-date-greater-equal-test
  (testing "year precision"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :birthDate #fhir/date"1990"}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :birthDate #fhir/date"1991"}]
        [:put {:fhir/type :fhir/Patient :id "2"
               :birthDate #fhir/date"1992"}]]]

      (given-type-query node "Patient" [["birthdate" "ge1990"]]
        count := 3
        [0 :id] := "0"
        [1 :id] := "1"
        [2 :id] := "2")

      (testing "it is possible to start with the second patient"
        (given (pull-type-query node "Patient" [["birthdate" "ge1990"]] "1")
          count := 2
          [0 :id] := "1"
          [1 :id] := "2"))))

  (testing "day precision"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :birthDate #fhir/date"2022-12-14"}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :birthDate #fhir/date"2022-12-15"}]]]

      (given-type-query node "Patient" [["birthdate" "ge2022-12-14"]]
        count := 2
        [0 :id] := "0"
        [1 :id] := "1")))

  (testing "as second clause"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :gender #fhir/code"male"
               :birthDate #fhir/date"2022-12-14"}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :gender #fhir/code"male"
               :birthDate #fhir/date"2022-12-15"}]]]

      (given-type-query node "Patient" [["gender" "male"]
                                        ["birthdate" "ge2022-12-14"]]
        count := 2
        [0 :id] := "0"
        [1 :id] := "1"))))

(deftest type-query-date-less-equal-test
  (testing "year precision"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :birthDate #fhir/date"1970"}]]
       [[:put {:fhir/type :fhir/Patient :id "0"
               :birthDate #fhir/date"1990"}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :birthDate #fhir/date"1989"}]
        [:put {:fhir/type :fhir/Patient :id "2"
               :birthDate #fhir/date"1988"}]]]

      (given-type-query node "Patient" [["birthdate" "le1990"]]
        count := 3
        [0 :id] := "2"
        [1 :id] := "1"
        [2 :id] := "0")

      (testing "it is possible to start with the second patient"
        (given (pull-type-query node "Patient" [["birthdate" "le1990"]] "1")
          count := 2
          [0 :id] := "1"
          [1 :id] := "0"))))

  (testing "day precision"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :birthDate #fhir/date"2022-12-14"}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :birthDate #fhir/date"2022-12-13"}]]]

      (given-type-query node "Patient" [["birthdate" "le2022-12-14"]]
        count := 2
        [0 :id] := "1"
        [1 :id] := "0")))

  (testing "as second clause"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :gender #fhir/code"male"
               :birthDate #fhir/date"2022-12-14"}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :gender #fhir/code"male"
               :birthDate #fhir/date"2022-12-13"}]]]

      (given-type-query node "Patient" [["gender" "male"]
                                        ["birthdate" "le2022-12-14"]]
        count := 2
        [0 :id] := "0"
        [1 :id] := "1"))))

(deftest type-query-date-starts-after-test
  (testing "year precision"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :birthDate #fhir/date"1990"}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :birthDate #fhir/date"1991"}]
        [:put {:fhir/type :fhir/Patient :id "2"
               :birthDate #fhir/date"1992"}]]]

      (given-type-query node "Patient" [["birthdate" "sa1990"]]
        count := 2
        [0 :id] := "1"
        [1 :id] := "2")

      (testing "it is possible to start with the second patient"
        (given (pull-type-query node "Patient" [["birthdate" "sa1990"]] "2")
          count := 1
          [0 :id] := "2"))))

  (testing "day precision"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :birthDate #fhir/date"2022-12-14"}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :birthDate #fhir/date"2022-12-15"}]]]

      (given-type-query node "Patient" [["birthdate" "sa2022-12-14"]]
        count := 1
        [0 :id] := "1")))

  (testing "as second clause"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :gender #fhir/code"male"
               :birthDate #fhir/date"2022-12-14"}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :gender #fhir/code"male"
               :birthDate #fhir/date"2022-12-15"}]]]

      (given-type-query node "Patient" [["gender" "male"]
                                        ["birthdate" "sa2022-12-14"]]
        count := 1
        [0 :id] := "1"))))

(deftest type-query-date-ends-before-test
  (testing "year precision"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :birthDate #fhir/date"1970"}]]
       [[:put {:fhir/type :fhir/Patient :id "0"
               :birthDate #fhir/date"1990"}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :birthDate #fhir/date"1989"}]
        [:put {:fhir/type :fhir/Patient :id "2"
               :birthDate #fhir/date"1988"}]]]

      (given-type-query node "Patient" [["birthdate" "eb1990"]]
        count := 2
        [0 :id] := "2"
        [1 :id] := "1")

      (testing "it is possible to start with the second patient"
        (given (pull-type-query node "Patient" [["birthdate" "eb1990"]] "1")
          count := 1
          [0 :id] := "1"))))

  (testing "day precision"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :birthDate #fhir/date"2022-12-14"}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :birthDate #fhir/date"2022-12-13"}]]]

      (given-type-query node "Patient" [["birthdate" "eb2022-12-14"]]
        count := 1
        [0 :id] := "1")))

  (testing "as second clause"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :gender #fhir/code"male"
               :birthDate #fhir/date"2022-12-14"}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :gender #fhir/code"male"
               :birthDate #fhir/date"2022-12-13"}]]]

      (given-type-query node "Patient" [["gender" "male"]
                                        ["birthdate" "eb2022-12-14"]]
        count := 1
        [0 :id] := "1"))))

(deftest type-query-date-encounter-test
  (with-system-data [{:blaze.db/keys [node]} config]
    [[[:put {:fhir/type :fhir/Encounter :id "E1"
             :period #fhir/Period{:start #fhir/dateTime"1999-08"
                                  :end #fhir/dateTime"2000-04"}}]]
     [[:put {:fhir/type :fhir/Encounter :id "E2"
             :period #fhir/Period{:start #fhir/dateTime"2000-03"
                                  :end #fhir/dateTime"2000-10"}}]]
     [[:put {:fhir/type :fhir/Encounter :id "E3"
             :period #fhir/Period{:start #fhir/dateTime"1999-11"
                                  :end #fhir/dateTime"2001-04"}}]]
     [[:put {:fhir/type :fhir/Encounter :id "E4"
             :period #fhir/Period{:start #fhir/dateTime"2000-09"
                                  :end #fhir/dateTime"2001-07"}}]]]

    (let [db (d/db node)
          num-encounter #(count (d/type-query db "Encounter" %))]
      (are [year n] (= n (num-encounter [["date" (format "gt%d-01-01" year)]
                                         ["date" (format "lt%d-01-01" (inc year))]]))
        1999 2
        2000 4
        2001 2)

      (are [year n] (= n (num-encounter [["date" (format "sa%d-01-01" year)]
                                         ["date" (format "eb%d-01-01" (inc year))]]))
        1999 0
        2000 1
        2001 0)

      (are [year n] (= n (num-encounter [["date" (str year)]]))
        1999 0
        2000 1
        2001 0)

      (are [year n] (= n (num-encounter [["date" (str "ap" year)]]))
        1999 2
        2000 4
        2001 2))))

(def encounter-gen
  (let [date-time (fg/dateTime :extension (gen/return nil)
                               :value (gen/fmap (partial apply format "%04d-%02d-%02d")
                                                (gen/tuple (gen/choose 1999 2001) fg/month fg/day)))]
    (fg/encounter
     :id (gen/fmap str gen/uuid)
     :meta (gen/return nil)
     :identifier (gen/return nil)
     :status (gen/return #fhir/code"finished")
     :type (gen/return nil)
     :priority (gen/return nil)
     :subject (gen/return nil)
     :period (fg/period :extension (gen/return nil)
                        :start date-time :end date-time))))

(deftest ^:slow type-query-date-encounter-eq-sa-eb-test
  (log/set-min-level! :warn)

  (satisfies-prop 100
    (prop/for-all [year (gen/choose 1999 2001)
                   tx-ops (create-tx encounter-gen 10)]
      (with-system-data [{:blaze.db/keys [node]} config]
        [tx-ops]

        (let [db (d/db node)
              num-encounter #(count (d/type-query db "Encounter" %))]
          (= (num-encounter [["date" (str year)]])
             (num-encounter [["date" (format "sa%d-12-31" (dec year))]
                             ["date" (format "eb%d-01-01" (inc year))]])))))))

(deftest ^:slow type-query-date-encounter-ap-gt-lt-test
  (log/set-min-level! :warn)

  (satisfies-prop 100
    (prop/for-all [year (gen/choose 1999 2001)
                   tx-ops (create-tx encounter-gen 10)]
      (with-system-data [{:blaze.db/keys [node]} config]
        [tx-ops]

        (let [db (d/db node)
              num-encounter #(count (d/type-query db "Encounter" %))]
          (= (num-encounter [["date" (str "ap" year)]])
             (num-encounter [["date" (format "ge%d-01-01" year)]
                             ["date" (format "lt%d-01-01" (inc year))]])))))))

(defn- range-below [date-time]
  [Long/MIN_VALUE (system/date-time-lower-bound date-time)])

(defn- range-above [date-time]
  [(system/date-time-lower-bound date-time) Long/MAX_VALUE])

(defn- date-time-range [date-time]
  [(system/date-time-lower-bound date-time)
   (system/date-time-upper-bound date-time)])

(defn- fhir-date-time-range [x]
  (condp = (type/type x)
    :fhir/Period
    [(system/date-time-lower-bound (type/value (:start x)))
     (system/date-time-upper-bound (type/value (:end x)))]
    :fhir/dateTime
    [(system/date-time-lower-bound (type/value x))
     (system/date-time-upper-bound (type/value x))]))

(defn- fully-contains? [[x1 x2] [y1 y2]]
  (<= x1 y1 y2 x2))

(defn- equal
  "The range of the parameter value fully contains the range of the resource
  value."
  [param-value]
  (let [param-range (date-time-range param-value)]
    (fn [resource-value]
      (fully-contains? param-range (fhir-date-time-range resource-value)))))

(defn- not-equal
  "The range of the parameter value does not fully contain the range of the
  resource value."
  [param-value]
  (let [param-range (date-time-range param-value)]
    (fn [resource-value]
      (not (fully-contains? param-range (fhir-date-time-range resource-value))))))

(defn- overlaps? [[x1 x2] [y1 y2]]
  (and (<= x1 y2) (<= y1 x2)))

(defn- greater-than
  "The range above the parameter value intersects (i.e. overlaps) with the range
  of the resource value."
  [param-value]
  (let [param-range (range-above param-value)]
    (fn [resource-value]
      (overlaps? param-range (fhir-date-time-range resource-value)))))

(defn- less-than
  "The range below the parameter value intersects (i.e. overlaps) with the range
  of the resource value."
  [param-value]
  (let [param-range (range-below param-value)]
    (fn [resource-value]
      (overlaps? (fhir-date-time-range resource-value) param-range))))

(defn- greater-equal
  "The range above the parameter value intersects (i.e. overlaps) with the range
  of the resource value, or the range of the parameter value fully contains the
  range of the resource value."
  [param-value]
  (let [greater-than (greater-than param-value)
        equal (equal param-value)]
    (fn [resource-value]
      (or (greater-than resource-value)
          (equal resource-value)))))

(defn- less-equal
  "The range below the parameter value intersects (i.e. overlaps) with the range
  of the resource value or the range of the parameter value fully contains the
  range of the resource value."
  [param-value]
  (let [less-than (less-than param-value)
        equal (equal param-value)]
    (fn [resource-value]
      (or (less-than resource-value)
          (equal resource-value)))))

(defn- starts-after
  "The range of the parameter value does not overlap with the range of the
  resource value, and the range above the parameter value contains the range of
  the resource value."
  [param-value]
  (let [param-range (date-time-range param-value)
        param-range-above (range-above param-value)]
    (fn [resource-value]
      (when-let [value-range (fhir-date-time-range resource-value)]
        (and (not (overlaps? param-range value-range))
             (fully-contains? param-range-above value-range))))))

(defn- ends-before
  "The range of the parameter value does not overlap with the range of the
  resource value, and the range below the parameter value contains the range of
  the resource value."
  [param-value]
  (let [param-range (date-time-range param-value)
        param-range-below (range-below param-value)]
    (fn [resource-value]
      (when-let [value-range (fhir-date-time-range resource-value)]
        (and (not (overlaps? param-range value-range))
             (fully-contains? param-range-below value-range))))))

(defn- approximately
  "The range of the parameter value overlaps with the range of the resource
  value."
  [param-value]
  (let [param-range (date-time-range param-value)]
    (fn [resource-value]
      (overlaps? param-range (fhir-date-time-range resource-value)))))

(defn- every-found-observation-matches? [pred node prefix date-time]
  (let [pull (partial pull-type-query node "Observation")
        pred (comp (pred (system/parse-date-time date-time)) :effective)
        observations (pull [["date" (str prefix date-time)]])]
    (and (every? pred observations)
         (or (< (count observations) 2)
             (every? pred (pull [["date" (str prefix date-time)]]
                                (:id (first observations)))))
         (every? pred (pull [["status" "final"]
                             ["date" (str prefix date-time)]])))))

(def ^:private num-date-tests 50)

(def observation-gen
  (fg/observation
   :id (gen/fmap str gen/uuid)
   :meta (gen/return nil)
   :identifier (gen/return nil)
   :status (gen/return #fhir/code"final")
   :category (gen/return nil)
   :code (gen/return nil)
   :subject (gen/return nil)
   :encounter (gen/return nil)
   :value (gen/return nil)))

(deftest ^:slow type-query-date-equal-generative-test
  (log/set-min-level! :warn)

  (satisfies-prop num-date-tests
    (prop/for-all [date-time (fg/dateTime-value)
                   tx-ops (create-tx observation-gen num-date-tests)]
      (with-system-data [{:blaze.db/keys [node]} config]
        [tx-ops]
        (every-found-observation-matches? equal node "" date-time)))))

(deftest ^:slow type-query-date-not-equal-generative-test
  (log/set-min-level! :warn)

  (satisfies-prop num-date-tests
    (prop/for-all [date-time (fg/dateTime-value)
                   tx-ops (create-tx observation-gen num-date-tests)]
      (with-system-data [{:blaze.db/keys [node]} config]
        [tx-ops]
        (every-found-observation-matches? not-equal node "ne" date-time)))))

(deftest ^:slow type-query-date-greater-than-generative-test
  (log/set-min-level! :warn)

  (satisfies-prop num-date-tests
    (prop/for-all [date-time (fg/dateTime-value)
                   tx-ops (create-tx observation-gen num-date-tests)]
      (with-system-data [{:blaze.db/keys [node]} config]
        [tx-ops]
        (every-found-observation-matches? greater-than node "gt" date-time)))))

(deftest ^:slow type-query-date-less-than-generative-test
  (log/set-min-level! :warn)

  (satisfies-prop num-date-tests
    (prop/for-all [date-time (fg/dateTime-value)
                   tx-ops (create-tx observation-gen num-date-tests)]
      (with-system-data [{:blaze.db/keys [node]} config]
        [tx-ops]
        (every-found-observation-matches? less-than node "lt" date-time)))))

(deftest ^:slow type-query-date-greater-equal-generative-test
  (log/set-min-level! :warn)

  (satisfies-prop num-date-tests
    (prop/for-all [date-time (fg/dateTime-value)
                   tx-ops (create-tx observation-gen num-date-tests)]
      (with-system-data [{:blaze.db/keys [node]} config]
        [tx-ops]
        (every-found-observation-matches? greater-equal node "ge" date-time)))))

(deftest ^:slow type-query-date-less-equal-generative-test
  (log/set-min-level! :warn)

  (satisfies-prop num-date-tests
    (prop/for-all [date-time (fg/dateTime-value)
                   tx-ops (create-tx observation-gen num-date-tests)]
      (with-system-data [{:blaze.db/keys [node]} config]
        [tx-ops]
        (every-found-observation-matches? less-equal node "le" date-time)))))

(deftest ^:slow type-query-date-starts-after-generative-test
  (log/set-min-level! :warn)

  (satisfies-prop num-date-tests
    (prop/for-all [date-time (fg/dateTime-value)
                   tx-ops (create-tx observation-gen num-date-tests)]
      (with-system-data [{:blaze.db/keys [node]} config]
        [tx-ops]
        (every-found-observation-matches? starts-after node "sa" date-time)))))

(deftest ^:slow type-query-date-ends-before-generative-test
  (log/set-min-level! :warn)

  (satisfies-prop num-date-tests
    (prop/for-all [date-time (fg/dateTime-value)
                   tx-ops (create-tx observation-gen num-date-tests)]
      (with-system-data [{:blaze.db/keys [node]} config]
        [tx-ops]
        (every-found-observation-matches? ends-before node "eb" date-time)))))

(deftest ^:slow type-query-date-approximately-generative-test
  (log/set-min-level! :warn)

  (satisfies-prop num-date-tests
    (prop/for-all [date-time (fg/dateTime-value)
                   tx-ops (create-tx observation-gen num-date-tests)]
      (with-system-data [{:blaze.db/keys [node]} config]
        [tx-ops]
        (every-found-observation-matches? approximately node "ap" date-time)))))

(deftest type-query-forward-chaining-test
  (testing "Encounter"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Encounter
               :id "0"
               :period #fhir/Period{:start #fhir/dateTime"2016"}
               :diagnosis
               [{:fhir/type :fhir.Encounter/diagnosis
                 :condition
                 #fhir/Reference{:reference "Condition/0"}}
                {:fhir/type :fhir.Encounter/diagnosis
                 :condition
                 #fhir/Reference{:reference "Condition/2"}}]}]
        [:put {:fhir/type :fhir/Encounter
               :id "1"
               :period #fhir/Period{:start #fhir/dateTime"2016"}
               :diagnosis
               [{:fhir/type :fhir.Encounter/diagnosis
                 :condition
                 #fhir/Reference{:reference "Condition/1"}}]}]
        [:put {:fhir/type :fhir/Encounter
               :id "2"
               :period #fhir/Period{:start #fhir/dateTime"2016"}
               :diagnosis
               [{:fhir/type :fhir.Encounter/diagnosis
                 :condition
                 #fhir/Reference{:reference "Condition/0"}}]}]
        [:put {:fhir/type :fhir/Condition
               :id "0"
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding{:code #fhir/code"foo"}]}}]
        [:put {:fhir/type :fhir/Condition
               :id "1"
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding{:code #fhir/code"bar"}]}}]
        [:put {:fhir/type :fhir/Condition
               :id "2"
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding{:code #fhir/code"foo"}]}}]]]

      (testing "Encounter with foo Condition"
        (given-type-query node "Encounter" [["diagnosis:Condition.code" "foo"]]
          count := 2
          [0 :fhir/type] := :fhir/Encounter
          [0 :id] := "0"
          [1 :fhir/type] := :fhir/Encounter
          [1 :id] := "2")

        (testing "it is possible to start with the second Encounter"
          (given (pull-type-query node "Encounter" [["diagnosis:Condition.code" "foo"]] "2")
            count := 1
            [0 :fhir/type] := :fhir/Encounter
            [0 :id] := "2"))

        (testing "as second parameter"
          (given-type-query node "Encounter" [["date" "ge2015-01-01"]
                                              ["diagnosis:Condition.code" "foo"]]
            count := 2
            [0 :fhir/type] := :fhir/Encounter
            [0 :id] := "0"
            [1 :fhir/type] := :fhir/Encounter
            [1 :id] := "2")

          (testing "it is possible to start with the second Encounter"
            (given (pull-type-query node "Encounter" [["date" "ge2015-01-01"]
                                                      ["diagnosis:Condition.code" "foo"]] "2")
              count := 1
              [0 :fhir/type] := :fhir/Encounter
              [0 :id] := "2"))))

      (testing "Encounter with bar Condition"
        (given-type-query node "Encounter" [["diagnosis:Condition.code" "bar"]]
          count := 1
          [0 :fhir/type] := :fhir/Encounter
          [0 :id] := "1")

        (testing "as second parameter"
          (given-type-query node "Encounter" [["date" "ge2015-01-01"]
                                              ["diagnosis:Condition.code" "bar"]]
            count := 1
            [0 :fhir/type] := :fhir/Encounter
            [0 :id] := "1")))

      (testing "search param foo is not found"
        (given (pull-type-query node "Encounter" [["foo.bar" "foo"]])
          ::anom/category := ::anom/not-found
          ::anom/message := "The search-param with code `foo` and type `Encounter` was not found."))

      (testing "search param bar is not found"
        (given (pull-type-query node "Encounter" [["diagnosis:Condition.bar" "foo"]])
          ::anom/category := ::anom/not-found
          ::anom/message := "The search-param with code `bar` and type `Condition` was not found."))

      (testing "diagnosis.code has ambiguous type"
        (given (pull-type-query node "Encounter" [["diagnosis.code" "foo"]])
          ::anom/category := ::anom/incorrect
          ::anom/message := "Ambiguous target types `Condition, Procedure` in the chain `diagnosis.code`. Please use a modifier to constrain the type."))

      (testing "class is not a reference search parameter"
        (given (pull-type-query node "Encounter" [["class.code" "foo"]])
          ::anom/category := ::anom/incorrect
          ::anom/message := "The search parameter with code `class` in the chain `class.code` must be of type reference but has type `token`."))

      (testing "chain of length 3"
        (given (pull-type-query node "Encounter" [["diagnosis.patient.name" "foo"]])
          ::anom/category := ::anom/unsupported
          ::anom/message := "Search parameter chains longer than 2 are currently not supported. Please file an issue."))))

  (testing "Observation"
    (testing "one Patient"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0"
                 :gender #fhir/code"male"}]
          [:put {:fhir/type :fhir/Observation :id "0"
                 :subject #fhir/Reference{:reference "Patient/0"}}]
          [:put {:fhir/type :fhir/Observation :id "1"
                 :subject #fhir/Reference{:reference "Patient/0"}}]]]

        (given-type-query node "Observation" [["patient.gender" "male"]]
          count := 2
          [0 :fhir/type] := :fhir/Observation
          [0 :id] := "0"
          [1 :fhir/type] := :fhir/Observation
          [1 :id] := "1")))

    (testing "two Patients"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0"
                 :gender #fhir/code"male"}]
          [:put {:fhir/type :fhir/Patient :id "1"
                 :gender #fhir/code"male"}]
          [:put {:fhir/type :fhir/Observation :id "0"
                 :subject #fhir/Reference{:reference "Patient/0"}}]
          [:put {:fhir/type :fhir/Observation :id "1"
                 :subject #fhir/Reference{:reference "Patient/0"}}]
          [:put {:fhir/type :fhir/Observation :id "2"
                 :subject #fhir/Reference{:reference "Patient/1"}}]
          [:put {:fhir/type :fhir/Observation :id "3"
                 :subject #fhir/Reference{:reference "Patient/1"}}]]]

        (given-type-query node "Observation" [["patient.gender" "male"]]
          count := 4
          [0 :fhir/type] := :fhir/Observation
          [0 :id] := "0"
          [1 :fhir/type] := :fhir/Observation
          [1 :id] := "1"
          [2 :fhir/type] := :fhir/Observation
          [2 :id] := "2"
          [3 :fhir/type] := :fhir/Observation
          [3 :id] := "3")))

    (testing "three Patients"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0"
                 :gender #fhir/code"male"}]
          [:put {:fhir/type :fhir/Patient :id "1"
                 :gender #fhir/code"male"}]
          [:put {:fhir/type :fhir/Patient :id "2"
                 :gender #fhir/code"male"}]
          [:put {:fhir/type :fhir/Observation :id "0"
                 :subject #fhir/Reference{:reference "Patient/0"}}]
          [:put {:fhir/type :fhir/Observation :id "1"
                 :subject #fhir/Reference{:reference "Patient/0"}}]
          [:put {:fhir/type :fhir/Observation :id "2"
                 :subject #fhir/Reference{:reference "Patient/1"}}]
          [:put {:fhir/type :fhir/Observation :id "3"
                 :subject #fhir/Reference{:reference "Patient/1"}}]
          [:put {:fhir/type :fhir/Observation :id "4"
                 :subject #fhir/Reference{:reference "Patient/2"}}]
          [:put {:fhir/type :fhir/Observation :id "5"
                 :subject #fhir/Reference{:reference "Patient/2"}}]]]

        (given-type-query node "Observation" [["patient.gender" "male"]]
          count := 6
          [0 :fhir/type] := :fhir/Observation
          [0 :id] := "0"
          [1 :fhir/type] := :fhir/Observation
          [1 :id] := "1"
          [2 :fhir/type] := :fhir/Observation
          [2 :id] := "2"
          [3 :fhir/type] := :fhir/Observation
          [3 :id] := "3"
          [4 :fhir/type] := :fhir/Observation
          [4 :id] := "4"
          [5 :fhir/type] := :fhir/Observation
          [5 :id] := "5"))))

  (testing "DocumentReference"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/DocumentReference :id "111430"
               :identifier [#fhir/Identifier
                             {:system #fhir/uri"system-111302"
                              :value #fhir/string"value-111304"}]}]
        [:put {:fhir/type :fhir/DocumentReference :id "105551"
               :identifier [#fhir/Identifier
                             {:system #fhir/uri"system-111302"
                              :value #fhir/string"value-111304"}]
               :author [#fhir/Reference{:reference "Organization/105545"}]}]
        [:put {:fhir/type :fhir/DocumentReference :id "111917"
               :identifier [#fhir/Identifier
                             {:system #fhir/uri"system-111302"
                              :value #fhir/string"value-111304"}]
               :author [#fhir/Reference{:reference "Organization/111026"}]}]
        [:put {:fhir/type :fhir/DocumentReference :id "111020"
               :author [#fhir/Reference{:reference "Organization/111026"}]}]
        [:put {:fhir/type :fhir/DocumentReference :id "111206"
               :author [#fhir/Reference{:reference "Patient/111115"}]}]
        [:put {:fhir/type :fhir/Patient :id "111115"
               :identifier [#fhir/Identifier
                             {:system #fhir/uri"system-105539"
                              :value #fhir/string"value-105542"}]}]
        [:put {:fhir/type :fhir/Organization :id "105545"
               :identifier [#fhir/Identifier
                             {:system #fhir/uri"system-105539"
                              :value #fhir/string"value-105542"}]}]
        [:put {:fhir/type :fhir/Organization :id "111026"
               :identifier [#fhir/Identifier
                             {:system #fhir/uri"system-105539"
                              :value #fhir/string"value-111043"}]}]]]

      (given-type-query node "DocumentReference" [["author:Organization.identifier" "system-105539|value-105542"]]
        count := 1
        [0 :fhir/type] := :fhir/DocumentReference
        [0 :id] := "105551")

      (testing "as second clause"
        (given-type-query node "DocumentReference" [["identifier" "system-111302|value-111304"]
                                                    ["author:Organization.identifier" "system-105539|value-105542"]]
          count := 1
          [0 :fhir/type] := :fhir/DocumentReference
          [0 :id] := "105551")))))

(defn- patient-w-identifier [i]
  {:fhir/type :fhir/Patient :id (str i)
   :identifier [(type/identifier {:value (str i)})]})

(deftest type-query-identifier-non-matching-test
  (st/unstrument)
  (log/set-min-level! :info)
  (testing "doesn't return non-matching resources"
    (let [test-size 200000]
      (with-system-data [{:blaze.db/keys [node]} config]
        [(mapv (fn [i] [:create (patient-w-identifier i)]) (range test-size))]

        (let [db (d/db node)]
          (is (every?
               #(= 1 @(d/count-query db (d/compile-type-query node "Patient" [["identifier" (str %)]])))
               (range test-size)))))))
  (log/set-min-level! :trace))

(deftest type-query-identifier-test
  (testing "works with non-unique identifiers"
    (doseq [system [nil #fhir/uri"foo"]]
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0"
                 :active true
                 :identifier
                 [(type/identifier {:system system :value "0"})]}]
          [:put {:fhir/type :fhir/Patient :id "1"
                 :active true
                 :identifier
                 [(type/identifier {:system system :value "0"})]}]
          [:put {:fhir/type :fhir/Patient :id "2"
                 :active true
                 :identifier
                 [(type/identifier {:system system :value "0"})]}]]]

        (doseq [value (if system ["0" "foo|0"] ["0" "|0"])]
          (given-type-query node "Patient" [["identifier" value]]
            count := 3
            [0 :id] := "0"
            [1 :id] := "1"
            [2 :id] := "2")

          (testing "it is possible to start with the second patient"
            (given (pull-type-query node "Patient" [["identifier" value]] "1")
              count := 2
              [0 :id] := "1"
              [1 :id] := "2"))

          (testing "as second clause"
            (given-type-query node "Patient" [["active" "true"]
                                              ["identifier" value]]
              count := 3
              [0 :id] := "0"
              [1 :id] := "1"
              [2 :id] := "2"))))))

  (testing "system search"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :active true
               :identifier
               [#fhir/Identifier{:system #fhir/uri"system-115849"}]}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :active true
               :identifier
               [#fhir/Identifier{:system #fhir/uri"system-115849"}]}]]]

      (given-type-query node "Patient" [["identifier" "system-115849|"]]
        count := 2
        [0 :id] := "0"
        [1 :id] := "1")

      (testing "it is possible to start with the second patient"
        (given (pull-type-query node "Patient" [["identifier" "system-115849|"]] "1")
          count := 1
          [0 :id] := "1"))

      (testing "as second clause"
        (given-type-query node "Patient" [["active" "true"] ["identifier" "system-115849|"]]
          count := 2
          [0 :id] := "0"
          [1 :id] := "1"))))

  (testing "doesn't find the Observation with identifier with hash collision"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Observation :id "0"
               :identifier
               [#fhir/Identifier{:value "2404351199702_20240422094702_DELTA-HE"}]}]]]

      (is (empty? (pull-type-query node "Observation" [["identifier" "2410301332030_20241009113701_FDP-D"]]))))

    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Observation :id "0"
               :identifier
               [#fhir/Identifier{:value "2404351199702_20240422094702_DELTA-HE"}]}]]
       [[:put {:fhir/type :fhir/Observation :id "1"
               :identifier
               [#fhir/Identifier{:value "2404351199702_20240422094702_DELTA-HE"}]}]]]

      (is (empty? (pull-type-query node "Observation" [["identifier" "2410301332030_20241009113701_FDP-D"]] "1"))))

    (testing "as second clause"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Observation :id "0"
                 :status #fhir/code"final"
                 :identifier
                 [#fhir/Identifier{:value "2404351199702_20240422094702_DELTA-HE"}]}]]]

        (is (empty? (pull-type-query node "Observation" [["status" "final"]
                                                         ["identifier" "2410301332030_20241009113701_FDP-D"]]))))))

  (testing "finding multiple patients"
    (with-system-data [{:blaze.db/keys [node]} config]
      (for [id (range 10)]
        [[:put {:fhir/type :fhir/Patient :id (str id)
                :identifier [(type/map->Identifier {:value (format "pat-%d" id)})]}]])

      (given-type-query node "Patient" [["identifier" "pat-2" "pat-4" "pat-8"]]
        count := 3
        [0 :id] := "2"
        [1 :id] := "4"
        [2 :id] := "8"))))

(deftest type-query-tag-test
  (with-system-data [{:blaze.db/keys [node]} config]
    [[[:put {:fhir/type :fhir/Patient :id "0"
             :meta #fhir/Meta{:tag [#fhir/Coding{:code #fhir/code"code-084033"}]}}]
      [:put {:fhir/type :fhir/Patient :id "1"}]
      [:put {:fhir/type :fhir/Patient :id "2"
             :meta #fhir/Meta{:tag [#fhir/Coding{:code #fhir/code"code-084517"}]}}]]]

    (given-type-query node "Patient" [["_tag" "code-084033"]]
      count := 1
      [0 :id] := "0")

    (given-type-query node "Patient" [["_tag" "code-084517"]]
      count := 1
      [0 :id] := "2")))

(defn- patient-type-query? [x]
  (instance? PatientTypeQuery x))

(deftest compile-type-query-test
  (testing "a node with one patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0" :active true}]]]

      (testing "the patient can be found"
        (with-open-db [db node]
          (doseq [target [node db]]
            (given @(->> (d/compile-type-query target "Patient" [["active" "true"]])
                         (d/execute-query db)
                         (d/pull-many target))
              count := 1
              [0 :fhir/type] := :fhir/Patient
              [0 :id] := "0"))))

      (testing "sort clause comes first"
        (doseq [target [node (d/db node)]]
          (given (-> (d/compile-type-query target "Patient" [[:sort "_id" :asc]
                                                             ["active" "true"]])
                     (d/query-clauses))
            count := 2
            [0] := [:sort "_id" :asc]
            [1] := ["active" "true"])))

      (testing "PatientTypeQuery"
        (testing "patient/subject and code"
          (doseq [target [node (d/db node)]
                  type ["Condition" "DiagnosticReport" "MedicationAdministration"
                        "MedicationRequest" "MedicationStatement" "Observation"
                        "Procedure"]
                  subjects [["Patient/0" "Patient/1"] ["0" "1"]]]

            (doseq [value ["system|code" "code" "|code"]
                    clauses [[["code" value]
                              (into ["patient"] subjects)]
                             [(into ["patient"] subjects)
                              ["code" value]]]]
              (is (patient-type-query? (d/compile-type-query target type clauses))))))

        (testing "patient and category"
          (doseq [target [node (d/db node)]
                  type ["Consent"]
                  subjects [["Patient/0" "Patient/1"] ["0" "1"]]]

            (let [clauses [["category" "system|code"]
                           (into ["patient"] subjects)]]
              (is (patient-type-query? (d/compile-type-query target type clauses))))))

        (testing "patient and class"
          (doseq [target [node (d/db node)]
                  type ["Encounter"]
                  subjects [["Patient/0" "Patient/1"] ["0" "1"]]]

            (let [clauses [["class" "system|code"]
                           (into ["patient"] subjects)]]
              (is (patient-type-query? (d/compile-type-query target type clauses))))))

        (testing "patient and modality"
          (doseq [target [node (d/db node)]
                  type ["ImagingStudy"]
                  subjects [["Patient/0" "Patient/1"] ["0" "1"]]]

            (let [clauses [["modality" "system|code"]
                           (into ["patient"] subjects)]]
              (is (patient-type-query? (d/compile-type-query target type clauses))))))

        (testing "patient and type"
          (doseq [target [node (d/db node)]
                  type ["Specimen"]
                  subjects [["Patient/0" "Patient/1"] ["0" "1"]]]

            (let [clauses [["type" "system|code"]
                           (into ["patient"] subjects)]]
              (is (patient-type-query? (d/compile-type-query target type clauses))))))

        (testing "patient and status"
          (doseq [target [node (d/db node)]
                  type ["Account"]
                  subjects [["Patient/0" "Patient/1"] ["0" "1"]]]

            (let [clauses [["type" "system|code"]
                           (into ["patient"] subjects)]]
              (is (patient-type-query? (d/compile-type-query target type clauses))))))

        (testing "subject and code"
          (doseq [target [node (d/db node)]
                  type ["Condition" "DiagnosticReport" "MedicationAdministration"
                        "MedicationRequest" "MedicationStatement" "Observation"
                        "Procedure"]]

            (let [clauses [["code" "system|code"]
                           ["subject" "Patient/0" "Patient/1"]]]
              (is (patient-type-query? (d/compile-type-query target type clauses))))))

        (testing "subject and class"
          (doseq [target [node (d/db node)]
                  type ["Encounter"]]

            (let [clauses [["class" "system|code"]
                           ["subject" "Patient/0" "Patient/1"]]]
              (is (patient-type-query? (d/compile-type-query target type clauses))))))

        (testing "subject and category"
          (doseq [target [node (d/db node)]
                  type ["AdverseEvent"]]

            (let [clauses [["category" "system|code"]
                           ["subject" "Patient/0" "Patient/1"]]]
              (is (patient-type-query? (d/compile-type-query target type clauses))))))

        (testing "subject and modality"
          (doseq [target [node (d/db node)]
                  type ["ImagingStudy"]]

            (let [clauses [["modality" "system|code"]
                           ["subject" "Patient/0" "Patient/1"]]]
              (is (patient-type-query? (d/compile-type-query target type clauses))))))

        (testing "subject and type"
          (doseq [target [node (d/db node)]
                  type ["Specimen"]]

            (let [clauses [["type" "system|code"]
                           ["subject" "Patient/0" "Patient/1"]]]
              (is (patient-type-query? (d/compile-type-query target type clauses)))))))

      (testing "an unknown search-param errors"
        (doseq [target [node (d/db node)]]
          (given (d/compile-type-query target "Patient" [["foo" "bar"]
                                                         ["active" "true"]])
            ::anom/category := ::anom/not-found
            ::anom/message := "The search-param with code `foo` and type `Patient` was not found.")))

      (testing "invalid date"
        (doseq [target [node (d/db node)]]
          (given (d/compile-type-query target "Patient" [["birthdate" "invalid"]])
            ::anom/category := ::anom/incorrect
            ::anom/message := "Invalid date-time value `invalid` in search parameter `birthdate`."))))))

(deftest compile-type-query-lenient-test
  (with-system-data [{:blaze.db/keys [node]} config]
    [[[:put {:fhir/type :fhir/Patient :id "0" :active true}]
      [:put {:fhir/type :fhir/Patient :id "1"}]]]

    (testing "the patient can be found"
      (with-open-db [db node]
        (doseq [target [node db]]
          (given @(->> (d/compile-type-query-lenient target "Patient" [["active" "true"]])
                       (d/execute-query db)
                       (d/pull-many target))
            count := 1
            [0 :fhir/type] := :fhir/Patient
            [0 :id] := "0"))))

    (testing "an unknown search-param is ignored"
      (with-open-db [db node]
        (doseq [target [node db]]
          (given @(->> (d/compile-type-query-lenient target "Patient" [["foo" "bar"] ["active" "true"]])
                       (d/execute-query db)
                       (d/pull-many target))
            count := 1
            [0 :fhir/type] := :fhir/Patient
            [0 :id] := "0")))

      (testing "one unknown search parameter will result in an empty query"
        (with-open-db [db node]
          (doseq [target [node db]]
            (let [clauses [["foo" "bar"]]
                  query (d/compile-type-query-lenient target "Patient" clauses)]
              (testing "all patients are found"
                (given @(d/pull-many target (d/execute-query db query))
                  count := 2
                  [0 :id] := "0"
                  [1 :id] := "1"))

              (testing "it is possible to start with the second patient"
                (given @(d/pull-many target (d/execute-query db query "1"))
                  count := 1
                  [0 :id] := "1"))

              (testing "the query clauses are empty"
                (is (empty? (d/query-clauses query))))

              (testing "explain query"
                (given (d/explain-query db query)
                  :scan-type := nil
                  [:scan-clauses count] := 0
                  [:seek-clauses count] := 0))

              (testing "count query"
                (is (= 2 @(d/count-query db query)))))))))

    (testing "invalid date"
      (given (d/compile-type-query-lenient node "Patient" [["birthdate" "invalid"]])
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid date-time value `invalid` in search parameter `birthdate`."))

    (testing "sort parameter"
      (let [query (d/compile-type-query-lenient
                   node "Patient" [[:sort "_lastUpdated" :asc]])]
        (is (= [[:sort "_lastUpdated" :asc]] (d/query-clauses query)))))))

(deftest ^:slow count-query-test
  (log/set-min-level! :warn)

  (testing "different sizes"
    (satisfies-prop 25
      (prop/for-all [n (gen/large-integer* {:min 1 :max 10000})]
        (with-system-data [{:blaze.db/keys [node]} config]
          [(mapv
            (fn [id] [:put {:fhir/type :fhir/Patient :id (str id) :active true}])
            (range n))]

          (= n (count-type-query node "Patient" [["active" "true"]])))))))

(deftest compile-type-matcher-test
  (testing "token search params"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :active true :gender #fhir/code"male"}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :gender #fhir/code"male"}]
        [:put {:fhir/type :fhir/Patient :id "2"
               :active true :gender #fhir/code"female"}]
        [:put {:fhir/type :fhir/Patient :id "3"
               :gender #fhir/code"female"}]]]

      (testing "one clause"
        (with-open-db [db node]
          (doseq [target [node db]]
            (let [matcher (d/compile-type-matcher target "Patient" [["active" "true"]])
                  xform (d/matcher-transducer db matcher)]
              (given (into [] xform (d/type-list db "Patient"))
                count := 2
                [0 :fhir/type] := :fhir/Patient
                [0 :id] := "0"
                [1 :fhir/type] := :fhir/Patient
                [1 :id] := "2"))

            (let [matcher (d/compile-type-matcher target "Patient" [["gender" "male"]])
                  xform (d/matcher-transducer db matcher)]
              (given (into [] xform (d/type-list db "Patient"))
                count := 2
                [0 :fhir/type] := :fhir/Patient
                [0 :id] := "0"
                [1 :fhir/type] := :fhir/Patient
                [1 :id] := "1")))))

      (testing "two clauses"
        (with-open-db [db node]
          (doseq [target [node db]]
            (doseq [clauses [[["active" "true"] ["gender" "male"]]
                             [["gender" "male"] ["active" "true"]]]]
              (let [matcher (d/compile-type-matcher target "Patient" clauses)
                    xform (d/matcher-transducer db matcher)]
                (given (into [] xform (d/type-list db "Patient"))
                  count := 1
                  [0 :fhir/type] := :fhir/Patient
                  [0 :id] := "0")))

            (doseq [clauses [[["active" "true"] ["gender" "female"]]
                             [["gender" "female"] ["active" "true"]]]]
              (let [matcher (d/compile-type-matcher target "Patient" clauses)
                    xform (d/matcher-transducer db matcher)]
                (given (into [] xform (d/type-list db "Patient"))
                  count := 1
                  [0 :fhir/type] := :fhir/Patient
                  [0 :id] := "2"))))))

      (testing "clauses can be read back"
        (given (-> (d/compile-type-matcher node "Patient" [["active" "true"] ["gender" "female"]])
                   (d/matcher-clauses))
          count := 2
          [0] := ["active" "true"]
          [1] := ["gender" "female"]))

      (testing "an unknown search-param errors"
        (with-open-db [db node]
          (doseq [target [node db]]
            (given (d/compile-type-matcher target "Patient" [["foo" "bar"]])
              ::anom/category := ::anom/not-found
              ::anom/message := "The search-param with code `foo` and type `Patient` was not found."))))

      (testing "invalid date"
        (with-open-db [db node]
          (doseq [target [node db]]
            (given (d/compile-type-matcher target "Patient" [["birthdate" "invalid"]])
              ::anom/category := ::anom/incorrect
              ::anom/message := "Invalid date-time value `invalid` in search parameter `birthdate`."))))))

  (testing "token identifier search param"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :identifier [#fhir/Identifier{:value "foo"}]}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :identifier [#fhir/Identifier{:value "bar"}]}]]]

      (with-open-db [db node]
        (doseq [target [node db]
                [value id] [["foo" "0"]
                            ["bar" "1"]]]
          (let [matcher (d/compile-type-matcher target "Patient" [["identifier" value]])
                xform (d/matcher-transducer db matcher)]
            (given (into [] xform (d/type-list db "Patient"))
              count := 1
              [0 :fhir/type] := :fhir/Patient
              [0 :id] := id))))))

  (testing "date search param"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :birthDate #fhir/date"2025"}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :birthDate #fhir/date"2023"}]]]

      (with-open-db [db node]
        (doseq [target [node db]
                [value id] [["2025" "0"]
                            ["ne2025" "1"]
                            ["lt2025" "1"]
                            ["le2024" "1"]
                            ["gt2023" "0"]
                            ["ge2024" "0"]
                            ["sa2023" "0"]
                            ["eb2025" "1"]
                            ["ap2025" "0"]]]
          (let [matcher (d/compile-type-matcher target "Patient" [["birthdate" value]])
                xform (d/matcher-transducer db matcher)]
            (given (into [] xform (d/type-list db "Patient"))
              count := 1
              [0 :fhir/type] := :fhir/Patient
              [0 :id] := id))))))

  (testing "quantity search param"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Observation :id "0"
               :value
               #fhir/Quantity
                {:code #fhir/code"kg/m2"
                 :unit #fhir/string"kg/m²"
                 :system #fhir/uri"http://unitsofmeasure.org"
                 :value 42M}}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :value
               #fhir/Quantity
                {:code #fhir/code"kg/m2"
                 :unit #fhir/string"kg/m²"
                 :system #fhir/uri"http://unitsofmeasure.org"
                 :value 23M}}]]]

      (with-open-db [db node]
        (doseq [target [node db]
                [value id] [["23" "1"]
                            ["gt23" "0"]
                            ["lt42" "1"]]]
          (let [matcher (d/compile-type-matcher target "Observation" [["value-quantity" value]])
                xform (d/matcher-transducer db matcher)]
            (given (into [] xform (d/type-list db "Observation"))
              count := 1
              [0 :fhir/type] := :fhir/Observation
              [0 :id] := id))))))

  (testing "chained search param"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :gender #fhir/code"male"}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :gender #fhir/code"female"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :subject #fhir/Reference{:reference "Patient/1"}}]]]

      (with-open-db [db node]
        (doseq [target [node db]]
          (let [matcher (d/compile-type-matcher target "Observation" [["patient.gender" "male"]])
                xform (d/matcher-transducer db matcher)]
            (given (into [] xform (d/type-list db "Observation"))
              count := 1
              [0 :fhir/type] := :fhir/Observation
              [0 :id] := "0")))))))

(deftest compile-system-matcher-test
  (with-system-data [{:blaze.db/keys [node]} config]
    [[[:put {:fhir/type :fhir/Patient :id "0"
             :meta (type/map->Meta {:tag [read-only-tag]})}]
      [:put {:fhir/type :fhir/Patient :id "1"}]]]

    (with-open-db [db node]
      (doseq [target [node db]]
        (let [matcher (d/compile-system-matcher target [["_tag" "https://samply.github.io/blaze/fhir/CodeSystem/AccessControl|read-only"]])
              xform (d/matcher-transducer db matcher)]
          (given (into [] xform (d/type-list db "Patient"))
            count := 1
            [0 :fhir/type] := :fhir/Patient
            [0 :id] := "0"))))

    (testing "clauses can be read back"
      (given (-> (d/compile-system-matcher node [["_tag" "https://samply.github.io/blaze/fhir/CodeSystem/AccessControl|read-only"]])
                 (d/matcher-clauses))
        count := 1
        [0] := ["_tag" "https://samply.github.io/blaze/fhir/CodeSystem/AccessControl|read-only"]))

    (testing "an unknown search-param errors"
      (with-open-db [db node]
        (doseq [target [node db]]
          (given (d/compile-system-matcher target [["foo" "bar"]])
            ::anom/category := ::anom/not-found
            ::anom/message := "The search-param with code `foo` and type `Resource` was not found."))))))

;; ---- System-Level Functions ------------------------------------------------

(deftest system-list-and-total-test
  (testing "a new node has no resources"
    (with-system [{:blaze.db/keys [node]} config]
      (is (coll/empty? (d/system-list (d/db node))))
      (is (zero? (d/system-total (d/db node))))))

  (testing "a node with one patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (testing "has one list entry"
        (is (= 1 (d/system-total (d/db node)))))

      (testing "contains that patient"
        (given @(d/pull-many node (d/system-list (d/db node)))
          count := 1
          [0 :fhir/type] := :fhir/Patient
          [0 :id] := "0"
          [0 :meta :versionId] := #fhir/id"1"))))

  (testing "a node with one deleted patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:delete "Patient" "0"]]]

      (testing "doesn't contain it in the list"
        (is (coll/empty? (d/system-list (d/db node))))
        (is (zero? (d/system-total (d/db node)))))))

  (testing "a node with two resources in two transactions"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:put {:fhir/type :fhir/Observation :id "0"}]]]

      (testing "has two list entries"
        (is (= 2 (d/system-total (d/db node)))))

      (testing "contains both resources in the order of their type hashes"
        (given @(d/pull-many node (d/system-list (d/db node)))
          count := 2
          [0 :fhir/type] := :fhir/Observation
          [0 :id] := "0"
          [0 :meta :versionId] := #fhir/id"2"
          [1 :fhir/type] := :fhir/Patient
          [1 :id] := "0"
          [1 :meta :versionId] := #fhir/id"1"))

      (testing "it is possible to start with the patient"
        (given @(d/pull-many node (d/system-list (d/db node) "Patient" "0"))
          count := 1
          [0 :fhir/type] := :fhir/Patient
          [0 :id] := "0"
          [0 :meta :versionId] := #fhir/id"1"))

      (testing "starting with Measure also returns the patient,
                because in type hash order, Measure comes before
                Patient but after Observation"
        (given @(d/pull-many node (d/system-list (d/db node) "Measure" "0"))
          count := 1
          [0 :fhir/type] := :fhir/Patient
          [0 :id] := "0"
          [0 :meta :versionId] := #fhir/id"1"))

      (testing "overshooting the start-id returns an empty collection"
        (is (coll/empty? (d/system-list (d/db node) "Patient" "1")))))))

(deftest system-query-test
  (testing "a new node has no resources"
    (with-system [{:blaze.db/keys [node]} config]
      (with-open-db [db node]
        (is (coll/empty? (d/system-query db [["_id" "0"]])))

        (testing "an unknown search-param errors"
          (given (d/system-query db [["foo" "bar"]])
            ::anom/category := ::anom/not-found
            ::anom/message := "The search-param with code `foo` and type `Resource` was not found."))))))

(deftest compile-system-query-test
  (with-system [{:blaze.db/keys [node]} config]

    (testing "an unknown search-param errors"
      (doseq [target [node (d/db node)]]
        (given (d/compile-system-query target [["foo" "bar"]])
          ::anom/category := ::anom/not-found
          ::anom/message := "The search-param with code `foo` and type `Resource` was not found.")))))

;; ---- Compartment-Level Functions -------------------------------------------

(defn- pull-compartment-resources
  ([node code id type]
   (->> (d/list-compartment-resource-handles (d/db node) code id type)
        (d/pull-many node)))
  ([node code id type start-id]
   (->> (d/list-compartment-resource-handles (d/db node) code id type start-id)
        (d/pull-many node))))

(deftest list-compartment-resources-test
  (testing "a new node has an empty list of resources in the Patient/0 compartment"
    (with-system [{:blaze.db/keys [node]} config]
      (is (coll/empty? (d/list-compartment-resource-handles
                        (d/db node) "Patient" "0" "Observation")))))

  (testing "a node contains one Observation in the Patient/0 compartment"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}}]]]

      (given @(pull-compartment-resources node "Patient" "0" "Observation")
        count := 1
        [0 :fhir/type] := :fhir/Observation
        [0 :id] := "0"
        [0 :meta :versionId] := #fhir/id"2")))

  (testing "a node contains two resources in the Patient/0 compartment"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}}]]
       [[:put {:fhir/type :fhir/Observation :id "1"
               :subject #fhir/Reference{:reference "Patient/0"}}]]]

      (given @(pull-compartment-resources node "Patient" "0" "Observation")
        count := 2
        [0 :fhir/type] := :fhir/Observation
        [0 :id] := "0"
        [0 :meta :versionId] := #fhir/id"2"
        [1 :fhir/type] := :fhir/Observation
        [1 :id] := "1"
        [1 :meta :versionId] := #fhir/id"3")))

  (testing "a deleted resource does not show up"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}}]]
       [[:delete "Observation" "0"]]]

      (is (coll/empty? (d/list-compartment-resource-handles
                        (d/db node) "Patient" "0" "Observation")))))

  (testing "it is possible to start at a later id"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}}]]
       [[:put {:fhir/type :fhir/Observation :id "1"
               :subject #fhir/Reference{:reference "Patient/0"}}]]
       [[:put {:fhir/type :fhir/Observation :id "2"
               :subject #fhir/Reference{:reference "Patient/0"}}]]]

      (given @(pull-compartment-resources node "Patient" "0" "Observation" "1")
        count := 2
        [0 :fhir/type] := :fhir/Observation
        [0 :id] := "1"
        [0 :meta :versionId] := #fhir/id"3"
        [1 :fhir/type] := :fhir/Observation
        [1 :id] := "2"
        [1 :meta :versionId] := #fhir/id"4")))

  (testing "Unknown compartment is not a problem"
    (with-system [{:blaze.db/keys [node]} config]
      (is (coll/empty? (d/list-compartment-resource-handles
                        (d/db node) "foo" "bar" "Condition"))))))

(defn- explain-compartment-query [node code type clauses]
  (when-ok [query (d/compile-compartment-query node code type clauses)]
    (d/explain-query (d/db node) query)))

(defn- pull-compartment-query [node code id type clauses]
  (when-ok [handles (d/compartment-query (d/db node) code id type clauses)]
    @(d/pull-many node handles)))

(deftest compartment-query-test
  (testing "a new node has an empty list of resources in the Patient/0 compartment"
    (with-system [{:blaze.db/keys [node]} config]
      (let [clauses [["code" "foo"]]]
        (is (coll/empty? (d/compartment-query (d/db node) "Patient" "0" "Observation" clauses)))

        (given (explain-compartment-query node "Patient" "Observation" clauses)
          :query-type := :compartment
          :scan-type := nil
          :scan-clauses := nil
          [:seek-clauses count] := 1
          [:seek-clauses 0 :code] := "code"))))

  (testing "returns the Observation in the Patient/0 compartment"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri"system-191514"
                    :code #fhir/code"code-191518"}]}}]]]

      (let [clauses [["code" "system-191514|code-191518"]]]
        (given (pull-compartment-query node "Patient" "0" "Observation" clauses)
          count := 1
          [0 :fhir/type] := :fhir/Observation
          [0 :id] := "0")

        (given (explain-compartment-query node "Patient" "Observation" clauses)
          :query-type := :compartment
          :scan-type := :ordered
          [:scan-clauses count] := 1
          [:scan-clauses 0 :code] := "code"
          [:seek-clauses count] := 0))))

  (testing "returns only the matching Observation in the Patient/0 compartment"
    (let [observation
          (fn [id code]
            {:fhir/type :fhir/Observation :id id
             :subject #fhir/Reference{:reference "Patient/0"}
             :code
             (type/codeable-concept
              {:coding
               [(type/coding
                 {:system #fhir/uri"system"
                  :code code})]})})]
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put (observation "0" #fhir/code"code-1")]
          [:put (observation "1" #fhir/code"code-2")]
          [:put (observation "2" #fhir/code"code-3")]]]

        (given (pull-compartment-query
                node "Patient" "0" "Observation"
                [["code" "system|code-2"]])
          count := 1
          [0 :fhir/type] := :fhir/Observation
          [0 :id] := "1"))))

  (testing "returns only the matching versions"
    (let [observation
          (fn [id code]
            {:fhir/type :fhir/Observation :id id
             :subject #fhir/Reference{:reference "Patient/0"}
             :code
             (type/codeable-concept
              {:coding
               [(type/coding
                 {:system #fhir/uri"system"
                  :code code})]})})]
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put (observation "0" #fhir/code"code-1")]
          [:put (observation "1" #fhir/code"code-2")]
          [:put (observation "2" #fhir/code"code-2")]
          [:put (observation "3" #fhir/code"code-2")]]
         [[:put (observation "0" #fhir/code"code-2")]
          [:put (observation "1" #fhir/code"code-1")]
          [:put (observation "3" #fhir/code"code-2")]]]

        (given (pull-compartment-query
                node "Patient" "0" "Observation"
                [["code" "system|code-2"]])
          count := 3
          [0 :fhir/type] := :fhir/Observation
          [0 :id] := "0"
          [0 :meta :versionId] := #fhir/id"2"
          [1 :fhir/type] := :fhir/Observation
          [1 :id] := "2"
          [1 :meta :versionId] := #fhir/id"1"
          [2 :id] := "3"
          [2 :meta :versionId] := #fhir/id"1"))))

  (testing "doesn't return deleted resources"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri"system"
                    :code #fhir/code"code"}]}}]]
       [[:delete "Observation" "0"]]]

      (is (coll/empty? (d/compartment-query
                        (d/db node) "Patient" "0" "Observation"
                        [["code" "system|code"]])))))

  (testing "finds resources after deleted ones"
    (let [observation
          (fn [id code]
            {:fhir/type :fhir/Observation :id id
             :subject #fhir/Reference{:reference "Patient/0"}
             :code
             (type/codeable-concept
              {:coding
               [(type/coding
                 {:system #fhir/uri"system"
                  :code code})]})})]
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put (observation "0" #fhir/code"code")]
          [:put (observation "1" #fhir/code"code")]]
         [[:delete "Observation" "0"]]]

        (given (pull-compartment-query
                node "Patient" "0" "Observation"
                [["code" "system|code"]])
          count := 1
          [0 :fhir/type] := :fhir/Observation
          [0 :id] := "1"))))

  (testing "returns the Observation in the Patient/0 compartment on the second criteria value"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri"system-191514"
                    :code #fhir/code"code-191518"}]}}]]]

      (given (pull-compartment-query
              node "Patient" "0" "Observation"
              [["code" "foo|bar" "system-191514|code-191518"]])
        count := 1
        [0 :fhir/type] := :fhir/Observation
        [0 :id] := "0")))

  (testing "with one patient and two observations"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :category
               [#fhir/CodeableConcept
                 {:coding
                  [#fhir/Coding
                    {:system #fhir/uri"system-182245"
                     :code #fhir/code"code-182257"}]}]
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri"system-191514"
                    :code #fhir/code"code-191518"}]}
               :subject #fhir/Reference{:reference "Patient/0"}
               :value
               #fhir/Quantity
                {:code #fhir/code"kg/m2"
                 :unit #fhir/string"kg/m²"
                 :system #fhir/uri"http://unitsofmeasure.org"
                 :value 42M}}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :category
               [#fhir/CodeableConcept
                 {:coding
                  [#fhir/Coding
                    {:system #fhir/uri"system-182245"
                     :code #fhir/code"code-182257"}]}]
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri"system-191514"
                    :code #fhir/code"code-191518"}]}
               :subject #fhir/Reference{:reference "Patient/0"}}]]]

      (testing "matches code and category criteria"
        (let [clauses [["code" "system-191514|code-191518"]
                       ["category" "system-182245|code-182257"]]]
          (given (pull-compartment-query
                  node "Patient" "0" "Observation"
                  clauses)
            count := 2
            [0 :fhir/type] := :fhir/Observation
            [0 :id] := "0"
            [1 :id] := "1")

          (given (explain-compartment-query node "Patient" "Observation" clauses)
            :query-type := :compartment
            :scan-type := :ordered
            [:scan-clauses count] := 2
            [:scan-clauses 0 :code] := "code"
            [:scan-clauses 1 :code] := "category"
            [:seek-clauses count] := 0)))

      (testing "matches code and value-quantity criteria"
        (let [clauses [["code" "system-191514|code-191518"]
                       ["value-quantity" "42"]]]
          (given (pull-compartment-query
                  node "Patient" "0" "Observation"
                  clauses)
            count := 1
            [0 :fhir/type] := :fhir/Observation
            [0 :id] := "0")

          (given (explain-compartment-query node "Patient" "Observation" clauses)
            :query-type := :compartment
            :scan-type := :ordered
            [:scan-clauses count] := 1
            [:scan-clauses 0 :code] := "code"
            [:seek-clauses count] := 1
            [:seek-clauses 0 :code] := "value-quantity")))

      (testing "returns nothing because of non-matching second criteria"
        (is (coll/empty? (d/compartment-query
                          (d/db node) "Patient" "0" "Observation"
                          [["code" "system-191514|code-191518"]
                           ["value-quantity" "23"]]))))))

  (testing "returns an anomaly on unknown search param code"
    (with-system [{:blaze.db/keys [node]} config]
      (given (d/compartment-query (d/db node) "Patient" "0" "Observation"
                                  [["unknown" "foo"]])
        ::anom/category := ::anom/not-found)))

  (testing "Unknown compartment is not a problem"
    (with-system [{:blaze.db/keys [node]} config]
      (is (coll/empty? (d/compartment-query
                        (d/db node) "foo" "bar" "Condition"
                        [["code" "baz"]])))))

  (testing "Unknown type is not a problem"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "id-0"}]]]

      (given (d/compartment-query (d/db node) "Patient" "id-0" "Foo" [["code" "baz"]])
        ::anom/category := ::anom/not-found
        ::anom/message := "The search-param with code `code` and type `Foo` was not found.")))

  (testing "works with types"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Condition :id "1"
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri"system"
                    :code #fhir/code"code-a"}]}
               :subject #fhir/Reference{:reference "Patient/0"}}]
        [:put {:fhir/type :fhir/Condition :id "2"
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri"system"
                    :code #fhir/code"code-b"}]}
               :subject #fhir/Reference{:reference "Patient/0"}}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :subject #fhir/Reference{:reference "Patient/0"}
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri"system"
                    :code #fhir/code"code-a"}]}}]]]

      (testing "only returns the condition"
        (given (pull-compartment-query node "Patient" "0" "Condition"
                                       [["code" "system|code-a"]])
          count := 1
          [0 :fhir/type] := :fhir/Condition
          [0 :id] := "1"))))

  (testing "works with date search params"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Condition :id "1"
               :onset #fhir/dateTime"2025-07-25"
               :subject #fhir/Reference{:reference "Patient/0"}}]
        [:put {:fhir/type :fhir/Condition :id "2"
               :onset #fhir/dateTime"2025-07-26"
               :subject #fhir/Reference{:reference "Patient/0"}}]]]

      (testing "only returns the condition with onset <= 2025-07-25"
        (given (pull-compartment-query node "Patient" "0" "Condition"
                                       [["onset-date" "le2025-07-25"]])
          count := 1
          [0 :fhir/type] := :fhir/Condition
          [0 :id] := "1")))))

(deftest compile-compartment-query-test
  (with-system-data [{:blaze.db/keys [node]} config]
    [[[:put {:fhir/type :fhir/Patient :id "0"}]
      [:put {:fhir/type :fhir/Observation :id "0"
             :subject #fhir/Reference{:reference "Patient/0"}
             :code
             #fhir/CodeableConcept
              {:coding
               [#fhir/Coding
                 {:system #fhir/uri"system-191514"
                  :code #fhir/code"code-191518"}]}}]]]

    (with-open-db [db node]
      (doseq [target [node db]]
        (given @(let [query (d/compile-compartment-query
                             target "Patient" "Observation"
                             [["code" "system-191514|code-191518"]])]
                  (d/pull-many target (d/execute-query db query "0")))
          count := 1
          [0 :fhir/type] := :fhir/Observation
          [0 :id] := "0")))))

(deftest compile-compartment-query-lenient-test
  (with-system-data [{:blaze.db/keys [node]} config]
    [[[:put {:fhir/type :fhir/Patient :id "0"}]
      [:put {:fhir/type :fhir/Observation :id "0"
             :subject #fhir/Reference{:reference "Patient/0"}
             :code
             #fhir/CodeableConcept
              {:coding
               [#fhir/Coding
                 {:system #fhir/uri"system-191514"
                  :code #fhir/code"code-191518"}]}}]]]

    (testing "the observation can be found"
      (with-open-db [db node]
        (doseq [target [node db]]
          (let [clauses [["code" "system-191514|code-191518"]]
                query (d/compile-compartment-query-lenient
                       target "Patient" "Observation"
                       clauses)]
            (given @(d/pull-many target (d/execute-query db query "0"))
              count := 1
              [0 :fhir/type] := :fhir/Observation
              [0 :id] := "0")

            (is (= clauses (d/query-clauses query)))

            (given (d/explain-query db query)
              :query-type := :compartment
              :scan-type := :ordered
              [:scan-clauses count] := 1
              [:scan-clauses 0 :code] := "code"
              [:seek-clauses count] := 0)))))

    (testing "an unknown search-param is ignored"
      (with-open-db [db node]
        (doseq [target [node db]]
          (let [query (d/compile-compartment-query-lenient
                       target "Patient" "Observation"
                       [["foo" "bar"]])]
            (given @(d/pull-many target (d/execute-query db query "0"))
              count := 1
              [0 :fhir/type] := :fhir/Observation
              [0 :id] := "0")

            (is (empty? (d/query-clauses query)))

            (given (d/explain-query db query)
              :query-type := :compartment
              :scan-type := nil
              :scan-clauses := nil
              [:seek-clauses count] := 0)))))))

(deftest patient-compartment-last-change-t-test
  (testing "non-existing patient"
    (with-system [{:blaze.db/keys [node]} config]

      (testing "just returns nil"
        (with-open-db [db node]
          (is (nil? (d/patient-compartment-last-change-t db "0")))))))

  (testing "single patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (testing "has no resources in its compartment"
        (with-open-db [db node]
          (is (nil? (d/patient-compartment-last-change-t db "0")))))))

  (testing "observation created in same transaction as patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}}]]]

      (with-open-db [db node]
        (is (= 1 (d/patient-compartment-last-change-t db "0"))))))

  (testing "observation created after the patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}}]]]

      (testing "the last change comes from the second transaction"
        (with-open-db [db node]
          (is (= 2 (d/patient-compartment-last-change-t db "0")))))

      (testing "at t=1 there was no change"
        (is (nil? (d/patient-compartment-last-change-t (d/as-of (d/db node) 1) "0"))))))

  (testing "patient with last change in t=1 isn't affected by later patient added in t=2"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:put {:fhir/type :fhir/Patient :id "1"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference "Patient/1"}}]]]

      (with-open-db [db node]
        (is (nil? (d/patient-compartment-last-change-t db "0")))))))

(defmethod ig/init-key ::defective-resource-store [_ {:keys [hashes-to-store]}]
  (let [store (atom {})]
    (reify
      rs/ResourceStore
      (-get [_ key]
        (ac/completed-future (get @store (second key))))
      (-multi-get [_ keys]
        (ac/completed-future
         (into
          {}
          (keep
           (fn [[_type hash :as key]]
             (some->> (get @store hash) (vector key))))
          keys)))
      (-put [_ entries]
        (swap! store merge (select-keys entries hashes-to-store))
        (ac/completed-future nil)))))

(defn- defective-resource-store-config
  "Returns the config of a system only storing resource contents of given hashes."
  [& hashes-to-store]
  (merge-with
   merge
   config
   {:blaze.db/node
    {:resource-store (ig/ref ::defective-resource-store)}
    ::node/resource-indexer
    {:resource-store (ig/ref ::defective-resource-store)}
    ::defective-resource-store
    {:hashes-to-store hashes-to-store}}))

(deftest pull-test
  (testing "success"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (let [db (d/db node)
            resource-handle (d/resource-handle db "Patient" "0")]
        (doseq [target [node db]]
          (given @(mtu/assoc-thread-name (d/pull target resource-handle))
            [meta :thread-name] :? mtu/common-pool-thread?
            :fhir/type := :fhir/Patient
            :id := "0"
            [:meta :versionId] := #fhir/id"1"
            [:meta :lastUpdated] := Instant/EPOCH))))

    (testing "summary"
      (testing "CodeSystem"
        (with-system-data [{:blaze.db/keys [node]} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri"system-115910"
                   :version #fhir/string"version-170327"
                   :content #fhir/code"complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code"code-115927"}]}]]]

          (let [db (d/db node)
                resource-handle (d/resource-handle db "CodeSystem" "0")]
            (doseq [target [node db]]
              (given @(mtu/assoc-thread-name (d/pull target resource-handle :summary))
                [meta :thread-name] :? mtu/common-pool-thread?
                :fhir/type := :fhir/CodeSystem
                :id := "0"
                [:meta :versionId] := #fhir/id"1"
                [:meta :lastUpdated] := Instant/EPOCH
                [:meta :tag 0 :system] := #fhir/uri"http://terminology.hl7.org/CodeSystem/v3-ObservationValue"
                [:meta :tag 0 :code] := #fhir/code"SUBSETTED"
                :url := #fhir/uri"system-115910"
                :version := #fhir/string"version-170327"
                :content := #fhir/code"complete"
                :concept := nil)))))

      (testing "ValueSet"
        (with-system-data [{:blaze.db/keys [node]} config]
          [[[:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri"value-set-154043"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri"system-115910"}]}}]]]

          (let [db (d/db node)
                resource-handle (d/resource-handle db "ValueSet" "0")]
            (doseq [target [node db]]
              (given @(mtu/assoc-thread-name (d/pull target resource-handle :summary))
                [meta :thread-name] :? mtu/common-pool-thread?
                :fhir/type := :fhir/ValueSet
                :id := "0"
                [:meta :versionId] := #fhir/id"1"
                [:meta :lastUpdated] := Instant/EPOCH
                [:meta :tag 0 :system] := #fhir/uri"http://terminology.hl7.org/CodeSystem/v3-ObservationValue"
                [:meta :tag 0 :code] := #fhir/code"SUBSETTED"
                :url := #fhir/uri"value-set-154043"
                :compose := nil)))))))

  (testing "resource content not-found"
    (with-system-data [{:blaze.db/keys [node]} (defective-resource-store-config)]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (let [db (d/db node)
            resource-handle (d/resource-handle db "Patient" "0")]
        (doseq [target [node db]]
          (given-failed-future (d/pull target resource-handle)
            ::anom/category := ::anom/not-found
            ::anom/message := (format "The resource content of `Patient/0` with hash `%s` was not found."
                                      (:hash resource-handle))))))))

(deftest pull-content-test
  (testing "success"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (let [db (d/db node)
            resource-handle (d/resource-handle db "Patient" "0")]
        (doseq [target [node db]]
          (given @(mtu/assoc-thread-name (d/pull-content target resource-handle))
            [meta :thread-name] :? mtu/common-pool-thread?
            :fhir/type := :fhir/Patient
            :id := "0"
            :meta := nil)))))

  (testing "resource content not-found"
    (with-system-data [{:blaze.db/keys [node]} (defective-resource-store-config)]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (let [resource-handle (d/resource-handle (d/db node) "Patient" "0")]
        (given-failed-future (d/pull-content node resource-handle)
          ::anom/category := ::anom/not-found
          ::anom/message := (format "The resource content of `Patient/0` with hash `%s` was not found."
                                    (:hash resource-handle)))))))

(deftest pull-many-test
  (with-system-data [{:blaze.db/keys [node]} config]
    [[[:put {:fhir/type :fhir/Patient :id "0"}]
      [:put {:fhir/type :fhir/Observation :id "0"
             :subject #fhir/Reference{:reference "Patient/0"}
             :code
             #fhir/CodeableConcept
              {:coding
               [#fhir/Coding
                 {:system #fhir/uri"system-191514"
                  :code #fhir/code"code-191518"}]}}]]]

    (with-open-db [db node]
      (doseq [target [node db]]
        (given @(mtu/assoc-thread-name (d/pull-many target (d/type-list db "Observation")))
          [meta :thread-name] :? mtu/common-pool-thread?
          count := 1
          [0 :fhir/type] := :fhir/Observation
          [0 :id] := "0"
          [0 :meta :tag] := nil
          [0 :code :coding 0 :code] := #fhir/code"code-191518"
          [0 :subject :reference] := #fhir/string"Patient/0"))))

  (testing "summary"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-115910"
               :version #fhir/string"version-170327"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-115927"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-154043"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-115910"}]}}]]]

      (let [db (d/db node)
            code-system-handle (d/resource-handle db "CodeSystem" "0")
            value-set-handle (d/resource-handle db "ValueSet" "0")]
        (doseq [target [node db]]
          (given @(mtu/assoc-thread-name (d/pull-many target [code-system-handle value-set-handle] :summary))
            [meta :thread-name] :? mtu/common-pool-thread?
            count := 2
            [0 :fhir/type] := :fhir/CodeSystem
            [0 :id] := "0"
            [0 :meta :versionId] := #fhir/id"1"
            [0 :meta :lastUpdated] := Instant/EPOCH
            [0 :meta :tag 0 :system] := #fhir/uri"http://terminology.hl7.org/CodeSystem/v3-ObservationValue"
            [0 :meta :tag 0 :code] := #fhir/code"SUBSETTED"
            [0 :url] := #fhir/uri"system-115910"
            [0 :version] := #fhir/string"version-170327"
            [0 :content] := #fhir/code"complete"
            [0 :concept] := nil
            [1 :fhir/type] := :fhir/ValueSet
            [1 :id] := "0"
            [1 :meta :versionId] := #fhir/id"1"
            [1 :meta :lastUpdated] := Instant/EPOCH
            [1 :meta :tag 0 :system] := #fhir/uri"http://terminology.hl7.org/CodeSystem/v3-ObservationValue"
            [1 :meta :tag 0 :code] := #fhir/code"SUBSETTED"
            [1 :url] := #fhir/uri"value-set-154043"
            [1 :compose] := nil)))))

  (testing "pull only certain elements"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri"system-191514"
                    :code #fhir/code"code-191518"}]}}]]]

      (with-open-db [db node]
        (doseq [target [node db]]
          (given @(mtu/assoc-thread-name (d/pull-many target (d/type-list db "Observation") [:subject]))
            [meta :thread-name] :? mtu/common-pool-thread?
            count := 1
            [0 :fhir/type] := :fhir/Observation
            [0 :id] := "0"
            [0 :meta :tag 0 :system] := #fhir/uri"http://terminology.hl7.org/CodeSystem/v3-ObservationValue"
            [0 :meta :tag 0 :code] := #fhir/code"SUBSETTED"
            [0 :code] := nil
            [0 :subject :reference] := #fhir/string"Patient/0")))))

  (testing "pull a single non-existing hash"
    (with-system-data [{:blaze.db/keys [node]} (defective-resource-store-config)]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (let [resource-handle (d/resource-handle (d/db node) "Patient" "0")]
        (given-failed-future (d/pull-many node [resource-handle])
          ::anom/category := ::anom/not-found
          ::anom/message := (format "The resource content of `Patient/0` with hash `%s` was not found."
                                    (:hash resource-handle))))))

  (testing "pull an existing and a non-existing hash"
    (let [patient-0 {:fhir/type :fhir/Patient :id "0"}
          patient-hash-0 (hash/generate patient-0)]
      (with-system-data [{:blaze.db/keys [node]} (defective-resource-store-config patient-hash-0)]
        [[[:put patient-0]
          [:put {:fhir/type :fhir/Patient :id "1"}]]]

        (let [db (d/db node)
              existing-rh (d/resource-handle db "Patient" "0")
              non-existing-rh (d/resource-handle db "Patient" "1")]
          (given-failed-future (d/pull-many node [existing-rh non-existing-rh])
            ::anom/category := ::anom/not-found
            ::anom/message := (format "The resource content of `Patient/1` with hash `%s` was not found."
                                      (:hash non-existing-rh))))))))

;; ---- Instance-Level History Functions --------------------------------------

(deftest instance-history-test
  (testing "a new node has an empty instance history"
    (with-system [{:blaze.db/keys [node]} config]
      (is (coll/empty? (d/instance-history (d/db node) "Patient" "0")))
      (is (zero? (d/total-num-of-instance-changes (d/db node) "Patient" "0")))))

  (testing "a node with one patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (testing "has one history entry"
        (is (= 1 (d/total-num-of-instance-changes (d/db node) "Patient" "0"))))

      (testing "contains that patient"
        (given @(pull-instance-history (d/db node) "Patient" "0")
          count := 1
          [0 :fhir/type] := :fhir/Patient
          [0 :id] := "0"
          [0 :meta :versionId] := #fhir/id"1"))

      (testing "has an empty history on another patient"
        (is (coll/empty? (d/instance-history (d/db node) "Patient" "1")))
        (is (zero? (d/total-num-of-instance-changes (d/db node) "Patient" "1"))))))

  (testing "a node with one deleted patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:delete "Patient" "0"]]]

      (testing "has two history entries"
        (is (= 2 (d/total-num-of-instance-changes (d/db node) "Patient" "0"))))

      (let [patients @(pull-instance-history (d/db node) "Patient" "0")]
        (is (= 2 (count patients)))

        (testing "the first history entry is the patient marked as deleted"
          (given patients
            [0 :fhir/type] := :fhir/Patient
            [0 :id] := "0"
            [0 :meta :versionId] := #fhir/id"2"
            [0 meta :blaze.db/op] := :delete))

        (testing "the second history entry is the patient marked as created"
          (given patients
            [1 :fhir/type] := :fhir/Patient
            [1 :id] := "0"
            [1 :meta :versionId] := #fhir/id"1"
            [1 meta :blaze.db/op] := :put)))))

  (testing "a node with one patient with two versions and another patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0" :active true}]
        [:put {:fhir/type :fhir/Patient :id "1"}]]
       [[:put {:fhir/type :fhir/Patient :id "0" :active false}]]]

      (testing "the first patient has two history entries"
        (is (= 2 (d/total-num-of-instance-changes (d/db node) "Patient" "0"))))

      (testing "contains both versions in reverse transaction order"
        (given @(pull-instance-history (d/db node) "Patient" "0")
          count := 2
          [0 :active] := false
          [1 :active] := true))

      (testing "it is possible to start with the older transaction"
        (given @(pull-instance-history (d/db node) "Patient" "0" 1)
          count := 1
          [0 :active] := true))

      (testing "overshooting the start-t returns an empty collection"
        (is (coll/empty? (d/instance-history (d/db node) "Patient" "0" 0))))))

  (testing "using since"
    (with-system-data [{:blaze.db/keys [node] :blaze.test/keys [system-clock]}
                       system-clock-config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (Thread/sleep 2000)
      (let [since (time/instant system-clock)
            _ (Thread/sleep 2000)
            db @(d/transact node [[:put {:fhir/type :fhir/Patient :id "0"
                                         :active true}]])]

        (testing "has one history entry"
          (is (= 1 (d/total-num-of-instance-changes db "Patient" "0" since))))

        (testing "contains the patient"
          (given (into [] (d/stop-history-at db since) (d/instance-history db "Patient" "0"))
            count := 1
            [0 :id] := "0")))))

  (testing "the database is immutable"
    (testing "while updating a patient"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0" :active false}]]]

        (let [db (d/db node)]
          @(d/transact node [[:put {:fhir/type :fhir/Patient :id "0" :active true}]])

          (testing "the original database"
            (testing "has still only one history entry"
              (is (= 1 (d/total-num-of-instance-changes db "Patient" "0"))))

            (testing "contains still the original patient"
              (given @(pull-instance-history db "Patient" "0")
                count := 1
                [0 :fhir/type] := :fhir/Patient
                [0 :id] := "0"
                [0 :active] := false
                [0 :meta :versionId] := #fhir/id"1"))))))))

;; ---- Type-Level History Functions ------------------------------------------

(deftest type-history-test
  (testing "a new node has an empty type history"
    (with-system [{:blaze.db/keys [node]} config]
      (is (coll/empty? (d/type-history (d/db node) "Patient")))
      (is (zero? (d/total-num-of-type-changes (d/db node) "Patient")))))

  (testing "a node with one patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (testing "has one history entry"
        (is (= 1 (d/total-num-of-type-changes (d/db node) "Patient"))))

      (testing "contains that patient"
        (given @(d/pull-many node (d/type-history (d/db node) "Patient"))
          count := 1
          [0 :fhir/type] := :fhir/Patient
          [0 :id] := "0"
          [0 :meta :versionId] := #fhir/id"1"))

      (testing "has an empty observation history"
        (is (coll/empty? (d/type-history (d/db node) "Observation")))
        (is (zero? (d/total-num-of-type-changes (d/db node) "Observation"))))))

  (testing "a node with one deleted patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:delete "Patient" "0"]]]

      (testing "has two history entries"
        (is (= 2 (d/total-num-of-type-changes (d/db node) "Patient"))))

      (let [patients @(d/pull-many node (d/type-history (d/db node) "Patient"))]
        (is (= 2 (count patients)))

        (testing "the first history entry is the patient marked as deleted"
          (given patients
            [0 :fhir/type] := :fhir/Patient
            [0 :id] := "0"
            [0 :meta :versionId] := #fhir/id"2"
            [0 meta :blaze.db/op] := :delete))

        (testing "the second history entry is the patient marked as created"
          (given patients
            [1 :fhir/type] := :fhir/Patient
            [1 :id] := "0"
            [1 :meta :versionId] := #fhir/id"1"
            [1 meta :blaze.db/op] := :put)))))

  (testing "a node with two patients in two transactions"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:put {:fhir/type :fhir/Patient :id "1"}]]]

      (testing "has two history entries"
        (is (= 2 (d/total-num-of-type-changes (d/db node) "Patient"))))

      (testing "contains both patients in reverse transaction order"
        (given (vec (d/type-history (d/db node) "Patient"))
          count := 2
          [0 :id] := "1"
          [1 :id] := "0"))

      (testing "it is possible to start with the older transaction"
        (given (vec (d/type-history (d/db node) "Patient" 1))
          count := 1
          [0 :id] := "0"))

      (testing "overshooting the start-t returns an empty collection"
        (is (coll/empty? (d/type-history (d/db node) "Patient" 0))))))

  (testing "a node with two patients in one transaction"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "1"}]]]

      (testing "has two history entries"
        (is (= 2 (d/total-num-of-type-changes (d/db node) "Patient"))))

      (testing "contains both patients in the order of their ids"
        (given @(d/pull-many node (d/type-history (d/db node) "Patient"))
          count := 2
          [0 :id] := "0"
          [1 :id] := "1"))

      (testing "it is possible to start with the second patient"
        (given @(d/pull-many node (d/type-history (d/db node) "Patient" 1 "1"))
          count := 1
          [0 :id] := "1"))))

  (testing "using since"
    (with-system-data [{:blaze.db/keys [node] :blaze.test/keys [system-clock]}
                       system-clock-config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (Thread/sleep 2000)
      (let [since (time/instant system-clock)
            _ (Thread/sleep 2000)
            db @(d/transact node [[:put {:fhir/type :fhir/Patient :id "1"}]])]

        (testing "has one history entry"
          (is (= 1 (d/total-num-of-type-changes db "Patient" since))))

        (testing "contains the patient"
          (given (into [] (d/stop-history-at db since) (d/type-history db "Patient"))
            count := 1
            [0 :id] := "1")))))

  (testing "the database is immutable"
    (testing "while updating a patient"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0" :active false}]]]

        (let [db (d/db node)]
          @(d/transact node [[:put {:fhir/type :fhir/Patient :id "0" :active true}]])

          (testing "the original database"
            (testing "has still only one history entry"
              (is (= 1 (d/total-num-of-type-changes db "Patient"))))

            (testing "contains still the original patient"
              (given @(d/pull-many node (d/type-history db "Patient"))
                count := 1
                [0 :fhir/type] := :fhir/Patient
                [0 :id] := "0"
                [0 :active] := false
                [0 :meta :versionId] := #fhir/id"1"))))))

    (testing "while adding another patient"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

        (let [db (d/db node)]
          @(d/transact node [[:put {:fhir/type :fhir/Patient :id "1"}]])

          (testing "the original database"
            (testing "has still only one history entry"
              (is (= 1 (d/total-num-of-type-changes db "Patient"))))

            (testing "contains still the first patient"
              (given @(d/pull-many node (d/type-history db "Patient"))
                count := 1
                [0 :fhir/type] := :fhir/Patient
                [0 :id] := "0"
                [0 :meta :versionId] := #fhir/id"1"))))))))

;; ---- System-Level History Functions ----------------------------------------

(deftest system-history-test
  (testing "a new node has an empty system history"
    (with-system [{:blaze.db/keys [node]} config]
      (is (coll/empty? (d/system-history (d/db node))))
      (is (zero? (d/total-num-of-system-changes (d/db node))))))

  (testing "a node with one patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (testing "has one history entry"
        (is (= 1 (d/total-num-of-system-changes (d/db node)))))

      (testing "contains that patient"
        (given @(d/pull-many node (d/system-history (d/db node)))
          count := 1
          [0 :fhir/type] := :fhir/Patient
          [0 :id] := "0"
          [0 :meta :versionId] := #fhir/id"1"))))

  (testing "a node with one deleted patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:delete "Patient" "0"]]]

      (testing "has two history entries"
        (is (= 2 (d/total-num-of-system-changes (d/db node)))))

      (let [patients @(d/pull-many node (d/system-history (d/db node)))]
        (is (= 2 (count patients)))

        (testing "the first history entry is the patient marked as deleted"
          (given patients
            [0 :fhir/type] := :fhir/Patient
            [0 :id] := "0"
            [0 :meta :versionId] := #fhir/id"2"
            [0 meta :blaze.db/op] := :delete))

        (testing "the second history entry is the patient marked as created"
          (given patients
            [1 :fhir/type] := :fhir/Patient
            [1 :id] := "0"
            [1 :meta :versionId] := #fhir/id"1"
            [1 meta :blaze.db/op] := :put)))))

  (testing "a node with one patient and one observation in two transactions"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:put {:fhir/type :fhir/Observation :id "0"}]]]

      (testing "has two history entries"
        (is (= 2 (d/total-num-of-system-changes (d/db node)))))

      (testing "contains both resources in reverse transaction order"
        (given @(d/pull-many node (d/system-history (d/db node)))
          count := 2
          [0 :fhir/type] := :fhir/Observation
          [1 :fhir/type] := :fhir/Patient))

      (testing "it is possible to start with the older transaction"
        (given @(d/pull-many node (d/system-history (d/db node) 1))
          [0 :fhir/type] := :fhir/Patient))))

  (testing "a node with one patient and one observation in one transaction"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"}]]]

      (testing "has two history entries"
        (is (= 2 (d/total-num-of-system-changes (d/db node)))))

      (testing "contains both resources in the order of their type hashes"
        (given @(d/pull-many node (d/system-history (d/db node)))
          count := 2
          [0 :fhir/type] := :fhir/Observation
          [1 :fhir/type] := :fhir/Patient))

      (testing "it is possible to start with the patient"
        (given @(d/pull-many node (d/system-history (d/db node) 1 "Patient"))
          count := 1
          [0 :fhir/type] := :fhir/Patient))))

  (testing "a node with two patients in one transaction"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "1"}]]]

      (testing "has two history entries"
        (is (= 2 (d/total-num-of-system-changes (d/db node)))))

      (testing "contains both patients"
        (given @(d/pull-many node (d/system-history (d/db node)))
          count := 2
          [0 :id] := "0"
          [1 :id] := "1"))

      (testing "it is possible to start with the second patient"
        (given @(d/pull-many node (d/system-history (d/db node) 1 "Patient" "1"))
          count := 1
          [0 :id] := "1"))))

  (testing "using since"
    (with-system-data [{:blaze.db/keys [node] :blaze.test/keys [system-clock]}
                       system-clock-config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (Thread/sleep 2000)
      (let [since (time/instant system-clock)
            _ (Thread/sleep 2000)
            db @(d/transact node [[:put {:fhir/type :fhir/Patient :id "1"}]])]

        (testing "has one history entry"
          (is (= 1 (d/total-num-of-system-changes db since))))

        (testing "contains the patient"
          (given (into [] (d/stop-history-at db since) (d/system-history db))
            count := 1
            [0 :id] := "1")))))

  (testing "the database is immutable"
    (testing "while updating a patient"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0" :active false}]]]

        (let [db (d/db node)]
          @(d/transact node [[:put {:fhir/type :fhir/Patient :id "0" :active true}]])

          (testing "the original database"
            (testing "has still only one history entry"
              (is (= 1 (d/total-num-of-system-changes db))))

            (testing "contains still the original patient"
              (given @(d/pull-many node (d/system-history db))
                count := 1
                [0 :fhir/type] := :fhir/Patient
                [0 :id] := "0"
                [0 :active] := false
                [0 :meta :versionId] := #fhir/id"1"))))))

    (testing "while adding another patient"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

        (let [db (d/db node)]
          @(d/transact node [[:put {:fhir/type :fhir/Patient :id "1"}]])

          (testing "the original database"
            (testing "has still only one history entry"
              (is (= 1 (d/total-num-of-system-changes db))))

            (testing "contains still the first patient"
              (given @(d/pull-many node (d/system-history db))
                count := 1
                [0 :fhir/type] := :fhir/Patient
                [0 :id] := "0"
                [0 :meta :versionId] := #fhir/id"1"))))))))

(deftest changes-test
  (testing "a node with one patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (testing "has that patient changed"
        (given @(d/pull-many node (d/changes (d/db node)))
          count := 1
          [0 :fhir/type] := :fhir/Patient
          [0 :id] := "0"
          [0 :meta :versionId] := #fhir/id"1")))))

(deftest include-test
  (testing "Observation"
    (doseq [code ["subject" "patient"]]
      (testing code
        (with-system-data [{:blaze.db/keys [node]} config]
          [[[:put {:fhir/type :fhir/Patient :id "0"}]
            [:put {:fhir/type :fhir/Patient :id "1"}]
            [:put {:fhir/type :fhir/Observation :id "0"
                   :subject #fhir/Reference{:reference "Patient/0"}}]]]

          (let [db (d/db node)
                observation (d/resource-handle db "Observation" "0")]

            (testing "without target type"
              (given (vec (d/include db observation code))
                count := 1
                [0 :fhir/type] := :fhir/Patient
                [0 :id] := "0"))

            (testing "with target type"
              (given (vec (d/include db observation code "Patient"))
                count := 1
                [0 :fhir/type] := :fhir/Patient
                [0 :id] := "0"))))))

    (testing "encounter"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Encounter :id "0"}]
          [:put {:fhir/type :fhir/Observation :id "0"
                 :subject #fhir/Reference{:reference "Patient/0"}
                 :encounter #fhir/Reference{:reference "Encounter/0"}}]]]

        (let [db (d/db node)
              observation (d/resource-handle db "Observation" "0")]
          (given (vec (d/include db observation "encounter"))
            count := 1
            [0 :fhir/type] := :fhir/Encounter
            [0 :id] := "0"))))

    (testing "with Group subject"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Group :id "0"}]
          [:put {:fhir/type :fhir/Observation :id "0"
                 :subject #fhir/Reference{:reference "Group/0"}}]]]

        (let [db (d/db node)
              observation (d/resource-handle db "Observation" "0")]

          (testing "returns group with subject param"
            (given (vec (d/include db observation "subject"))
              count := 1
              [0 :fhir/type] := :fhir/Group
              [0 :id] := "0"))

          (testing "returns nothing with patient param"
            (is (coll/empty? (d/include db observation "patient"))))

          (testing "returns group with subject param and Group target type"
            (given (vec (d/include db observation "subject" "Group"))
              count := 1
              [0 :fhir/type] := :fhir/Group
              [0 :id] := "0"))

          (testing "returns nothing with subject param and Patient target type"
            (is (coll/empty? (d/include db observation "subject" "Patient")))))))

    (testing "non-reference search parameter code"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Observation :id "0"
                 :code
                 #fhir/CodeableConcept
                  {:coding
                   [#fhir/Coding
                     {:system #fhir/uri"http://loinc.org"
                      :code #fhir/code"8480-6"}]}}]]]

        (let [db (d/db node)
              observation (d/resource-handle db "Observation" "0")]
          (is (coll/empty? (d/include db observation "code")))))))

  (testing "Patient"
    (testing "non-reference search parameter family"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0"
                 :name [#fhir/HumanName{:family "Müller"}]}]]]

        (let [db (d/db node)
              patient (d/resource-handle db "Patient" "0")]
          (is (coll/empty? (d/include db patient "family"))))))))

(deftest rev-include-test
  (testing "Patient"
    (testing "all resources"
      (testing "with patient only"
        (with-system-data [{:blaze.db/keys [node]} config]
          [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

          (let [db (d/db node)
                patient (d/resource-handle db "Patient" "0")]

            (is (coll/empty? (d/rev-include db patient))))))

      (testing "with three resources"
        (with-system-data [{:blaze.db/keys [node]} config]
          [[[:put {:fhir/type :fhir/Patient :id "0"}]
            [:put {:fhir/type :fhir/Observation :id "0"
                   :subject #fhir/Reference{:reference "Patient/0"}}]
            [:put {:fhir/type :fhir/Condition :id "0"
                   :subject #fhir/Reference{:reference "Patient/0"}}]
            [:put {:fhir/type :fhir/Specimen :id "0"
                   :subject #fhir/Reference{:reference "Patient/0"}}]]]

          (let [db (d/db node)
                patient (d/resource-handle db "Patient" "0")]

            (given (vec (d/rev-include db patient))
              count := 3
              [0 fhir-spec/fhir-type] := :fhir/Condition
              [0 :id] := "0"
              [1 fhir-spec/fhir-type] := :fhir/Observation
              [1 :id] := "0"
              [2 fhir-spec/fhir-type] := :fhir/Specimen
              [2 :id] := "0"))))

      (testing "With MedicationAdministration because it is reachable twice via
                the search param `patient` and `subject`.
              
                This test should assure that MedicationAdministration resources
                are returned only once."
        (with-system-data [{:blaze.db/keys [node]} config]
          [[[:put {:fhir/type :fhir/Patient :id "0"}]
            [:put {:fhir/type :fhir/Medication :id "0"}]
            [:put {:fhir/type :fhir/MedicationAdministration :id "0"
                   :subject #fhir/Reference{:reference "Patient/0"}}]]]

          (let [db (d/db node)
                patient (d/resource-handle db "Patient" "0")]

            (given (vec (d/rev-include db patient))
              count := 1
              [0 fhir-spec/fhir-type] := :fhir/MedicationAdministration
              [0 :id] := "0")))))

    (doseq [code ["subject" "patient"]]
      (testing (str "Observation with search parameter " code)
        (with-system-data [{:blaze.db/keys [node]} config]
          [[[:put {:fhir/type :fhir/Patient :id "0"}]
            [:put {:fhir/type :fhir/Observation :id "0"
                   :subject #fhir/Reference{:reference "Patient/0"}}]
            [:put {:fhir/type :fhir/Observation :id "1"
                   :subject #fhir/Reference{:reference "Patient/0"}}]]]

          (let [db (d/db node)
                patient (d/resource-handle db "Patient" "0")]

            (given (vec (d/rev-include db patient "Observation" code))
              count := 2
              [0 :fhir/type] := :fhir/Observation
              [0 :id] := "0"
              [1 :fhir/type] := :fhir/Observation
              [1 :id] := "1")))))

    (testing "non-reference search parameter code"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Observation :id "0"
                 :code
                 #fhir/CodeableConcept
                  {:coding
                   [#fhir/Coding
                     {:system #fhir/uri"http://loinc.org"
                      :code #fhir/code"8480-6"}]}}]]]

        (let [db (d/db node)
              patients (d/resource-handle db "Patient" "0")]
          (is (coll/empty? (d/rev-include db patients "Observation" "code"))))))))

(deftest patient-everything-test
  (testing "with patient only"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (let [db (d/db node)
            patient (d/resource-handle db "Patient" "0")]

        (given (vec (d/patient-everything db patient))
          count := 1
          [0 :fhir/type] := :fhir/Patient
          [0 :id] := "0"))))

  (testing "with three resources"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}}]
        [:put {:fhir/type :fhir/Condition :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}}]
        [:put {:fhir/type :fhir/Specimen :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}}]]]

      (let [db (d/db node)
            patient (d/resource-handle db "Patient" "0")]

        (given (vec (d/patient-everything db patient))
          count := 4
          [0 :fhir/type] := :fhir/Patient
          [0 :id] := "0"
          [1 :fhir/type] := :fhir/Condition
          [1 :id] := "0"
          [2 :fhir/type] := :fhir/Observation
          [2 :id] := "0"
          [3 :fhir/type] := :fhir/Specimen
          [3 :id] := "0"))))

  (testing "With MedicationAdministration because it is reachable twice via
            the search param `patient` and `subject`.

            This test should assure that MedicationAdministration resources
            are returned only once."
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/MedicationAdministration :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}}]]]

      (let [db (d/db node)
            patient (d/resource-handle db "Patient" "0")]

        (given (vec (d/patient-everything db patient))
          count := 2
          [0 :fhir/type] := :fhir/Patient
          [0 :id] := "0"
          [1 :fhir/type] := :fhir/MedicationAdministration
          [1 :id] := "0"))))

  (testing "one MedicationAdministration with referenced Medication"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Medication :id "0"}]
        [:put {:fhir/type :fhir/MedicationAdministration :id "0"
               :medication #fhir/Reference{:reference "Medication/0"}
               :subject #fhir/Reference{:reference "Patient/0"}}]]]

      (let [db (d/db node)
            patient (d/resource-handle db "Patient" "0")]

        (testing "contains also the Medication"
          (given (vec (d/patient-everything db patient))
            count := 3
            [0 :fhir/type] := :fhir/Patient
            [0 :id] := "0"
            [1 :fhir/type] := :fhir/MedicationAdministration
            [1 :id] := "0"
            [2 :fhir/type] := :fhir/Medication
            [2 :id] := "0")))))

  (testing "two MedicationAdministrations with two referenced Medications"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Medication :id "0"}]
        [:put {:fhir/type :fhir/Medication :id "1"}]
        [:put {:fhir/type :fhir/MedicationAdministration :id "0"
               :medication #fhir/Reference{:reference "Medication/0"}
               :subject #fhir/Reference{:reference "Patient/0"}}]
        [:put {:fhir/type :fhir/MedicationAdministration :id "1"
               :medication #fhir/Reference{:reference "Medication/1"}
               :subject #fhir/Reference{:reference "Patient/0"}}]]]

      (let [db (d/db node)
            patient (d/resource-handle db "Patient" "0")]

        (testing "contains both Medications"
          (given (vec (d/patient-everything db patient))
            count := 5
            [0 :fhir/type] := :fhir/Patient
            [0 :id] := "0"
            [1 :fhir/type] := :fhir/MedicationAdministration
            [1 :id] := "0"
            [2 :fhir/type] := :fhir/Medication
            [2 :id] := "0"
            [3 :fhir/type] := :fhir/MedicationAdministration
            [3 :id] := "1"
            [4 :fhir/type] := :fhir/Medication
            [4 :id] := "1")))))

  (testing "two MedicationAdministrations with one referenced Medication"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Medication :id "0"}]
        [:put {:fhir/type :fhir/MedicationAdministration :id "0"
               :medication #fhir/Reference{:reference "Medication/0"}
               :subject #fhir/Reference{:reference "Patient/0"}}]
        [:put {:fhir/type :fhir/MedicationAdministration :id "1"
               :medication #fhir/Reference{:reference "Medication/0"}
               :subject #fhir/Reference{:reference "Patient/0"}}]]]

      (let [db (d/db node)
            patient (d/resource-handle db "Patient" "0")]

        (testing "contains the Medication only once"
          (given (vec (d/patient-everything db patient))
            count := 4
            [0 :fhir/type] := :fhir/Patient
            [0 :id] := "0"
            [1 :fhir/type] := :fhir/MedicationAdministration
            [1 :id] := "0"
            [2 :fhir/type] := :fhir/Medication
            [2 :id] := "0"
            [3 :fhir/type] := :fhir/MedicationAdministration
            [3 :id] := "1")))))

  (testing "no duplicates are returned"
    (satisfies-prop 10
      (prop/for-all [resources mixed-resource-gen]
        (with-system-data [{:blaze.db/keys [node]} config]
          [(into [[:put {:fhir/type :fhir/Patient :id "0"}]
                  [:put {:fhir/type :fhir/Medication :id "0"}]
                  [:put {:fhir/type :fhir/Practitioner :id "0"}]]
                 (map (partial vector :create))
                 resources)]

          (let [db (d/db node)
                patient (d/resource-handle db "Patient" "0")
                res (vec (d/patient-everything db patient))]
            (= (count (set res)) (count res)))))))

  (testing "with start date"
    (testing "Observation"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Observation :id "0"
                 :subject #fhir/Reference{:reference "Patient/0"}}]
          [:put {:fhir/type :fhir/Observation :id "1"
                 :subject #fhir/Reference{:reference "Patient/0"}
                 :effective #fhir/dateTime"2024-01-04T23:45:50Z"}]]]

        (let [db (d/db node)
              patient (d/resource-handle db "Patient" "0")]

          (given (vec (d/patient-everything db patient #system/date"2024" nil))
            count := 2
            [0 fhir-spec/fhir-type] := :fhir/Patient
            [0 :id] := "0"
            [1 fhir-spec/fhir-type] := :fhir/Observation
            [1 :id] := "1"))))

    (testing "Encounter"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Encounter :id "0"
                 :subject #fhir/Reference{:reference "Patient/0"}}]
          [:put {:fhir/type :fhir/Encounter :id "1"
                 :subject #fhir/Reference{:reference "Patient/0"}
                 :period #fhir/Period{:start #fhir/dateTime"2024-01-04T23:45:50Z"}}]]]

        (let [db (d/db node)
              patient (d/resource-handle db "Patient" "0")]

          (given (vec (d/patient-everything db patient #system/date"2024" nil))
            count := 2
            [0 fhir-spec/fhir-type] := :fhir/Patient
            [0 :id] := "0"
            [1 fhir-spec/fhir-type] := :fhir/Encounter
            [1 :id] := "1")))))

  (testing "with end date"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :subject #fhir/Reference{:reference "Patient/0"}
               :effective #fhir/dateTime"2024-01-04T23:45:50Z"}]]]

      (let [db (d/db node)
            patient (d/resource-handle db "Patient" "0")]

        (given (vec (d/patient-everything db patient nil #system/date"2024"))
          count := 2
          [0 fhir-spec/fhir-type] := :fhir/Patient
          [0 :id] := "0"
          [1 fhir-spec/fhir-type] := :fhir/Observation
          [1 :id] := "1"))))

  (testing "with start and end date"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :subject #fhir/Reference{:reference "Patient/0"}
               :effective #fhir/dateTime"2024-01-04T23:45:50Z"}]
        [:put {:fhir/type :fhir/Observation :id "2"
               :subject #fhir/Reference{:reference "Patient/0"}
               :effective #fhir/dateTime"2025-01-04T23:45:50Z"}]]]

      (let [db (d/db node)
            patient (d/resource-handle db "Patient" "0")]

        (given (vec (d/patient-everything db patient #system/date"2024" #system/date"2024"))
          count := 2
          [0 fhir-spec/fhir-type] := :fhir/Patient
          [0 :id] := "0"
          [1 fhir-spec/fhir-type] := :fhir/Observation
          [1 :id] := "1"))))

  (testing "doesn't include the deleted observation"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :subject #fhir/Reference{:reference "Patient/0"}}]]
       [[:delete "Observation" "0"]]]

      (let [db (d/db node)
            patient (d/resource-handle db "Patient" "0")]

        (given (vec (d/patient-everything db patient))
          count := 2
          [0 :fhir/type] := :fhir/Patient
          [0 :id] := "0"
          [1 :fhir/type] := :fhir/Observation
          [1 :id] := "1")))))

(deftest batch-db-test
  (testing "resource-handle"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]]]

      (with-open [batch-db (d/new-batch-db (d/db node))]
        (let [resource-handle (d/resource-handle batch-db "Patient" "0")]
          (testing "pull"
            (given @(d/pull batch-db resource-handle)
              :fhir/type := :fhir/Patient
              :id := "0"
              [:meta :versionId] := #fhir/id"1"))

          (testing "pull-content"
            (given @(d/pull-content batch-db resource-handle)
              :fhir/type := :fhir/Patient
              :id := "0"
              :meta := nil))))))

  (testing "type-list-and-total"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (with-open [batch-db (d/new-batch-db (d/db node))]
        (is (= 1 (count (d/type-list batch-db "Patient"))))
        (is (= 1 (d/type-total batch-db "Patient"))))))

  (testing "compile-type-query"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0" :active true}]]]

      (with-open [batch-db (d/new-batch-db (d/db node))]
        (given @(->> (d/compile-type-query batch-db "Patient" [["active" "true"]])
                     (d/execute-query batch-db)
                     (d/pull-many batch-db))
          count := 1
          [0 :id] := "0"))))

  (testing "compile-type-query-lenient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0" :active true}]]]

      (with-open [batch-db (d/new-batch-db (d/db node))]
        (given @(->> (d/compile-type-query-lenient batch-db "Patient" [["active" "true"]])
                     (d/execute-query batch-db)
                     (d/pull-many batch-db))
          count := 1
          [0 :id] := "0"))))

  (testing "system-list-and-total"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (with-open [batch-db (d/new-batch-db (d/db node))]
        (is (= 1 (count (d/system-list batch-db))))
        (is (= 1 (d/system-total batch-db))))))

  (testing "compile-compartment-query"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri"system-191514"
                    :code #fhir/code"code-191518"}]}}]]]

      (with-open [batch-db (d/new-batch-db (d/db node))]
        (given @(let [query (d/compile-compartment-query
                             batch-db "Patient" "Observation"
                             [["code" "system-191514|code-191518"]])]
                  (->> (d/execute-query batch-db query "0")
                       (d/pull-many batch-db)))
          [0 :fhir/type] := :fhir/Observation
          [0 :id] := "0"))))

  (testing "compile-compartment-query-lenient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri"system-191514"
                    :code #fhir/code"code-191518"}]}}]]]

      (with-open [batch-db (d/new-batch-db (d/db node))]
        (given @(let [query (d/compile-compartment-query-lenient
                             batch-db "Patient" "Observation"
                             [["code" "system-191514|code-191518"]])]
                  (->> (d/execute-query batch-db query "0")
                       (d/pull-many batch-db)))
          [0 :fhir/type] := :fhir/Observation
          [0 :id] := "0"))))

  (testing "node"
    (with-system [{:blaze.db/keys [node]} config]
      (with-open [batch-db (d/new-batch-db (d/db node))]
        (is (identical? node (d/node batch-db))))))

  (testing "pull"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]]]

      (with-open [batch-db (d/new-batch-db (d/db node))]
        (given @(d/pull batch-db (d/resource-handle batch-db "Patient" "0"))
          :fhir/type := :fhir/Patient
          :id := "0"
          [:meta :versionId] := #fhir/id"1"))))

  (testing "pull-content"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]]]

      (with-open [batch-db (d/new-batch-db (d/db node))]
        (given @(d/pull-content batch-db (d/resource-handle batch-db "Patient" "0"))
          :fhir/type := :fhir/Patient
          :id := "0"
          :meta := nil))))

  (testing "pull-many"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0" :active true}]]]

      (with-open [batch-db (d/new-batch-db (d/db node))]
        (testing "full pull"
          (given @(d/pull-many batch-db (d/type-list batch-db "Patient"))
            count := 1
            [0 :id] := "0"
            [0 :active] := true))

        (testing "elements pull"
          (given @(d/pull-many batch-db (d/type-list batch-db "Patient") [:id])
            count := 1
            [0 :id] := "0"
            [0 :active] := nil)))))

  (testing "tx"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "id-142136"}]]]

      (with-open [batch-db (d/new-batch-db (d/db node))]
        (given (d/tx batch-db (d/basis-t batch-db))
          :blaze.db.tx/instant := Instant/EPOCH)))))

(defn- patient-profile-create-op [id]
  [:create {:fhir/type :fhir/Patient :id (format "%05d" id)
            :meta #fhir/Meta{:profile [#fhir/canonical"profile-uri-145024"]}}])

(defn- observation-create-op [id]
  [:create {:fhir/type :fhir/Observation :id (format "%05d" id)
            :category
            [(type/codeable-concept
              {:coding
               [(type/coding
                 {:system #fhir/uri"system-141902"
                  :code (type/code (format "%05d" id))})]})]}])

(defn- condition-create-op [id]
  [:create {:fhir/type :fhir/Condition :id (format "%05d" id)}])

(deftest ^:slow re-index-test
  (log/set-min-level! :warn)

  (testing "unknown search param"
    (with-system [{:blaze.db/keys [node]} config]
      (given (d/re-index-total (d/db node) "unknown")
        ::anom/category := ::anom/not-found
        ::anom/message := "Search parameter with URL `unknown` not found.")

      (given-failed-future (d/re-index (d/db node) "unknown")
        ::anom/category := ::anom/not-found
        ::anom/message := "Search parameter with URL `unknown` not found.")))

  (testing "profile"
    (let [search-param-url "http://hl7.org/fhir/SearchParameter/Resource-profile"]
      (testing "one resource"
        (with-system-data [{:blaze.db/keys [node]} config]
          [[(patient-profile-create-op 0)]]

          (with-open-db [db node]
            (is (= 1 (d/re-index-total db search-param-url)))

            (given @(d/re-index db search-param-url)
              :num-resources := 1
              :next := nil))))

      (testing "10001 resources"
        (with-system-data [{:blaze.db/keys [node]} config]
          [(mapv patient-profile-create-op (range 10001))]

          (with-open-db [db node]
            (is (= 10001 (d/re-index-total db search-param-url)))

            (testing "returns Patient with id 10000 as the next one to re-index"
              (given @(d/re-index db search-param-url)
                :num-resources := 10000
                [:next :id] := "10000"))

            (testing "can start with that next Patient"
              (given @(d/re-index db search-param-url
                                  "Patient" "10000")
                :num-resources := 1
                :next := nil)))))))

  (testing "Observation category"
    (let [search-param-url "http://hl7.org/fhir/SearchParameter/Observation-category"]
      (testing "one resource"
        (with-system-data [{:blaze.db/keys [node]} config]
          [[(observation-create-op 0)]]

          (with-open-db [db node]
            (is (= 1 (d/re-index-total db search-param-url)))

            (given @(d/re-index db search-param-url)
              :num-resources := 1
              :next := nil))))

      (testing "10001 resources"
        (with-system-data [{:blaze.db/keys [node]} config]
          [(mapv observation-create-op (range 10001))]

          (with-open-db [db node]
            (is (= 10001 (d/re-index-total db search-param-url)))

            (testing "returns Observation with id 10000 as the next one to re-index"
              (given @(d/re-index db search-param-url)
                :num-resources := 10000
                [:next :id] := "10000"))

            (testing "can start with that next Observation"
              (given @(d/re-index db search-param-url
                                  "Observation" "10000")
                :num-resources := 1
                :next := nil)))))))

  (testing "multiple base types"
    (let [search-param-url "http://hl7.org/fhir/SearchParameter/clinical-code"]
      (testing "1001 Condition and 10000 Observation resources"
        (with-system-data [{:blaze.db/keys [node]} config]
          [(mapv condition-create-op (range 10001))
           (mapv observation-create-op (range 10000))]

          (with-open-db [db node]
            (is (= 20001 (d/re-index-total db search-param-url)))

            (testing "returns Condition with id 10000 as the next one to re-index"
              (given @(d/re-index db search-param-url)
                :num-resources := 10000
                [:next :fhir/type] := :fhir/Condition
                [:next :id] := "10000"))

            (testing "can start with that next Condition"
              (given @(d/re-index db search-param-url
                                  "Condition" "10000")
                :num-resources := 10000
                [:next :fhir/type] := :fhir/Observation
                [:next :id] := "09999"))

            (testing "can start with that next Observation"
              (given @(d/re-index db search-param-url
                                  "Observation" "09999")
                :num-resources := 1
                :next := nil))))))))
