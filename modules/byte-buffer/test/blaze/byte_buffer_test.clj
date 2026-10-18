(ns blaze.byte-buffer-test
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.test-util :as tu :refer [given-thrown satisfies-prop]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop])
  (:import
   [java.nio BufferOverflowException BufferUnderflowException ByteBuffer ReadOnlyBufferException]
   [java.nio.charset StandardCharsets]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest allocate-test
  (satisfies-prop 100
    (prop/for-all [capacity gen/nat]
      (let [buf (apply bb/allocate capacity [])]
        (and (zero? (bb/position buf))
             (= capacity (bb/capacity buf) (bb/limit buf)
                (bb/remaining buf)))))))

(deftest wrap-test
  (satisfies-prop 100
    (prop/for-all [bytes gen/bytes]
      (let [buf (apply bb/wrap bytes [])]
        (and (zero? (bb/position buf))
             (= (alength ^bytes bytes) (bb/capacity buf) (bb/limit buf)
                (bb/remaining buf)))))))

(deftest capacity-test
  (satisfies-prop 100
    (prop/for-all [capacity gen/nat]
      (let [buf (bb/allocate capacity)]
        (= capacity (bb/capacity buf) (apply bb/capacity buf []))))))

(deftest put-byte-test
  (testing "inlined"
    (let [buf (bb/allocate 1)]
      (bb/put-byte! buf 1)
      (is (zero? (bb/remaining buf)))
      (is (= 1 (bb/get-byte! buf 0)))))

  (testing "function"
    (let [buf (bb/allocate 1)]
      (apply bb/put-byte! buf 1 [])
      (is (zero? (bb/remaining buf)))
      (is (= 1 (bb/get-byte! buf 0))))))

(deftest put-int-test
  (testing "inlined"
    (let [buf (bb/allocate 4)]
      (bb/put-int! buf 1)
      (is (zero? (bb/remaining buf)))
      (is (= 0 (bb/get-byte! buf 0)))
      (is (= 0 (bb/get-byte! buf 1)))
      (is (= 0 (bb/get-byte! buf 2)))
      (is (= 1 (bb/get-byte! buf 3)))))

  (testing "function"
    (let [buf (bb/allocate 4)]
      (apply bb/put-int! buf 1 [])
      (is (zero? (bb/remaining buf)))
      (is (= 0 (bb/get-byte! buf 0)))
      (is (= 0 (bb/get-byte! buf 1)))
      (is (= 0 (bb/get-byte! buf 2)))
      (is (= 1 (bb/get-byte! buf 3))))))

(deftest put-long-test
  (testing "inlined"
    (let [buf (bb/allocate 8)]
      (bb/put-long! buf 1)
      (is (zero? (bb/remaining buf)))
      (is (= 1 (bb/get-long! buf 0)))))

  (testing "function"
    (let [buf (bb/allocate 8)]
      (apply bb/put-long! buf 1 [])
      (is (zero? (bb/remaining buf)))
      (is (= 1 (bb/get-long! buf 0))))))

(deftest put-byte-array-test
  (testing "inlined"
    (testing "zero length"
      (let [buf (bb/allocate 0)]
        (bb/put-byte-array! buf (byte-array 0))
        (is (zero? (bb/remaining buf))))

      (let [buf (bb/allocate 1)]
        (bb/put-byte-array! buf (byte-array 0))
        (is (= 1 (bb/remaining buf)))))

    (testing "length one"
      (let [buf (bb/allocate 1)]
        (bb/put-byte-array! buf (byte-array [1]))
        (is (zero? (bb/remaining buf)))
        (is (= 1 (bb/get-byte! buf 0))))

      (testing "offset/length"
        (let [buf (bb/allocate 1)]
          (bb/put-byte-array! buf (byte-array [1]) 1 0)
          (is (= 1 (bb/remaining buf))))))

    (testing "length two"
      (let [buf (bb/allocate 2)]
        (bb/put-byte-array! buf (byte-array [1 2]))
        (is (zero? (bb/remaining buf)))
        (is (= 1 (bb/get-byte! buf 0)))
        (is (= 2 (bb/get-byte! buf 1))))

      (testing "offset/length"
        (let [buf (bb/allocate 1)]
          (bb/put-byte-array! buf (byte-array [1 2]) 1 1)
          (is (= 0 (bb/remaining buf)))
          (is (= 2 (bb/get-byte! buf 0)))))))

  (testing "function"
    (testing "zero length"
      (let [buf (bb/allocate 0)]
        (apply bb/put-byte-array! buf (byte-array 0) [])
        (is (zero? (bb/remaining buf))))

      (let [buf (bb/allocate 1)]
        (apply bb/put-byte-array! buf (byte-array 0) [])
        (is (= 1 (bb/remaining buf)))))

    (testing "length one"
      (let [buf (bb/allocate 1)]
        (apply bb/put-byte-array! buf (byte-array [1]) [])
        (is (zero? (bb/remaining buf)))
        (is (= 1 (bb/get-byte! buf 0))))

      (testing "offset/length"
        (let [buf (bb/allocate 1)]
          (apply bb/put-byte-array! buf (byte-array [1]) 1 0 [])
          (is (= 1 (bb/remaining buf))))))

    (testing "length two"
      (let [buf (bb/allocate 2)]
        (apply bb/put-byte-array! buf (byte-array [1 2]) [])
        (is (zero? (bb/remaining buf)))
        (is (= 1 (bb/get-byte! buf 0)))
        (is (= 2 (bb/get-byte! buf 1))))

      (testing "offset/length"
        (let [buf (bb/allocate 1)]
          (apply bb/put-byte-array! buf (byte-array [1 2]) 1 1 [])
          (is (= 0 (bb/remaining buf)))
          (is (= 2 (bb/get-byte! buf 0))))))))

(deftest put-byte-buffer-test
  (testing "inlined"
    (testing "zero length"
      (let [buf (bb/allocate 0)]
        (bb/put-byte-buffer! buf (bb/wrap (byte-array 0)))
        (is (zero? (bb/remaining buf))))

      (let [buf (bb/allocate 1)]
        (bb/put-byte-buffer! buf (bb/wrap (byte-array 0)))
        (is (= 1 (bb/remaining buf)))))

    (testing "length one"
      (let [buf (bb/allocate 1)]
        (bb/put-byte-buffer! buf (bb/wrap (byte-array [1])))
        (is (zero? (bb/remaining buf)))
        (is (= 1 (bb/get-byte! buf 0))))))

  (testing "function"
    (testing "zero length"
      (let [buf (bb/allocate 0)]
        (apply bb/put-byte-buffer! buf (bb/wrap (byte-array 0)) [])
        (is (zero? (bb/remaining buf))))

      (let [buf (bb/allocate 1)]
        (apply bb/put-byte-buffer! buf (bb/wrap (byte-array 0)) [])
        (is (= 1 (bb/remaining buf)))))

    (testing "length one"
      (let [buf (bb/allocate 1)]
        (apply bb/put-byte-buffer! buf (bb/wrap (byte-array [1])) [])
        (is (zero? (bb/remaining buf)))
        (is (= 1 (bb/get-byte! buf 0)))))))

(deftest put-byte-string-test
  (testing "inlined"
    (testing "zero length"
      (let [buf (bb/allocate 0)]
        (bb/put-byte-string! buf (bs/from-hex ""))
        (is (zero? (bb/remaining buf))))

      (let [buf (bb/allocate 1)]
        (bb/put-byte-string! buf (bs/from-hex ""))
        (is (= 1 (bb/remaining buf)))))

    (testing "length one"
      (let [buf (bb/allocate 1)]
        (bb/put-byte-string! buf (bs/from-hex "01"))
        (is (zero? (bb/remaining buf)))
        (is (= 1 (bb/get-byte! buf 0))))))

  (testing "function"
    (testing "zero length"
      (let [buf (bb/allocate 0)]
        (apply bb/put-byte-string! buf (bs/from-hex "") [])
        (is (zero? (bb/remaining buf))))

      (let [buf (bb/allocate 1)]
        (apply bb/put-byte-string! buf (bs/from-hex "") [])
        (is (= 1 (bb/remaining buf)))))

    (testing "length one"
      (let [buf (bb/allocate 1)]
        (apply bb/put-byte-string! buf (bs/from-hex "01") [])
        (is (zero? (bb/remaining buf)))
        (is (= 1 (bb/get-byte! buf 0)))))))

(deftest put-iso-8859-1-string-test
  (testing "zero length"
    (let [buf (bb/allocate 1)]
      (bb/put-iso-8859-1-string! buf "")
      (is (= 1 (bb/remaining buf)))))

  (testing "length two"
    (let [buf (bb/allocate 2)]
      (bb/put-iso-8859-1-string! buf "ab")
      (is (zero? (bb/remaining buf)))
      (is (= 0x61 (bb/get-byte! buf 0)))
      (is (= 0x62 (bb/get-byte! buf 1)))))

  (testing "returns the byte buffer"
    (let [buf (bb/allocate 1)]
      (is (identical? buf (bb/put-iso-8859-1-string! buf "a")))))

  (testing "function"
    (let [buf (bb/allocate 1)]
      (is (identical? buf (apply bb/put-iso-8859-1-string! buf "a" [])))
      (is (= 0x61 (bb/get-byte! buf 0)))))

  (testing "chars of the upper half of ISO-8859-1"
    (let [buf (bb/allocate 2)]
      (bb/put-iso-8859-1-string! buf "éÿ")
      (is (= (unchecked-byte 0xE9) (bb/get-byte! buf 0)))
      (is (= (unchecked-byte 0xFF) (bb/get-byte! buf 1)))))

  (testing "fails on chars outside of ISO-8859-1"
    (doseq [s ["Ł" "aŁ" "a€b"]]
      (is (thrown? IllegalArgumentException
                   (bb/put-iso-8859-1-string! (bb/allocate 3) s)))
      (given-thrown (bb/put-iso-8859-1-string! (bb/allocate 3) s)
        :message := (format "The string `%s` contains chars outside of ISO-8859-1." s))))

  (testing "fails on chars outside of ISO-8859-1 without writing anything"
    (doseq [s ["Ł" "aŁ" "a€b"]]
      (let [buf (bb/allocate 3)]
        (is (thrown? IllegalArgumentException (bb/put-iso-8859-1-string! buf s)))
        (is (zero? (bb/position buf)))
        (is (= (bb/allocate 3) (bb/rewind! buf))))))

  (testing "fails on overflow without writing anything"
    (let [buf (bb/allocate 2)]
      (is (thrown? BufferOverflowException (bb/put-iso-8859-1-string! buf "abc")))
      (is (zero? (bb/position buf)))
      (is (= (bb/allocate 2) (bb/rewind! buf)))))

  (testing "respects the array offset of a sliced buffer"
    (let [buf (.slice ^ByteBuffer (doto (bb/allocate 3) (bb/set-position! 1)))]
      (bb/put-iso-8859-1-string! buf "ab")
      (is (= 2 (bb/position buf)))
      (is (= (bb/wrap (byte-array [0 0x61 0x62])) (bb/wrap (.array ^ByteBuffer buf))))))

  (testing "matches String.getBytes with ISO-8859-1"
    (satisfies-prop 100
      (prop/for-all [s gen/string-alphanumeric]
        (let [buf (bb/allocate (count s))]
          (bb/put-iso-8859-1-string! buf s)
          (= (bb/wrap (.getBytes ^String s StandardCharsets/ISO_8859_1))
             (bb/flip! buf)))))))

(deftest put-null-terminated-iso-8859-1-string-test
  (testing "zero length adds only the null byte"
    (let [buf (bb/allocate 1)]
      (bb/put-null-terminated-iso-8859-1-string! buf "")
      (is (zero? (bb/remaining buf)))
      (is (= 0 (bb/get-byte! buf 0)))))

  (testing "fails on chars outside of ISO-8859-1"
    (given-thrown (bb/put-null-terminated-iso-8859-1-string! (bb/allocate 2) "Ł")
      :message := "The string `Ł` contains chars outside of ISO-8859-1."))

  (testing "length one followed by null"
    (let [buf (bb/allocate 2)]
      (bb/put-null-terminated-iso-8859-1-string! buf "a")
      (is (zero? (bb/remaining buf)))
      (is (= 0x61 (bb/get-byte! buf 0)))
      (is (= 0 (bb/get-byte! buf 1)))))

  (testing "function"
    (let [buf (bb/allocate 2)]
      (is (identical? buf (apply bb/put-null-terminated-iso-8859-1-string! buf "a" [])))
      (is (= 0x61 (bb/get-byte! buf 0)))
      (is (= 0 (bb/get-byte! buf 1))))))

(deftest limit-test
  (satisfies-prop 100
    (prop/for-all [capacity gen/nat]
      (let [buf (bb/allocate capacity)]
        (= capacity (bb/limit buf) (apply bb/limit buf []))))))

(deftest set-limit-test
  (testing "inlined"
    (let [buf (bb/allocate 1)]
      (bb/set-limit! buf 0)
      (is (zero? (bb/remaining buf)))))

  (testing "function"
    (let [buf (bb/allocate 1)]
      (apply bb/set-limit! buf 0 [])
      (is (zero? (bb/remaining buf))))))

(deftest position-test
  (let [buf (bb/allocate 1)]
    (is (zero? (bb/position buf)))
    (is (zero? (apply bb/position buf [])))))

(deftest set-position-test
  (testing "inlined"
    (let [buf (bb/allocate 1)]
      (bb/set-position! buf 1)
      (is (zero? (bb/remaining buf)))))

  (testing "function"
    (let [buf (bb/allocate 1)]
      (apply bb/set-position! buf 1 [])
      (is (zero? (bb/remaining buf))))))

(deftest remaining-test
  (testing "inlined"
    (let [buf (bb/allocate 1)]
      (is (= 1 (bb/remaining buf)))))

  (testing "function"
    (let [buf (bb/allocate 1)]
      (is (= 1 (apply bb/remaining buf []))))))

(deftest flip-test
  (testing "inlined"
    (let [buf (bb/allocate 2)]
      (bb/put-byte! buf 1)
      (bb/put-byte! buf 2)
      (bb/set-position! buf 1)
      (bb/flip! buf)
      (is (zero? (bb/position buf)))
      (is (= 1 (bb/limit buf)))
      (is (= 1 (bb/remaining buf)))
      (is (= 1 (bb/get-byte! buf)))))

  (testing "function"
    (let [buf (bb/allocate 2)]
      (bb/put-byte! buf 1)
      (bb/put-byte! buf 2)
      (bb/set-position! buf 1)
      (apply bb/flip! buf [])
      (is (zero? (bb/position buf)))
      (is (= 1 (bb/limit buf)))
      (is (= 1 (bb/remaining buf)))
      (is (= 1 (bb/get-byte! buf))))))

(deftest rewind-test
  (testing "inlined"
    (let [buf (bb/allocate 2)]
      (bb/put-byte! buf 1)
      (bb/put-byte! buf 2)
      (bb/rewind! buf)
      (is (zero? (bb/position buf)))
      (is (= 2 (bb/limit buf)))
      (is (= 2 (bb/remaining buf)))
      (is (= 1 (bb/get-byte! buf)))
      (is (= 2 (bb/get-byte! buf)))))

  (testing "function"
    (let [buf (bb/allocate 2)]
      (bb/put-byte! buf 1)
      (bb/put-byte! buf 2)
      (apply bb/rewind! buf [])
      (is (zero? (bb/position buf)))
      (is (= 2 (bb/limit buf)))
      (is (= 2 (bb/remaining buf)))
      (is (= 1 (bb/get-byte! buf)))
      (is (= 2 (bb/get-byte! buf))))))

(deftest clear-test
  (testing "inlined"
    (let [buf (bb/allocate 2)]
      (bb/put-byte! buf 1)
      (bb/set-limit! buf 1)
      (bb/clear! buf)
      (is (zero? (bb/position buf)))
      (is (= 2 (bb/limit buf)))
      (is (= 2 (bb/remaining buf)))))

  (testing "function"
    (let [buf (bb/allocate 2)]
      (bb/put-byte! buf 1)
      (bb/set-limit! buf 1)
      (apply bb/clear! buf [])
      (is (zero? (bb/position buf)))
      (is (= 2 (bb/limit buf)))
      (is (= 2 (bb/remaining buf))))))

(deftest mark-reset-test
  (testing "inlined"
    (let [buf (bb/allocate 2)]
      (bb/put-byte! buf 1)
      (bb/mark! buf)
      (bb/put-byte! buf 2)
      (bb/reset! buf)
      (is (= 1 (bb/position buf)))
      (is (= 2 (bb/limit buf)))
      (is (= 1 (bb/remaining buf)))
      (is (= 2 (bb/get-byte! buf)))))

  (testing "function"
    (let [buf (bb/allocate 2)]
      (bb/put-byte! buf 1)
      (apply bb/mark! buf [])
      (bb/put-byte! buf 2)
      (apply bb/reset! buf [])
      (is (= 1 (bb/position buf)))
      (is (= 2 (bb/limit buf)))
      (is (= 1 (bb/remaining buf)))
      (is (= 2 (bb/get-byte! buf))))))

(deftest get-byte-test
  (testing "inlined"
    (let [buf (bb/allocate 1)]
      (bb/put-byte! buf 1)
      (bb/flip! buf)
      (is (= 1 (bb/get-byte! buf)))
      (is (= 1 (bb/get-byte! buf 0)))))

  (testing "function"
    (let [buf (bb/allocate 1)]
      (bb/put-byte! buf 1)
      (bb/flip! buf)
      (is (= 1 (apply bb/get-byte! buf [])))
      (is (= 1 (apply bb/get-byte! buf 0 []))))))

(deftest get-int-test
  (testing "inlined"
    (let [buf (bb/allocate 4)]
      (bb/put-int! buf 1)
      (bb/flip! buf)
      (is (= 1 (bb/get-int! buf)))))

  (testing "function"
    (let [buf (bb/allocate 4)]
      (bb/put-int! buf 1)
      (bb/flip! buf)
      (is (= 1 (apply bb/get-int! buf []))))))

(deftest get-long-test
  (testing "inlined"
    (let [buf (bb/allocate 8)]
      (bb/put-long! buf 1)
      (bb/flip! buf)
      (is (= 1 (bb/get-long! buf)))
      (is (= 1 (bb/get-long! buf 0)))))

  (testing "function"
    (let [buf (bb/allocate 8)]
      (bb/put-long! buf 1)
      (bb/flip! buf)
      (is (= 1 (apply bb/get-long! buf [])))
      (is (= 1 (apply bb/get-long! buf 0 []))))))

(deftest copy-into-byte-array-test
  (testing "inlined"
    (let [buf (bb/allocate 1)
          ba (byte-array 1)]
      (bb/put-byte! buf 23)
      (bb/flip! buf)
      (bb/copy-into-byte-array! buf ba)
      (is (= 23 (aget ba 0))))

    (testing "with offset and length"
      (let [buf (bb/allocate 1)
            ba (byte-array 3)]
        (bb/put-byte! buf 23)
        (bb/flip! buf)
        (bb/copy-into-byte-array! buf ba 1 1)
        (is (= 23 (aget ba 1))))))

  (testing "function"
    (let [buf (bb/allocate 1)
          ba (byte-array 1)]
      (bb/put-byte! buf 23)
      (bb/flip! buf)
      (apply bb/copy-into-byte-array! buf ba [])
      (is (= 23 (aget ba 0))))

    (testing "with offset and length"
      (let [buf (bb/allocate 1)
            ba (byte-array 3)]
        (bb/put-byte! buf 23)
        (bb/flip! buf)
        (apply bb/copy-into-byte-array! buf ba 1 1 [])
        (is (= 23 (aget ba 1)))))))

(deftest get-iso-8859-1-string-test
  (testing "function"
    (let [buf (bb/wrap (byte-array [0x61 0x62]))]
      (is (= "ab" (apply bb/get-iso-8859-1-string! buf 2 [])))
      (is (= 2 (bb/position buf)))))

  (testing "zero length"
    (let [buf (bb/wrap (byte-array [0x61]))]
      (is (= "" (bb/get-iso-8859-1-string! buf 0)))
      (is (zero? (bb/position buf)))))

  (testing "reads from the current position and increments it"
    (let [buf (bb/wrap (byte-array [0x61 0x62 0x63 0x64]))]
      (bb/set-position! buf 1)
      (is (= "bc" (bb/get-iso-8859-1-string! buf 2)))
      (is (= 3 (bb/position buf)))))

  (testing "respects the array offset of sliced buffers"
    (let [buf (.slice ^ByteBuffer (bb/wrap (byte-array [0x61 0x62 0x63])) 1 2)]
      (is (= "bc" (bb/get-iso-8859-1-string! buf 2)))
      (is (= 2 (bb/position buf)))))

  (testing "fails on buffers without accessible backing array"
    (let [buf (.asReadOnlyBuffer ^ByteBuffer (bb/wrap (byte-array [0x61 0x62])))]
      (is (thrown? ReadOnlyBufferException (bb/get-iso-8859-1-string! buf 2)))
      (is (zero? (bb/position buf)))))

  (testing "fails on less remaining bytes than requested"
    (let [buf (bb/wrap (byte-array [0x61]))]
      (is (thrown? BufferUnderflowException (bb/get-iso-8859-1-string! buf 2)))
      (is (zero? (bb/position buf)))))

  (testing "is the inverse of put-iso-8859-1-string!"
    (satisfies-prop 100
      (prop/for-all [s gen/string-alphanumeric]
        (let [buf (bb/allocate (count s))]
          (bb/put-iso-8859-1-string! buf s)
          (bb/flip! buf)
          (= s (bb/get-iso-8859-1-string! buf (count s))))))))

(deftest size-up-to-null-test
  (testing "empty buffer"
    (let [buf (bb/allocate 0)]
      (is (nil? (bb/size-up-to-null buf)))))

  (testing "buffer with only one null byte"
    (let [buf (bb/allocate 1)]
      (bb/put-byte! buf 0)
      (bb/flip! buf)
      (is (zero? (bb/size-up-to-null buf)))))

  (testing "buffer with only one non-null byte"
    (let [buf (bb/allocate 1)]
      (bb/put-byte! buf 1)
      (bb/flip! buf)
      (is (nil? (bb/size-up-to-null buf)))))

  (testing "buffer with one non-null and one null byte"
    (let [buf (bb/allocate 2)]
      (bb/put-byte! buf 1)
      (bb/put-byte! buf 0)
      (bb/flip! buf)
      (is (= 1 (bb/size-up-to-null buf)))))

  (testing "buffer with two null bytes"
    (let [buf (bb/allocate 2)]
      (bb/put-byte! buf 0)
      (bb/put-byte! buf 0)
      (bb/flip! buf)
      (is (zero? (bb/size-up-to-null buf)))))

  (testing "buffer with two non-null and one null byte"
    (let [buf (bb/allocate 3)]
      (bb/put-byte! buf 1)
      (bb/put-byte! buf 2)
      (bb/put-byte! buf 0)
      (bb/flip! buf)
      (is (= 2 (bb/size-up-to-null buf))))))

(deftest mismatch-test
  (let [a (bb/allocate 0)
        b (bb/allocate 0)]
    (is (= -1 (bb/mismatch a b) (apply bb/mismatch a b [])))))

(deftest array-test
  (let [buf (bb/allocate 0)]
    (is (zero? (alength ^bytes (bb/array buf))))
    (is (zero? (alength ^bytes (apply bb/array buf []))))))
