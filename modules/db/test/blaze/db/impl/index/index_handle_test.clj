(ns blaze.db.impl.index.index-handle-test
  (:refer-clojure :exclude [hash])
  (:require
   [blaze.db.impl.index-spec]
   [blaze.db.impl.index.index-handle :as ih]
   [blaze.db.impl.index.index-handle-spec]
   [blaze.db.impl.index.resource-handle-test-util :refer [resource-handle]]
   [blaze.db.impl.index.single-version-id :as svi]
   [blaze.db.impl.index.single-version-id-spec]
   [blaze.db.kv.mem]
   [blaze.db.kv.mem-spec]
   [blaze.fhir.hash :as hash]
   [blaze.test-util :as tu :refer [given-thrown]]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [are deftest is testing]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn- hash [hp]
  (hash/from-hex (format "%08d%s" hp (str/join (repeat 56 "0")))))

(defn- svi
  ([hp]
   (svi 0 hp))
  ([id hp]
   (svi/single-version-id (str id) (hash hp))))

(defn- ih
  ([hp]
   (ih 0 hp))
  ([id hp]
   (ih/from-single-version-id (svi id hp))))

(deftest from-resource-handle-test
  (let [ih (ih/from-resource-handle (resource-handle "Patient" "0" 1 (hash 1)))]
    (is (= "0" (ih/id ih)))
    (is (= [1] (ih/hash-prefixes ih)))))

(deftest matches-hash-test
  (testing "matches"
    (is (true? (ih/matches-hash? (ih/conj (ih 1) (svi 2))
                                 (resource-handle "Patient" "0" 1 (hash 2))))))

  (testing "doesn't match"
    (is (false? (ih/matches-hash? (ih/conj (ih 1) (svi 2))
                                  (resource-handle "Patient" "0" 1 (hash 3)))))))

(deftest conj-hash-prefix-from-test
  (let [ih (ih/conj (ih 1) (svi 2))]
    (is (= "0" (ih/id ih)))
    (is (= [1 2] (ih/hash-prefixes ih))))

  (let [ih (ih/conj (ih 2) (svi 1))]
    (is (= "0" (ih/id ih)))
    (is (= [1 2] (ih/hash-prefixes ih))))

  (let [ih (ih/conj (ih/conj (ih 1) (svi 3)) (svi 2))]
    (is (= "0" (ih/id ih)))
    (is (= [1 2 3] (ih/hash-prefixes ih)))))

(defn- ihs
  "Creates an index handle with id 0 and all hash prefixes `hps`."
  [& hps]
  (reduce #(ih/conj %1 (svi %2)) (ih (first hps)) (rest hps)))

(def ^:private empty-ih
  (ih/intersection (ih 1) (ih 2)))

(deftest matches-hash-single-test
  (is (true? (ih/matches-hash? (ih 1) (resource-handle "Patient" "0" 1 (hash 1)))))
  (is (false? (ih/matches-hash? (ih 1) (resource-handle "Patient" "0" 1 (hash 2)))))
  (is (false? (ih/matches-hash? empty-ih (resource-handle "Patient" "0" 1 (hash 1))))))

(deftest conj-test
  (testing "conj of an existing hash prefix"
    (is (= (ih 1) (ih/conj (ih 1) (svi 1))))
    (is (= (ihs 1 2) (ih/conj (ihs 1 2) (svi 2)))))

  (testing "conj onto an empty index handle"
    (is (= (ih 3) (ih/conj empty-ih (svi 3))))
    (is (= [3] (ih/hash-prefixes (ih/conj empty-ih (svi 3)))))))

(deftest equals-test
  (testing "index handles with one hash prefix are equal regardless of how they were created"
    (are [x] (and (= (ih 1) x) (= x (ih 1)) (= (clojure.core/hash (ih 1)) (clojure.core/hash x)))
      (ih/from-resource-handle (resource-handle "Patient" "0" 1 (hash 1)))
      (ih/conj (ih 1) (svi 1))
      (ih/intersection (ihs 1 2) (ih 1))
      (ih/intersection (ih 1) (ihs 1 2))
      (ih/intersection (ihs 1 2) (ihs 1 3))
      (ih/union empty-ih (ih 1))
      (ih/union (ih 1) empty-ih)
      (ih/conj empty-ih (svi 1))))

  (testing "index handles with multiple hash prefixes are equal regardless of how they were created"
    (are [x] (and (= (ihs 1 2) x) (= x (ihs 1 2)) (= (clojure.core/hash (ihs 1 2)) (clojure.core/hash x)))
      (ihs 2 1)
      (ih/union (ih 1) (ih 2))
      (ih/union (ih 2) (ih 1))
      (ih/union (ihs 1 2) (ih 1))
      (ih/intersection (ihs 1 2 3) (ihs 1 2 4))))

  (testing "empty index handles are equal regardless of how they were created"
    (are [x] (and (= empty-ih x) (= x empty-ih) (= (clojure.core/hash empty-ih) (clojure.core/hash x)))
      (ih/intersection (ih 1) (ihs 2 3))
      (ih/intersection (ihs 2 3) (ih 1))
      (ih/intersection (ihs 1 2) (ihs 3 4))
      (ih/union empty-ih empty-ih)))

  (testing "not equal"
    (are [x y] (not= x y)
      (ih 1) (ih 2)
      (ih 0 1) (ih 1 1)
      (ih 1) (ihs 1 2)
      (ihs 1 2) (ihs 1 3)
      (ih 0 1) "foo"
      (ihs 1 2) "foo"
      (ih 1) empty-ih
      empty-ih (ih 1))))

(deftest to-single-version-ids-test
  (are [x svis] (= svis (ih/to-single-version-ids x))
    (ih 1) [(svi 1)]
    (ihs 1 2) [(svi 1) (svi 2)]
    empty-ih []))

(deftest to-string-test
  (are [x s] (= s (str x))
    (ih 1) "IndexHandle{id=0, hashPrefixes=[0x1]}"
    (ihs 1 2) "IndexHandle{id=0, hashPrefixes=[0x1,0x2]}"
    empty-ih "IndexHandle{id=0, hashPrefixes=[]}"))

(deftest intersection-test
  (are [ih-1 ih-2 hps] (= hps (ih/hash-prefixes (ih/intersection ih-1 ih-2)))
    (ih 1) (ih 1) [1]
    (ih 1) (ih 2) []
    (ih 1) (ih 3) []
    (ih 1) (ihs 1 2) [1]
    (ih 3) (ihs 1 2) []
    (ihs 1 2) (ih 2) [2]
    (ihs 1 2) (ih 3) []
    (ihs 1 2) (ihs 1 2) [1 2]
    (ihs 1 2 3) (ihs 2 3 4) [2 3]
    (ihs 1 2) (ihs 3 4) []
    empty-ih (ih 1) []
    (ih 1) empty-ih []
    empty-ih (ihs 1 2) []
    (ihs 1 2) empty-ih [])

  (testing "fails on unequal ids"
    (given-thrown (ih/intersection (ih 0 0) (ih 1 0))
      :message := "ids 0 and 1 differ")
    (given-thrown (ih/intersection (ih 0 0) (ih/conj (ih 1 0) (svi 1 1)))
      :message := "ids 0 and 1 differ")
    (given-thrown (ih/intersection (ih/conj (ih 0 0) (svi 0 1)) (ih 1 0))
      :message := "ids 0 and 1 differ")))

(deftest union-test
  (are [ih-1 ih-2 hps] (= hps (ih/hash-prefixes (ih/union ih-1 ih-2)))
    (ih 1) (ih 1) [1]
    (ih 1) (ih 2) [1 2]
    (ih 1) (ih 3) [1 3]
    (ih 2) (ih 1) [1 2]
    (ih 1) (ihs 1 2) [1 2]
    (ih 3) (ihs 1 2) [1 2 3]
    (ihs 1 2) (ih 2) [1 2]
    (ihs 2 3) (ih 1) [1 2 3]
    (ihs 1 3) (ihs 2 3) [1 2 3]
    (ihs 1 2) (ihs 3 4) [1 2 3 4]
    (ihs 3 4) (ihs 1 2) [1 2 3 4]
    empty-ih (ih 1) [1]
    (ih 1) empty-ih [1]
    empty-ih (ihs 1 2) [1 2]
    (ihs 1 2) empty-ih [1 2]
    empty-ih empty-ih [])

  (testing "fails on unequal ids"
    (given-thrown (ih/union (ih 0 0) (ih 1 0))
      :message := "ids 0 and 1 differ")
    (given-thrown (ih/union (ih 0 0) (ih/conj (ih 1 0) (svi 1 1)))
      :message := "ids 0 and 1 differ")
    (given-thrown (ih/union (ih/conj (ih 0 0) (svi 0 1)) (ih 1 0))
      :message := "ids 0 and 1 differ")))
