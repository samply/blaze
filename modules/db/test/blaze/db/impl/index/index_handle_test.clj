(ns blaze.db.impl.index.index-handle-test
  (:require
   [blaze.byte-string]
   [blaze.db.impl.index-spec]
   [blaze.db.impl.index.index-handle :as ih]
   [blaze.db.impl.index.index-handle-spec]
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
  (let [svi (ih/from-single-version-id one)
        mvi (ih/conj svi two)]
    (is (= #blaze/byte-string"00" (ih/id mvi)))
    (is (= [1 2] (ih/hash-prefixes mvi))))

  (let [svi (ih/from-single-version-id two)
        mvi (ih/conj svi one)]
    (is (= #blaze/byte-string"00" (ih/id mvi)))
    (is (= [1 2] (ih/hash-prefixes mvi))))

  (let [svi (ih/from-single-version-id one)
        mvi (ih/conj (ih/conj svi three) two)]
    (is (= #blaze/byte-string"00" (ih/id mvi)))
    (is (= [1 2 3] (ih/hash-prefixes mvi)))))
