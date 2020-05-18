(ns blaze.db.api-test
  "Main high-level test of all database API functions."
  (:require
    [blaze.coll.core :as coll]
    [blaze.db.api :as d]
    [blaze.db.api-spec]
    [blaze.db.impl.db-spec]
    [blaze.db.indexer.resource :refer [init-resource-indexer]]
    [blaze.db.indexer.tx :refer [init-tx-indexer]]
    [blaze.db.kv.mem :refer [init-mem-kv-store]]
    [blaze.db.node :as node]
    [blaze.db.node-spec]
    [blaze.db.resource-cache :refer [new-resource-cache]]
    [blaze.db.search-param-registry :as sr]
    [blaze.db.tx-log-spec]
    [blaze.db.tx-log.local :refer [init-local-tx-log]]
    [blaze.db.tx-log.local-spec]
    [blaze.executors :as ex]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]
    [manifold.deferred :as md])
  (:import
    [java.time Clock Instant ZoneId]))


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def search-param-registry (sr/init-search-param-registry))


(defn new-node []
  (let [kv-store
        (init-mem-kv-store
          {:search-param-value-index nil
           :resource-value-index nil
           :compartment-search-param-value-index nil
           :compartment-resource-value-index nil
           :compartment-resource-type-index nil
           :resource-index nil
           :active-search-params nil
           :tx-success-index nil
           :tx-error-index nil
           :t-by-instant-index nil
           :resource-as-of-index nil
           :type-as-of-index nil
           :system-as-of-index nil
           :type-stats-index nil
           :system-stats-index nil})
        r-i (init-resource-indexer
              search-param-registry kv-store
              (ex/cpu-bound-pool "resource-indexer-%d"))
        tx-i (init-tx-indexer kv-store)
        clock (Clock/fixed Instant/EPOCH (ZoneId/of "UTC"))
        tx-log (init-local-tx-log r-i 1 tx-i clock)
        resource-cache (new-resource-cache kv-store 0)]
    (node/new-node tx-log tx-i kv-store resource-cache search-param-registry)))


(deftest submit-tx
  (testing "create"
    (let [node (new-node)]
      @(d/submit-tx node [[:create {:resourceType "Patient" :id "0"}]])
      (given (d/resource (d/db node) "Patient" "0")
        :resourceType := "Patient"
        :id := "0"
        [:meta :versionId] := "1"
        [meta :blaze.db/op] := :create)))

  (testing "put"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      (given (d/resource (d/db node) "Patient" "0")
        :resourceType := "Patient"
        :id := "0"
        [:meta :versionId] := "1"
        [meta :blaze.db/op] := :put)))

  (testing "a transaction with duplicate resources fails"
    (testing "two puts"
      (given @(-> (d/submit-tx (new-node) [[:put {:resourceType "Patient" :id "0"}]
                                           [:put {:resourceType "Patient" :id "0"}]])
                  (md/catch' identity))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Duplicate resource `Patient/0`."))

    (testing "one put and one delete"
      (given @(-> (d/submit-tx (new-node) [[:put {:resourceType "Patient" :id "0"}]
                                           [:delete "Patient" "0"]])
                  (md/catch' identity))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Duplicate resource `Patient/0`."))))


(deftest tx
  (let [node (new-node)]
    @(d/submit-tx node [[:put {:resourceType "Patient" :id "id-142136"}]])
    (let [db (d/db node)]
      (given (d/tx db (d/basis-t db))
        :blaze.db.tx/instant := Instant/EPOCH))))



;; ---- Instance-Level Functions ----------------------------------------------

(deftest deleted?
  (testing "a node with a patient"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "id-142136"}]])

      (testing "deleted returns false"
        (is (false? (d/deleted? (d/resource (d/db node) "Patient" "id-142136")))))))

  (testing "a node with a deleted patient"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "id-141820"}]])
      @(d/submit-tx node [[:delete "Patient" "id-141820"]])

      (testing "deleted returns true"
        (is (true? (d/deleted? (d/resource (d/db node) "Patient" "id-141820"))))))))


(deftest resource
  (testing "a new node does not contain a resource"
    (is (nil? (d/resource (d/db (new-node)) "Patient" "foo"))))

  (testing "a node contains a resource after a put transaction"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      (given (d/resource (d/db node) "Patient" "0")
        :resourceType := "Patient"
        :id := "0"
        [:meta :versionId] := "1"
        [meta :blaze.db/tx :blaze.db/t] := 1)))

  (testing "a deleted resource is flagged"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      @(d/submit-tx node [[:delete "Patient" "0"]])
      (given (d/resource (d/db node) "Patient" "0")
        :resourceType := "Patient"
        :id := "0"
        [:meta :versionId] := "2"
        [meta :blaze.db/op] := :delete
        [meta :blaze.db/tx :blaze.db/t] := 2))))



;; ---- Type-Level Functions --------------------------------------------------

(deftest list-resources-and-type-total
  (testing "a new node has no patients"
    (let [db (d/db (new-node))]
      (is (coll/empty? (d/list-resources db "Patient")))
      (is (zero? (d/type-total (d/db (new-node)) "Patient")))))

  (testing "a node with one patient"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])

      (testing "has one list entry"
        (is (= 1 (count (into [] (d/list-resources (d/db node) "Patient")))))
        (is (= 1 (d/type-total (d/db node) "Patient"))))

      (testing "contains that patient"
        (given (into [] (d/list-resources (d/db node) "Patient"))
          [0 :resourceType] := "Patient"
          [0 :id] := "0"
          [0 :meta :versionId] := "1"))))

  (testing "a node with one deleted patient"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      @(d/submit-tx node [[:delete "Patient" "0"]])

      (testing "doesn't contain it in the list"
        (is (coll/empty? (d/list-resources (d/db node) "Patient")))
        (is (zero? (d/type-total (d/db node) "Patient"))))))

  (testing "a node with two patients in two transactions"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "1"}]])

      (testing "has two list entries"
        (is (= 2 (count (into [] (d/list-resources (d/db node) "Patient")))))
        (is (= 2 (d/type-total (d/db node) "Patient"))))

      (testing "contains both patients in id order"
        (given (into [] (d/list-resources (d/db node) "Patient"))
          [0 :resourceType] := "Patient"
          [0 :id] := "0"
          [0 :meta :versionId] := "1"
          [1 :resourceType] := "Patient"
          [1 :id] := "1"
          [1 :meta :versionId] := "2"))

      (testing "it is possible to start with the second patient"
        (given (into [] (d/list-resources (d/db node) "Patient" "1"))
          [0 :resourceType] := "Patient"
          [0 :id] := "1"
          [0 :meta :versionId] := "2"))

      (testing "overshooting the start-id returns an empty collection"
        (is (coll/empty? (d/list-resources (d/db node) "Patient" "2"))))))

  (testing "a node with two patients in one transaction"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]
                          [:put {:resourceType "Patient" :id "1"}]])

      (testing "has two list entries"
        (is (= 2 (count (into [] (d/list-resources (d/db node) "Patient")))))
        (is (= 2 (d/type-total (d/db node) "Patient"))))

      (testing "contains both patients in id order"
        (given (into [] (d/list-resources (d/db node) "Patient"))
          [0 :resourceType] := "Patient"
          [0 :id] := "0"
          [0 :meta :versionId] := "1"
          [1 :resourceType] := "Patient"
          [1 :id] := "1"
          [1 :meta :versionId] := "1"))

      (testing "it is possible to start with the second patient"
        (given (into [] (d/list-resources (d/db node) "Patient" "1"))
          [0 :resourceType] := "Patient"
          [0 :id] := "1"
          [0 :meta :versionId] := "1"))

      (testing "overshooting the start-id returns an empty collection"
        (is (coll/empty? (d/list-resources (d/db node) "Patient" "2"))))))

  (testing "a node with one updated patient"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0" :active false}]])
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0" :active true}]])

      (testing "has one list entry"
        (is (= 1 (count (into [] (d/list-resources (d/db node) "Patient")))))
        (is (= 1 (d/type-total (d/db node) "Patient"))))

      (testing "contains the updated patient"
        (given (into [] (d/list-resources (d/db node) "Patient"))
          [0 :resourceType] := "Patient"
          [0 :id] := "0"
          [0 :active] := true
          [0 :meta :versionId] := "2"))))

  (testing "a node with resources of different types"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      @(d/submit-tx node [[:put {:resourceType "Observation" :id "0"}]])

      (testing "has one patient list entry"
        (is (= 1 (count (into [] (d/list-resources (d/db node) "Patient")))))
        (is (= 1 (d/type-total (d/db node) "Patient"))))

      (testing "has one observation list entry"
        (is (= 1 (count (into [] (d/list-resources (d/db node) "Observation")))))
        (is (= 1 (d/type-total (d/db node) "Observation"))))))

  (testing "the database is immutable"
    (testing "while updating a patient"
      (let [node (new-node)]
        @(d/submit-tx node [[:put {:resourceType "Patient" :id "0" :active false}]])

        (let [db (d/db node)]
          @(d/submit-tx node [[:put {:resourceType "Patient" :id "0" :active true}]])

          (testing "the original database"
            (testing "has still only one list entry"
              (is (= 1 (count (into [] (d/list-resources db "Patient")))))
              (is (= 1 (d/type-total db "Patient"))))

            (testing "contains still the original patient"
              (given (into [] (d/list-resources db "Patient"))
                [0 :resourceType] := "Patient"
                [0 :id] := "0"
                [0 :active] := false
                [0 :meta :versionId] := "1"))))))

    (testing "while adding another patient"
      (let [node (new-node)]
        @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])

        (let [db (d/db node)]
          @(d/submit-tx node [[:put {:resourceType "Patient" :id "1"}]])

          (testing "the original database"
            (testing "has still only one list entry"
              (is (= 1 (count (into [] (d/list-resources db "Patient")))))
              (is (= 1 (d/type-total db "Patient"))))

            (testing "contains still the first patient"
              (given (into [] (d/list-resources db "Patient"))
                [0 :resourceType] := "Patient"
                [0 :id] := "0"
                [0 :meta :versionId] := "1"))))))))


(deftest type-query
  (testing "a new node has no patients"
    (let [db (d/db (new-node))]
      (is (coll/empty? (d/type-query db "Patient" [["gender" "male"]])))))

  (testing "a node with one patient"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0" :active true}]])

      (testing "the patient can be found"
        (given (into [] (d/type-query (d/db node) "Patient" [["active" "true"]]))
          [0 :resourceType] := "Patient"
          [0 :id] := "0"))))

  (testing "a node with two patients in one transaction"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0" :active true}]
                          [:put {:resourceType "Patient" :id "1" :active false}]])

      (testing "only the active patient will be found"
        (given (into [] (d/type-query (d/db node) "Patient" [["active" "true"]]))
          [0 :resourceType] := "Patient"
          [0 :id] := "0"
          1 := nil))

      (testing "only the non-active patient will be found"
        (given (into [] (d/type-query (d/db node) "Patient" [["active" "false"]]))
          [0 :resourceType] := "Patient"
          [0 :id] := "1"
          1 := nil))

      (testing "both patients will be found"
        (given (into [] (d/type-query (d/db node) "Patient" [["active" "true" "false"]]))
          [0 :resourceType] := "Patient"
          [0 :id] := "0"
          [1 :resourceType] := "Patient"
          [1 :id] := "1"))))

  (testing "does not find the deleted male patient"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0" :active true}]
                          [:put {:resourceType "Patient" :id "1" :active true}]])
      @(d/submit-tx node [[:delete "Patient" "1"]])

      (given (into [] (d/type-query (d/db node) "Patient" [["active" "true"]]))
        [0 :resourceType] := "Patient"
        [0 :id] := "0"
        1 := nil)))

  (testing "Patient"
    (let [node (new-node)]
      @(d/submit-tx
         node
         [[:put {:resourceType "Patient"
                 :id "id-0"
                 :meta
                 {:profile
                  ["profile-uri-145024"]}
                 :identifier
                 [{:value "0"}]
                 :active false
                 :gender "male"
                 :birthDate "2020-02-08"
                 :deceasedBoolean true
                 :address
                 [{:line ["Philipp-Rosenthal-Straße 27"]
                   :city "Leipzig"}]
                 :name
                 [{:family "Müller"}]}]
          [:put {:resourceType "Patient"
                 :id "id-1"
                 :active true
                 :gender "female"
                 :birthDate "2020-02"
                 :address
                 [{:city "Berlin"}]
                 :telecom
                 [{:system "email"
                   :value "foo@bar.baz"}
                  {:system "phone"
                   :value "0815"}]}]
          [:put {:resourceType "Patient"
                 :id "id-2"
                 :active false
                 :gender "female"
                 :birthDate "2020"
                 :deceasedDateTime "2020-03"
                 :address
                 [{:line ["Liebigstraße 20a"]
                   :city "Leipzig"}]
                 :name
                 [{:family "Schmidt"}]}]])

      (testing "_profile"
        (given (into [] (d/type-query (d/db node) "Patient"
                                      [["_profile" "profile-uri-145024"]]))
          [0 :id] := "id-0"
          1 := nil))

      (testing "active"
        (given (into [] (d/type-query (d/db node) "Patient" [["active" "true"]]))
          [0 :id] := "id-1"
          1 := nil))

      (testing "address with line"
        (testing "in first position"
          (given (into [] (d/type-query (d/db node) "Patient"
                                        [["address" "Liebigstraße"]]))
            [0 :id] := "id-2"
            1 := nil))

        (testing "in second position"
          (given (into [] (d/type-query (d/db node) "Patient" [["gender" "female"]
                                                               ["address" "Liebigstraße"]]))
            [0 :id] := "id-2"
            1 := nil)))

      (testing "address with city"
        (given (into [] (d/type-query (d/db node) "Patient" [["address" "Leipzig"]]))
          [0 :id] := "id-0"
          [1 :id] := "id-2"
          2 := nil))

      (testing "address-city full"
        (given (into [] (d/type-query (d/db node) "Patient" [["address-city" "Leipzig"]]))
          [0 :id] := "id-0"
          [1 :id] := "id-2"
          2 := nil))

      (testing "address-city prefix"
        (given (into [] (d/type-query (d/db node) "Patient" [["address-city" "Leip"]]))
          [0 :id] := "id-0"
          [1 :id] := "id-2"
          2 := nil))

      (testing "address-city and family prefix"
        (given (into [] (d/type-query (d/db node) "Patient" [["address-city" "Leip"]
                                                             ["family" "Sch"]]))
          [0 :id] := "id-2"
          1 := nil))

      (testing "address-city and gender"
        (given (into [] (d/type-query (d/db node) "Patient" [["address-city" "Leipzig"]
                                                             ["gender" "female"]]))
          [0 :id] := "id-2"
          1 := nil))

      (testing "birthdate YYYYMMDD"
        (given (into [] (d/type-query (d/db node) "Patient" [["birthdate" "2020-02-08"]]))
          [0 :id] := "id-0"
          1 := nil))

      (testing "birthdate YYYYMM"
        (given (into [] (d/type-query (d/db node) "Patient" [["birthdate" "2020-02"]]))
          [0 :id] := "id-1"
          [1 :id] := "id-0"
          2 := nil))

      (testing "birthdate YYYY"
        (given (into [] (d/type-query (d/db node) "Patient" [["birthdate" "2020"]]))
          [0 :id] := "id-2"
          [1 :id] := "id-1"
          [2 :id] := "id-0"))

      (testing "birthdate with `eq` prefix"
        (given (into [] (d/type-query (d/db node) "Patient" [["birthdate" "eq2020-02-08"]]))
          [0 :id] := "id-0"
          1 := nil))

      (testing "birthdate with `ne` prefix is unsupported"
        (try
          (into [] (d/type-query (d/db node) "Patient" [["birthdate" "ne2020-02-08"]]))
          (catch Exception e
            (given (ex-data e)
              ::anom/category := ::anom/unsupported))))

      (testing "birthdate with `ge` prefix"
        (testing "finds equal date"
          (given (into [] (d/type-query (d/db node) "Patient" [["birthdate" "ge2020-02-08"]]))
            [0 :id] := "id-0"
            [0 :birthDate] := "2020-02-08"
            1 := nil))

        (testing "finds greater date"
          (given (into [] (d/type-query (d/db node) "Patient" [["birthdate" "ge2020-02-07"]]))
            [0 :id] := "id-0"
            [0 :birthDate] := "2020-02-08"
            1 := nil))

        (testing "finds more precise dates"
          (given (into [] (d/type-query (d/db node) "Patient" [["birthdate" "ge2020-02"]]))
            [0 :id] := "id-1"
            [0 :birthDate] := "2020-02"
            [1 :id] := "id-0"
            [1 :birthDate] := "2020-02-08"
            2 := nil)))

      (testing "birthdate with `le` prefix"
        (testing "finds equal date"
          (given (into [] (d/type-query (d/db node) "Patient" [["birthdate" "le2020-02-08"]]))
            [0 :id] := "id-0"
            [0 :birthDate] := "2020-02-08"
            1 := nil))

        (testing "finds less date"
          (given (into [] (d/type-query (d/db node) "Patient" [["birthdate" "le2020-02-09"]]))
            [0 :id] := "id-0"
            [0 :birthDate] := "2020-02-08"
            1 := nil))

        (testing "finds more precise dates"
          (given (into [] (d/type-query (d/db node) "Patient" [["birthdate" "le2020-03"]]))
            [0 :id] := "id-1"
            [0 :birthDate] := "2020-02"
            [1 :id] := "id-0"
            [1 :birthDate] := "2020-02-08"
            2 := nil)))

      (testing "deceased"
        (given (into [] (d/type-query (d/db node) "Patient" [["deceased" "true"]]))
          [0 :id] := "id-0"
          [1 :id] := "id-2"
          2 := nil))

      (testing "email"
        (given (into [] (d/type-query (d/db node) "Patient" [["email" "foo@bar.baz"]]))
          [0 :id] := "id-1"
          1 := nil))

      (testing "family lower-case"
        (given (into [] (d/type-query (d/db node) "Patient" [["family" "schmidt"]]))
          [0 :id] := "id-2"
          1 := nil))

      (testing "gender"
        (given (into [] (d/type-query (d/db node) "Patient" [["gender" "male"]]))
          [0 :id] := "id-0"
          1 := nil))

      (testing "identifier"
        (given (into [] (d/type-query (d/db node) "Patient" [["identifier" "0"]]))
          [0 :id] := "id-0"
          1 := nil))

      (testing "telecom"
        (given (into [] (d/type-query (d/db node) "Patient" [["telecom" "0815"]]))
          [0 :id] := "id-1"
          1 := nil))))

  (testing "Practitioner"
    (let [node (new-node)]
      @(d/submit-tx
         node
         [[:put {:resourceType "Practitioner"
                 :id "id-0"
                 :name
                 [{:family "Müller"
                   :given ["Hans" "Martin"]}]}]])

      (testing "name"
        (testing "using family"
          (given (into [] (d/type-query (d/db node) "Practitioner" [["name" "müller"]]))
            [0 :id] := "id-0"
            1 := nil))

        (testing "using first given"
          (given (into [] (d/type-query (d/db node) "Practitioner" [["name" "hans"]]))
            [0 :id] := "id-0"
            1 := nil))

        (testing "using second given"
          (given (into [] (d/type-query (d/db node) "Practitioner" [["name" "martin"]]))
            [0 :id] := "id-0"
            1 := nil)))))

  (testing "Specimen"
    (let [node (new-node)]
      @(d/submit-tx
         node
         [[:put {:resourceType "Specimen"
                 :id "id-0"
                 :type
                 {:coding
                  [{:system "https://fhir.bbmri.de/CodeSystem/SampleMaterialType"
                    :code "dna"}]}
                 :collection
                 {:bodySite
                  {:coding
                   [{:system "urn:oid:2.16.840.1.113883.6.43.1"
                     :code "C77.4"}]}}}]])

      (testing "bodysite"
        (testing "using system|code"
          (given (into [] (d/type-query (d/db node) "Specimen" [["bodysite" "urn:oid:2.16.840.1.113883.6.43.1|C77.4"]]))
            [0 :id] := "id-0"
            1 := nil))

        (testing "using code"
          (given (into [] (d/type-query (d/db node) "Specimen" [["bodysite" "C77.4"]]))
            [0 :id] := "id-0"
            1 := nil))

        (testing "using system|"
          (given (into [] (d/type-query (d/db node) "Specimen" [["bodysite" "urn:oid:2.16.840.1.113883.6.43.1|"]]))
            [0 :id] := "id-0"
            1 := nil)))

      (testing "type"
        (given (into [] (d/type-query (d/db node) "Specimen" [["type" "https://fhir.bbmri.de/CodeSystem/SampleMaterialType|dna"]]))
          [0 :id] := "id-0"
          1 := nil))

      (testing "bodysite and type"
        (testing "using system|code"
          (given (into [] (d/type-query (d/db node) "Specimen" [["bodysite" "urn:oid:2.16.840.1.113883.6.43.1|C77.4"]
                                                                ["type" "https://fhir.bbmri.de/CodeSystem/SampleMaterialType|dna"]]))
            [0 :id] := "id-0"
            1 := nil))

        (testing "using code"
          (given (into [] (d/type-query (d/db node) "Specimen" [["bodysite" "urn:oid:2.16.840.1.113883.6.43.1|C77.4"]
                                                                ["type" "dna"]]))
            [0 :id] := "id-0"
            1 := nil))

        (testing "using system|"
          (given (into [] (d/type-query (d/db node) "Specimen" [["bodysite" "urn:oid:2.16.840.1.113883.6.43.1|C77.4"]
                                                                ["type" "https://fhir.bbmri.de/CodeSystem/SampleMaterialType|"]]))
            [0 :id] := "id-0"
            1 := nil))

        (testing "does not match"
          (testing "using system|code"
            (given (into [] (d/type-query (d/db node) "Specimen" [["bodysite" "urn:oid:2.16.840.1.113883.6.43.1|C77.4"]
                                                                  ["type" "https://fhir.bbmri.de/CodeSystem/SampleMaterialType|urine"]]))
              0 := nil))))))

  (testing "ActivityDefinition"
    (let [node (new-node)]
      @(d/submit-tx
         node
         [[:put {:resourceType "ActivityDefinition"
                 :id "id-0"
                 :url "url-111619"
                 :description "desc-121208"}]
          [:put {:resourceType "ActivityDefinition"
                 :id "id-1"
                 :url "url-111721"}]])

      (testing "url"
        (given (into [] (d/type-query (d/db node) "ActivityDefinition" [["url" "url-111619"]]))
          [0 :id] := "id-0"
          1 := nil))

      (testing "description"
        (given (into [] (d/type-query (d/db node) "ActivityDefinition" [["description" "desc-121208"]]))
          [0 :id] := "id-0"
          1 := nil))))

  (testing "CodeSystem"
    (let [node (new-node)]
      @(d/submit-tx
         node
         [[:put {:resourceType "CodeSystem"
                 :id "id-0"
                 :version "version-122443"}]
          [:put {:resourceType "CodeSystem"
                 :id "id-1"
                 :version "version-122456"}]])

      (testing "version"
        (given (into [] (d/type-query (d/db node) "CodeSystem" [["version" "version-122443"]]))
          [0 :id] := "id-0"
          1 := nil))))

  (testing "MedicationKnowledge"
    (let [node (new-node)]
      @(d/submit-tx
         node
         [[:put {:resourceType "MedicationKnowledge"
                 :id "id-0"
                 :monitoringProgram
                 [{:name "name-123124"}]}]
          [:put {:resourceType "MedicationKnowledge"
                 :id "id-1"}]])

      (testing "monitoring-program-name"
        (given (into [] (d/type-query (d/db node) "MedicationKnowledge" [["monitoring-program-name" "name-123124"]]))
          [0 :id] := "id-0"
          1 := nil))))

  (testing "Condition"
    (let [node (new-node)]
      @(d/submit-tx
         node
         [[:put {:resourceType "Patient"
                 :id "id-0"}]
          [:put {:resourceType "Condition"
                 :id "id-0"
                 :subject
                 {:reference "Patient/id-0"}}]
          [:put {:resourceType "Condition"
                 :id "id-1"}]])

      (testing "patient"
        (given (into [] (d/type-query (d/db node) "Condition" [["patient" "id-0"]]))
          [0 :id] := "id-0"
          1 := nil))))

  (testing "Observation"
    (let [node (new-node)]
      @(d/submit-tx
         node
         [[:put {:resourceType "Observation"
                 :id "id-0"
                 :valueQuantity
                 {:code "kg/m2"
                  :unit "kg/m²"
                  :system "http://unitsofmeasure.org"
                  :value 23.42M}}]])

      (testing "without unit"
        (let [clauses [["value-quantity" "23.42"]]]
          (given (into [] (d/type-query (d/db node) "Observation" clauses))
            [0 :id] := "id-0"
            1 := nil)))

      (testing "with minimal unit"
        (let [clauses [["value-quantity" "23.42|kg/m2"]]]
          (given (into [] (d/type-query (d/db node) "Observation" clauses))
            [0 :id] := "id-0"
            1 := nil)))

      (testing "with human unit"
        (let [clauses [["value-quantity" "23.42|kg/m²"]]]
          (given (into [] (d/type-query (d/db node) "Observation" clauses))
            [0 :id] := "id-0"
            1 := nil)))

      (testing "with full unit"
        (let [clauses [["value-quantity" "23.42|http://unitsofmeasure.org|kg/m2"]]]
          (given (into [] (d/type-query (d/db node) "Observation" clauses))
            [0 :id] := "id-0"
            1 := nil)))))

  (testing "MeasureReport"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "MeasureReport"
                                 :id "foo"
                                 :measure "measure-url-181106"}]])

      (testing "measure"
        (let [clauses [["measure" "measure-url-181106"]]]
          (is (= 1 (count (into [] (d/type-query (d/db node) "MeasureReport" clauses))))))))))



;; ---- System-Level Functions ------------------------------------------------

(deftest system-list-and-total
  (testing "a new node has no resources"
    (is (zero? (d/system-total (d/db (new-node))))))

  (testing "a node with one patient"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])

      (testing "has one list entry"
        (is (= 1 (count (into [] (d/system-list (d/db node))))))
        (is (= 1 (d/system-total (d/db node)))))

      (testing "contains that patient"
        (given (into [] (d/system-list (d/db node)))
          [0 :resourceType] := "Patient"
          [0 :id] := "0"
          [0 :meta :versionId] := "1"))))

  (testing "a node with one deleted patient"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      @(d/submit-tx node [[:delete "Patient" "0"]])

      (testing "doesn't contain it in the list"
        (is (coll/empty? (d/system-list (d/db node))))
        (is (zero? (d/system-total (d/db node)))))))

  (testing "a node with two resources in two transactions"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      @(d/submit-tx node [[:put {:resourceType "Observation" :id "0"}]])

      (testing "has two list entries"
        (is (= 2 (count (into [] (d/system-list (d/db node))))))
        (is (= 2 (d/system-total (d/db node)))))

      (testing "contains both resources in the order of their type hashes"
        (given (into [] (d/system-list (d/db node)))
          [0 :resourceType] := "Observation"
          [0 :id] := "0"
          [0 :meta :versionId] := "2"
          [1 :resourceType] := "Patient"
          [1 :id] := "0"
          [1 :meta :versionId] := "1"))

      (testing "it is possible to start with the patient"
        (given (into [] (d/system-list (d/db node) "Patient"))
          [0 :resourceType] := "Patient"
          [0 :id] := "0"
          [0 :meta :versionId] := "1"))

      (testing "starting with Measure also returns the patient,
                because in type hash order, Measure comes before
                Patient but after Observation"
        (given (into [] (d/system-list (d/db node) "Measure"))
          [0 :resourceType] := "Patient"
          [0 :id] := "0"
          [0 :meta :versionId] := "1"))

      (testing "overshooting the start-id returns an empty collection"
        (is (coll/empty? (d/system-list (d/db node) "Patient" "1")))))))



;; ---- Compartment-Level Functions -------------------------------------------

(deftest list-compartment-resources
  (testing "a new node has an empty list of resources in the Patient/0 compartment"
    (let [db (d/db (new-node))]
      (is (coll/empty? (d/list-compartment-resources db "Patient" "0" "Observation")))))

  (testing "a node contains one Observation in the Patient/0 compartment"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      @(d/submit-tx node [[:put {:resourceType "Observation" :id "0"
                                 :subject {:reference "Patient/0"}}]])
      (given (coll/first (d/list-compartment-resources (d/db node) "Patient" "0" "Observation"))
        :resourceType := "Observation"
        :id := "0"
        [:meta :versionId] := "2")))

  (testing "a node contains two resources in the Patient/0 compartment"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      @(d/submit-tx node [[:put {:resourceType "Observation" :id "0"
                                 :subject {:reference "Patient/0"}}]])
      @(d/submit-tx node [[:put {:resourceType "Observation" :id "1"
                                 :subject {:reference "Patient/0"}}]])
      (given (into [] (d/list-compartment-resources (d/db node) "Patient" "0" "Observation"))
        [0 :resourceType] := "Observation"
        [0 :id] := "0"
        [0 :meta :versionId] := "2"
        [1 :resourceType] := "Observation"
        [1 :id] := "1"
        [1 :meta :versionId] := "3")))

  (testing "a deleted resource does not show up"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      @(d/submit-tx node [[:put {:resourceType "Observation" :id "0"
                                 :subject {:reference "Patient/0"}}]])
      @(d/submit-tx node [[:delete "Observation" "0"]])
      (is (coll/empty? (d/list-compartment-resources (d/db node) "Patient" "0" "Observation")))))

  (testing "it is possible to start at a later id"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      @(d/submit-tx node [[:put {:resourceType "Observation" :id "0"
                                 :subject {:reference "Patient/0"}}]])
      @(d/submit-tx node [[:put {:resourceType "Observation" :id "1"
                                 :subject {:reference "Patient/0"}}]])
      @(d/submit-tx node [[:put {:resourceType "Observation" :id "2"
                                 :subject {:reference "Patient/0"}}]])
      (given (into [] (d/list-compartment-resources
                        (d/db node) "Patient" "0" "Observation" "1"))
        [0 :resourceType] := "Observation"
        [0 :id] := "1"
        [0 :meta :versionId] := "3"
        [1 :resourceType] := "Observation"
        [1 :id] := "2"
        [1 :meta :versionId] := "4"
        2 := nil)))

  (testing "Unknown compartment is not a problem"
    (is (coll/empty? (d/list-compartment-resources (d/db (new-node)) "foo" "bar" "Condition")))))


(deftest compartment-query
  (testing "a new node has an empty list of resources in the Patient/0 compartment"
    (let [db (d/db (new-node))]
      (is (coll/empty? (d/compartment-query db "Patient" "0" "Observation" [["code" "foo"]])))))

  (testing "returns the Observation in the Patient/0 compartment"
    (let [node (new-node)]
      @(d/submit-tx
         node
         [[:put {:resourceType "Patient" :id "0"}]
          [:put {:resourceType "Observation" :id "0"
                 :subject {:reference "Patient/0"}
                 :code
                 {:coding
                  [{:system "system-191514"
                    :code "code-191518"}]}}]])
      (given (coll/first (d/compartment-query
                           (d/db node) "Patient" "0" "Observation"
                           [["code" "system-191514|code-191518"]]))
        :resourceType := "Observation"
        :id := "0")))

  (testing "returns only the matching Observation in the Patient/0 compartment"
    (let [observation
          (fn [id code]
            {:resourceType "Observation" :id id
             :subject {:reference "Patient/0"}
             :code
             {:coding
              [{:system "system"
                :code code}]}})
          node (new-node)]
      @(d/submit-tx
         node
         [[:put {:resourceType "Patient" :id "0"}]
          [:put (observation "0" "code-1")]
          [:put (observation "1" "code-2")]
          [:put (observation "2" "code-3")]])
      (given (into [] (d/compartment-query
                        (d/db node) "Patient" "0" "Observation"
                        [["code" "system|code-2"]]))
        [0 :resourceType] := "Observation"
        [0 :id] := "1"
        1 := nil)))

  (testing "returns only the matching versions"
    (let [observation
          (fn [id code]
            {:resourceType "Observation" :id id
             :subject {:reference "Patient/0"}
             :code
             {:coding
              [{:system "system"
                :code code}]}})
          node (new-node)]
      @(d/submit-tx
         node
         [[:put {:resourceType "Patient" :id "0"}]
          [:put (observation "0" "code-1")]
          [:put (observation "1" "code-2")]
          [:put (observation "2" "code-2")]
          [:put (observation "3" "code-2")]])
      @(d/submit-tx
         node
         [[:put (observation "0" "code-2")]
          [:put (observation "1" "code-1")]
          [:put (observation "3" "code-2")]])
      (given (into [] (d/compartment-query
                        (d/db node) "Patient" "0" "Observation"
                        [["code" "system|code-2"]]))
        [0 :resourceType] := "Observation"
        [0 :id] := "0"
        [0 :meta :versionId] := "2"
        [1 :resourceType] := "Observation"
        [1 :id] := "2"
        [1 :meta :versionId] := "1"
        [2 :id] := "3"
        [2 :meta :versionId] := "2"
        3 := nil)))

  (testing "doesn't return deleted resources"
    (let [node (new-node)]
      @(d/submit-tx
         node
         [[:put {:resourceType "Patient" :id "0"}]
          [:put {:resourceType "Observation" :id "0"
                 :subject {:reference "Patient/0"}
                 :code
                 {:coding
                  [{:system "system"
                    :code "code"}]}}]])
      @(d/submit-tx
         node
         [[:delete "Observation" "0"]])
      (is (coll/empty? (d/compartment-query
                         (d/db node) "Patient" "0" "Observation"
                         [["code" "system|code"]])))))

  (testing "finds resources after deleted ones"
    (let [observation
          (fn [id code]
            {:resourceType "Observation" :id id
             :subject {:reference "Patient/0"}
             :code
             {:coding
              [{:system "system"
                :code code}]}})
          node (new-node)]
      @(d/submit-tx
         node
         [[:put {:resourceType "Patient" :id "0"}]
          [:put (observation "0" "code")]
          [:put (observation "1" "code")]])
      @(d/submit-tx
         node
         [[:delete "Observation" "0"]])
      (given (into [] (d/compartment-query
                        (d/db node) "Patient" "0" "Observation"
                        [["code" "system|code"]]))
        [0 :resourceType] := "Observation"
        [0 :id] := "1"
        1 := nil)))

  (testing "returns the Observation in the Patient/0 compartment on the second criteria value"
    (let [node (new-node)]
      @(d/submit-tx
         node
         [[:put {:resourceType "Patient" :id "0"}]
          [:put {:resourceType "Observation" :id "0"
                 :subject {:reference "Patient/0"}
                 :code
                 {:coding
                  [{:system "system-191514"
                    :code "code-191518"}]}}]])
      (given (coll/first (d/compartment-query
                           (d/db node) "Patient" "0" "Observation"
                           [["code" "foo|bar" "system-191514|code-191518"]]))
        :resourceType := "Observation"
        :id := "0")))

  (testing "returns nothing because of non-matching second criteria"
    (let [node (new-node)]
      @(d/submit-tx
         node
         [[:put {:resourceType "Patient" :id "0"}]
          [:put {:resourceType "Observation" :id "0"
                 :subject {:reference "Patient/0"}
                 :code
                 {:coding
                  [{:system "system-191514"
                    :code "code-191518"}]}
                 :valueQuantity
                 {:code "kg/m2"
                  :unit "kg/m²"
                  :system "http://unitsofmeasure.org"
                  :value 42M}}]])
      (is (coll/empty?
            (d/compartment-query
              (d/db node) "Patient" "0" "Observation"
              [["code" "system-191514|code-191518"]
               ["value-quantity" "23"]])))))

  (testing "returns an anomaly on unknown search param code"
    (given (d/compartment-query (d/db (new-node)) "Patient" "0" "Observation"
                                [["unknown" "foo"]])
      ::anom/category := ::anom/not-found))

  (testing "Unknown compartment is not a problem"
    (let [node (new-node)]
      (is (coll/empty? (d/compartment-query (d/db node) "foo" "bar" "Condition" [["code" "baz"]])))))

  (testing "Unknown type is not a problem"
    (let [node (new-node)]
      @(d/submit-tx
         node
         [[:put {:resourceType "Patient"
                 :id "id-0"}]])

      (given (d/compartment-query (d/db node) "Patient" "id-0" "Foo" [["code" "baz"]])
        ::anom/category := ::anom/not-found
        ::anom/message := "search-param with code `code` and type `Foo` not found")))

  (testing "Patient Compartment"
    (testing "Condition"
      (let [node (new-node)]
        @(d/submit-tx
           node
           [[:put {:resourceType "Patient"
                   :id "id-0"}]
            [:put {:resourceType "Condition"
                   :id "id-1"
                   :code
                   {:coding
                    [{:system "system-a-122701"
                      :code "code-a-122652"}]}
                   :subject
                   {:reference "Patient/id-0"}}]
            [:put {:resourceType "Condition"
                   :id "id-2"
                   :code
                   {:coding
                    [{:system "system-b-122747"
                      :code "code-b-122750"}]}
                   :subject
                   {:reference "Patient/id-0"}}]])

        (testing "code"
          (given (into [] (d/compartment-query (d/db node) "Patient" "id-0" "Condition" [["code" "system-a-122701|code-a-122652"]]))
            [0 :id] := "id-1"
            1 := nil))))))



;; ---- Instance-Level History Functions --------------------------------------

(deftest instance-history
  (testing "a new node has an empty instance history"
    (let [db (d/db (new-node))]
      (is (coll/empty? (d/instance-history db "Patient" "0")))
      (is (zero? (d/total-num-of-instance-changes db "Patient" "0")))))

  (testing "a node with one patient"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])

      (testing "has one history entry"
        (is (= 1 (count (into [] (d/instance-history (d/db node) "Patient" "0")))))
        (is (= 1 (d/total-num-of-instance-changes (d/db node) "Patient" "0"))))

      (testing "contains that patient"
        (given (into [] (d/instance-history (d/db node) "Patient" "0"))
          [0 :resourceType] := "Patient"
          [0 :id] := "0"
          [0 :meta :versionId] := "1"))

      (testing "has an empty history on another patient"
        (is (coll/empty? (d/instance-history (d/db node) "Patient" "1")))
        (is (zero? (d/total-num-of-instance-changes (d/db node) "Patient" "1"))))))

  (testing "a node with one deleted patient"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      @(d/submit-tx node [[:delete "Patient" "0"]])

      (testing "has two history entries"
        (is (= 2 (count (into [] (d/instance-history (d/db node) "Patient" "0")))))
        (is (= 2 (d/total-num-of-instance-changes (d/db node) "Patient" "0"))))

      (testing "the first history entry is the patient marked as deleted"
        (given (into [] (d/instance-history (d/db node) "Patient" "0"))
          [0 :resourceType] := "Patient"
          [0 :id] := "0"
          [0 :meta :versionId] := "2"
          [0 meta :blaze.db/op] := :delete))

      (testing "the second history entry is the patient marked as created"
        (given (into [] (d/instance-history (d/db node) "Patient" "0"))
          [1 :resourceType] := "Patient"
          [1 :id] := "0"
          [1 :meta :versionId] := "1"
          [1 meta :blaze.db/op] := :put))))

  (testing "a node with two versions"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0" :active true}]])
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0" :active false}]])

      (testing "has two history entries"
        (is (= 2 (count (into [] (d/instance-history (d/db node) "Patient" "0")))))
        (is (= 2 (d/total-num-of-instance-changes (d/db node) "Patient" "0"))))

      (testing "contains both versions in reverse transaction order"
        (given (into [] (d/instance-history (d/db node) "Patient" "0"))
          [0 :active] := false
          [1 :active] := true))

      (testing "it is possible to start with the older transaction"
        (given (into [] (d/instance-history (d/db node) "Patient" "0" 1))
          [0 :active] := true))

      (testing "overshooting the start-t returns an empty collection"
        (is (coll/empty? (d/instance-history (d/db node) "Patient" "0" 0))))))

  (testing "the database is immutable"
    (testing "while updating a patient"
      (let [node (new-node)]
        @(d/submit-tx node [[:put {:resourceType "Patient" :id "0" :active false}]])

        (let [db (d/db node)]
          @(d/submit-tx node [[:put {:resourceType "Patient" :id "0" :active true}]])

          (testing "the original database"
            (testing "has still only one history entry"
              (is (= 1 (count (into [] (d/instance-history db "Patient" "0")))))
              (is (= 1 (d/total-num-of-instance-changes db "Patient" "0"))))

            (testing "contains still the original patient"
              (given (into [] (d/instance-history db "Patient" "0"))
                [0 :resourceType] := "Patient"
                [0 :id] := "0"
                [0 :active] := false
                [0 :meta :versionId] := "1"))))))))



;; ---- Type-Level History Functions ------------------------------------------

(deftest type-history
  (testing "a new node has an empty type history"
    (let [db (d/db (new-node))]
      (is (coll/empty? (d/type-history db "Patient")))
      (is (zero? (d/total-num-of-type-changes db "Patient")))))

  (testing "a node with one patient"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])

      (testing "has one history entry"
        (is (= 1 (count (into [] (d/type-history (d/db node) "Patient")))))
        (is (= 1 (d/total-num-of-type-changes (d/db node) "Patient"))))

      (testing "contains that patient"
        (given (into [] (d/type-history (d/db node) "Patient"))
          [0 :resourceType] := "Patient"
          [0 :id] := "0"
          [0 :meta :versionId] := "1"))

      (testing "has an empty observation history"
        (is (coll/empty? (d/type-history (d/db node) "Observation")))
        (is (zero? (d/total-num-of-type-changes (d/db node) "Observation"))))))

  (testing "a node with one deleted patient"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      @(d/submit-tx node [[:delete "Patient" "0"]])

      (testing "has two history entries"
        (is (= 2 (count (into [] (d/type-history (d/db node) "Patient")))))
        (is (= 2 (d/total-num-of-type-changes (d/db node) "Patient"))))

      (testing "the first history entry is the patient marked as deleted"
        (given (into [] (d/type-history (d/db node) "Patient"))
          [0 :resourceType] := "Patient"
          [0 :id] := "0"
          [0 :meta :versionId] := "2"
          [0 meta :blaze.db/op] := :delete))

      (testing "the second history entry is the patient marked as created"
        (given (into [] (d/type-history (d/db node) "Patient"))
          [1 :resourceType] := "Patient"
          [1 :id] := "0"
          [1 :meta :versionId] := "1"
          [1 meta :blaze.db/op] := :put))))

  (testing "a node with two patients in two transactions"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "1"}]])

      (testing "has two history entries"
        (is (= 2 (count (into [] (d/type-history (d/db node) "Patient")))))
        (is (= 2 (d/total-num-of-type-changes (d/db node) "Patient"))))

      (testing "contains both patients in reverse transaction order"
        (given (into [] (d/type-history (d/db node) "Patient"))
          [0 :id] := "1"
          [1 :id] := "0"))

      (testing "it is possible to start with the older transaction"
        (given (into [] (d/type-history (d/db node) "Patient" 1))
          [0 :id] := "0"))

      (testing "overshooting the start-t returns an empty collection"
        (is (coll/empty? (d/type-history (d/db node) "Patient" 0))))))

  (testing "a node with two patients in one transaction"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]
                          [:put {:resourceType "Patient" :id "1"}]])

      (testing "has two history entries"
        (is (= 2 (count (into [] (d/type-history (d/db node) "Patient")))))
        (is (= 2 (d/total-num-of-type-changes (d/db node) "Patient"))))

      (testing "contains both patients in the order of their ids"
        (given (into [] (d/type-history (d/db node) "Patient"))
          [0 :id] := "0"
          [1 :id] := "1"))

      (testing "it is possible to start with the second patient"
        (given (into [] (d/type-history (d/db node) "Patient" 1 "1"))
          [0 :id] := "1"))))

  (testing "the database is immutable"
    (testing "while updating a patient"
      (let [node (new-node)]
        @(d/submit-tx node [[:put {:resourceType "Patient" :id "0" :active false}]])

        (let [db (d/db node)]
          @(d/submit-tx node [[:put {:resourceType "Patient" :id "0" :active true}]])

          (testing "the original database"
            (testing "has still only one history entry"
              (is (= 1 (count (into [] (d/type-history db "Patient")))))
              (is (= 1 (d/total-num-of-type-changes db "Patient"))))

            (testing "contains still the original patient"
              (given (into [] (d/type-history db "Patient"))
                [0 :resourceType] := "Patient"
                [0 :id] := "0"
                [0 :active] := false
                [0 :meta :versionId] := "1"))))))

    (testing "while adding another patient"
      (let [node (new-node)]
        @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])

        (let [db (d/db node)]
          @(d/submit-tx node [[:put {:resourceType "Patient" :id "1"}]])

          (testing "the original database"
            (testing "has still only one history entry"
              (is (= 1 (count (into [] (d/type-history db "Patient")))))
              (is (= 1 (d/total-num-of-type-changes db "Patient"))))

            (testing "contains still the first patient"
              (given (into [] (d/type-history db "Patient"))
                [0 :resourceType] := "Patient"
                [0 :id] := "0"
                [0 :meta :versionId] := "1"))))))))



;; ---- System-Level History Functions ----------------------------------------

(deftest system-history
  (testing "a new node has an empty system history"
    (let [db (d/db (new-node))]
      (is (coll/empty? (d/system-history db)))
      (is (zero? (d/total-num-of-system-changes db)))))

  (testing "a node with one patient"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])

      (testing "has one history entry"
        (is (= 1 (count (into [] (d/system-history (d/db node))))))
        (is (= 1 (d/total-num-of-system-changes (d/db node)))))

      (testing "contains that patient"
        (given (into [] (d/system-history (d/db node)))
          [0 :resourceType] := "Patient"
          [0 :id] := "0"
          [0 :meta :versionId] := "1"))))

  (testing "a node with one deleted patient"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      @(d/submit-tx node [[:delete "Patient" "0"]])

      (testing "has two history entries"
        (is (= 2 (count (into [] (d/system-history (d/db node))))))
        (is (= 2 (d/total-num-of-system-changes (d/db node)))))

      (testing "the first history entry is the patient marked as deleted"
        (given (into [] (d/system-history (d/db node)))
          [0 :resourceType] := "Patient"
          [0 :id] := "0"
          [0 :meta :versionId] := "2"
          [0 meta :blaze.db/op] := :delete))

      (testing "the second history entry is the patient marked as created"
        (given (into [] (d/system-history (d/db node)))
          [1 :resourceType] := "Patient"
          [1 :id] := "0"
          [1 :meta :versionId] := "1"
          [1 meta :blaze.db/op] := :put))))

  (testing "a node with one patient and one observation in two transactions"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      @(d/submit-tx node [[:put {:resourceType "Observation" :id "0"}]])

      (testing "has two history entries"
        (is (= 2 (count (into [] (d/system-history (d/db node))))))
        (is (= 2 (d/total-num-of-system-changes (d/db node)))))

      (testing "contains both resources in reverse transaction order"
        (given (into [] (d/system-history (d/db node)))
          [0 :resourceType] := "Observation"
          [1 :resourceType] := "Patient"))

      (testing "it is possible to start with the older transaction"
        (given (into [] (d/system-history (d/db node) 1))
          [0 :resourceType] := "Patient"))))

  (testing "a node with one patient and one observation in one transaction"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]
                          [:put {:resourceType "Observation" :id "0"}]])

      (testing "has two history entries"
        (is (= 2 (count (into [] (d/system-history (d/db node))))))
        (is (= 2 (d/total-num-of-system-changes (d/db node)))))

      (testing "contains both resources in the order of their type hashes"
        (given (into [] (d/system-history (d/db node)))
          [0 :resourceType] := "Observation"
          [1 :resourceType] := "Patient"))

      (testing "it is possible to start with the patient"
        (given (into [] (d/system-history (d/db node) 1 "Patient"))
          [0 :resourceType] := "Patient"))))

  (testing "a node with two patients in one transaction"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]
                          [:put {:resourceType "Patient" :id "1"}]])

      (testing "has two history entries"
        (is (= 2 (count (into [] (d/system-history (d/db node))))))
        (is (= 2 (d/total-num-of-system-changes (d/db node)))))

      (testing "it is possible to start with the second patient"
        (given (into [] (d/system-history (d/db node) 1 "Patient" "1"))
          [0 :id] := "1"))))

  (testing "the database is immutable"
    (testing "while updating a patient"
      (let [node (new-node)]
        @(d/submit-tx node [[:put {:resourceType "Patient" :id "0" :active false}]])

        (let [db (d/db node)]
          @(d/submit-tx node [[:put {:resourceType "Patient" :id "0" :active true}]])

          (testing "the original database"
            (testing "has still only one history entry"
              (is (= 1 (count (into [] (d/system-history db)))))
              (is (= 1 (d/total-num-of-system-changes db))))

            (testing "contains still the original patient"
              (given (into [] (d/system-history db))
                [0 :resourceType] := "Patient"
                [0 :id] := "0"
                [0 :active] := false
                [0 :meta :versionId] := "1"))))))

    (testing "while adding another patient"
      (let [node (new-node)]
        @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])

        (let [db (d/db node)]
          @(d/submit-tx node [[:put {:resourceType "Patient" :id "1"}]])

          (testing "the original database"
            (testing "has still only one history entry"
              (is (= 1 (count (into [] (d/system-history db)))))
              (is (= 1 (d/total-num-of-system-changes db))))

            (testing "contains still the first patient"
              (given (into [] (d/system-history db))
                [0 :resourceType] := "Patient"
                [0 :id] := "0"
                [0 :meta :versionId] := "1"))))))))
