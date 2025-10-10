(ns blaze.db.node.resource-indexer-test
  (:require
   [blaze.byte-string :as bs]
   [blaze.byte-string-spec]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.codec.date :as codec-date]
   [blaze.db.impl.index.compartment.resource-test-util :as cr-tu]
   [blaze.db.impl.index.compartment.search-param-value-resource-test-util
    :as c-sp-vr-tu]
   [blaze.db.impl.index.resource-search-param-value-test-util :as r-sp-v-tu]
   [blaze.db.impl.index.search-param-value-resource-test-util :as sp-vr-tu]
   [blaze.db.kv :as kv]
   [blaze.db.kv.mem]
   [blaze.db.kv.mem-spec]
   [blaze.db.node :as-alias node]
   [blaze.db.node.resource-indexer :as resource-indexer]
   [blaze.db.node.resource-indexer-spec]
   [blaze.db.node.resource-indexer.spec]
   [blaze.db.resource-store :as rs]
   [blaze.db.resource-store.kv :as rs-kv]
   [blaze.db.resource-store.spec]
   [blaze.db.search-param-registry.spec]
   [blaze.fhir-path :as fhir-path]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.hash-spec]
   [blaze.fhir.parsing-context]
   [blaze.fhir.spec.type]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.fhir.writing-context]
   [blaze.metrics.spec]
   [blaze.module.test-util :refer [given-failed-future given-failed-system with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [taoensso.timbre :as log])
  (:import
   [java.time Instant]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def base-config
  {[::kv/mem :blaze.db/index-kv-store]
   {:column-families
    {:search-param-value-index nil
     :resource-value-index nil
     :compartment-search-param-value-index nil
     :compartment-resource-type-index nil
     :active-search-params nil}}

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
   {:structure-definition-repo structure-definition-repo}})

(def config
  (assoc
   base-config
   ::node/resource-indexer
   {:kv-store (ig/ref :blaze.db/index-kv-store)
    :resource-store (ig/ref ::rs/kv)
    :search-param-registry (ig/ref :blaze.db/search-param-registry)
    :executor (ig/ref ::resource-indexer/executor)}

   ::resource-indexer/executor {}))

(def main-config
  (assoc
   base-config
   [::node/resource-indexer :blaze.db.node.main/resource-indexer]
   {:kv-store (ig/ref :blaze.db/index-kv-store)
    :resource-store (ig/ref ::rs/kv)
    :search-param-registry (ig/ref :blaze.db/search-param-registry)
    :executor (ig/ref ::resource-indexer/executor)}

   [::resource-indexer/executor :blaze.db.node.resource-indexer.main/executor]
   {}))

(deftest init-test
  (testing "nil config"
    (given-failed-system {::node/resource-indexer nil}
      :key := ::node/resource-indexer
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {::node/resource-indexer {}}
      :key := ::node/resource-indexer
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :kv-store))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :resource-store))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :search-param-registry))
      [:cause-data ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :executor))))

  (testing "invalid kv-store"
    (given-failed-system (assoc-in config [::node/resource-indexer :kv-store] ::invalid)
      :key := ::node/resource-indexer
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/kv-store]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid resource-store"
    (given-failed-system (assoc-in config [::node/resource-indexer :resource-store] ::invalid)
      :key := ::node/resource-indexer
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/resource-store]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid search-param-registry"
    (given-failed-system (assoc-in config [::node/resource-indexer :search-param-registry] ::invalid)
      :key := ::node/resource-indexer
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/search-param-registry]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid executor"
    (given-failed-system (assoc-in config [::node/resource-indexer :executor] ::invalid)
      :key := ::node/resource-indexer
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::resource-indexer/executor]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "with custom name"
    (let [system (ig/init main-config)
          {main-resource-indexer [::node/resource-indexer :blaze.db.node.main/resource-indexer]} system]
      (is (s/valid? ::node/resource-indexer main-resource-indexer)))))

(deftest executor-init-test
  (testing "nil config"
    (given-failed-system {::resource-indexer/executor nil}
      :key := ::resource-indexer/executor
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "invalid num-threads"
    (given-failed-system (assoc-in config [::resource-indexer/executor :num-threads] ::invalid)
      :key := ::resource-indexer/executor
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::resource-indexer/num-threads]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(deftest duration-seconds-collector-init-test
  (with-system [{collector ::resource-indexer/duration-seconds} {::resource-indexer/duration-seconds {}}]
    (is (s/valid? :blaze.metrics/collector collector))))

(deftest index-entries-collector-init-test
  (with-system [{collector ::resource-indexer/index-entries} {::resource-indexer/index-entries {}}]
    (is (s/valid? :blaze.metrics/collector collector))))

(deftest fails-on-kv-put-test
  (with-system [{::node/keys [resource-indexer]} config]
    (let [patient {:fhir/type :fhir/Patient :id "0"}
          hash (hash/generate patient)]
      (with-redefs [kv/put! (fn [_ _] (throw (Exception. "msg-200802")))]
        (given-failed-future
         (resource-indexer/index-resources
          resource-indexer
          {:t 0
           :instant Instant/EPOCH
           :tx-cmds
           [{:op "put"
             :type "Patient"
             :id "0"
             :hash hash}]
           :local-payload
           {hash patient}})
          ::anom/category := ::anom/fault
          ::anom/message := "msg-200802")))))

(deftest skips-on-failing-fhir-path-eval-test
  (with-system [{kv-store [::kv/mem :blaze.db/index-kv-store]
                 ::node/keys [resource-indexer]} config]

    (let [observation {:fhir/type :fhir/Observation :id "0"
                       :subject #fhir/Reference{:reference #fhir/string "foo"}}
          hash (hash/generate observation)]
      (with-redefs [fhir-path/eval (fn [_ _ _] {::anom/category ::anom/fault ::x ::y})]
        @(resource-indexer/index-resources
          resource-indexer
          {:t 0
           :instant Instant/EPOCH
           :tx-cmds
           [{:op "put"
             :type "Observation"
             :id "0"
             :hash hash}]
           :local-payload
           {hash observation}}))

      (is (empty? (sp-vr-tu/decode-index-entries kv-store :id))))))

(deftest index-patient-resource-test
  (with-system [{kv-store [::kv/mem :blaze.db/index-kv-store]
                 resource-store ::rs/kv
                 ::node/keys [resource-indexer]} config]
    (let [resource
          {:fhir/type :fhir/Patient :id "id-104313"
           :active #fhir/boolean true}
          hash (hash/generate resource)]
      @(rs/put! resource-store {hash resource})
      @(resource-indexer/index-resources
        resource-indexer
        {:t 0
         :instant Instant/EPOCH
         :tx-cmds
         [{:op "put"
           :type "Patient"
           :id "id-104313"
           :hash hash}]})

      (testing "SearchParamValueResource index"
        (is (every? #{["Patient" "id-104313" #blaze/hash-prefix"45142904"]}
                    (sp-vr-tu/decode-index-entries
                     kv-store :type :id :hash-prefix)))
        (is (= (sp-vr-tu/decode-index-entries kv-store :code :v-hash)
               [["active" (codec/v-hash "true")]
                ["deceased" (codec/v-hash "false")]
                ["_lastUpdated" #blaze/byte-string"80008001"]])))

      (testing "ResourceSearchParamValue index"
        (is (every? #{["Patient" "id-104313" #blaze/hash-prefix"45142904"]}
                    (r-sp-v-tu/decode-index-entries
                     kv-store :type :id :hash-prefix)))
        (is (= (r-sp-v-tu/decode-index-entries kv-store :code :v-hash)
               [["active" (codec/v-hash "true")]
                ["deceased" (codec/v-hash "false")]
                ["_lastUpdated" #blaze/byte-string"80008001"]])))

      (testing "CompartmentResourceType index"
        (is (empty? (cr-tu/decode-index-entries kv-store))))

      (testing "CompartmentSearchParamValueResource index"
        (is (empty? (c-sp-vr-tu/decode-index-entries kv-store)))))))

(deftest index-condition-resource-test
  (with-system [{kv-store [::kv/mem :blaze.db/index-kv-store]
                 resource-store ::rs/kv
                 ::node/keys [resource-indexer]} config]
    (let [resource
          {:fhir/type :fhir/Condition :id "id-204446"
           :code
           #fhir/CodeableConcept
            {:coding
             [#fhir/Coding
               {:system #fhir/uri "system-204435"
                :code #fhir/code "code-204441"}]}
           :onset #fhir/dateTime "2020-01-30"
           :subject #fhir/Reference{:reference #fhir/string "Patient/id-145552"}
           :meta
           #fhir/Meta
            {:versionId #fhir/id "1"
             :profile [#fhir/canonical "url-164445"]}}
          hash (hash/generate resource)]
      @(rs/put! resource-store {hash resource})
      @(resource-indexer/index-resources
        resource-indexer
        {:t 0
         :instant Instant/EPOCH
         :tx-cmds
         [{:op "put"
           :type "Condition"
           :id "id-204446"
           :hash hash}]})

      (testing "SearchParamValueResource index"
        (is (every? #{["Condition" "id-204446" #blaze/hash-prefix"4AB29C7B"]}
                    (sp-vr-tu/decode-index-entries
                     kv-store :type :id :hash-prefix)))
        (is (= (sp-vr-tu/decode-index-entries kv-store :code :v-hash)
               [["patient" (codec/v-hash "Patient/id-145552")]
                ["patient" (codec/tid-id
                            (codec/tid "Patient")
                            (codec/id-byte-string "id-145552"))]
                ["patient" (codec/v-hash "id-145552")]
                ["code" (codec/v-hash "code-204441")]
                ["code" (codec/v-hash "system-204435|")]
                ["code" (codec/v-hash "system-204435|code-204441")]
                ["_profile:below" (codec/v-hash "url-164445")]
                ["onset-date" (codec-date/encode-range #system/date-time"2020-01-30")]
                ["subject" (codec/v-hash "Patient/id-145552")]
                ["subject" (codec/tid-id
                            (codec/tid "Patient")
                            (codec/id-byte-string "id-145552"))]
                ["subject" (codec/v-hash "id-145552")]
                ["_profile" (codec/v-hash "url-164445")]
                ["_lastUpdated" #blaze/byte-string"80008001"]])))

      (testing "ResourceSearchParamValue index"
        (is (every? #{["Condition" "id-204446" #blaze/hash-prefix"4AB29C7B"]}
                    (r-sp-v-tu/decode-index-entries
                     kv-store :type :id :hash-prefix)))
        (is (= (r-sp-v-tu/decode-index-entries kv-store :code :v-hash)
               [["patient" (codec/v-hash "Patient/id-145552")]
                ["patient" (codec/tid-id
                            (codec/tid "Patient")
                            (codec/id-byte-string "id-145552"))]
                ["patient" (codec/v-hash "id-145552")]
                ["code" (codec/v-hash "code-204441")]
                ["code" (codec/v-hash "system-204435|")]
                ["code" (codec/v-hash "system-204435|code-204441")]
                ["_profile:below" (codec/v-hash "url-164445")]
                ["onset-date" (codec-date/encode-range #system/date-time"2020-01-30")]
                ["subject" (codec/v-hash "Patient/id-145552")]
                ["subject" (codec/tid-id
                            (codec/tid "Patient")
                            (codec/id-byte-string "id-145552"))]
                ["subject" (codec/v-hash "id-145552")]
                ["_profile" (codec/v-hash "url-164445")]
                ["_lastUpdated" #blaze/byte-string"80008001"]])))

      (testing "CompartmentResourceType index"
        (is (= (cr-tu/decode-index-entries kv-store :compartment :type :id)
               [[["Patient" "id-145552"] "Condition" "id-204446"]])))

      (testing "CompartmentSearchParamValueResource index"
        (is (every? #{[["Patient" "id-145552"] "Condition" "id-204446"
                       #blaze/hash-prefix"4AB29C7B"]}
                    (c-sp-vr-tu/decode-index-entries
                     kv-store :compartment :type :id :hash-prefix)))
        (is (= (c-sp-vr-tu/decode-index-entries kv-store :code :v-hash)
               [["code" (codec/v-hash "system-204435|code-204441")]]))))))

(deftest index-observation-resource-test
  (with-system [{kv-store [::kv/mem :blaze.db/index-kv-store]
                 resource-store ::rs/kv
                 ::node/keys [resource-indexer]} config]
    (let [resource {:fhir/type :fhir/Observation :id "id-192702"
                    :status #fhir/code "status-193613"
                    :category
                    [#fhir/CodeableConcept
                      {:coding
                       [#fhir/Coding
                         {:system #fhir/uri "system-193558"
                          :code #fhir/code "code-193603"}]}]
                    :code
                    #fhir/CodeableConcept
                     {:coding
                      [#fhir/Coding
                        {:system #fhir/uri "system-193821"
                         :code #fhir/code "code-193824"}]}
                    :subject #fhir/Reference{:reference #fhir/string "Patient/id-180857"}
                    :effective #fhir/dateTime "2005-06-17"
                    :value
                    #fhir/Quantity
                     {:code #fhir/code "kg/m2"
                      :system #fhir/uri "http://unitsofmeasure.org"
                      :value #fhir/decimal 23.42M}}
          hash (hash/generate resource)]
      @(rs/put! resource-store {hash resource})
      @(resource-indexer/index-resources
        resource-indexer
        {:t 0
         :instant Instant/EPOCH
         :tx-cmds
         [{:op "put"
           :type "Observation"
           :id "id-192702"
           :hash hash}]})

      (testing "SearchParamValueResource index"
        (is (every? #{["Observation" "id-192702" #blaze/hash-prefix"651D1F37"]}
                    (sp-vr-tu/decode-index-entries
                     kv-store :type :id :hash-prefix)))
        (is (= (sp-vr-tu/decode-index-entries kv-store :code :v-hash)
               [["code-value-quantity"
                 #blaze/byte-string"82821D0F00000000900926"]
                ["code-value-quantity"
                 #blaze/byte-string"82821D0F32690DC8900926"]
                ["code-value-quantity"
                 #blaze/byte-string"82821D0FA3C37576900926"]
                ["code-value-quantity"
                 #blaze/byte-string"9F7C9B9400000000900926"]
                ["code-value-quantity"
                 #blaze/byte-string"9F7C9B9432690DC8900926"]
                ["code-value-quantity"
                 #blaze/byte-string"9F7C9B94A3C37576900926"]
                ["code-value-quantity"
                 (bs/concat (codec/v-hash "code-193824")
                            (codec/quantity "" 23.42M))]
                ["code-value-quantity"
                 (bs/concat (codec/v-hash "code-193824")
                            (codec/quantity "kg/m2" 23.42M))]
                ["code-value-quantity"
                 (bs/concat (codec/v-hash "code-193824")
                            (codec/quantity "http://unitsofmeasure.org|kg/m2"
                                            23.42M))]
                ["date" (codec-date/encode-range #system/date-time"2005-06-17")]
                ["category" (codec/v-hash "system-193558|code-193603")]
                ["category" (codec/v-hash "system-193558|")]
                ["category" (codec/v-hash "code-193603")]
                ["patient" (codec/v-hash "id-180857")]
                ["patient" (codec/tid-id
                            (codec/tid "Patient")
                            (codec/id-byte-string "id-180857"))]
                ["patient" (codec/v-hash "Patient/id-180857")]
                ["code" (codec/v-hash "system-193821|")]
                ["code" (codec/v-hash "system-193821|code-193824")]
                ["code" (codec/v-hash "code-193824")]
                ["value-quantity" (codec/quantity "" 23.42M)]
                ["value-quantity" (codec/quantity "kg/m2" 23.42M)]
                ["value-quantity" (codec/quantity
                                   "http://unitsofmeasure.org|kg/m2"
                                   23.42M)]
                ["combo-code" (codec/v-hash "system-193821|")]
                ["combo-code" (codec/v-hash "system-193821|code-193824")]
                ["combo-code" (codec/v-hash "code-193824")]
                ["combo-value-quantity"
                 #blaze/byte-string"00000000900926"]
                ["combo-value-quantity"
                 #blaze/byte-string"32690DC8900926"]
                ["combo-value-quantity"
                 #blaze/byte-string"A3C37576900926"]
                ["combo-code-value-quantity"
                 #blaze/byte-string"82821D0F00000000900926"]
                ["combo-code-value-quantity"
                 #blaze/byte-string"82821D0F32690DC8900926"]
                ["combo-code-value-quantity"
                 #blaze/byte-string"82821D0FA3C37576900926"]
                ["combo-code-value-quantity"
                 #blaze/byte-string"9F7C9B9400000000900926"]
                ["combo-code-value-quantity"
                 #blaze/byte-string"9F7C9B9432690DC8900926"]
                ["combo-code-value-quantity"
                 #blaze/byte-string"9F7C9B94A3C37576900926"]
                ["combo-code-value-quantity"
                 #blaze/byte-string"A75DEC9D00000000900926"]
                ["combo-code-value-quantity"
                 #blaze/byte-string"A75DEC9D32690DC8900926"]
                ["combo-code-value-quantity"
                 #blaze/byte-string"A75DEC9DA3C37576900926"]
                ["subject" (codec/v-hash "id-180857")]
                ["subject" (codec/tid-id
                            (codec/tid "Patient")
                            (codec/id-byte-string "id-180857"))]
                ["subject" (codec/v-hash "Patient/id-180857")]
                ["status" (codec/v-hash "status-193613")]
                ["_lastUpdated" #blaze/byte-string"80008001"]])))

      (testing "CompartmentResourceType index"
        (is (= (cr-tu/decode-index-entries kv-store :compartment :type :id)
               [[["Patient" "id-180857"] "Observation" "id-192702"]])))

      (testing "CompartmentSearchParamValueResource index"
        (is (every? #{[["Patient" "id-180857"] "Observation" "id-192702"
                       #blaze/hash-prefix"651D1F37"]}
                    (c-sp-vr-tu/decode-index-entries
                     kv-store :compartment :type :id :hash-prefix)))
        (is (= (c-sp-vr-tu/decode-index-entries kv-store :code :v-hash)
               [["category" (codec/v-hash "system-193558|code-193603")]
                ["code" (codec/v-hash "system-193821|code-193824")]
                ["combo-code" (codec/v-hash "system-193821|code-193824")]
                ["status" (codec/v-hash "status-193613")]]))))))

(deftest index-appointment-resource-test
  (with-system [{kv-store [::kv/mem :blaze.db/index-kv-store]
                 resource-store ::rs/kv
                 ::node/keys [resource-indexer]} config]
    (let [resource {:fhir/type :fhir/Appointment :id "id-151125"
                    :status #fhir/code "status-151938"
                    :participant
                    [{:fhir/type :fhir.Appointment/participant
                      :actor #fhir/Reference{:reference #fhir/string "Patient/id-151354"}}]}
          hash (hash/generate resource)]
      @(rs/put! resource-store {hash resource})
      @(resource-indexer/index-resources
        resource-indexer
        {:t 0
         :instant Instant/EPOCH
         :tx-cmds
         [{:op "put"
           :type "Appointment"
           :id "id-151125"
           :hash hash}]})

      (testing "SearchParamValueResource index"
        (is (every? #{["Appointment" "id-151125" #blaze/hash-prefix"8351D5EB"]}
                    (sp-vr-tu/decode-index-entries
                     kv-store :type :id :hash-prefix)))
        (is (= (sp-vr-tu/decode-index-entries kv-store :code :v-hash)
               [["patient" (codec/v-hash "id-151354")]
                ["patient" (codec/tid-id
                            (codec/tid "Patient")
                            (codec/id-byte-string "id-151354"))]
                ["patient" (codec/v-hash "Patient/id-151354")]
                ["actor" (codec/v-hash "id-151354")]
                ["actor" (codec/tid-id
                          (codec/tid "Patient")
                          (codec/id-byte-string "id-151354"))]
                ["actor" (codec/v-hash "Patient/id-151354")]
                ["status" (codec/v-hash "status-151938")]
                ["_lastUpdated" #blaze/byte-string"80008001"]])))

      (testing "CompartmentResourceType index"
        (is (= (cr-tu/decode-index-entries kv-store :compartment :type :id)
               [[["Patient" "id-151354"] "Appointment" "id-151125"]])))

      (testing "CompartmentSearchParamValueResource index"
        (is (every? #{[["Patient" "id-151354"] "Appointment" "id-151125"
                       #blaze/hash-prefix"8351D5EB"]}
                    (c-sp-vr-tu/decode-index-entries
                     kv-store :compartment :type :id :hash-prefix)))
        (is (= (c-sp-vr-tu/decode-index-entries kv-store :code :v-hash)
               [["status" (codec/v-hash "status-151938")]]))))))

(deftest index-delete-cmd-test
  (with-system [{kv-store [::kv/mem :blaze.db/index-kv-store]
                 ::node/keys [resource-indexer]} config]
    @(resource-indexer/index-resources
      resource-indexer
      {:t 0
       :instant Instant/EPOCH
       :tx-cmds
       [{:op "delete"
         :type "Patient"
         :id "0"}]})

    (testing "doesn't index anything"
      (is (empty? (sp-vr-tu/decode-index-entries kv-store :id))))))
