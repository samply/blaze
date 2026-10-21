(ns blaze.db.impl.index.search-param-value-resource-test
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.index-handle :as ih]
   [blaze.db.impl.index.index-handle-spec]
   [blaze.db.impl.index.search-param-value-resource :as sp-vr]
   [blaze.db.impl.index.search-param-value-resource-spec]
   [blaze.db.impl.index.single-version-id :as svi]
   [blaze.db.impl.index.single-version-id-spec]
   [blaze.db.impl.search-param.util :as u]
   [blaze.db.impl.search-param.util-spec]
   [blaze.fhir.hash :as hash]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop])
  (:import
   [blaze.db.impl.index SearchParamValueResource]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn- create-prefix [c-hash tid value]
  (-> (bb/allocate (+ codec/c-hash-size codec/tid-size (bs/size value)))
      (bb/put-int! c-hash)
      (bb/put-int! tid)
      (bb/put-byte-string! value)
      bb/flip!
      bs/from-byte-buffer!))

(deftest decode-key-test
  (satisfies-prop 100
    (prop/for-all [c-hash (s/gen :blaze.db/c-hash)
                   tid (s/gen :blaze.db/tid)
                   value (s/gen :blaze.db/byte-string)
                   id (s/gen :blaze.resource/id)
                   hash (s/gen :blaze.resource/hash)]
      (let [buf (bb/wrap (sp-vr/encode-key c-hash tid value id hash))
            [prefix single-version-id] (sp-vr/decode-key buf)]
        (and (= (create-prefix c-hash tid value) prefix)
             (= id (svi/id single-version-id))
             (= (hash/prefix hash) (svi/hash-prefix single-version-id)))))))

(deftest decode-value-single-version-id-test
  (satisfies-prop 100
    (prop/for-all [c-hash (s/gen :blaze.db/c-hash)
                   tid (s/gen :blaze.db/tid)
                   value (s/gen :blaze.db/byte-string)
                   id (s/gen :blaze.resource/id)
                   hash (s/gen :blaze.resource/hash)]
      (let [buf (bb/wrap (sp-vr/encode-key c-hash tid value id hash))
            [act-value single-version-id] (sp-vr/decode-value-single-version-id buf)]
        (and (= value act-value)
             (= id (svi/id single-version-id))
             (= (hash/prefix hash) (svi/hash-prefix single-version-id)))))))

(deftest decode-single-version-id-test
  (satisfies-prop 100
    (prop/for-all [c-hash (s/gen :blaze.db/c-hash)
                   tid (s/gen :blaze.db/tid)
                   value (s/gen :blaze.db/byte-string)
                   id (s/gen :blaze.resource/id)
                   hash (s/gen :blaze.resource/hash)]
      (let [buf (bb/wrap (sp-vr/encode-key c-hash tid value id hash))
            single-version-id (sp-vr/decode-single-version-id buf)]
        (and (= id (svi/id single-version-id))
             (= (hash/prefix hash) (svi/hash-prefix single-version-id)))))))

(deftest id-size-test
  (satisfies-prop 1000
    (prop/for-all [c-hash (s/gen :blaze.db/c-hash)
                   tid (s/gen :blaze.db/tid)
                   value (s/gen :blaze.db/byte-string)
                   id (s/gen :blaze.resource/id)
                   hash (s/gen :blaze.resource/hash)]
      (let [buf (bb/wrap (sp-vr/encode-key c-hash tid value id hash))]
        (= (codec/id-size id) (SearchParamValueResource/idSize buf))))))

(defn- reusing-buffer
  "Returns a transducer which puts each key into the same byte buffer, like the
  key reader of an iterator does."
  [keys]
  (let [buf (bb/allocate (transduce (map alength) max 0 keys))]
    (map
     (fn [key]
       (bb/clear! buf)
       (bb/put-byte-array! buf key)
       (bb/flip! buf)))))

(def ^:private id-gen
  "Generates ids from a small set in order to produce consecutive equal ids
  and ids with common prefixes and suffixes."
  (gen/one-of [(gen/elements ["0" "1" "00" "10" "01" "a"])
               (s/gen :blaze.resource/id)]))

(deftest by-id-grouper-test
  (testing "emits the same index handles as decoding and grouping single version ids"
    (satisfies-prop 1000
      (prop/for-all [c-hash (s/gen :blaze.db/c-hash)
                     tid (s/gen :blaze.db/tid)
                     entries (gen/vector (gen/tuple (s/gen :blaze.db/byte-string)
                                                    id-gen
                                                    (s/gen :blaze.resource/hash)))]
        (let [keys (mapv (fn [[value id hash]]
                           (sp-vr/encode-key c-hash tid value id hash))
                         entries)]
          (= (into [] (comp (map bb/wrap)
                            (map sp-vr/decode-single-version-id)
                            u/by-id-grouper)
                   keys)
             (into [] (comp (reusing-buffer keys) sp-vr/by-id-grouper)
                   keys))))))

  (testing "works with reduced values"
    (let [hash #blaze/hash"C9ADE22457D5AD750735B6B166E3CE8D6878D09B64C2C2868DCB6DE4C9EFBD4F"
          key (fn [id] (bb/wrap (sp-vr/encode-key 0 0 bs/empty id hash)))]
      (testing "on single id"
        (is (= "0" (transduce
                    sp-vr/by-id-grouper
                    (completing (fn [_ x] (reduced (ih/id x))))
                    nil
                    [(key "0")]))))

      (testing "on subsequent different id"
        (is (= "0" (transduce
                    sp-vr/by-id-grouper
                    (completing (fn [_ x] (reduced (ih/id x))))
                    nil
                    [(key "0") (key "1")])))))))
