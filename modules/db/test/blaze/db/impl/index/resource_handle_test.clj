(ns blaze.db.impl.index.resource-handle-test
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.byte-string-builder :as bsb]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.index.resource-handle-spec]
   [blaze.db.impl.index.resource-handle-test-util :refer [resource-handle]]
   [blaze.fhir.hash :as hash]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as sg]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [clojure.test.check.properties :as prop])
  (:import
   [blaze.db.impl.index ResourceHandle]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest state->num-changes-test
  (are [state num-changes] (= num-changes (ResourceHandle/numChanges state))
    0 0
    256 1
    512 2))

(deftest fhir-type-test
  (testing "the type is the interned keyword of the type of the tid"
    (satisfies-prop 100
      (prop/for-all [type (sg/elements ["Patient" "Observation" "Condition"])]
        (let [rh (resource-handle type "0" 1)]
          (and (identical? (keyword "fhir" type) (:fhir/type rh))
               (identical? (keyword "fhir" type) (get rh :fhir/type)))))))

  (testing "the type lookup fails on unknown tid"
    (let [rh (rh/resource-handle! 0 "0" 1 0 (bb/allocate 40))]
      (is (thrown-with-msg? IllegalStateException #"Unknown tid 0\."
                            (:fhir/type rh)))
      (is (thrown-with-msg? IllegalStateException #"Unknown tid 0\."
                            (get rh :fhir/type))))))

(deftest not-found-key-test
  (is (nil? (:foo (resource-handle "Patient" "foo" 0))))
  (is (= ::not-found (:foo (resource-handle "Patient" "foo" 0) ::not-found))))

(deftest hash-test
  (satisfies-prop 100
    (prop/for-all [hash (s/gen :blaze.resource/hash)]
      (let [rh (resource-handle "Patient" "0" 1 hash)]
        (and (= hash (:hash rh))
             (= hash (get rh :hash)))))))

(deftest hash-prefix-test
  (satisfies-prop 100
    (prop/for-all [hash (s/gen :blaze.resource/hash)]
      (let [rh (resource-handle "Patient" "0" 1 hash)]
        (and (= (hash/prefix hash) (:hash-prefix rh))
             (= (hash/prefix hash) (get rh :hash-prefix)))))))

(deftest hash-into-byte-string-builder-test
  (satisfies-prop 100
    (prop/for-all [hash (s/gen :blaze.resource/hash)]
      (= (vec (hash/to-byte-array hash))
         (-> (bsb/allocate hash/size)
             (rh/hash-into-byte-string-builder! (resource-handle "Patient" "0" 1 hash))
             bsb/to-bytes
             vec)))))

(deftest tid-into-byte-buffer-test
  (satisfies-prop 100
    (prop/for-all [type (sg/elements ["Patient" "Observation" "Condition"])]
      (= (codec/tid type)
         (-> (bb/allocate 4)
             (rh/tid-into-byte-buffer! (resource-handle type "0" 1))
             bb/flip!
             bb/get-int!)))))

(deftest id-into-byte-buffer-test
  (satisfies-prop 100
    (prop/for-all [id (s/gen :blaze.resource/id)]
      (= (codec/id-byte-string id)
         (-> (bb/allocate (count id))
             (rh/id-into-byte-buffer! (resource-handle "Patient" id 1))
             bb/flip!
             bs/from-byte-buffer!)))))

(deftest hash-prefix-into-byte-buffer-test
  (satisfies-prop 100
    (prop/for-all [hash (s/gen :blaze.resource/hash)]
      (= (hash/prefix hash)
         (-> (bb/allocate hash/prefix-size)
             (rh/hash-prefix-into-byte-buffer! (resource-handle "Patient" "0" 1 hash))
             bb/flip!
             bb/get-int!
             (bit-and 0xFFFFFFFF))))))

(deftest with-id-test
  (satisfies-prop 100
    (prop/for-all [type (sg/elements ["Patient" "Observation" "Condition"])
                   id (s/gen :blaze.resource/id)
                   t (s/gen :blaze.db/t)
                   hash (s/gen :blaze.resource/hash)]
      (let [handle (resource-handle type id t hash)
            ;; an equal id that is another instance
            id (String. ^String id)
            result (rh/with-id handle id)]
        (and (identical? id (:id result))
             (= (:tid handle) (:tid result))
             (= t (:t result))
             (= hash (:hash result))
             (= (:num-changes handle) (:num-changes result))
             (= (:op handle) (:op result)))))))

(deftest equals-test
  (satisfies-prop 100
    (prop/for-all [type (sg/elements ["Patient" "Observation" "Condition"])
                   id (s/gen :blaze.resource/id)
                   t (s/gen :blaze.db/t)]

      (testing "same instance"
        (let [rh (resource-handle type id t)]
          (= rh rh)))

      (testing "separate instances"
        (let [rh-1 (resource-handle type id t)
              rh-2 (resource-handle type id t)]
          (= rh-1 rh-2))))))

(deftest to-string-test
  (satisfies-prop 100
    (prop/for-all [type (sg/elements ["Patient" "Observation" "Condition"])
                   id (s/gen :blaze.resource/id)
                   t (s/gen :blaze.db/t)]

      (= (format "%s[id = %s, t = %d]" type id t)
         (str (resource-handle type id t))))))
