(ns blaze.byte-string-test
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.byte-string-spec]
   [blaze.test-util :as tu :refer [ba bb bytes=]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest from-byte-array-test
  (are [ba bs] (= bs (bs/from-byte-array ba) (apply bs/from-byte-array ba []))
    (ba) #blaze/byte-string""
    (ba 0x01) #blaze/byte-string"01"
    (ba 0x01 0x02) #blaze/byte-string"0102"))

(deftest from-utf8-string-test
  (are [s bs] (= bs (bs/from-utf8-string s) (apply bs/from-utf8-string s []))
    "" #blaze/byte-string""
    "a" #blaze/byte-string"61"
    "ab" #blaze/byte-string"6162"
    "👍" #blaze/byte-string"F09F918D"))

(deftest from-iso-8859-1-string-test
  (are [s bs] (= bs (bs/from-iso-8859-1-string s)
                 (apply bs/from-iso-8859-1-string s []))
    "" #blaze/byte-string""
    "a" #blaze/byte-string"61"
    "ab" #blaze/byte-string"6162"))

(deftest from-byte-buffer-test
  (testing "without size"
    (are [bb bs] (= bs (bs/from-byte-buffer! bb)
                    (apply bs/from-byte-buffer! bb []))
      (bb) #blaze/byte-string""
      (bb 0x01) #blaze/byte-string"01"
      (bb 0x01 0x02) #blaze/byte-string"0102"))

  (testing "with size"
    (are [bb size bs] (= bs (bs/from-byte-buffer! bb size)
                         (apply bs/from-byte-buffer! bb size []))
      (bb) 0 #blaze/byte-string""
      (bb 0x01) 1 #blaze/byte-string"01"
      (bb 0x01 0x02) 1 #blaze/byte-string"01"
      (bb 0x01 0x02) 2 #blaze/byte-string"0102")))

(deftest from-byte-buffer-null-terminated-test
  (testing "empty byte buffer"
    (is (nil? (bs/from-byte-buffer-null-terminated! (bb)))))

  (testing "one non-null byte"
    (is (nil? (bs/from-byte-buffer-null-terminated! (bb 0x01)))))

  (testing "one null byte"
    (let [bb (bb 0x00)]
      (is (= #blaze/byte-string"" (bs/from-byte-buffer-null-terminated! bb)))
      (is (zero? (bb/remaining bb)))))

  (testing "one non-null byte followed by one null byte"
    (let [bb (bb 0x01 0x00)]
      (is (= #blaze/byte-string"01" (bs/from-byte-buffer-null-terminated! bb)))
      (is (zero? (bb/remaining bb)))))

  (testing "one non-null byte followed by one null byte and one non-null byte"
    (let [bb (bb 0x01 0x00 0x01)]
      (is (= #blaze/byte-string"01" (bs/from-byte-buffer-null-terminated! bb)))
      (is (= 1 (bb/remaining bb))))))

(deftest from-hex-test
  (are [s bs] (= bs (bs/from-hex s))
    "" #blaze/byte-string""
    "01" #blaze/byte-string"01"
    "0102" #blaze/byte-string"0102"))

(deftest nth-test
  (are [bs index b] (= b (apply bs/nth bs index []))
    #blaze/byte-string"01" 0 1
    #blaze/byte-string"0102" 1 2))

(deftest size-test
  (are [bs size] (= size (apply bs/size bs []))
    #blaze/byte-string"" 0
    #blaze/byte-string"01" 1
    #blaze/byte-string"0102" 2))

(deftest subs-test
  (are [bs start res] (= res (apply bs/subs bs start []))
    #blaze/byte-string"" 0 #blaze/byte-string""
    #blaze/byte-string"01" 0 #blaze/byte-string"01"
    #blaze/byte-string"01" 1 #blaze/byte-string""
    #blaze/byte-string"0102" 0 #blaze/byte-string"0102"
    #blaze/byte-string"0102" 1 #blaze/byte-string"02"
    #blaze/byte-string"0102" 2 #blaze/byte-string"")

  (are [bs start end res] (= res (apply bs/subs bs start end []))
    #blaze/byte-string"" 0 0 #blaze/byte-string""
    #blaze/byte-string"01" 0 0 #blaze/byte-string""
    #blaze/byte-string"01" 0 1 #blaze/byte-string"01"
    #blaze/byte-string"01" 1 1 #blaze/byte-string""
    #blaze/byte-string"0102" 0 0 #blaze/byte-string""
    #blaze/byte-string"0102" 0 1 #blaze/byte-string"01"
    #blaze/byte-string"0102" 0 2 #blaze/byte-string"0102"
    #blaze/byte-string"0102" 1 1 #blaze/byte-string""
    #blaze/byte-string"0102" 1 2 #blaze/byte-string"02"
    #blaze/byte-string"0102" 2 2 #blaze/byte-string""))

(deftest concat-test
  (are [a b res] (= res (apply bs/concat a b []))
    #blaze/byte-string"" #blaze/byte-string"" #blaze/byte-string""
    #blaze/byte-string"" #blaze/byte-string"00" #blaze/byte-string"00"
    #blaze/byte-string"00" #blaze/byte-string"" #blaze/byte-string"00"
    #blaze/byte-string"00" #blaze/byte-string"00" #blaze/byte-string"0000"
    #blaze/byte-string"01" #blaze/byte-string"02" #blaze/byte-string"0102"
    #blaze/byte-string"02" #blaze/byte-string"01" #blaze/byte-string"0201"))

(deftest lt-test
  (testing "true"
    (are [a b] (true? (bs/< a b))
      #blaze/byte-string"" #blaze/byte-string"00"
      #blaze/byte-string"00" #blaze/byte-string"01"))

  (testing "false"
    (are [a b] (false? (bs/< a b))
      #blaze/byte-string"" #blaze/byte-string""
      #blaze/byte-string"00" #blaze/byte-string"00")))

(deftest le-test
  (testing "two args"
    (testing "true"
      (are [a b] (true? (bs/<= a b))
        #blaze/byte-string"" #blaze/byte-string""
        #blaze/byte-string"" #blaze/byte-string"00"
        #blaze/byte-string"" #blaze/byte-string"00"
        #blaze/byte-string"00" #blaze/byte-string"00"))

    (testing "false"
      (are [a b] (false? (bs/<= a b))
        #blaze/byte-string"00" #blaze/byte-string"")))

  (testing "three args"
    (testing "true"
      (are [a b c] (true? (bs/<= a b c))
        #blaze/byte-string"" #blaze/byte-string"" #blaze/byte-string""
        #blaze/byte-string"" #blaze/byte-string"" #blaze/byte-string"00"
        #blaze/byte-string"" #blaze/byte-string"00" #blaze/byte-string"00"
        #blaze/byte-string"00" #blaze/byte-string"00" #blaze/byte-string"00"
        #blaze/byte-string"00" #blaze/byte-string"00" #blaze/byte-string"01"
        #blaze/byte-string"00" #blaze/byte-string"01" #blaze/byte-string"01"
        #blaze/byte-string"01" #blaze/byte-string"01" #blaze/byte-string"01"))

    (testing "false"
      (are [a b c] (false? (bs/<= a b c))
        #blaze/byte-string"00" #blaze/byte-string"" #blaze/byte-string""
        #blaze/byte-string"" #blaze/byte-string"00" #blaze/byte-string""
        #blaze/byte-string"00" #blaze/byte-string"00" #blaze/byte-string""))))

(deftest gt-test
  (testing "true"
    (are [a b] (true? (bs/> a b))
      #blaze/byte-string"00" #blaze/byte-string""
      #blaze/byte-string"01" #blaze/byte-string"00"))

  (testing "false"
    (are [a b] (false? (bs/> a b))
      #blaze/byte-string"" #blaze/byte-string""
      #blaze/byte-string"00" #blaze/byte-string"00")))

(deftest comparable-test
  (are [unsorted sorted] (= sorted (sort unsorted))
    [#blaze/byte-string"02" #blaze/byte-string"00" #blaze/byte-string"01"]
    [#blaze/byte-string"00" #blaze/byte-string"01" #blaze/byte-string"02"]))

(deftest hex-test
  (are [bs res] (= res (bs/hex bs))
    #blaze/byte-string"" ""
    #blaze/byte-string"00" "00"
    #blaze/byte-string"01" "01"))

(deftest to-byte-array-test
  (are [bs res] (bytes= res (apply bs/to-byte-array bs []))
    #blaze/byte-string"" (ba)
    #blaze/byte-string"00" (ba 0x00)
    #blaze/byte-string"01" (ba 0x01)))

(deftest to-string-utf8-test
  (are [bs res] (= res (apply bs/to-string-utf8 bs []))
    #blaze/byte-string"" ""
    #blaze/byte-string"61" "a"
    #blaze/byte-string"6162" "ab"
    #blaze/byte-string"F09F918D" "👍"))

(deftest to-string-iso-8859-1-test
  (are [bs res] (= res (apply bs/to-string-iso-8859-1 bs []))
    #blaze/byte-string"" ""
    #blaze/byte-string"61" "a"
    #blaze/byte-string"6162" "ab"))

(deftest as-read-only-byte-buffer-test
  (are [bs res] (= res (bs/as-read-only-byte-buffer bs))
    #blaze/byte-string"" (bb)
    #blaze/byte-string"00" (bb 0x00)))

(deftest print-test
  (are [bs s] (= s (pr-str bs))
    #blaze/byte-string"" "#blaze/byte-string\"\""
    #blaze/byte-string"6162" "#blaze/byte-string\"6162\""))
