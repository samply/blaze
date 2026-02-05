(ns blaze.elm.compiler.string-operators-test
  "17. String Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.db.api :as d]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.elm.compiler :as c]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.core-spec]
   [blaze.elm.compiler.string-operators]
   [blaze.elm.compiler.test-util :as ctu :refer [has-form]]
   [blaze.elm.literal :as elm]
   [blaze.elm.literal-spec]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]))

(st/instrument)
(ctu/instrument-compile)

(defn- fixture [f]
  (st/instrument)
  (ctu/instrument-compile)
  (f)
  (st/unstrument))

(test/use-fixtures :each fixture)

;; 17.1. Combine
;;
;; The Combine operator combines a list of strings, optionally separating each
;; string with the given separator.
;;
;; If either argument is null, or any element in the source list of strings is
;; null, the result is null.
;;
;; TODO: This definition is inconsistent with the CQL definition https://cql.hl7.org/2019May/09-b-cqlreference.html#combine
(deftest compile-combine-test
  (testing "Without separator"
    (are [src res] (= res (core/-eval (c/compile {} (elm/combine src)) {} nil nil))
      #elm/list [#elm/string "a"] "a"
      #elm/list [#elm/string "a" #elm/string "b"] "ab"

      #elm/list [] nil
      #elm/list [#elm/string "a" {:type "Null"}] nil
      #elm/list [{:type "Null"}] nil
      {:type "Null"} nil)

    (ctu/testing-unary-op elm/combine)

    (testing "form and static"
      (let [expr (ctu/dynamic-compile #elm/combine #elm/parameter-ref "x")]

        (has-form expr '(combine (param-ref "x")))

        (is (false? (core/-static expr))))))

  (testing "With separator"
    (are [src res] (= res (core/-eval (c/compile {} (elm/combine [src #elm/string " "])) {} nil nil))
      #elm/list [#elm/string "a"] "a"
      #elm/list [#elm/string "a" #elm/string "b"] "a b"

      #elm/list [] nil
      #elm/list [#elm/string "a" {:type "Null"}] nil
      #elm/list [{:type "Null"}] nil
      {:type "Null"} nil)

    (ctu/testing-binary-op elm/combine)

    (testing "form and static"
      (let [expr (ctu/dynamic-compile #elm/combine [#elm/parameter-ref "x"
                                                    #elm/parameter-ref "y"])]

        (has-form expr '(combine (param-ref "x") (param-ref "y")))

        (is (false? (core/-static expr)))))))

;; 17.2. Concatenate
;;
;; The Concatenate operator performs string concatenation of its arguments.
;;
;; If any argument is null, the result is null.
(deftest compile-concatenate-test
  (are [args res] (= res (core/-eval (c/compile {} (elm/concatenate args)) {} nil nil))
    [#elm/string "a"] "a"
    [#elm/string "a" #elm/string "b"] "ab"

    [#elm/string "a" {:type "Null"}] nil
    [{:type "Null"}] nil)

  (testing "form"
    (are [args form] (= form (c/form (c/compile {} (elm/concatenate args))))
      [#elm/string "a"] '(concatenate "a")
      [#elm/string "a" #elm/string "b"] '(concatenate "a" "b")
      [#elm/string "a" {:type "Null"}] '(concatenate "a" nil)))

  (testing "Static"
    (are [args] (false? (core/-static (c/compile {} (elm/concatenate args))))
      [#elm/string "a"]
      [#elm/string "a" #elm/string "b"]
      [#elm/string "a" {:type "Null"}]))

  (ctu/testing-binary-op elm/concatenate))

;; 17.3. EndsWith
;;
;; The EndsWith operator returns true if the given string ends with the given
;; suffix.
;;
;; If the suffix is the empty string, the result is true.
;;
;; If either argument is null, the result is null.
(deftest compile-ends-with-test
  (testing "Static"
    (are [s suffix pred] (pred (c/compile {} (elm/ends-with [s suffix])))
      #elm/string "a" #elm/string "a" true?
      #elm/string "ab" #elm/string "b" true?

      #elm/string "a" #elm/string "b" false?
      #elm/string "ba" #elm/string "b" false?))

  (testing "Dynamic"
    (are [s suffix pred] (pred (ctu/dynamic-compile-eval (elm/ends-with [s suffix])))
      #elm/parameter-ref "a" #elm/string "a" true?
      #elm/parameter-ref "ab" #elm/string "b" true?

      #elm/parameter-ref "a" #elm/string "b" false?
      #elm/parameter-ref "ba" #elm/string "b" false?))

  (ctu/testing-binary-null elm/ends-with #elm/string "a")

  (ctu/testing-binary-op elm/ends-with))

;; 17.4. Equal
;;
;; See 12.1. Equal

;; 17.5. Equivalent
;;
;; See 12.2. Equivalent

;; 17.6. Indexer
;;
;; The Indexer operator returns the indexth element in a string or list.
;;
;; Indexes in strings and lists are defined to be 0-based.
;;
;; If the index is less than 0 or greater than the length of the string or list
;; being indexed, the result is null.
;;
;; If either argument is null, the result is null.
(deftest compile-indexer-test
  (testing "String"
    (are [x i res] (= res (c/compile {} (elm/indexer [x i])))
      #elm/string "a" #elm/integer "0" "a"
      #elm/string "ab" #elm/integer "1" "b"

      #elm/string "" #elm/integer "-1" nil
      #elm/string "a" #elm/integer "1" nil)

    (ctu/testing-binary-null elm/indexer #elm/string "a" #elm/integer "0"))

  (testing "List"
    (are [x i res] (= res (c/compile {} (elm/indexer [x i])))
      #elm/list [#elm/integer "1"] #elm/integer "0" 1
      #elm/list [#elm/integer "1" #elm/integer "2"] #elm/integer "1" 2

      #elm/list [] #elm/integer "-1" nil
      #elm/list [#elm/integer "1"] #elm/integer "1" nil)

    (ctu/testing-binary-null elm/indexer #elm/list [] #elm/integer "0"))

  (ctu/testing-binary-op elm/indexer))

;; 17.7. LastPositionOf
;;
;; The LastPositionOf operator returns the 0-based index of the beginning of the
;; last appearance of the given pattern in the given string.
;;
;; If the pattern is not found, the result is -1.
;;
;; If either argument is null, the result is null.
(deftest compile-last-position-of-test
  (are [pattern s res] (= res (core/-eval (c/compile {} (elm/last-position-of [pattern s])) {} nil nil))
    #elm/string "a" #elm/string "a" 0
    #elm/string "a" #elm/string "aa" 1

    #elm/string "a" #elm/string "b" -1)

  (ctu/testing-binary-dynamic-null elm/last-position-of #elm/string "a" #elm/string "a")

  (ctu/testing-binary-op elm/last-position-of))

;; 17.8. Length
;;
;; The Length operator returns the length of its argument.
;;
;; For strings, the length is the number of characters in the string.
;;
;; For lists, the length is the number of elements in the list.
;;
;; If the argument is null, the result is 0.
(deftest compile-length-test
  ;; It's important to use identical? here because we like to test that length
  ;; returns a long instead of an integer.
  (testing "Static"
    (are [x res] (identical? res (c/compile {} (elm/length x)))
      #elm/string "" 0
      #elm/string "a" 1
      #elm/list [] 0
      #elm/list [#elm/integer "1"] 1

      {:type "Null"} 0))

  (testing "Dynamic"
    (are [x res] (identical? res (ctu/dynamic-compile-eval (elm/length x)))
      #elm/parameter-ref "empty-string" 0
      #elm/parameter-ref "a" 1
      #elm/parameter-ref "nil" 0))

  (testing "retrieve"
    (doseq [count [0 1 2]]
      (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
        [(into [[:put {:fhir/type :fhir/Patient :id "0"}]]
               (map (fn [id]
                      [:put {:fhir/type :fhir/Observation :id (str id)
                             :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]))
               (range count))]

        (let [context
              {:node node
               :eval-context "Patient"
               :library {}}
              expr (c/compile context #elm/length #elm/retrieve{:type "Observation"})
              db (d/db node)
              patient (d/resource-handle db "Patient" "0")]

          (is (identical? count (core/-eval expr {:db db} patient nil)))))))

  (ctu/testing-unary-op elm/length)

  (ctu/testing-optimize elm/length
    (testing "empty and nil"
      #ctu/optimize-to ""
      #ctu/optimize-to nil
      0)

    (testing "a"
      #ctu/optimize-to "a"
      1)))

;; 17.9. Lower
;;
;; The Lower operator returns the given string with all characters converted to
;; their lowercase equivalents.
;;
;; Note that the definition of lowercase for a given character is a
;; locale-dependent determination, and is not specified by CQL. Implementations
;; are expected to provide appropriate and consistent handling of locale for
;; their environment.
;;
;; If the argument is null, the result is null.
(deftest compile-lower-test
  (testing "Static"
    (are [s res] (= res (c/compile {} (elm/lower s)))
      #elm/string "" ""
      #elm/string "A" "a"))

  (testing "Dynamic"
    (are [s res] (= res (ctu/dynamic-compile-eval (elm/lower s)))
      #elm/parameter-ref "empty-string" ""
      #elm/parameter-ref "A" "a"))

  (ctu/testing-unary-null elm/lower)

  (ctu/testing-unary-op elm/lower)

  (ctu/testing-optimize elm/lower
    (testing ""
      #ctu/optimize-to ""
      "")

    (testing "A"
      #ctu/optimize-to "A"
      "a")

    (testing "nil"
      #ctu/optimize-to nil
      nil)))

;; 17.10. Matches
;;
;; The Matches operator returns true if the given string matches the given
;; regular expression pattern. Regular expressions should function consistently,
;; regardless of any culture- and locale-specific settings in the environment,
;; should be case-sensitive, use single line mode, and allow Unicode characters.
;;
;; If either argument is null, the result is null.
;;
;; Platforms will typically use native regular expression implementations. These
;; are typically fairly similar, but there will always be small differences. As
;; such, CQL does not prescribe a particular dialect, but recommends the use of
;; the PCRE dialect.
(deftest compile-matches-test
  (are [s pattern pred] (pred (c/compile {} (elm/matches [s pattern])))
    #elm/string "a" #elm/string "a" true?

    #elm/string "a" #elm/string "\\d" false?)

  (ctu/testing-binary-null elm/matches #elm/string "a")

  (ctu/testing-binary-op elm/matches))

;; 17.11. NotEqual
;;
;; See 12.7. NotEqual

;; 17.12. PositionOf
;;
;; The PositionOf operator returns the 0-based index of the beginning given
;; pattern in the given string.
;;
;; If the pattern is not found, the result is -1.
;;
;; If either argument is null, the result is null.
(deftest compile-position-of-test
  (are [pattern s res] (= res (core/-eval (c/compile {} (elm/position-of [pattern s])) {} nil nil))
    #elm/string "a" #elm/string "a" 0
    #elm/string "a" #elm/string "aa" 0

    #elm/string "a" #elm/string "b" -1)

  (ctu/testing-binary-dynamic-null elm/position-of #elm/string "a" #elm/string "a")

  (ctu/testing-binary-op elm/position-of))

;; 17.13. ReplaceMatches
;;
;; The ReplaceMatches operator matches the given string using the regular
;; expression pattern, replacing each match with the given substitution. The
;; substitution string may refer to identified match groups in the regular
;; expression. Regular expressions should function consistently, regardless of
;; any culture- and locale-specific settings in the environment, should be
;; case-sensitive, use single line mode and allow Unicode characters.
;;
;; If any argument is null, the result is null.
;;
;; Platforms will typically use native regular expression implementations. These
;; are typically fairly similar, but there will always be small differences. As
;; such, CQL does not prescribe a particular dialect, but recommends the use of
;; the PCRE dialect.
(deftest compile-replace-matches-test
  (are [s pattern substitution res] (= res (c/compile {} (elm/replace-matches [s pattern substitution])))
    #elm/string "a" #elm/string "a" #elm/string "b" "b")

  (ctu/testing-ternary-dynamic-null elm/replace-matches #elm/string "a" #elm/string "a" #elm/string "a")

  (ctu/testing-ternary-op elm/replace-matches))

;; 17.14. Split
;;
;; The Split operator splits a string into a list of strings using a separator.
;;
;; If the stringToSplit argument is null, the result is null.
;;
;; If the stringToSplit argument does not contain any appearances of the
;; separator, the result is a list of strings containing one element that is the
;; value of the stringToSplit argument.
(deftest compile-split-test
  (testing "Dynamic"
    (are [s separator res] (= res (ctu/dynamic-compile-eval (elm/split [s separator])))
      #elm/parameter-ref "empty-string" #elm/string "," [""]
      #elm/parameter-ref "a,b" #elm/string "," ["a" "b"]
      #elm/parameter-ref "a,,b" #elm/string "," ["a" "" "b"]

      #elm/parameter-ref "nil" #elm/string "," nil
      #elm/parameter-ref "a" {:type "Null"} ["a"]
      #elm/parameter-ref "nil" {:type "Null"} nil))

  (ctu/testing-binary-op elm/split))

;; 17.15. SplitOnMatches
;;
;; The SplitOnMatches operator splits a string into a list of strings using
;; matches of a regex pattern.
;;
;; The separatorPattern argument is a regex pattern, following the same
;; semantics as the Matches operator.
;;
;; If the stringToSplit argument is null, the result is null.
;;
;; If the stringToSplit argument does not contain any appearances of the
;; separator pattern, the result is a list of strings containing one element
;; that is the input value of the stringToSplit argument.

;; 17.16. StartsWith
;;
;; The StartsWith operator returns true if the given string starts with the
;; given prefix.
;;
;; If the prefix is the empty string, the result is true.
;;
;; If either argument is null, the result is null.
(deftest compile-starts-with-test
  (testing "Static"
    (are [s prefix pred] (pred (c/compile {} (elm/starts-with [s prefix])))
      #elm/string "a" #elm/string "a" true?
      #elm/string "ba" #elm/string "b" true?

      #elm/string "a" #elm/string "b" false?
      #elm/string "ab" #elm/string "b" false?))

  (testing "Dynamic"
    (are [s prefix pred] (pred (ctu/dynamic-compile-eval (elm/starts-with [s prefix])))
      #elm/parameter-ref "a" #elm/string "a" true?
      #elm/parameter-ref "ba" #elm/string "b" true?

      #elm/parameter-ref "a" #elm/string "b" false?
      #elm/parameter-ref "ab" #elm/string "b" false?))

  (ctu/testing-binary-null elm/starts-with #elm/string "a")

  (ctu/testing-binary-op elm/starts-with))

;; 17.17. Substring
;;
;; The Substring operator returns the string within stringToSub, starting at the
;; 0-based index startIndex, and consisting of length characters.
;;
;; If length is omitted, the substring returned starts at startIndex and
;; continues to the end of stringToSub.
;;
;; If stringToSub or startIndex is null, or startIndex is out of range, the
;; result is null.
;;
;; TODO: what todo if the length is out of range?
(deftest compile-substring-test
  (testing "Without length"
    (are [s start-index res] (= res (core/-eval (c/compile {} (elm/substring [s start-index])) {} nil nil))
      #elm/string "ab" #elm/integer "1" "b"

      #elm/string "a" #elm/integer "-1" nil
      #elm/string "a" #elm/integer "1" nil
      {:type "Null"} #elm/integer "0" nil
      #elm/string "a" {:type "Null"} nil
      {:type "Null"} {:type "Null"} nil)

    (testing "form and static"
      (let [expr (ctu/dynamic-compile {:type "Substring"
                                       :stringToSub #elm/parameter-ref "x"
                                       :startIndex #elm/parameter-ref "y"})]

        (has-form expr '(substring (param-ref "x") (param-ref "y")))

        (is (false? (core/-static expr))))))

  (testing "With length"
    (are [s start-index length res] (= res (core/-eval (c/compile {} (elm/substring [s start-index length])) {} nil nil))
      #elm/string "a" #elm/integer "0" #elm/integer "1" "a"
      #elm/string "a" #elm/integer "0" #elm/integer "2" "a"
      #elm/string "abc" #elm/integer "1" #elm/integer "1" "b"

      #elm/string "a" #elm/integer "-1" #elm/integer "0" nil
      #elm/string "a" #elm/integer "2" #elm/integer "0" nil
      {:type "Null"} #elm/integer "0" #elm/integer "0" nil
      #elm/string "a" {:type "Null"} #elm/integer "0" nil
      {:type "Null"} {:type "Null"} #elm/integer "0" nil)

    (testing "form and static"
      (let [expr (ctu/dynamic-compile {:type "Substring"
                                       :stringToSub #elm/parameter-ref "x"
                                       :startIndex #elm/parameter-ref "y"
                                       :length #elm/parameter-ref "z"})]

        (has-form expr '(substring (param-ref "x") (param-ref "y") (param-ref "z")))

        (is (false? (core/-static expr))))))

  (ctu/testing-binary-op elm/substring)

  (ctu/testing-ternary-op elm/substring))

;; 17.18. Upper
;;
;; The Upper operator returns the given string with all characters converted to
;; their upper case equivalents.
;;
;; Note that the definition of uppercase for a given character is a
;; locale-dependent determination, and is not specified by CQL. Implementations
;; are expected to provide appropriate and consistent handling of locale for
;; their environment.
;;
;; If the argument is null, the result is null.
(deftest compile-upper-test
  (testing "Static"
    (are [s res] (= res (c/compile {} (elm/upper s)))
      #elm/string "" ""
      #elm/string "a" "A"))

  (testing "Dynamic"
    (are [s res] (= res (ctu/dynamic-compile-eval (elm/upper s)))
      #elm/parameter-ref "empty-string" ""
      #elm/parameter-ref "a" "A"))

  (ctu/testing-unary-null elm/upper)

  (ctu/testing-unary-op elm/upper)

  (ctu/testing-optimize elm/upper
    (testing ""
      #ctu/optimize-to ""
      "")

    (testing "a"
      #ctu/optimize-to "a"
      "A")

    (testing "nil"
      #ctu/optimize-to nil
      nil)))
