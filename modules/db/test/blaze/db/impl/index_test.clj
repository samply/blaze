(ns blaze.db.impl.index-test
  (:require
    [blaze.db.impl.bytes :as bytes]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.codec-stub :as codec-stub]
    [blaze.db.impl.index :as index]
    [blaze.db.impl.index-spec]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.kv :as kv]
    [blaze.db.kv.mem :refer [init-mem-kv-store]]
    [blaze.db.kv-stub :as kv-stub]
    [cheshire.core :as json]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [juxt.iota :refer [given]]
    [taoensso.nippy :as nippy])
  (:import
    [blaze.db.impl.index Hash Resource]
    [com.github.benmanes.caffeine.cache LoadingCache]
    [java.time Instant]))


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn resource-cache [kv-store]
  (reify LoadingCache
    (get [_ hash]
      (index/load-resource-content kv-store hash))))


(defn new-context []
  (let [kv-store
        (init-mem-kv-store
          {:search-param-value-index nil
           :resource-value-index nil
           :compartment-search-param-value-index nil
           :compartment-resource-value-index nil
           :resource-index nil
           :resource-as-of-index nil
           :tx-success-index nil
           :t-by-instant-index nil})]
    {:blaze.db/kv-store kv-store
     :blaze.db/resource-cache (resource-cache kv-store)}))


(defn- mk-resource [context type id state t]
  (index/mk-resource context id (codec/hash {:resourceType type :id id}) state t))


(deftest resource
  (testing "hash is part of equals"
    (is (not (.equals (mk-resource nil "Patient" "0" 0 0)
                      (mk-resource nil "Patient" "1" 0 0)))))

  (testing "state is not part of equals"
    (is (.equals (mk-resource nil "Patient" "0" 0 0)
                 (mk-resource nil "Patient" "0" 1 0))))

  (testing "t is part of equals"
    (is (not (.equals (mk-resource nil "Patient" "0" 0 0)
                      (mk-resource nil "Patient" "0" 0 1)))))

  (testing "resources can be serialized to JSON"
    (let [resource-cache
          (reify LoadingCache
            (get [_ _]
              {:id "0"
               :resourceType "Patient"}))
          resource (mk-resource {:blaze.db/resource-cache resource-cache} "Patient" "0" 0 0)]
      (is (= "{\"id\":\"0\",\"resourceType\":\"Patient\",\"meta\":{\"versionId\":\"0\"}}"
             (json/generate-string resource)))))

  (testing "resources has the right meta data"
    (let [{:blaze.db/keys [kv-store] :as context} (new-context)
          type "Patient"
          id "0"
          resource (mk-resource context "Patient" "0" (codec/state 1 :put) 0)]
      (kv/put kv-store (conj
                         (index/tx-success-entries 0 (Instant/ofEpochSecond 194004))
                         [:resource-index
                          (codec/hash {:resourceType type :id id})
                          (nippy/fast-freeze {:resourceType type :id id})]))
      (given (meta resource)
        :type := :fhir/Patient
        :blaze.db/num-changes := 1
        :blaze.db/op := :put
        :blaze.db/t := 0
        [:blaze.db/tx :blaze.db/t] := 0
        [:blaze.db/tx :blaze.db.tx/instant] := (Instant/ofEpochSecond 194004)
        count := 5))))


(deftest tx
  (st/unstrument `index/tx)
  (codec-stub/t-key ::t ::t-key)

  (testing "existing transaction"
    (kv-stub/get ::kv-store :tx-success-index ::t-key #{::tx-bytes})
    (codec-stub/decode-tx ::tx-bytes ::t ::tx)

    (is (= ::tx (index/tx ::kv-store ::t))))

  (testing "missing transaction"
    (kv-stub/get ::kv-store :tx-success-index ::t-key nil?)

    (is (nil? (index/tx ::kv-store ::t)))))


(def ^:private hash-mapper (map #(.hash ^Hash (.hash ^Resource %))))


(deftest type-query
  (testing "with an empty store"
    (let [context (new-context)
          search-param {:type "token" :code "foo" :expression "foo"}]
      (testing "finds nothing"
        (is
          (empty?
            (into
              []
              (index/type-query
                context
                (codec/tid "Foo")
                [[(search-param/search-param search-param) "bar"]]
                1)))))))

  (testing "with one token search-param entry in store"
    (let [{:blaze.db/keys [kv-store] :as context} (new-context)
          code "foo"
          search-param
          {:type "token" :code code :expression code}
          type "Type_125240"
          tid (codec/tid type)
          value "value-125223"
          id "foo-125323"
          hash (codec/hash {:id id :resourceType type})
          id (codec/id-bytes id)
          t 1]
      (kv/put kv-store [[:search-param-value-index
                         (codec/search-param-value-key
                           (codec/c-hash code)
                           tid
                           (codec/v-hash value)
                           id
                           hash)
                         bytes/empty]
                        [:resource-as-of-index
                         (codec/resource-as-of-key tid id t)
                         (codec/resource-as-of-value hash (codec/state 1 :put))]])

      (testing "finds the hash"
        (is
          (bytes/=
            hash
            (first
              (into
                []
                hash-mapper
                (index/type-query
                  context
                  tid
                  [[(search-param/search-param search-param) value]]
                  t))))))))

  (testing "with two token search-param entries of two resources"
    (let [{:blaze.db/keys [kv-store] :as context} (new-context)
          code "foo"
          search-param
          {:type "token" :code code :expression code}
          type "Type_125240"
          tid (codec/tid type)
          value "value-125223"
          id-0 "foo-125323"
          id-1 "foo-175333"
          hash-0 (codec/hash {:id id-0 :resourceType type})
          hash-1 (codec/hash {:id id-1 :resourceType type})
          id-0 (codec/id-bytes id-0)
          id-1 (codec/id-bytes id-1)
          t 1]
      (kv/put kv-store [[:search-param-value-index
                         (codec/search-param-value-key
                           (codec/c-hash code)
                           tid
                           (codec/v-hash value)
                           id-0
                           hash-0)
                         bytes/empty]
                        [:search-param-value-index
                         (codec/search-param-value-key
                           (codec/c-hash code)
                           tid
                           (codec/v-hash value)
                           id-1
                           hash-1)
                         bytes/empty]
                        [:resource-as-of-index
                         (codec/resource-as-of-key tid id-0 t)
                         (codec/resource-as-of-value hash-0 (codec/state 1 :put))]
                        [:resource-as-of-index
                         (codec/resource-as-of-key tid id-1 t)
                         (codec/resource-as-of-value hash-1 (codec/state 1 :put))]])

      (testing "finds both hashes"
        (let [[h0 h1]
              (into
                []
                hash-mapper
                (index/type-query
                  context
                  tid
                  [[(search-param/search-param search-param) value]]
                  t))]
          (is (bytes/= hash-0 h0))
          (is (bytes/= hash-1 h1))))))

  (testing "with two token search-param entries of the same resource"
    (let [{:blaze.db/keys [kv-store] :as context} (new-context)
          code "foo"
          search-param
          {:type "token" :code code :expression code}
          type "Type_125240"
          tid (codec/tid type)
          value "value-125223"
          id "foo-125323"
          hash-0 (codec/hash {:id id :resourceType type :a 1})
          hash-1 (codec/hash {:id id :resourceType type :a 2})
          id (codec/id-bytes id)
          old-t 1
          t 2]
      (kv/put kv-store [[:search-param-value-index
                         (codec/search-param-value-key
                           (codec/c-hash code)
                           tid
                           (codec/v-hash value)
                           id
                           hash-0)
                         bytes/empty]
                        [:search-param-value-index
                         (codec/search-param-value-key
                           (codec/c-hash code)
                           tid
                           (codec/v-hash value)
                           id
                           hash-1)
                         bytes/empty]
                        [:resource-as-of-index
                         (codec/resource-as-of-key tid id old-t)
                         (codec/resource-as-of-value hash-0 (codec/state 1 :put))]
                        [:resource-as-of-index
                         (codec/resource-as-of-key tid id t)
                         (codec/resource-as-of-value hash-1 (codec/state 2 :put))]])

      (testing "finds the hash on t"
        (is
          (bytes/=
            hash-1
            (first
              (into
                []
                hash-mapper
                (index/type-query
                  context
                  tid
                  [[(search-param/search-param search-param) value]]
                  t))))))))

  (testing "with two different token search-param entries of the same resource"
    (let [{:blaze.db/keys [kv-store] :as context} (new-context)
          code-0 "foo"
          code-1 "bar"
          search-param-0
          {:type "token" :code code-0 :expression code-0}
          search-param-1
          {:type "token" :code code-1 :expression code-1}
          type "Type_125240"
          tid (codec/tid type)
          value-0 "value-125223"
          value-1 "value-100853"
          id "foo-125323"
          hash (codec/hash {:id id :resourceType type})
          id (codec/id-bytes id)
          t 1]
      (kv/put kv-store [[:search-param-value-index
                         (codec/search-param-value-key
                           (codec/c-hash code-0)
                           tid
                           (codec/v-hash value-0)
                           id
                           hash)
                         bytes/empty]
                        [:resource-value-index
                         (codec/resource-value-key
                           tid
                           id
                           hash
                           (codec/c-hash code-1))
                         (codec/v-hash value-1)]
                        [:resource-as-of-index
                         (codec/resource-as-of-key tid id t)
                         (codec/resource-as-of-value hash (codec/state 1 :put))]])

      (testing "finds the hash"
        (is
          (bytes/=
            hash
            (first
              (into
                []
                hash-mapper
                (index/type-query
                  context
                  tid
                  [[(search-param/search-param search-param-0) value-0]
                   [(search-param/search-param search-param-1) value-1]]
                  t))))))))

  (testing "with one quantity search-param entry in store"
    (let [{:blaze.db/keys [kv-store] :as context} (new-context)
          code "foo"
          search-param
          {:type "quantity" :code code :expression code}
          type "Type_125240"
          tid (codec/tid type)
          value "23|http://unitsofmeasure.org|kg"
          id "foo-190951"
          hash (codec/hash {:id id :resourceType type})
          id (codec/id-bytes id)
          t 1]
      (kv/put kv-store [[:search-param-value-index
                         (codec/search-param-value-key
                           (codec/c-hash code)
                           tid
                           (codec/quantity 23M "http://unitsofmeasure.org|kg")
                           id
                           hash)
                         bytes/empty]
                        [:resource-as-of-index
                         (codec/resource-as-of-key tid id t)
                         (codec/resource-as-of-value hash (codec/state 1 :put))]])

      (testing "finds the hash"
        (is
          (bytes/=
            hash
            (first
              (into
                []
                hash-mapper
                (index/type-query
                  context
                  tid
                  [[(search-param/search-param search-param) value]]
                  t))))))))

  (testing "with two different quantity search-param entries of the same resource"
    (let [{:blaze.db/keys [kv-store] :as context} (new-context)
          code-0 "foo"
          code-1 "bar"
          search-param-0
          {:type "quantity" :code code-0 :expression code-0}
          search-param-1
          {:type "quantity" :code code-1 :expression code-1}
          type "Type_125240"
          tid (codec/tid type)
          value-0 "23|http://unitsofmeasure.org|kg"
          value-1 "42|http://unitsofmeasure.org|kg"
          id "foo-125323"
          hash (codec/hash {:id id :resourceType type})
          id (codec/id-bytes id)
          t 1]
      (kv/put kv-store [[:search-param-value-index
                         (codec/search-param-value-key
                           (codec/c-hash code-0)
                           tid
                           (codec/quantity 23M "http://unitsofmeasure.org|kg")
                           id
                           hash)
                         bytes/empty]
                        [:resource-value-index
                         (codec/resource-value-key
                           tid
                           id
                           hash
                           (codec/c-hash code-1))
                         (codec/quantity 42M "http://unitsofmeasure.org|kg")]
                        [:resource-as-of-index
                         (codec/resource-as-of-key tid id t)
                         (codec/resource-as-of-value hash (codec/state 1 :put))]])

      (testing "finds the hash"
        (is
          (bytes/=
            hash
            (first
              (into
                []
                hash-mapper
                (index/type-query
                  context
                  tid
                  [[(search-param/search-param search-param-0) value-0]
                   [(search-param/search-param search-param-1) value-1]]
                  t)))))))))
