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
    [blaze.db.resource-store :as rs]
    [blaze.db.resource-store.kv :as rs-kv]
    [blaze.db.resource-store.spec :refer [resource-store?]]
    [blaze.db.search-param-registry.spec :refer [search-param-registry?]]
    [blaze.executors :as ex]
    [blaze.fhir-path :as fhir-path]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.hash-spec]
    [blaze.fhir.spec.type]
    [blaze.fhir.structure-definition-repo]
    [blaze.metrics.spec]
    [blaze.test-util :as tu :refer [given-failed-future given-thrown with-system]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:import
    [java.time Instant LocalDate]))


(set! *warn-on-reflection* true)
(st/instrument)
(log/set-level! :trace)


(test/use-fixtures :each tu/fixture)


(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {::node/resource-indexer nil})
      :key := ::node/resource-indexer
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {::node/resource-indexer {}})
      :key := ::node/resource-indexer
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :kv-store))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :resource-store))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :search-param-registry))
      [:explain ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :executor))))

  (testing "invalid kv-store"
    (given-thrown (ig/init {::node/resource-indexer {:kv-store ::invalid}})
      :key := ::node/resource-indexer
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :resource-store))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :search-param-registry))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :executor))
      [:explain ::s/problems 3 :pred] := `kv/store?
      [:explain ::s/problems 3 :val] := ::invalid))

  (testing "invalid resource-store"
    (given-thrown (ig/init {::node/resource-indexer {:resource-store ::invalid}})
      :key := ::node/resource-indexer
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :kv-store))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :search-param-registry))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :executor))
      [:explain ::s/problems 3 :pred] := `resource-store?
      [:explain ::s/problems 3 :val] := ::invalid))

  (testing "invalid search-param-registry"
    (given-thrown (ig/init {::node/resource-indexer {:search-param-registry ::invalid}})
      :key := ::node/resource-indexer
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :kv-store))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :resource-store))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :executor))
      [:explain ::s/problems 3 :pred] := `search-param-registry?
      [:explain ::s/problems 3 :val] := ::invalid))

  (testing "invalid executor"
    (given-thrown (ig/init {::node/resource-indexer {:executor ::invalid}})
      :key := ::node/resource-indexer
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :kv-store))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :resource-store))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :search-param-registry))
      [:explain ::s/problems 3 :pred] := `ex/executor?
      [:explain ::s/problems 3 :val] := ::invalid)))


(deftest executor-init-test
  (testing "nil config"
    (given-thrown (ig/init {::resource-indexer/executor nil})
      :key := ::resource-indexer/executor
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "invalid num-threads"
    (given-thrown (ig/init {::resource-indexer/executor {:num-threads ::invalid}})
      :key := ::resource-indexer/executor
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `pos-int?
      [:explain ::s/problems 0 :val] := ::invalid)))


(deftest duration-seconds-collector-init-test
  (with-system [{collector ::resource-indexer/duration-seconds} {::resource-indexer/duration-seconds {}}]
    (is (s/valid? :blaze.metrics/collector collector))))


(deftest index-entries-collector-init-test
  (with-system [{collector ::resource-indexer/index-entries} {::resource-indexer/index-entries {}}]
    (is (s/valid? :blaze.metrics/collector collector))))


(def system
  {[::kv/mem :blaze.db/index-kv-store]
   {:column-families
    {:search-param-value-index nil
     :resource-value-index nil
     :compartment-search-param-value-index nil
     :compartment-resource-type-index nil
     :active-search-params nil}}

   ::rs/kv
   {:kv-store (ig/ref :blaze.db/resource-kv-store)
    :executor (ig/ref ::rs-kv/executor)}

   [::kv/mem :blaze.db/resource-kv-store]
   {:column-families {}}

   ::rs-kv/executor {}

   :blaze.db/search-param-registry
   {:structure-definition-repo (ig/ref :blaze.fhir/structure-definition-repo)}

   :blaze.fhir/structure-definition-repo {}

   :blaze.db.node/resource-indexer
   {:kv-store (ig/ref :blaze.db/index-kv-store)
    :resource-store (ig/ref ::rs/kv)
    :search-param-registry (ig/ref :blaze.db/search-param-registry)
    :executor (ig/ref ::resource-indexer/executor)}

   ::resource-indexer/executor {}})


(deftest fails-on-kv-put-test
  (with-system [{:blaze.db.node/keys [resource-indexer]} system]
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
                 :blaze.db.node/keys [resource-indexer]} system]

    (let [observation {:fhir/type :fhir/Observation :id "0"
                       :subject #fhir/Reference{:reference "foo"}}
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


(deftest index-condition-resource-test
  (with-system [{kv-store [::kv/mem :blaze.db/index-kv-store]
                 resource-store ::rs/kv
                 :blaze.db.node/keys [resource-indexer]} system]
    (let [resource
          {:fhir/type :fhir/Condition :id "id-204446"
           :code
           #fhir/CodeableConcept
                   {:coding
                    [#fhir/Coding
                            {:system #fhir/uri"system-204435"
                             :code #fhir/code"code-204441"}]}
           :onset #fhir/dateTime"2020-01-30"
           :subject
           #fhir/Reference
                   {:reference "Patient/id-145552"}
           :meta
           #fhir/Meta
                   {:versionId #fhir/id"1"
                    :profile [#fhir/canonical"url-164445"]}}
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
                ["patient" (codec/v-hash "id-145552")]
                ["patient" (codec/tid-id
                             (codec/tid "Patient")
                             (codec/id-byte-string "id-145552"))]

                ["code" (codec/v-hash "system-204435|code-204441")]
                ["code" (codec/v-hash "system-204435|")]
                ["code" (codec/v-hash "code-204441")]
                ["onset-date" (codec-date/encode-range (LocalDate/of 2020 1 30))]
                ["subject" (codec/v-hash "Patient/id-145552")]
                ["subject" (codec/v-hash "id-145552")]
                ["subject" (codec/tid-id
                             (codec/tid "Patient")
                             (codec/id-byte-string "id-145552"))]
                ["_profile" (codec/v-hash "url-164445")]
                ["_id" (codec/v-hash "id-204446")]
                ["_lastUpdated" #blaze/byte-string"80008001"]])))

      (testing "ResourceSearchParamValue index"
        (is (every? #{["Condition" "id-204446" #blaze/hash-prefix"4AB29C7B"]}
                    (r-sp-v-tu/decode-index-entries
                      kv-store :type :id :hash-prefix)))
        (is (= (r-sp-v-tu/decode-index-entries kv-store :code :v-hash)
               [["patient" (codec/v-hash "Patient/id-145552")]
                ["patient" (codec/v-hash "id-145552")]
                ["patient" (codec/tid-id
                             (codec/tid "Patient")
                             (codec/id-byte-string "id-145552"))]
                ["code" (codec/v-hash "system-204435|code-204441")]
                ["code" (codec/v-hash "system-204435|")]
                ["code" (codec/v-hash "code-204441")]
                ["onset-date" (codec-date/encode-range (LocalDate/of 2020 1 30))]
                ["subject" (codec/v-hash "Patient/id-145552")]
                ["subject" (codec/v-hash "id-145552")]
                ["subject" (codec/tid-id
                             (codec/tid "Patient")
                             (codec/id-byte-string "id-145552"))]
                ["_profile" (codec/v-hash "url-164445")]
                ["_id" (codec/v-hash "id-204446")]
                ["_lastUpdated" #blaze/byte-string"80008001"]])))

      (testing "CompartmentResource index"
        (is (= (cr-tu/decode-index-entries kv-store :compartment :type :id)
               [[["Patient" "id-145552"] "Condition" "id-204446"]])))

      (testing "CompartmentSearchParamValueResource index"
        (is (every? #{[["Patient" "id-145552"] "Condition" "id-204446"
                       #blaze/hash-prefix"4AB29C7B"]}
                    (c-sp-vr-tu/decode-index-entries
                      kv-store :compartment :type :id :hash-prefix)))
        (is (= (c-sp-vr-tu/decode-index-entries kv-store :code :v-hash)
               [["patient" (codec/v-hash "Patient/id-145552")]
                ["patient" (codec/v-hash "id-145552")]
                ["patient" (codec/tid-id
                             (codec/tid "Patient")
                             (codec/id-byte-string "id-145552"))]
                ["code" (codec/v-hash "system-204435|code-204441")]
                ["code" (codec/v-hash "system-204435|")]
                ["code" (codec/v-hash "code-204441")]
                ["onset-date" (codec-date/encode-range (LocalDate/of 2020 1 30))]
                ["subject" (codec/v-hash "Patient/id-145552")]
                ["subject" (codec/v-hash "id-145552")]
                ["subject" (codec/tid-id
                             (codec/tid "Patient")
                             (codec/id-byte-string "id-145552"))]
                ["_profile" (codec/v-hash "url-164445")]
                ["_id" (codec/v-hash "id-204446")]
                ["_lastUpdated" #blaze/byte-string"80008001"]]))))))


(deftest index-observation-resource-test
  (with-system [{kv-store [::kv/mem :blaze.db/index-kv-store]
                 resource-store ::rs/kv
                 :blaze.db.node/keys [resource-indexer]} system]
    (let [resource {:fhir/type :fhir/Observation :id "id-192702"
                    :status #fhir/code"status-193613"
                    :category
                    [#fhir/CodeableConcept
                            {:coding
                             [#fhir/Coding
                                     {:system #fhir/uri"system-193558"
                                      :code #fhir/code"code-193603"}]}]
                    :code
                    #fhir/CodeableConcept
                            {:coding
                             [#fhir/Coding
                                     {:system #fhir/uri"system-193821"
                                      :code #fhir/code"code-193824"}]}
                    :subject
                    #fhir/Reference
                            {:reference "Patient/id-180857"}
                    :effective #fhir/dateTime"2005-06-17"
                    :value
                    #fhir/Quantity
                            {:code #fhir/code"kg/m2"
                             :system #fhir/uri"http://unitsofmeasure.org"
                             :value 23.42M}}
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
                 (bs/concat (codec/v-hash "code-193824")
                            (codec/quantity "" 23.42M))]
                ["code-value-quantity"
                 (bs/concat (codec/v-hash "code-193824")
                            (codec/quantity "kg/m2" 23.42M))]
                ["code-value-quantity"
                 (bs/concat (codec/v-hash "code-193824")
                            (codec/quantity "http://unitsofmeasure.org|kg/m2"
                                            23.42M))]
                ["code-value-quantity"
                 #blaze/byte-string"B02358E02AD0942D4F40902F3B6AE19A900926"]
                ["code-value-quantity"
                 #blaze/byte-string"B02358E02AD0942DE95B25E4B02F01AF900926"]
                ["code-value-quantity"
                 #blaze/byte-string"B02358E02AD0942DF35972C2DDEDDFE6900926"]
                ["code-value-quantity"
                 #blaze/byte-string"D47C56F6D0C25BA34F40902F3B6AE19A900926"]
                ["code-value-quantity"
                 #blaze/byte-string"D47C56F6D0C25BA3E95B25E4B02F01AF900926"]
                ["code-value-quantity"
                 #blaze/byte-string"D47C56F6D0C25BA3F35972C2DDEDDFE6900926"]
                ["date" (codec-date/encode-range (LocalDate/of 2005 6 17))]
                ["category" (codec/v-hash "system-193558|")]
                ["category" (codec/v-hash "code-193603")]
                ["category" (codec/v-hash "system-193558|code-193603")]
                ["patient" (codec/v-hash "id-180857")]
                ["patient" (codec/tid-id
                             (codec/tid "Patient")
                             (codec/id-byte-string "id-180857"))]
                ["patient" (codec/v-hash "Patient/id-180857")]
                ["code" (codec/v-hash "code-193824")]
                ["code" (codec/v-hash "system-193821|")]
                ["code" (codec/v-hash "system-193821|code-193824")]
                ["value-quantity" (codec/quantity "" 23.42M)]
                ["value-quantity" (codec/quantity "kg/m2" 23.42M)]
                ["value-quantity" (codec/quantity
                                    "http://unitsofmeasure.org|kg/m2"
                                    23.42M)]
                ["combo-code" (codec/v-hash "code-193824")]
                ["combo-code" (codec/v-hash "system-193821|")]
                ["combo-code" (codec/v-hash "system-193821|code-193824")]
                ["combo-value-quantity"
                 #blaze/byte-string"4F40902F3B6AE19A900926"]
                ["combo-value-quantity"
                 #blaze/byte-string"E95B25E4B02F01AF900926"]
                ["combo-value-quantity"
                 #blaze/byte-string"F35972C2DDEDDFE6900926"]
                ["combo-code-value-quantity"
                 #blaze/byte-string"825F9E2AAE526A184F40902F3B6AE19A900926"]
                ["combo-code-value-quantity"
                 #blaze/byte-string"825F9E2AAE526A18E95B25E4B02F01AF900926"]
                ["combo-code-value-quantity"
                 #blaze/byte-string"825F9E2AAE526A18F35972C2DDEDDFE6900926"]
                ["combo-code-value-quantity"
                 #blaze/byte-string"B02358E02AD0942D4F40902F3B6AE19A900926"]
                ["combo-code-value-quantity"
                 #blaze/byte-string"B02358E02AD0942DE95B25E4B02F01AF900926"]
                ["combo-code-value-quantity"
                 #blaze/byte-string"B02358E02AD0942DF35972C2DDEDDFE6900926"]
                ["combo-code-value-quantity"
                 #blaze/byte-string"D47C56F6D0C25BA34F40902F3B6AE19A900926"]
                ["combo-code-value-quantity"
                 #blaze/byte-string"D47C56F6D0C25BA3E95B25E4B02F01AF900926"]
                ["combo-code-value-quantity"
                 #blaze/byte-string"D47C56F6D0C25BA3F35972C2DDEDDFE6900926"]
                ["subject" (codec/v-hash "id-180857")]
                ["subject" (codec/tid-id
                             (codec/tid "Patient")
                             (codec/id-byte-string "id-180857"))]
                ["subject" (codec/v-hash "Patient/id-180857")]
                ["status" (codec/v-hash "status-193613")]
                ["_id" (codec/v-hash "id-192702")]
                ["_lastUpdated" #blaze/byte-string"80008001"]]))))))


(deftest index-delete-cmd-test
  (with-system [{kv-store [::kv/mem :blaze.db/index-kv-store]
                 :blaze.db.node/keys [resource-indexer]} system]
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
