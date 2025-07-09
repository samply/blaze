(ns blaze.db.impl.index.index-handle-test
  (:refer-clojure :exclude [hash])
  (:require
   [blaze.byte-string :as bs]
   [blaze.db.impl.index-spec]
   [blaze.db.impl.index.index-handle :as ih]
   [blaze.db.impl.index.index-handle-spec]
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
   (svi/single-version-id (bs/from-hex (format "%02d" id)) (hash hp))))

(defn- ih
  ([hp]
   (ih 0 hp))
  ([id hp]
   (ih/from-single-version-id (svi id hp))))

(deftest conj-hash-prefix-from-test
  (let [ih (ih/conj (ih 1) (svi 2))]
    (is (= #blaze/byte-string"00" (ih/id ih)))
    (is (= [1 2] (ih/hash-prefixes ih))))

  (let [ih (ih/conj (ih 2) (svi 1))]
    (is (= #blaze/byte-string"00" (ih/id ih)))
    (is (= [1 2] (ih/hash-prefixes ih))))

  (let [ih (ih/conj (ih/conj (ih 1) (svi 3)) (svi 2))]
    (is (= #blaze/byte-string"00" (ih/id ih)))
    (is (= [1 2 3] (ih/hash-prefixes ih)))))

(deftest intersection-test
  (are [ih-1 ih-2 hps] (= hps (ih/hash-prefixes (ih/intersection ih-1 ih-2)))
    (ih 1) (ih 1) [1]
    (ih 1) (ih 2) []
    (ih 1) (ih 3) [])

  (testing "fails on unequal ids"
    (given-thrown (ih/intersection (ih 0 0) (ih 1 0))
      :message := "ids 0x00 and 0x01 differ")))

(deftest union-test
  (are [ih-1 ih-2 hps] (= hps (ih/hash-prefixes (ih/union ih-1 ih-2)))
    (ih 1) (ih 1) [1]
    (ih 1) (ih 2) [1 2]
    (ih 1) (ih 3) [1 3])

  (testing "fails on unequal ids"
    (given-thrown (ih/union (ih 0 0) (ih 1 0))
      :message := "ids 0x00 and 0x01 differ")))
