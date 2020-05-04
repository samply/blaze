(ns blaze.db.db-test
  (:require
    [blaze.db.api :as d]
    [blaze.db.api-spec]
    [blaze.db.db :as db]
    [blaze.db.db-spec]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index :as index]
    [blaze.db.search-param-registry :as sr]
    [blaze.db.indexer :as indexer]
    [blaze.db.indexer.resource :refer [init-resource-indexer]]
    [blaze.db.indexer.tx :refer [init-tx-indexer]]
    [blaze.db.kv.mem :refer [init-mem-kv-store]]
    [blaze.db.kv-spec]
    [blaze.executors :as ex]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]])
  (:import
    [com.github.benmanes.caffeine.cache LoadingCache]
    [java.time Instant]))


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def search-param-registry (sr/init-mem-search-param-registry))


(def t 1)
(def tx-instant (Instant/ofEpochSecond 3724562743))

(defn init-db []
  (let [kv-store
        (init-mem-kv-store
          {:search-param-value-index nil
           :resource-value-index nil
           :compartment-search-param-value-index nil
           :compartment-resource-value-index nil
           :resource-type-index nil
           :compartment-resource-type-index nil
           :resource-index nil
           :active-search-params nil
           :tx-success-index nil
           :tx-error-index nil
           :t-by-instant-index nil
           :resource-as-of-index nil
           :type-as-of-index nil
           :type-stats-index nil
           :system-stats-index nil})
        r-i (init-resource-indexer
              search-param-registry kv-store
              (ex/cpu-bound-pool "resource-indexer-%d"))
        tx-i (init-tx-indexer kv-store)
        resource-cache
        (reify LoadingCache
          (get [_ hash]
            (index/load-resource-content kv-store hash)))]
    {:r-i r-i
     :tx-i tx-i
     :db (db/db kv-store resource-cache search-param-registry t)}))


(deftest tx
  (let [{:keys [db tx-i]} (init-db)]
    (indexer/index-tx tx-i t tx-instant [])
    (given (d/tx db t)
      :blaze.db.tx/instant := tx-instant)))


(deftest resource-exists?
  (testing "resource does not exist at `t`"
    (testing "returns nil"
      (is (false? (d/resource-exists? (:db (init-db)) "Type_152837" "id-152941")))))

  (testing "resource is deleted at `t`"
    (let [resource {:resourceType "Type_155302" :id "id-155253"}
          hash (codec/hash resource)
          {:keys [db r-i tx-i]} (init-db)]
      @(indexer/index-resources r-i [[hash resource]])
      (indexer/index-tx
        tx-i t (Instant/now)
        [[:delete "Type_155302" "id-155253" hash]])

      (is (false? (d/resource-exists? db "Type_155302" "id-155253")))))

  (testing "resource exists at `t`"
    (let [resource {:resourceType "Type_155302" :id "id-155253"}
          hash (codec/hash resource)
          {:keys [db r-i tx-i]} (init-db)]
      @(indexer/index-resources r-i [[hash resource]])
      (indexer/index-tx
        tx-i t (Instant/now)
        [[:put "Type_155302" "id-155253" hash]])

      (is (true? (d/resource-exists? db "Type_155302" "id-155253"))))))


(deftest resource
  (testing "resource does not exist at `t`"
    (testing "returns nil"
      (is (nil? (d/resource (:db (init-db)) "Type_152837" "id-152941")))))

  (testing "resource is deleted at `t`"
    (let [resource {:resourceType "Type_155302" :id "id-155253"}
          hash (codec/hash resource)
          {:keys [db r-i tx-i]} (init-db)]
      @(indexer/index-resources r-i [[hash resource]])
      (indexer/index-tx
        tx-i t (Instant/now)
        [[:delete "Type_155302" "id-155253" hash]])

      (testing "returns a resource stub in deleted state"
        (given (d/resource db "Type_155302" "id-155253")
          [:meta :versionId] := (str t)
          [meta :blaze.db/op] := :delete
          [meta :blaze.db/tx :blaze.db/t] := t))))

  (testing "resource exists at `t`"
    (let [resource {:resourceType "Type_155302" :id "id-155253"}
          hash (codec/hash resource)
          {:keys [db r-i tx-i]} (init-db)]
      @(indexer/index-resources r-i [[hash resource]])
      (indexer/index-tx
        tx-i t (Instant/now)
        [[:put "Type_155302" "id-155253" hash]])

      (testing "returns the resource with added versionId, hash and transaction"
        (given (d/resource db "Type_155302" "id-155253")
          :id := "id-155253"
          [:meta :versionId] := (str t)
          [meta :blaze.db/tx :blaze.db/t] := t)))))


(deftest compartment-list
  (testing "Patient Compartment"
    (testing "Condition"
      (let [r0 {:resourceType "Patient"
                :id "id-0"}
            h0 (codec/hash r0)
            r1 {:resourceType "Condition"
                :id "id-1"
                :subject
                {:reference "Patient/id-0"}}
            h1 (codec/hash r1)
            r2 {:resourceType "Condition"
                :id "id-2"
                :subject
                {:reference "Patient/id-0"}}
            h2 (codec/hash r2)
            r3 {:resourceType "Condition"
                :id "id-3"}
            h3 (codec/hash r3)
            {:keys [db r-i tx-i]} (init-db)]
        @(indexer/index-resources r-i [[h0 r0]])
        @(indexer/index-resources r-i [[h1 r1]])
        @(indexer/index-resources r-i [[h2 r2]])
        @(indexer/index-resources r-i [[h3 r3]])
        (indexer/index-tx
          tx-i t (Instant/now)
          [[:put "Patient" "id-0" h0]
           [:put "Condition" "id-1" h1]
           [:put "Condition" "id-2" h2]
           [:put "Condition" "id-3" h3]])

        (given (into [] (d/list-compartment-resources db "Patient" "id-0" "Condition"))
          [0 :id] := "id-1"
          [1 :id] := "id-2"
          2 := nil))))

  (testing "Unknown compartment is not a problem"
    (let [{:keys [db]} (init-db)]
      (is (empty? (into [] (d/list-compartment-resources db "foo" "bar" "Condition")))))))


(deftest type-query
  (testing "Patient"
    (let [p0 {:resourceType "Patient"
              :id "id-0"
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
              [{:family "Müller"}]}
          h0 (codec/hash p0)
          p1 {:resourceType "Patient"
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
                :value "0815"}]}
          h1 (codec/hash p1)
          p2 {:resourceType "Patient"
              :id "id-2"
              :active false
              :gender "female"
              :birthDate "2020"
              :deceasedDateTime "2020-03"
              :address
              [{:line ["Liebigstraße 20a"]
                :city "Leipzig"}]
              :name
              [{:family "Schmidt"}]}
          h2 (codec/hash p2)
          {:keys [db r-i tx-i]} (init-db)]
      @(indexer/index-resources r-i [[h0 p0]])
      @(indexer/index-resources r-i [[h1 p1]])
      @(indexer/index-resources r-i [[h2 p2]])
      (indexer/index-tx
        tx-i t (Instant/now)
        [[:put "Patient" "id-0" h0]
         [:put "Patient" "id-1" h1]
         [:put "Patient" "id-2" h2]])

      (testing "active"
        (given (into [] (d/type-query db "Patient" [["active" "true"]]))
          [0 :id] := "id-1"
          1 := nil))

      (testing "address with line"
        (testing "in first position"
          (given (into [] (d/type-query db "Patient" [["address" "Liebigstraße"]]))
            [0 :id] := "id-2"
            1 := nil))

        (testing "in second position"
          (given (into [] (d/type-query db "Patient" [["gender" "female"]
                                                      ["address" "Liebigstraße"]]))
            [0 :id] := "id-2"
            1 := nil)))

      (testing "address with city"
        (given (into [] (d/type-query db "Patient" [["address" "Leipzig"]]))
          [0 :id] := "id-0"
          [1 :id] := "id-2"
          2 := nil))

      (testing "address-city full"
        (given (into [] (d/type-query db "Patient" [["address-city" "Leipzig"]]))
          [0 :id] := "id-0"
          [1 :id] := "id-2"
          2 := nil))

      (testing "address-city prefix"
        (given (into [] (d/type-query db "Patient" [["address-city" "Leip"]]))
          [0 :id] := "id-0"
          [1 :id] := "id-2"
          2 := nil))

      (testing "address-city and family prefix"
        (given (into [] (d/type-query db "Patient" [["address-city" "Leip"]
                                                    ["family" "Sch"]]))
          [0 :id] := "id-2"
          1 := nil))

      (testing "address-city and gender"
        (given (into [] (d/type-query db "Patient" [["address-city" "Leipzig"]
                                                    ["gender" "female"]]))
          [0 :id] := "id-2"
          1 := nil))

      (testing "birthdate YYYYMMDD"
        (given (into [] (d/type-query db "Patient" [["birthdate" "2020-02-08"]]))
          [0 :id] := "id-0"
          1 := nil))

      (testing "birthdate YYYYMM"
        (given (into [] (d/type-query db "Patient" [["birthdate" "2020-02"]]))
          [0 :id] := "id-1"
          [1 :id] := "id-0"
          2 := nil))

      (testing "birthdate YYYY"
        (given (into [] (d/type-query db "Patient" [["birthdate" "2020"]]))
          [0 :id] := "id-2"
          [1 :id] := "id-1"
          [2 :id] := "id-0"))

      (testing "birthdate with `eq` prefix"
        (given (into [] (d/type-query db "Patient" [["birthdate" "eq2020-02-08"]]))
          [0 :id] := "id-0"
          1 := nil))

      (testing "birthdate with `ne` prefix is unsupported"
        (try
          (into [] (d/type-query db "Patient" [["birthdate" "ne2020-02-08"]]))
          (catch Exception e
            (given (ex-data e)
              ::anom/category := ::anom/unsupported))))

      (testing "birthdate with `ge` prefix"
        (testing "finds equal date"
          (given (into [] (d/type-query db "Patient" [["birthdate" "ge2020-02-08"]]))
            [0 :id] := "id-0"
            [0 :birthDate] := "2020-02-08"
            1 := nil))

        (testing "finds greater date"
          (given (into [] (d/type-query db "Patient" [["birthdate" "ge2020-02-07"]]))
            [0 :id] := "id-0"
            [0 :birthDate] := "2020-02-08"
            1 := nil))

        (testing "finds more precise dates"
          (given (into [] (d/type-query db "Patient" [["birthdate" "ge2020-02"]]))
            [0 :id] := "id-1"
            [0 :birthDate] := "2020-02"
            [1 :id] := "id-0"
            [1 :birthDate] := "2020-02-08"
            2 := nil)))

      (testing "birthdate with `le` prefix"
        (testing "finds equal date"
          (given (into [] (d/type-query db "Patient" [["birthdate" "le2020-02-08"]]))
            [0 :id] := "id-0"
            [0 :birthDate] := "2020-02-08"
            1 := nil))

        (testing "finds less date"
          (given (into [] (d/type-query db "Patient" [["birthdate" "le2020-02-09"]]))
            [0 :id] := "id-0"
            [0 :birthDate] := "2020-02-08"
            1 := nil))

        (testing "finds more precise dates"
          (given (into [] (d/type-query db "Patient" [["birthdate" "le2020-03"]]))
            [0 :id] := "id-1"
            [0 :birthDate] := "2020-02"
            [1 :id] := "id-0"
            [1 :birthDate] := "2020-02-08"
            2 := nil)))

      (testing "deceased"
        (given (into [] (d/type-query db "Patient" [["deceased" "true"]]))
          [0 :id] := "id-0"
          [1 :id] := "id-2"
          2 := nil))

      (testing "email"
        (given (into [] (d/type-query db "Patient" [["email" "foo@bar.baz"]]))
          [0 :id] := "id-1"
          1 := nil))

      (testing "family lower-case"
        (given (into [] (d/type-query db "Patient" [["family" "schmidt"]]))
          [0 :id] := "id-2"
          1 := nil))

      (testing "gender"
        (given (into [] (d/type-query db "Patient" [["gender" "male"]]))
          [0 :id] := "id-0"
          1 := nil))

      (testing "identifier"
        (given (into [] (d/type-query db "Patient" [["identifier" "0"]]))
          [0 :id] := "id-0"
          1 := nil))

      (testing "telecom"
        (given (into [] (d/type-query db "Patient" [["telecom" "0815"]]))
          [0 :id] := "id-1"
          1 := nil))))


  (testing "Practitioner"
    (let [p0 {:resourceType "Practitioner"
              :id "id-0"
              :name
              [{:family "Müller"
                :given ["Hans" "Martin"]}]}
          h0 (codec/hash p0)
          {:keys [db r-i tx-i]} (init-db)]
      @(indexer/index-resources r-i [[h0 p0]])
      (indexer/index-tx
        tx-i t (Instant/now)
        [[:put "Practitioner" "id-0" h0]])

      (testing "name"
        (testing "using family"
          (given (into [] (d/type-query db "Practitioner" [["name" "müller"]]))
            [0 :id] := "id-0"
            1 := nil))

        (testing "using first given"
          (given (into [] (d/type-query db "Practitioner" [["name" "hans"]]))
            [0 :id] := "id-0"
            1 := nil))

        (testing "using second given"
          (given (into [] (d/type-query db "Practitioner" [["name" "martin"]]))
            [0 :id] := "id-0"
            1 := nil)))))


  (testing "Specimen"
    (let [s0 {:resourceType "Specimen"
              :id "id-0"
              :type
              {:coding
               [{:system "https://fhir.bbmri.de/CodeSystem/SampleMaterialType"
                 :code "dna"}]}
              :collection
              {:bodySite
               {:coding
                [{:system "urn:oid:2.16.840.1.113883.6.43.1"
                  :code "C77.4"}]}}}
          h0 (codec/hash s0)
          {:keys [db r-i tx-i]} (init-db)]
      @(indexer/index-resources r-i [[h0 s0]])
      (indexer/index-tx
        tx-i t (Instant/now)
        [[:put "Specimen" "id-0" h0]])

      (testing "bodysite"
        (testing "using system|code"
          (given (into [] (d/type-query db "Specimen" [["bodysite" "urn:oid:2.16.840.1.113883.6.43.1|C77.4"]]))
            [0 :id] := "id-0"
            1 := nil))

        (testing "using code"
          (given (into [] (d/type-query db "Specimen" [["bodysite" "C77.4"]]))
            [0 :id] := "id-0"
            1 := nil))

        (testing "using system|"
          (given (into [] (d/type-query db "Specimen" [["bodysite" "urn:oid:2.16.840.1.113883.6.43.1|"]]))
            [0 :id] := "id-0"
            1 := nil)))

      (testing "type"
        (given (into [] (d/type-query db "Specimen" [["type" "https://fhir.bbmri.de/CodeSystem/SampleMaterialType|dna"]]))
          [0 :id] := "id-0"
          1 := nil))

      (testing "bodysite and type"
        (testing "using system|code"
          (given (into [] (d/type-query db "Specimen" [["bodysite" "urn:oid:2.16.840.1.113883.6.43.1|C77.4"]
                                                       ["type" "https://fhir.bbmri.de/CodeSystem/SampleMaterialType|dna"]]))
            [0 :id] := "id-0"
            1 := nil))

        (testing "using code"
          (given (into [] (d/type-query db "Specimen" [["bodysite" "urn:oid:2.16.840.1.113883.6.43.1|C77.4"]
                                                       ["type" "dna"]]))
            [0 :id] := "id-0"
            1 := nil))

        (testing "using system|"
          (given (into [] (d/type-query db "Specimen" [["bodysite" "urn:oid:2.16.840.1.113883.6.43.1|C77.4"]
                                                       ["type" "https://fhir.bbmri.de/CodeSystem/SampleMaterialType|"]]))
            [0 :id] := "id-0"
            1 := nil))

        (testing "does not match"
          (testing "using system|code"
            (given (into [] (d/type-query db "Specimen" [["bodysite" "urn:oid:2.16.840.1.113883.6.43.1|C77.4"]
                                                         ["type" "https://fhir.bbmri.de/CodeSystem/SampleMaterialType|urine"]]))
              0 := nil))))))


  (testing "ActivityDefinition"
    (let [a0 {:resourceType "ActivityDefinition"
              :id "id-0"
              :url "url-111619"
              :description "desc-121208"}
          h0 (codec/hash a0)
          a1 {:resourceType "ActivityDefinition"
              :id "id-1"
              :url "url-111721"}
          h1 (codec/hash a1)
          {:keys [db r-i tx-i]} (init-db)]
      @(indexer/index-resources r-i [[h0 a0]])
      @(indexer/index-resources r-i [[h1 a1]])
      (indexer/index-tx
        tx-i t (Instant/now)
        [[:put "ActivityDefinition" "id-0" h0]
         [:put "ActivityDefinition" "id-1" h1]])

      (testing "url"
        (given (into [] (d/type-query db "ActivityDefinition" [["url" "url-111619"]]))
          [0 :id] := "id-0"
          1 := nil))

      (testing "description"
        (given (into [] (d/type-query db "ActivityDefinition" [["description" "desc-121208"]]))
          [0 :id] := "id-0"
          1 := nil))))


  (testing "CodeSystem"
    (let [a0 {:resourceType "CodeSystem"
              :id "id-0"
              :version "version-122443"}
          h0 (codec/hash a0)
          a1 {:resourceType "CodeSystem"
              :id "id-1"
              :version "version-122456"}
          h1 (codec/hash a1)
          {:keys [db r-i tx-i]} (init-db)]
      @(indexer/index-resources r-i [[h0 a0]])
      @(indexer/index-resources r-i [[h1 a1]])
      (indexer/index-tx
        tx-i t (Instant/now)
        [[:put "CodeSystem" "id-0" h0]
         [:put "CodeSystem" "id-1" h1]])

      (testing "version"
        (given (into [] (d/type-query db "CodeSystem" [["version" "version-122443"]]))
          [0 :id] := "id-0"
          1 := nil))))


  (testing "MedicationKnowledge"
    (let [a0 {:resourceType "MedicationKnowledge"
              :id "id-0"
              :monitoringProgram
              [{:name "name-123124"}]}
          h0 (codec/hash a0)
          a1 {:resourceType "MedicationKnowledge"
              :id "id-1"}
          h1 (codec/hash a1)
          {:keys [db r-i tx-i]} (init-db)]
      @(indexer/index-resources r-i [[h0 a0]])
      @(indexer/index-resources r-i [[h1 a1]])
      (indexer/index-tx
        tx-i t (Instant/now)
        [[:put "MedicationKnowledge" "id-0" h0]
         [:put "MedicationKnowledge" "id-1" h1]])

      (testing "monitoring-program-name"
        (given (into [] (d/type-query db "MedicationKnowledge" [["monitoring-program-name" "name-123124"]]))
          [0 :id] := "id-0"
          1 := nil))))

  (testing "Condition"
    (let [r0 {:resourceType "Patient"
              :id "id-0"}
          h0 (codec/hash r0)
          r1 {:resourceType "Condition"
              :id "id-0"
              :subject
              {:reference "Patient/id-0"}}
          h1 (codec/hash r1)
          r2 {:resourceType "Condition"
              :id "id-1"}
          h2 (codec/hash r2)
          {:keys [db r-i tx-i]} (init-db)]
      @(indexer/index-resources r-i [[h0 r0]])
      @(indexer/index-resources r-i [[h1 r1]])
      @(indexer/index-resources r-i [[h2 r2]])
      (indexer/index-tx
        tx-i t (Instant/now)
        [[:put "Patient" "id-0" h0]
         [:put "Condition" "id-0" h1]
         [:put "Condition" "id-1" h2]])

      (testing "patient"
        (given (into [] (d/type-query db "Condition" [["patient" "id-0"]]))
          [0 :id] := "id-0"
          1 := nil)))))


(deftest compartment-query
  (testing "Patient Compartment"
    (testing "Condition"
      (let [r0 {:resourceType "Patient"
                :id "id-0"}
            h0 (codec/hash r0)
            r1 {:resourceType "Condition"
                :id "id-1"
                :code
                {:coding
                 [{:system "system-a-122701"
                   :code "code-a-122652"}]}
                :subject
                {:reference "Patient/id-0"}}
            h1 (codec/hash r1)
            r2 {:resourceType "Condition"
                :id "id-2"
                :code
                {:coding
                 [{:system "system-b-122747"
                   :code "code-b-122750"}]}
                :subject
                {:reference "Patient/id-0"}}
            h2 (codec/hash r2)
            {:keys [db r-i tx-i]} (init-db)]
        @(indexer/index-resources r-i [[h0 r0]])
        @(indexer/index-resources r-i [[h1 r1]])
        @(indexer/index-resources r-i [[h2 r2]])
        (indexer/index-tx
          tx-i t (Instant/now)
          [[:put "Patient" "id-0" h0]
           [:put "Condition" "id-1" h1]
           [:put "Condition" "id-2" h2]])

        (testing "code"
          (given (into [] (d/compartment-query db "Patient" "id-0" "Condition" [["code" "system-a-122701|code-a-122652"]]))
            [0 :id] := "id-1"
            1 := nil)))))

  (testing "Unknown compartment is not a problem"
    (let [{:keys [db]} (init-db)]
      (is (empty? (into [] (d/compartment-query db "foo" "bar" "Condition" [["code" "baz"]]))))))

  (testing "Unknown type is not a problem"
    (let [r0 {:resourceType "Patient"
              :id "id-0"}
          h0 (codec/hash r0)
          {:keys [db r-i tx-i]} (init-db)]
      @(indexer/index-resources r-i [[h0 r0]])
      (indexer/index-tx
        tx-i t (Instant/now)
        [[:put "Patient" "id-0" h0]])

      (given (d/compartment-query db "Patient" "id-0" "Foo" [["code" "baz"]])
        ::anom/category := ::anom/not-found
        ::anom/message := "search-param with code `code` and type `Foo` not found"))))
