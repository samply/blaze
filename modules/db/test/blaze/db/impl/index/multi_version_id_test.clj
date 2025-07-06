(ns blaze.db.impl.index.multi-version-id-test
  (:require
   [blaze.byte-string]
   [blaze.db.impl.index-spec]
   [blaze.db.impl.index.multi-version-id :as mvi]
   [blaze.db.impl.index.multi-version-id-spec]
   [blaze.db.impl.index.single-version-id :as svi]
   [blaze.db.impl.index.single-version-id-spec]
   [blaze.db.kv.mem]
   [blaze.db.kv.mem-spec]
   [blaze.fhir.hash :as hash]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest is]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def ^:private one
  (svi/single-version-id #blaze/byte-string"00" (hash/from-hex (str "00000001" (str/join (repeat 56 "0"))))))

(def ^:private two
  (svi/single-version-id #blaze/byte-string"00" (hash/from-hex (str "00000002" (str/join (repeat 56 "0"))))))

(def ^:private three
  (svi/single-version-id #blaze/byte-string"00" (hash/from-hex (str "00000003" (str/join (repeat 56 "0"))))))

(deftest conj-hash-prefix-from-test
  (let [set (mvi/from-single-version-id one)]
    (is (= (str (mvi/conj set two))
           "MultiVersionId{id=0x00, hashPrefixes=[0x1,0x2]}")))

  (let [set (mvi/from-single-version-id two)]
    (is (= (str (mvi/conj set one))
           "MultiVersionId{id=0x00, hashPrefixes=[0x1,0x2]}")))

  (let [set (mvi/from-single-version-id one)]
    (is (= (str (mvi/conj (mvi/conj set three) two))
           "MultiVersionId{id=0x00, hashPrefixes=[0x1,0x2,0x3]}"))))
