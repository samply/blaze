(ns blaze.db.node.tx-indexer.verify.impl-test
  (:require
    [blaze.db.api :as d]
    [blaze.db.node.tx-indexer.verify.impl :as impl]
    [blaze.db.node.tx-indexer.verify.impl-spec]
    [blaze.db.test-util :refer [system with-system-data]]
    [blaze.fhir.spec.type :as type]
    [blaze.test-util :refer [satisfies-prop with-system]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [clojure.test.check.properties :as prop]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def ^:private patient-hash
  #blaze/byte-string"C9ADE22457D5AD750735B6B166E3CE8D6878D09B64C2C2868DCB6DE4C9EFBD4F")


(def ^:private observation-hash
  #blaze/byte-string"7B3980C2BFCF43A8CDD61662E1AABDA9CA6431964820BC8D52958AEC9A270378")


(defn resolve-conditional-create [txs cmds]
  (with-system-data [{:blaze.db/keys [node]} system]
    txs
    (impl/resolve-conditional-create (d/db node) cmds)))


(deftest resolve-conditional-create-test
  (testing "non-existing patient"
    (satisfies-prop 10
      (prop/for-all [id (s/gen :blaze.resource/id)
                     identifier (s/gen string?)]
        (given (resolve-conditional-create
                 []
                 [{:op "create"
                   :type "Patient"
                   :id id
                   :hash patient-hash
                   :if-none-exist [["identifier" identifier]]}])
          [0 :op] := "create"
          [0 :type] := "Patient"
          [0 :id] := id
          [0 :hash] := patient-hash))))

  (testing "existing patient"
    (satisfies-prop 10
      (prop/for-all [id (s/gen :blaze.resource/id)
                     identifier (s/gen string?)]
        (given
          (resolve-conditional-create
            [[[:create
               {:fhir/type :fhir/Patient :id id
                :identifier [(type/map->Identifier {:value identifier})]}]]]
            [{:op "create"
              :type "Patient"
              :id "foo"
              :hash patient-hash
              :if-none-exist [["identifier" identifier]]}])
          [0 :op] := "hold"
          [0 :type] := "Patient"
          [0 :id] := id
          [0 :if-none-exist] := [["identifier" identifier]]))))

  (testing "multiple matches"
    (given (resolve-conditional-create
             [[[:create
                {:fhir/type :fhir/Patient :id "0"
                 :gender #fhir/code"male"}]
               [:create
                {:fhir/type :fhir/Patient :id "1"
                 :gender #fhir/code"male"}]]]
             [{:op "create"
               :type "Patient"
               :id "foo"
               :hash patient-hash
               :if-none-exist [["gender" "male"]]}])
      ::anom/category := ::anom/conflict
      ::anom/message := "Conditional create of a Patient with query `gender=male` failed because at least the two matches `Patient/0/_history/1` and `Patient/1/_history/1` were found.")))


(defn resolve-conditional-refs [txs cmds]
  (with-system-data [{:blaze.db/keys [node]} system]
    txs
    (impl/resolve-conditional-refs (d/db node) cmds)))


(deftest resolve-conditional-refs-test
  (testing "unresolvable reference"
    (given (resolve-conditional-refs
             []
             [{:op "create"
               :type "Observation"
               :id "id-230652"
               :hash observation-hash
               :refs [["Patient" [["identifier" "foo"]]]]}])
      ::anom/category := ::anom/conflict
      ::anom/message := "Resolving the conditional reference `Patient?identifier=foo` failed because to match was found."
      :blaze/unresolvable-ref := ["Patient" [["identifier" "foo"]]]))

  (testing "resolves the patient id on existing patient"
    (satisfies-prop 1
      (prop/for-all [patient-id (s/gen :blaze.resource/id)
                     observation-id (s/gen :blaze.resource/id)
                     identifier (s/gen string?)]
        (given (resolve-conditional-refs
                 [[[:create
                    {:fhir/type :fhir/Patient :id patient-id
                     :identifier [(type/map->Identifier {:value identifier})]}]]]
                 [{:op "create"
                   :type "Observation"
                   :id observation-id
                   :hash observation-hash
                   :resource
                   {:fhir/type :fhir/Observation
                    :id observation-id
                    :subject
                    (type/map->Reference
                      {:reference (str "Patient?identifier=" identifier)})}
                   :refs [["Patient" [["identifier" identifier]]]]}])
          [0 :op] := "create"
          [0 :type] := "Observation"
          [0 :id] := observation-id
          [0 :hash] := observation-hash
          [0 :resource] :=
          {:fhir/type :fhir/Observation
           :id observation-id
           :subject
           (type/map->Reference {:reference (str "Patient/" patient-id)})}
          [0 :refs] := [["Patient" patient-id]]
          [0 :ref-mappings] := {["Patient" [["identifier" identifier]]]
                                ["Patient" patient-id]}))))

  (testing "multiple matches"
    (given (resolve-conditional-refs
             [[[:create
                {:fhir/type :fhir/Patient :id "0"
                 :gender #fhir/code"male"}]
               [:create
                {:fhir/type :fhir/Patient :id "1"
                 :gender #fhir/code"male"}]]]
             [{:op "create"
               :type "Observation"
               :id "id-230652"
               :hash observation-hash
               :refs [["Patient" [["gender" "male"]]]]}])
      ::anom/category := ::anom/conflict
      ::anom/message := "Resolving the conditional reference `Patient?gender=male` failed because at least the two matches `Patient/0/_history/1` and `Patient/1/_history/1` were found."
      :blaze/unresolvable-ref := ["Patient" [["gender" "male"]]])))


(deftest detect-duplicate-commands-test
  (satisfies-prop 10
    (prop/for-all [op-1 (s/gen :blaze.db.tx-cmd/op)
                   op-2 (s/gen :blaze.db.tx-cmd/op)
                   type (s/gen :fhir.resource/type)
                   id (s/gen :blaze.resource/id)
                   hash (s/gen :blaze.resource/hash)]
      (given (impl/detect-duplicate-commands
               [{:op op-1
                 :type type
                 :id id
                 :hash hash}
                {:op op-2
                 :type type
                 :id id
                 :hash hash}])
        ::anom/category := ::anom/conflict
        ::anom/message := (format "Duplicate transaction commands `%s %s/%s` and `%s %s/%s`." op-2 type id op-1 type id))))

  (testing "hold"
    (given (impl/detect-duplicate-commands
             [{:op "hold"
               :type "Patient"
               :id "id-151035"
               :hash hash
               :if-none-exist [["identifier" "identifier-151136"]]}
              {:op "delete"
               :type "Patient"
               :id "id-151035"
               :hash hash}])
      ::anom/category := ::anom/conflict
      ::anom/message := "Duplicate transaction commands `delete Patient/id-151035` and `create Patient?identifier=identifier-151136 (resolved to id id-151035)`.")))


(defn verify-commands [txs cmds]
  (with-system-data [{:blaze.db/keys [node]} system]
    txs
    (impl/verify-commands (d/db node) cmds)))


(deftest verify-commands-test
  (testing "can't create existing resource again"
    (satisfies-prop 10
      (prop/for-all [id (s/gen :blaze.resource/id)]
        (given (verify-commands
                 [[[:create {:fhir/type :fhir/Patient :id id}]]]
                 [{:op "create"
                   :type "Patient"
                   :id id
                   :hash patient-hash}])
          ::anom/category := ::anom/conflict
          ::anom/message := (format "Resource `Patient/%s` already exists and can't be created again." id)))))

  (testing "other create"
    (satisfies-prop 10
      (prop/for-all [id (s/gen :blaze.resource/id)]
        (given (verify-commands
                 [[[:create {:fhir/type :fhir/Patient :id id}]]]
                 [{:op "create"
                   :type "Observation"
                   :id id
                   :hash observation-hash}])
          [0 :op] := "create"
          [0 :type] := "Observation"
          [0 :id] := id
          [0 :hash] := observation-hash))))

  (testing "failing precondition on update"
    (satisfies-prop 10
      (prop/for-all [id (s/gen :blaze.resource/id)]
        (given (verify-commands
                 [[[:create {:fhir/type :fhir/Patient :id id}]]
                  [[:put {:fhir/type :fhir/Patient :id id}]]]
                 [{:op "put"
                   :type "Patient"
                   :id id
                   :hash patient-hash
                   :if-match 1}])
          ::anom/category := ::anom/conflict
          ::anom/message := (format "Precondition `W/\"1\"` failed on `Patient/%s`." id)))))

  (testing "matching precondition on update"
    (satisfies-prop 10
      (prop/for-all [id (s/gen :blaze.resource/id)]
        (given (verify-commands
                 [[[:create {:fhir/type :fhir/Patient :id id}]]]
                 [{:op "put"
                   :type "Patient"
                   :id id
                   :hash patient-hash
                   :if-match 1}])
          [0 :op] := "put"
          [0 :type] := "Patient"
          [0 :id] := id
          [0 :hash] := patient-hash))))

  (testing "delete"
    (satisfies-prop 10
      (prop/for-all [id (s/gen :blaze.resource/id)]
        (given (verify-commands
                 []
                 [{:op "delete"
                   :type "Patient"
                   :id id
                   :hash patient-hash}])
          [0 :op] := "delete"
          [0 :type] := "Patient"
          [0 :id] := id
          [0 :hash] := patient-hash)))))


(defn check-referential-integrity [txs cmds]
  (with-system-data [{:blaze.db/keys [node]} system]
    txs
    (impl/check-referential-integrity (d/db node) cmds)))


(deftest check-referential-integrity-test
  (testing "existing resource"
    (satisfies-prop 10
      (prop/for-all [id (s/gen :blaze.resource/id)]
        (is (nil? (check-referential-integrity
                    [[[:create {:fhir/type :fhir/Patient :id id}]]]
                    [{:op "create"
                      :type "Observation"
                      :id "foo"
                      :hash observation-hash
                      :refs [["Patient" id]]}]))))))

  (testing "missing resource"
    (satisfies-prop 10
      (prop/for-all [id (s/gen :blaze.resource/id)]
        (given (check-referential-integrity
                 []
                 [{:op "create"
                   :type "Observation"
                   :id "foo"
                   :hash observation-hash
                   :refs [["Patient" id]]}])
          ::anom/category := ::anom/conflict
          ::anom/message := (format "Referential integrity violated. Resource `Patient/%s` doesn't exist." id)))))

  (testing "existing but deleted resource"
    (satisfies-prop 10
      (prop/for-all [id (s/gen :blaze.resource/id)]
        (given (check-referential-integrity
                 [[[:create {:fhir/type :fhir/Patient :id id}]]
                  [[:delete "Patient" id]]]
                 [{:op "create"
                   :type "Observation"
                   :id "foo"
                   :hash observation-hash
                   :refs [["Patient" id]]}])
          ::anom/category := ::anom/conflict
          ::anom/message := (format "Referential integrity violated. Resource `Patient/%s` doesn't exist." id))))))
