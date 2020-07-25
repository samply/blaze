(ns blaze.db.node.resource-indexer-test
  (:require
    [blaze.async-comp :as ac]
    [blaze.db.hash :as hash]
    [blaze.db.impl.codec :as codec]
    [blaze.db.kv :as kv]
    [blaze.db.kv.mem :refer [new-mem-kv-store]]
    [blaze.db.node.resource-indexer :as i :refer [new-resource-indexer]]
    [blaze.db.node.resource-indexer-spec]
    [blaze.db.resource-store :as rs]
    [blaze.db.search-param-registry :as sr]
    [blaze.executors :as ex]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]])
  (:import
    [java.time ZoneId]))


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
  (let [resource {:resourceType "Condition" :id "id-204446"
                  :code
                  {:coding
                   [{:system "system-204435"
                     :code "code-204441"}]}
                  :onsetDateTime "2020-01-30"
                  :subject {:reference "Patient/id-145552"}
                  :meta
                  {:versionId "1"
                   :profile
                   ["https://fhir.bbmri.de/StructureDefinition/Condition"]}}
        hash (hash/generate resource)
        rl (reify
              rs/ResourceLookup
              (-multi-get [_ _]
                (ac/completed-future {hash resource})))
        kv-store (init-kv-store)
        i (new-resource-indexer rl search-param-registry kv-store
                                (ex/single-thread-executor) 1)]
    @(i/index-resources i [hash])

    (testing "compartment-resource-type"
      (is
        (kv/get
          kv-store
          :compartment-resource-type-index
          (codec/compartment-resource-type-key
            (codec/c-hash "Patient") (codec/id-bytes "id-145552")
            (codec/tid "Condition") (codec/id-bytes "id-204446")))))

    (testing "code `code-204441` entry"
      (testing "standalone"
        (testing "search-param-value-index"
          (is
            (kv/get
              kv-store
              :search-param-value-index
              (codec/search-param-value-key
                (codec/c-hash "code")
                (codec/tid "Condition")
                (codec/v-hash "code-204441")
                (codec/id-bytes "id-204446")
                hash))))

        (testing "resource-value-index"
          (is
            (kv/get
              kv-store
              :resource-value-index
              (codec/resource-value-key
                (codec/tid "Condition")
                (codec/id-bytes "id-204446")
                hash
                (codec/c-hash "code")
                (codec/v-hash "code-204441"))))
          (is
            (kv/get
              kv-store
              :resource-value-index
              (codec/resource-value-key
                (codec/tid "Condition")
                (codec/id-bytes "id-204446")
                hash
                (codec/c-hash "code")
                (codec/v-hash "system-204435|"))))
          (is
            (kv/get
              kv-store
              :resource-value-index
              (codec/resource-value-key
                (codec/tid "Condition")
                (codec/id-bytes "id-204446")
                hash
                (codec/c-hash "code")
                (codec/v-hash "system-204435|code-204441"))))))

      (testing "Patient compartment"
        (testing "search-param-value-index"
          (is
            (kv/get
              kv-store
              :compartment-search-param-value-index
              (codec/compartment-search-param-value-key
                (codec/c-hash "Patient")
                (codec/id-bytes "id-145552")
                (codec/c-hash "code")
                (codec/tid "Condition")
                (codec/v-hash "code-204441")
                (codec/id-bytes "id-204446")
                hash))))))

    (testing "code `system-204435|code-204441` entry"
      (testing "standalone"
        (is
          (kv/get
            kv-store
            :search-param-value-index
            (codec/search-param-value-key
              (codec/c-hash "code")
              (codec/tid "Condition")
              (codec/v-hash "system-204435|code-204441")
              (codec/id-bytes "id-204446")
              hash))))

      (testing "Patient compartment"
        (is
          (kv/get
            kv-store
            :compartment-search-param-value-index
            (codec/compartment-search-param-value-key
              (codec/c-hash "Patient")
              (codec/id-bytes "id-145552")
              (codec/c-hash "code")
              (codec/tid "Condition")
              (codec/v-hash "system-204435|code-204441")
              (codec/id-bytes "id-204446")
              hash)))))

    (testing "onset-date `2020-01-30` lower bound entry"
      (is
        (kv/get
          kv-store
          :search-param-value-index
          (codec/search-param-value-key
            (codec/c-hash "onset-date")
            (codec/tid "Condition")
            (codec/date-lb (ZoneId/systemDefault) "2020-01-30")
            (codec/id-bytes "id-204446")
            hash))))

    (testing "onset-date `2020-01-30` upper bound entry"
      (is
        (kv/get
          kv-store
          :search-param-value-index
          (codec/search-param-value-key
            (codec/c-hash "onset-date")
            (codec/tid "Condition")
            (codec/date-ub (ZoneId/systemDefault) "2020-01-30")
            (codec/id-bytes "id-204446")
            hash))))

    (testing "subject `Patient/0` entry"
      (is
        (kv/get
          kv-store
          :search-param-value-index
          (codec/search-param-value-key
            (codec/c-hash "subject")
            (codec/tid "Condition")
            (codec/v-hash "Patient/id-145552")
            (codec/id-bytes "id-204446")
            hash))))

    (testing "_id `id-204446` entry"
      (is
        (kv/get
          kv-store
          :search-param-value-index
          (codec/search-param-value-key
            (codec/c-hash "_id")
            (codec/tid "Condition")
            (codec/v-hash "id-204446")
            (codec/id-bytes "id-204446")
            hash))))

    (testing "_profile `https://fhir.bbmri.de/StructureDefinition/Condition` entry"
      (is
        (kv/get
          kv-store
          :search-param-value-index
          (codec/search-param-value-key
            (codec/c-hash "_profile")
            (codec/tid "Condition")
            (codec/v-hash "https://fhir.bbmri.de/StructureDefinition/Condition")
            (codec/id-bytes "id-204446")
            hash))))))


(deftest index-observation-resource
  (let [resource {:resourceType "Observation" :id "id-192702"
                  :status "status-193613"
                  :category
                  [{:coding
                    [{:system "system-193558"
                      :code "code-193603"}]}]
                  :code
                  {:coding
                   [{:system "system-193821"
                     :code "code-193824"}]}
                  :subject {:reference "reference-193945"}
                  :effectiveDateTime "2005-06-17"
                  :valueQuantity
                  {:code "kg/m2"
                   :system "http://unitsofmeasure.org"
                   :value 23.42M}}
        hash (hash/generate resource)
        rl (reify
              rs/ResourceLookup
              (-multi-get [_ _]
                (ac/completed-future {hash resource})))
        kv-store (init-kv-store)
        i (new-resource-indexer rl search-param-registry kv-store
                                (ex/single-thread-executor) 1)]
    @(i/index-resources i [hash])

    (testing "status `status-193613` entry"
      (is
        (kv/get
          kv-store
          :search-param-value-index
          (codec/search-param-value-key
            (codec/c-hash "status")
            (codec/tid "Observation")
            (codec/v-hash "status-193613")
            (codec/id-bytes "id-192702")
            hash))))

    (testing "category `code-193603` entry"
      (is
        (kv/get
          kv-store
          :search-param-value-index
          (codec/search-param-value-key
            (codec/c-hash "category")
            (codec/tid "Observation")
            (codec/v-hash "code-193603")
            (codec/id-bytes "id-192702")
            hash))))

    (testing "code `code-193824` entry"
      (is
        (kv/get
          kv-store
          :search-param-value-index
          (codec/search-param-value-key
            (codec/c-hash "code")
            (codec/tid "Observation")
            (codec/v-hash "code-193824")
            (codec/id-bytes "id-192702")
            hash))))

    (testing "subject `reference-193945` entry"
      (is
        (kv/get
          kv-store
          :search-param-value-index
          (codec/search-param-value-key
            (codec/c-hash "subject")
            (codec/tid "Observation")
            (codec/v-hash "reference-193945")
            (codec/id-bytes "id-192702")
            hash))))

    (testing "date `2005-06-17` lower bound entry"
      (is
        (kv/get
          kv-store
          :search-param-value-index
          (codec/search-param-value-key
            (codec/c-hash "date")
            (codec/tid "Observation")
            (codec/date-lb (ZoneId/systemDefault) "2005-06-17")
            (codec/id-bytes "id-192702")
            hash))))

    (testing "date `2005-06-17` upper bound entry"
      (is
        (kv/get
          kv-store
          :search-param-value-index
          (codec/search-param-value-key
            (codec/c-hash "date")
            (codec/tid "Observation")
            (codec/date-ub (ZoneId/systemDefault) "2005-06-17")
            (codec/id-bytes "id-192702")
            hash))))

    (testing "value-quantity `23` entry"
      (is
        (kv/get
          kv-store
          :search-param-value-index
          (codec/search-param-value-key
            (codec/c-hash "value-quantity")
            (codec/tid "Observation")
            (codec/quantity 23.42M)
            (codec/id-bytes "id-192702")
            hash))))

    (testing "value-quantity `http://unitsofmeasure.org|kg/m2` `23` entry"
      (is
        (kv/get
          kv-store
          :search-param-value-index
          (codec/search-param-value-key
            (codec/c-hash "value-quantity")
            (codec/tid "Observation")
            (codec/quantity 23.42M "http://unitsofmeasure.org|kg/m2")
            (codec/id-bytes "id-192702")
            hash))))))
