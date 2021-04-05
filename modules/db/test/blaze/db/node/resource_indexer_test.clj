(ns blaze.db.node.resource-indexer-test
  (:require
    [blaze.async.comp :as ac]
    [blaze.byte-string :as bs]
    [blaze.byte-string-spec]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.compartment.resource-test-util :as cr-tu]
    [blaze.db.impl.index.compartment.search-param-value-resource-test-util
     :as c-sp-vr-tu]
    [blaze.db.impl.index.resource-search-param-value-test-util :as r-sp-v-tu]
    [blaze.db.impl.index.search-param-value-resource-test-util :as sp-vr-tu]
    [blaze.db.kv.mem :refer [new-mem-kv-store]]
    [blaze.db.kv.mem-spec]
    [blaze.db.node.resource-indexer :as ri :refer [new-resource-indexer]]
    [blaze.db.node.resource-indexer-spec]
    [blaze.db.resource-store :as rs]
    [blaze.db.search-param-registry :as sr]
    [blaze.executors :as ex]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.hash-spec]
    [blaze.fhir.spec.type]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]])
  (:import
    [java.time ZoneId LocalDate]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def search-param-registry (sr/init-search-param-registry))


(defn init-kv-store []
  (new-mem-kv-store
    {:search-param-value-index nil
     :resource-value-index nil
     :compartment-search-param-value-index nil
     :compartment-resource-type-index nil
     :active-search-params nil}))


(deftest index-condition-resource
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
        hash (hash/generate resource)
        rl (reify
             rs/ResourceLookup
             (-multi-get [_ _]
               (ac/completed-future {hash resource})))
        kv-store (init-kv-store)
        i (new-resource-indexer rl search-param-registry kv-store
                                (ex/single-thread-executor) 1)]
    @(ri/index-resources i [hash])

    (testing "SearchParamValueResource index"
      (is (every? #{["Condition" "id-204446" #blaze/byte-string"4AB29C7B"]}
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
              ["onset-date" (codec/date-lb-ub
                              (codec/date-lb
                                (ZoneId/systemDefault)
                                (LocalDate/of 2020 1 30))
                              (codec/date-ub
                                (ZoneId/systemDefault)
                                (LocalDate/of 2020 1 30)))]
              ["subject" (codec/v-hash "Patient/id-145552")]
              ["subject" (codec/tid-id
                           (codec/tid "Patient")
                           (codec/id-byte-string "id-145552"))]
              ["subject" (codec/v-hash "id-145552")]
              ["_profile" (codec/v-hash "url-164445")]
              ["_id" (codec/v-hash "id-204446")]])))

    (testing "ResourceSearchParamValue index"
      (is (every? #{["Condition" "id-204446" #blaze/byte-string"4AB29C7B"]}
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
              ["onset-date" (codec/date-lb-ub
                              (codec/date-lb
                                (ZoneId/systemDefault)
                                (LocalDate/of 2020 1 30))
                              (codec/date-ub
                                (ZoneId/systemDefault)
                                (LocalDate/of 2020 1 30)))]
              ["subject" (codec/v-hash "Patient/id-145552")]
              ["subject" (codec/tid-id
                           (codec/tid "Patient")
                           (codec/id-byte-string "id-145552"))]
              ["subject" (codec/v-hash "id-145552")]
              ["_profile" (codec/v-hash "url-164445")]
              ["_id" (codec/v-hash "id-204446")]])))

    (testing "CompartmentResource index"
      (is (= (cr-tu/decode-index-entries kv-store :compartment :type :id)
             [[["Patient" "id-145552"] "Condition" "id-204446"]])))

    (testing "CompartmentSearchParamValueResource index"
      (is (every? #{[["Patient" "id-145552"] "Condition" "id-204446"
                     #blaze/byte-string"4AB29C7B"]}
                  (c-sp-vr-tu/decode-index-entries
                    kv-store :compartment :type :id :hash-prefix)))
      (is (= (c-sp-vr-tu/decode-index-entries kv-store :code :v-hash)
             [["patient" (codec/v-hash "Patient/id-145552")]
              ["patient" (codec/tid-id
                           (codec/tid "Patient")
                           (codec/id-byte-string "id-145552"))]
              ["patient" (codec/v-hash "id-145552")]
              ["code" (codec/v-hash "code-204441")]
              ["code" (codec/v-hash "system-204435|")]
              ["code" (codec/v-hash "system-204435|code-204441")]
              ["onset-date" (codec/date-lb-ub
                              (codec/date-lb
                                (ZoneId/systemDefault)
                                (LocalDate/of 2020 1 30))
                              (codec/date-ub
                                (ZoneId/systemDefault)
                                (LocalDate/of 2020 1 30)))]
              ["subject" (codec/v-hash "Patient/id-145552")]
              ["subject" (codec/tid-id
                           (codec/tid "Patient")
                           (codec/id-byte-string "id-145552"))]
              ["subject" (codec/v-hash "id-145552")]
              ["_profile" (codec/v-hash "url-164445")]
              ["_id" (codec/v-hash "id-204446")]])))))


(deftest index-observation-resource
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
        hash (hash/generate resource)
        rl (reify
             rs/ResourceLookup
             (-multi-get [_ _]
               (ac/completed-future {hash resource})))
        kv-store (init-kv-store)
        i (new-resource-indexer rl search-param-registry kv-store
                                (ex/single-thread-executor) 1)]
    @(ri/index-resources i [hash])

    (testing "SearchParamValueResource index"
      (is (every? #{["Observation" "id-192702" #blaze/byte-string"651D1F37"]}
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
              ["date" (codec/date-lb-ub
                        (codec/date-lb
                          (ZoneId/systemDefault)
                          (LocalDate/of 2005 6 17))
                        (codec/date-ub
                          (ZoneId/systemDefault)
                          (LocalDate/of 2005 6 17)))]
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
              ["_id" (codec/v-hash "id-192702")]])))))
