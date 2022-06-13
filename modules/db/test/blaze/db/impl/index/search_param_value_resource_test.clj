(ns blaze.db.impl.index.search-param-value-resource-test
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.byte-string :as bs]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.search-param-value-resource :as sp-vr]
    [blaze.db.impl.index.search-param-value-resource-spec]
    [blaze.fhir.hash :as hash]
    [blaze.test-util :refer [satisfies-prop]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest]]
    [clojure.test.check.properties :as prop]))


(set! *warn-on-reflection* true)
(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn- create-prefix [c-hash tid value]
  (-> (bb/allocate (+ codec/c-hash-size codec/tid-size (bs/size value)))
      (bb/put-int! c-hash)
      (bb/put-int! tid)
      (bb/put-byte-string! value)
      bb/flip!
      bs/from-byte-buffer!))


(deftest decode-key-test
  (satisfies-prop 1000
    (prop/for-all [c-hash (s/gen :blaze.db/c-hash)
                   tid (s/gen :blaze.db/tid)
                   value (s/gen :blaze.db/byte-string)
                   did (s/gen :blaze.db/did)
                   hash (s/gen :blaze.resource/hash)]
      (let [buf (bb/wrap (sp-vr/encode-key c-hash tid value did hash))
            [prefix act-did hash-prefix] (sp-vr/decode-key buf)]
        (and (= (create-prefix c-hash tid value) prefix)
             (= did act-did)
             (= (hash/prefix hash) hash-prefix))))))


(deftest decode-value-id-hash-prefix-test
  (satisfies-prop 1000
    (prop/for-all [c-hash (s/gen :blaze.db/c-hash)
                   tid (s/gen :blaze.db/tid)
                   value (s/gen :blaze.db/byte-string)
                   did (s/gen :blaze.db/did)
                   hash (s/gen :blaze.resource/hash)]
      (let [buf (bb/wrap (sp-vr/encode-key c-hash tid value did hash))
            [act-value act-did hash-prefix] (sp-vr/decode-value-did-hash-prefix buf)]
        (and (= value act-value)
             (= did act-did)
             (= (hash/prefix hash) hash-prefix))))))


(deftest decode-id-hash-prefix-test
  (satisfies-prop 1000
    (prop/for-all [c-hash (s/gen :blaze.db/c-hash)
                   tid (s/gen :blaze.db/tid)
                   value (s/gen :blaze.db/byte-string)
                   did (s/gen :blaze.db/did)
                   hash (s/gen :blaze.resource/hash)]
      (let [buf (bb/wrap (sp-vr/encode-key c-hash tid value did hash))
            [act-did hash-prefix] (sp-vr/decode-did-hash-prefix buf)]
        (and (= did act-did)
             (= (hash/prefix hash) hash-prefix))))))
