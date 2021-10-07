(ns blaze.elm.compiler.string-operators-test
  "17. String Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.test-util :as tu]
    [blaze.elm.literal-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest testing]]))


(st/instrument)
(tu/instrument-compile)


(defn- fixture [f]
  (st/instrument)
  (tu/instrument-compile)
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
    (are [src res] (= res (core/-eval (c/compile {} {:type "Combine" :source src}) {} nil nil))
      #elm/list [#elm/string "a"] "a"
      #elm/list [#elm/string "a" #elm/string "b"] "ab"

      #elm/list [] nil
      #elm/list [#elm/string "a" {:type "Null"}] nil
      #elm/list [{:type "Null"}] nil
      {:type "Null"} nil))

  (testing "With separator"
    (are [src res] (= res (core/-eval (c/compile {} {:type "Combine" :source src :separator #elm/string " "}) {} nil nil))
      #elm/list [#elm/string "a"] "a"
      #elm/list [#elm/string "a" #elm/string "b"] "a b"

      #elm/list [] nil
      #elm/list [#elm/string "a" {:type "Null"}] nil
      #elm/list [{:type "Null"}] nil
      {:type "Null"} nil)))


;; 17.2. Concatenate
;;
;; The Concatenate operator performs string concatenation of its arguments.
;;
;; If any argument is null, the result is null.
(deftest compile-concatenate-test
  (are [args res] (= res (core/-eval (c/compile {} {:type "Concatenate" :operand args}) {} nil nil))
    [#elm/string "a"] "a"
    [#elm/string "a" #elm/string "b"] "ab"

    [#elm/string "a" {:type "Null"}] nil
    [{:type "Null"}] nil))


;; 17.3. EndsWith
;;
;; The EndsWith operator returns true if the given string ends with the given
;; suffix.
;;
;; If the suffix is the empty string, the result is true.
;;
;; If either argument is null, the result is null.
(deftest compile-ends-with-test
  (are [s suffix res] (= res (core/-eval (c/compile {} {:type "EndsWith" :operand [s suffix]}) {} nil nil))
    #elm/string "a" #elm/string "a" true
    #elm/string "ab" #elm/string "b" true

    #elm/string "a" #elm/string "b" false
    #elm/string "ba" #elm/string "b" false

    {:type "Null"} #elm/string "a" nil
    #elm/string "a" {:type "Null"} nil
    {:type "Null"} {:type "Null"} nil))


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
    (are [x i res] (= res (core/-eval (c/compile {} {:type "Indexer" :operand [x i]}) {} nil nil))
      #elm/string "a" #elm/integer"0" "a"
      #elm/string "ab" #elm/integer"1" "b"

      #elm/string "" #elm/integer"-1" nil
      #elm/string "" #elm/integer"0" nil
      #elm/string "a" #elm/integer"1" nil

      #elm/string "" {:type "Null"} nil
      {:type "Null"} #elm/integer"0" nil))

  (testing "List"
    (are [x i res] (= res (core/-eval (c/compile {} {:type "Indexer" :operand [x i]}) {} nil nil))
      #elm/list [#elm/integer"1"] #elm/integer"0" 1
      #elm/list [#elm/integer"1" #elm/integer"2"] #elm/integer"1" 2

      #elm/list [] #elm/integer"-1" nil
      #elm/list [] #elm/integer"0" nil
      #elm/list [#elm/integer"1"] #elm/integer"1" nil

      #elm/list [] {:type "Null"} nil
      {:type "Null"} #elm/integer"0" nil)))


;; 17.7. LastPositionOf
;;
;; The LastPositionOf operator returns the 0-based index of the beginning of the
;; last appearance of the given pattern in the given string.
;;
;; If the pattern is not found, the result is -1.
;;
;; If either argument is null, the result is null.
(deftest compile-last-position-of-test
  (are [pattern s res] (= res (core/-eval (c/compile {} {:type "LastPositionOf" :pattern pattern :string s}) {} nil nil))
    #elm/string "a" #elm/string "a" 0
    #elm/string "a" #elm/string "aa" 1

    #elm/string "a" #elm/string "b" -1

    {:type "Null"} #elm/string "a" nil
    #elm/string "a" {:type "Null"} nil
    {:type "Null"} {:type "Null"} nil))


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
  (are [x res] (= res (core/-eval (c/compile {} {:type "Length" :operand x}) {} nil nil))
    #elm/string "" 0
    #elm/string "a" 1
    #elm/list [] 0
    #elm/list [#elm/integer"1"] 1

    {:type "Null"} 0))


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
  (are [s res] (= res (core/-eval (c/compile {} {:type "Lower" :operand s}) {} nil nil))
    #elm/string "" ""
    #elm/string "A" "a"

    {:type "Null"} nil))


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
  (are [s pattern res] (= res (core/-eval (c/compile {} {:type "Matches" :operand [s pattern]}) {} nil nil))
    #elm/string "a" #elm/string "a" true

    #elm/string "a" #elm/string "\\d" false

    {:type "Null"} #elm/string "a" nil
    #elm/string "a" {:type "Null"} nil
    {:type "Null"} {:type "Null"} nil))


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
  (are [pattern s res] (= res (core/-eval (c/compile {} {:type "PositionOf" :pattern pattern :string s}) {} nil nil))
    #elm/string "a" #elm/string "a" 0
    #elm/string "a" #elm/string "aa" 0

    #elm/string "a" #elm/string "b" -1

    {:type "Null"} #elm/string "a" nil
    #elm/string "a" {:type "Null"} nil
    {:type "Null"} {:type "Null"} nil))


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
  (are [s pattern substitution res] (= res (core/-eval (c/compile {} {:type "ReplaceMatches" :operand [s pattern substitution]}) {} nil nil))
    #elm/string "a" #elm/string "a" #elm/string "b" "b"

    {:type "Null"} #elm/string "a" {:type "Null"} nil
    #elm/string "a" {:type "Null"} {:type "Null"} nil
    {:type "Null"} {:type "Null"} {:type "Null"} nil))


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
  (testing "Without separator"
    (are [s res] (= res (core/-eval (c/compile {} {:type "Split" :stringToSplit s}) {} nil nil))
      #elm/string "" [""]
      #elm/string "a" ["a"]

      {:type "Null"} nil))

  (testing "With separator"
    (are [s separator res] (= res (core/-eval (c/compile {} {:type "Split" :stringToSplit s :separator separator}) {} nil nil))
      #elm/string "" #elm/string "," [""]
      #elm/string "a,b" #elm/string "," ["a" "b"]
      #elm/string "a,,b" #elm/string "," ["a" "" "b"]

      {:type "Null"} #elm/string "," nil
      #elm/string "a" {:type "Null"} ["a"]
      {:type "Null"} {:type "Null"} nil)))


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
  (are [s prefix res] (= res (core/-eval (c/compile {} {:type "StartsWith" :operand [s prefix]}) {} nil nil))
    #elm/string "a" #elm/string "a" true
    #elm/string "ba" #elm/string "b" true

    #elm/string "a" #elm/string "b" false
    #elm/string "ab" #elm/string "b" false

    {:type "Null"} #elm/string "a" nil
    #elm/string "a" {:type "Null"} nil
    {:type "Null"} {:type "Null"} nil))


;; 17.17. Substring
;;
;; The Substring operator returns the string within stringToSub, starting at the
;; 0-based index startIndex, and consisting of length characters.
;;
;; If length is ommitted, the substring returned starts at startIndex and
;; continues to the end of stringToSub.
;;
;; If stringToSub or startIndex is null, or startIndex is out of range, the
;; result is null.
;;
;; TODO: what todo if the length is out of range?
(deftest compile-substring-test
  (testing "Without length"
    (are [s start-index res] (= res (core/-eval (c/compile {} {:type "Substring" :stringToSub s :startIndex start-index}) {} nil nil))
      #elm/string "ab" #elm/integer"1" "b"

      #elm/string "a" #elm/integer"-1" nil
      #elm/string "a" #elm/integer"1" nil
      {:type "Null"} #elm/integer"0" nil
      #elm/string "a" {:type "Null"} nil
      {:type "Null"} {:type "Null"} nil))

  (testing "With length"
    (are [s start-index length res] (= res (core/-eval (c/compile {} {:type "Substring" :stringToSub s :startIndex start-index :length length}) {} nil nil))
      #elm/string "a" #elm/integer"0" #elm/integer"1" "a"
      #elm/string "a" #elm/integer"0" #elm/integer"2" "a"
      #elm/string "abc" #elm/integer"1" #elm/integer"1" "b"

      #elm/string "a" #elm/integer"-1" #elm/integer"0" nil
      #elm/string "a" #elm/integer"2" #elm/integer"0" nil
      {:type "Null"} #elm/integer"0" #elm/integer"0" nil
      #elm/string "a" {:type "Null"} #elm/integer"0" nil
      {:type "Null"} {:type "Null"} #elm/integer"0" nil)))


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
  (are [s res] (= res (core/-eval (c/compile {} {:type "Upper" :operand s}) {} nil nil))
    #elm/string "" ""
    #elm/string "a" "A"

    {:type "Null"} nil))
