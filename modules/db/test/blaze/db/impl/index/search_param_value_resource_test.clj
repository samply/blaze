(ns blaze.db.impl.index.search-param-value-resource-test
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.search-param-value-resource :as sp-vr]
   [blaze.db.impl.index.search-param-value-resource-spec]
   [blaze.db.impl.index.single-version-id :as svi]
   [blaze.db.impl.index.single-version-id-spec]
   [blaze.fhir.hash :as hash]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest]]
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
                   id (s/gen :blaze.db/id-byte-string)
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
                   id (s/gen :blaze.db/id-byte-string)
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
                   id (s/gen :blaze.db/id-byte-string)
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
                   id (s/gen :blaze.db/id-byte-string)
                   hash (s/gen :blaze.resource/hash)]
      (let [buf (bb/wrap (sp-vr/encode-key c-hash tid value id hash))]
        (= (bs/size id) (SearchParamValueResource/idSize buf))))))
