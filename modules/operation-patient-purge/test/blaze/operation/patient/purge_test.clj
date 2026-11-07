(ns blaze.operation.patient.purge-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.api-stub :as api-stub :refer [with-db with-system-data]]
   [blaze.db.permissions :as p]
   [blaze.db.spec]
   [blaze.fhir.spec.type :as type]
   [blaze.handler.fhir.util-spec]
   [blaze.handler.util :as handler-util]
   [blaze.middleware.fhir.page-id-spec]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.operation.patient.purge]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [java-time.api :as time]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)
(tu/set-default-locale-english!)                            ; important for the thousands separator in 10,000

(test/use-fixtures :each tu/fixture)

(def ^:private config
  (assoc
   api-stub/mem-node-config
   :blaze.operation.patient/purge
   {:node (ig/ref :blaze.db/node)
    :parsing-context (ig/ref ::api-stub/parsing-context)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}))

(defn- purge-config [system]
  {:node (:blaze.db/node system)
   :parsing-context (::api-stub/parsing-context system)
   :clock (:blaze.test/fixed-clock system)
   :rng-fn (:blaze.test/fixed-rng-fn system)})

(def ^:private purge-graph-url
  "https://blaze-server.org/fhir/GraphDefinition/PatientPurge")

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.operation.patient/purge nil}
      :key := :blaze.operation.patient/purge
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.operation.patient/purge {}}
      :key := :blaze.operation.patient/purge
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :parsing-context))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))))

  (testing "invalid node"
    (given-failed-system (assoc-in config [:blaze.operation.patient/purge :node] ::invalid)
      :key := :blaze.operation.patient/purge
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/node]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid parsing-context"
    (given-failed-system (assoc-in config [:blaze.operation.patient/purge :parsing-context] ::invalid)
      :key := :blaze.operation.patient/purge
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.fhir/parsing-context]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid clock"
    (given-failed-system (assoc-in config [:blaze.operation.patient/purge :clock] ::invalid)
      :key := :blaze.operation.patient/purge
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `time/clock?
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid rng-fn"
    (given-failed-system (assoc-in config [:blaze.operation.patient/purge :rng-fn] ::invalid)
      :key := :blaze.operation.patient/purge
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `fn?
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(defn- graph-defs [db]
  @(d/pull-many db (vec (d/type-list db "GraphDefinition"))))

(defn- current-t [node]
  @(d/with-db node (comp ac/completed-future d/t)))

(def ^:private internal-label
  #fhir/Coding
   {:system #fhir/uri "https://blaze-server.org/fhir/CodeSystem/AccessControl"
    :code #fhir/code "internal"})

(deftest install-graph-definition-test
  (testing "the GraphDefinition is created at startup"
    (with-system [{:blaze.db/keys [node]} config]
      (with-db [db node]
        (given (graph-defs db)
          count := 1
          [0 :url] := (type/uri purge-graph-url)
          [0 :version] := #fhir/string "1"
          [0 :name] := #fhir/string "PatientPurge"
          [0 :link count] := 58
          [0 :meta :security] := [internal-label]
          [0 :meta :tag 0 :system] := #fhir/uri "https://blaze-server.org/fhir/CodeSystem/AccessControl"
          [0 :meta :tag 0 :code] := #fhir/code "read-only"
          [0 :meta :tag 1 :system] := #fhir/uri "https://samply.github.io/blaze/fhir/CodeSystem/AccessControl"
          [0 :meta :tag 1 :code] := #fhir/code "read-only"))))

  (testing "an existing GraphDefinition stays unchanged"
    (with-system [{:blaze.db/keys [node] :as system} config]
      (let [t (current-t node)]
        (ig/init-key :blaze.operation.patient/purge (purge-config system))

        (with-db [db node]
          (testing "no transaction happened"
            (is (= t (d/t db))))

          (given (graph-defs db)
            count := 1
            [0 :meta :versionId] := (type/id (str t)))))))

  (testing "a copy without the internal security label isn't taken for Blaze's own"
    (with-system [{:blaze.db/keys [node] :as system} api-stub/mem-node-config]
      @(d/transact node [[:create {:fhir/type :fhir/GraphDefinition :id "copy"
                                   :url (type/uri purge-graph-url)
                                   :version #fhir/string "1"
                                   :name #fhir/string "PatientPurge"
                                   :status #fhir/code "active"
                                   :start #fhir/code "Patient"}]])

      (ig/init-key :blaze.operation.patient/purge (purge-config system))

      (with-db [db node]
        (given (graph-defs db)
          count := 2
          [0 :meta :security] := [internal-label]
          [1 :id] := "copy"
          [1 :meta :security count] := 0)))))

(def base-url "base-url-113047")
(def context-path "/context-path-173858")

(defn wrap-error [handler]
  (fn [request]
    (-> (handler request)
        (ac/exceptionally handler-util/error-response))))

(defn- with-handler-form [config [handler-binding & [node-binding]] more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{node# :blaze.db/node
                         handler# :blaze.operation.patient/purge} ~config]
       ~txs
       (let [~handler-binding (-> handler# wrap-error)
             ~(or node-binding '_) node#]
         ~@body))))

(defmacro with-handler [bindings & more]
  (with-handler-form `config bindings more))

(defmacro with-handler-non-ref [bindings & more]
  (with-handler-form
    `(assoc-in config [:blaze.db/node :enforce-referential-integrity] false)
    bindings more))

(defn- literal-ref [s]
  (type/reference {:reference (type/string s)}))

(def ^:private observations-of-patient-0-and-1
  "Observation/0 is about Patient/0. Observation/1 is about Patient/1 and only
  references Patient/0 as performer."
  [[:create {:fhir/type :fhir/Patient :id "0"}]
   [:create {:fhir/type :fhir/Patient :id "1"}]
   [:create {:fhir/type :fhir/Observation :id "0"
             :subject (literal-ref "Patient/0")}]
   [:create {:fhir/type :fhir/Observation :id "1"
             :subject (literal-ref "Patient/1")
             :performer [(literal-ref "Patient/0")]}]])

(def ^:private provenance-of-patient-0-and-1
  "Provenance/0 targets Patient/0 and Patient/1."
  [[:create {:fhir/type :fhir/Patient :id "0"}]
   [:create {:fhir/type :fhir/Patient :id "1"}]
   [:create {:fhir/type :fhir/Provenance :id "0"
             :target [(literal-ref "Patient/0") (literal-ref "Patient/1")]}]])

(defn- resource-ids
  "Returns the type and id of all resources in `db` except GraphDefinitions."
  [db]
  (into #{}
        (comp (map (juxt (comp name :fhir/type) :id))
              (remove (comp #{"GraphDefinition"} first)))
        (d/system-list db)))

(defn- current-graph-defs
  "Returns the GraphDefinitions currently in the database of `node`."
  [node]
  @(d/with-db node (comp ac/completed-future graph-defs)))

(defn- current-resource-ids [node]
  @(d/with-db node (comp ac/completed-future resource-ids)))

(deftest handler-test
  (testing "Success on non-existing patient"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler
              {:path-params {:id "0"}})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "success"
          [:issue 0 :code] := #fhir/code "success"
          [:issue 0 :diagnostics] := #fhir/string "The patient with id `0` was purged successfully."))))

  (testing "Success on existing patient"
    (with-handler [handler node]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]]]

      (let [{:keys [status body]}
            @(handler
              {:path-params {:id "0"}})]

        (testing "the patient is gone"
          (with-db [db node]
            (is (nil? (d/resource-handle db "Patient" "0")))))

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "success"
          [:issue 0 :code] := #fhir/code "success"
          [:issue 0 :diagnostics] := #fhir/string "The patient with id `0` was purged successfully."))))

  (testing "Fails on one observation referenced by another observation outside the patients compartment"
    (with-handler [handler]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]
        [:create {:fhir/type :fhir/Observation :id "0"
                  :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
        [:create {:fhir/type :fhir/Observation :id "1"
                  :hasMember [#fhir/Reference{:reference #fhir/string "Observation/0"}]}]]]

      (let [{:keys [status body]}
            @(handler
              {:path-params {:id "0"}})]

        (is (= 409 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "conflict"
          [:issue 0 :diagnostics] := #fhir/string "Referential integrity violated. Resource `Observation/0` should be deleted but is referenced from `Observation/1`."))))

  (testing "Observations referencing the patient as performer are not purged"
    (with-handler-non-ref [handler node]
      [observations-of-patient-0-and-1]

      (let [{:keys [status]} @(handler {:path-params {:id "0"}})]

        (is (= 200 status))

        (is (= #{["Patient" "1"] ["Observation" "1"]}
               (current-resource-ids node))))))

  (testing "Fails on observations referencing the patient as performer"
    (with-handler [handler node]
      [observations-of-patient-0-and-1]

      (let [{:keys [status body]} @(handler {:path-params {:id "0"}})]

        (is (= 409 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "conflict"
          [:issue 0 :diagnostics] := #fhir/string "Referential integrity violated. Resource `Patient/0` should be deleted but is referenced from `Observation/1`.")

        (testing "nothing is purged"
          (is (= #{["Patient" "0"] ["Patient" "1"] ["Observation" "0"] ["Observation" "1"]}
                 (current-resource-ids node)))))))

  (testing "A Provenance targeting another patient too is not purged"
    (with-handler-non-ref [handler node]
      [provenance-of-patient-0-and-1]

      (let [{:keys [status]} @(handler {:path-params {:id "0"}})]

        (is (= 200 status))

        (is (= #{["Patient" "1"] ["Provenance" "0"]}
               (current-resource-ids node))))))

  (testing "Fails on a Provenance targeting the patient"
    (with-handler [handler node]
      [provenance-of-patient-0-and-1]

      (let [{:keys [status body]} @(handler {:path-params {:id "0"}})]

        (is (= 409 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "conflict"
          [:issue 0 :diagnostics] := #fhir/string "Referential integrity violated. Resource `Patient/0` should be deleted but is referenced from `Provenance/0`.")

        (testing "nothing is purged"
          (is (= #{["Patient" "0"] ["Patient" "1"] ["Provenance" "0"]}
                 (current-resource-ids node)))))))

  (testing "Fails with a server error if the GraphDefinition is missing"
    (with-handler [handler node]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]]]

      (let [[{:keys [id]}] (current-graph-defs node)]
        @(d/transact node [[:delete "GraphDefinition" id]] p/internal))

      (let [{:keys [status body]} @(handler {:path-params {:id "0"}})]

        (is (= 500 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "exception"
          [:issue 0 :diagnostics] := #fhir/string "The GraphDefinition `https://blaze-server.org/fhir/GraphDefinition/PatientPurge|1` for purging Patients wasn't found.")

        (testing "the patient isn't purged"
          (is (= #{["Patient" "0"]} (current-resource-ids node))))))))
