(ns blaze.db.impl.index.single-version-id-test
  (:require
   [blaze.db.impl.index.resource-handle-test-util :refer [resource-handle]]
   [blaze.db.impl.index.single-version-id :as svi]
   [blaze.db.impl.index.single-version-id-spec]
   [blaze.fhir.hash :as hash]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest]]
   [clojure.test.check.properties :as prop]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest from-resource-handle-test
  (satisfies-prop 100
    (prop/for-all [id (s/gen :blaze.resource/id)
                   hash (s/gen :blaze.resource/hash)]
      (let [svi (svi/from-resource-handle (resource-handle "Patient" id 1 hash))]
        (and (= id (svi/id svi))
             (= (hash/prefix hash) (svi/hash-prefix svi)))))))

(deftest matches-hash-test
  (satisfies-prop 100
    (prop/for-all [hash-1 (s/gen :blaze.resource/hash)
                   hash-2 (s/gen :blaze.resource/hash)]
      (let [svi (svi/from-resource-handle (resource-handle "Patient" "0" 1 hash-1))]
        (and (svi/matches-hash? svi (resource-handle "Patient" "0" 1 hash-1))
             (= (= (hash/prefix hash-1) (hash/prefix hash-2))
                (svi/matches-hash? svi (resource-handle "Patient" "0" 1 hash-2))))))))
