(ns blaze.db.impl.search-param.search-param-code-registry-test
  (:require
   [blaze.byte-string :as bs]
   [blaze.db.impl.search-param.search-param-code-registry :as search-param-code-registry]
   [blaze.db.impl.search-param.search-param-code-registry-spec]
   [blaze.db.kv :as kv]
   [blaze.db.kv.mem]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu :refer [ba satisfies-prop]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest encode-id-test
  (testing "out of range"
    (given (search-param-code-registry/encode-id 0)
      ::anom/category := ::anom/conflict
      ::anom/message := "The search-param-code identifier `0` is out of range. The range goes from 1 to 2^24 (exclusive).")

    (given (search-param-code-registry/encode-id (bit-shift-left 1 24))
      ::anom/category := ::anom/conflict
      ::anom/message := "The search-param-code identifier `16777216` is out of range. The range goes from 1 to 2^24 (exclusive)."))

  (testing "examples"
    (are [id bs] (= bs (bs/from-byte-array (search-param-code-registry/encode-id id)))
      1 #blaze/byte-string"000001"
      2 #blaze/byte-string"000002"
      10 #blaze/byte-string"00000A"
      15 #blaze/byte-string"00000F"
      16 #blaze/byte-string"000010"
      (bit-shift-left 1 20) #blaze/byte-string"100000"
      (dec (bit-shift-left 1 24)) #blaze/byte-string"FFFFFF")))

(deftest decode-id-test
  (testing "examples"
    (are [bs id] (= id (search-param-code-registry/decode-id bs))
      (ba 0x00 0x00 0x01) 1
      (ba 0x00 0x00 0x02) 2
      (ba 0x00 0x00 0x0A) 10
      (ba 0x00 0x00 0x0F) 15
      (ba 0x00 0x00 0x10) 16
      (ba 0x10 0x00 0x00) (bit-shift-left 1 20)
      (ba 0xFF 0xFF 0xFF) (dec (bit-shift-left 1 24)))))

(deftest encode-decode-id-test
  (satisfies-prop 10000
    (prop/for-all [i (gen/choose 1 (dec (bit-shift-left 1 24)))]
      (= i (search-param-code-registry/decode-id (search-param-code-registry/encode-id i))))))

(def ^:private config
  {::kv/mem
   {:column-families
    {:search-param-code nil}}})

(deftest id-of-test
  (with-system [{::kv/keys [mem]} config]

    (testing "first code has the identifier 000001"
      (is (= #blaze/byte-string"000001" (search-param-code-registry/id-of mem "foo"))))

    (testing "second code has the identifier 000002"
      (is (= #blaze/byte-string"000002" (search-param-code-registry/id-of mem "bar"))))

    (testing "first code has still the identifier 000001"
      (is (= #blaze/byte-string"000001" (search-param-code-registry/id-of mem "foo"))))

    (testing "second code has still the identifier 000002"
      (is (= #blaze/byte-string"000002" (search-param-code-registry/id-of mem "bar"))))))
