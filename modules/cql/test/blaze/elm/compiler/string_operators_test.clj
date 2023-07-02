(ns blaze.elm.compiler.string-operators-test
  "17. String Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.db.api :as d]
    [blaze.db.api-stub :refer [mem-node-config with-system-data]]
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.test-util :as tu]
    [blaze.elm.literal :as elm]
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
    [{:type "Null"}] nil)

  (testing "form"
    (are [args form] (= form (core/-form (c/compile {} {:type "Concatenate" :operand args})))
      [#elm/string "a"] '(concatenate "a")
      [#elm/string "a" #elm/string "b"] '(concatenate "a" "b")
      [#elm/string "a" {:type "Null"}] '(concatenate "a" nil))))


;; 17.3. EndsWith
;;
;; The EndsWith operator returns true if the given string ends with the given
;; suffix.
;;
;; If the suffix is the empty string, the result is true.
;;
;; If either argument is null, the result is null.
(deftest compile-ends-with-test
  (testing "static"
    (are [s suffix res] (= res (c/compile {} (elm/ends-with [s suffix])))
      #elm/string "a" #elm/string "a" true
      #elm/string "ab" #elm/string "b" true

      #elm/string "a" #elm/string "b" false
      #elm/string "ba" #elm/string "b" false))

  (testing "dynamic"
    (are [s suffix res] (= res (tu/dynamic-compile-eval (elm/ends-with [s suffix])))
      #elm/parameter-ref "a" #elm/string "a" true
      #elm/parameter-ref "ab" #elm/string "b" true

      #elm/parameter-ref "a" #elm/string "b" false
      #elm/parameter-ref "ba" #elm/string "b" false))

  (tu/testing-binary-null elm/ends-with #elm/string "a")

  (tu/testing-binary-form elm/ends-with))


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

    (tu/testing-binary-null elm/indexer #elm/string "a" #elm/integer "0"))

  (testing "List"
    (are [x i res] (= res (c/compile {} (elm/indexer [x i])))
      #elm/list [#elm/integer "1"] #elm/integer "0" 1
      #elm/list [#elm/integer "1" #elm/integer "2"] #elm/integer "1" 2

      #elm/list [] #elm/integer "-1" nil
      #elm/list [#elm/integer "1"] #elm/integer "1" nil)

    (tu/testing-binary-null elm/indexer #elm/list [] #elm/integer "0"))

  (tu/testing-binary-form elm/indexer))


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
  ;; It's important to use identical? here because we like to test that length
  ;; returns a long instead of an integer.
  (testing "static"
    (are [x res] (identical? res (c/compile {} (elm/length x)))
      #elm/string "" 0
      #elm/string "a" 1
      #elm/list [] 0
      #elm/list [#elm/integer "1"] 1

      {:type "Null"} 0))

  (testing "dynamic"
    (are [x res] (identical? res (tu/dynamic-compile-eval (elm/length x)))
      #elm/parameter-ref "empty-string" 0
      #elm/parameter-ref "a" 1
      #elm/parameter-ref "nil" 0))

  (testing "retrieve"
    (are [count]
      (with-system-data [{:blaze.db/keys [node]} mem-node-config]
        [(into [[:put {:fhir/type :fhir/Patient :id "0"}]]
               (map (fn [id]
                      [:put {:fhir/type :fhir/Observation :id (str id)
                             :subject #fhir/Reference{:reference "Patient/0"}}]))
               (range count))]

        (let [context
              {:node node
               :eval-context "Patient"
               :library {}}
              expr (c/compile context #elm/length #elm/retrieve{:type "Observation"})
              db (d/db node)
              patient (d/resource-handle db "Patient" "0")]

          (identical? count (core/-eval expr {:db db} patient nil))))
      0 1 2))

  (tu/testing-unary-form elm/length))


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
  (testing "static"
    (are [s res] (= res (c/compile {} (elm/lower s)))
      #elm/string "" ""
      #elm/string "A" "a"))

  (testing "dynamic"
    (are [s res] (= res (tu/dynamic-compile-eval (elm/lower s)))
      #elm/parameter-ref "empty-string" ""
      #elm/parameter-ref "A" "a"))

  (tu/testing-unary-null elm/lower)

  (tu/testing-unary-form elm/lower))


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
  (are [s pattern res] (= res (c/compile {} (elm/matches [s pattern])))
    #elm/string "a" #elm/string "a" true

    #elm/string "a" #elm/string "\\d" false)

  (tu/testing-binary-null elm/matches #elm/string "a")

  (tu/testing-binary-form elm/matches))


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
  (testing "static"
    (are [s prefix res] (= res (c/compile {} (elm/starts-with [s prefix])))
      #elm/string "a" #elm/string "a" true
      #elm/string "ba" #elm/string "b" true

      #elm/string "a" #elm/string "b" false
      #elm/string "ab" #elm/string "b" false))

  (testing "dynamic"
    (are [s prefix res] (= res (tu/dynamic-compile-eval (elm/starts-with [s prefix])))
      #elm/parameter-ref "a" #elm/string "a" true
      #elm/parameter-ref "ba" #elm/string "b" true

      #elm/parameter-ref "a" #elm/string "b" false
      #elm/parameter-ref "ab" #elm/string "b" false))

  (tu/testing-binary-null elm/starts-with #elm/string "a")

  (tu/testing-binary-form elm/starts-with))


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
      #elm/string "ab" #elm/integer "1" "b"

      #elm/string "a" #elm/integer "-1" nil
      #elm/string "a" #elm/integer "1" nil
      {:type "Null"} #elm/integer "0" nil
      #elm/string "a" {:type "Null"} nil
      {:type "Null"} {:type "Null"} nil))

  (testing "With length"
    (are [s start-index length res] (= res (core/-eval (c/compile {} {:type "Substring" :stringToSub s :startIndex start-index :length length}) {} nil nil))
      #elm/string "a" #elm/integer "0" #elm/integer "1" "a"
      #elm/string "a" #elm/integer "0" #elm/integer "2" "a"
      #elm/string "abc" #elm/integer "1" #elm/integer "1" "b"

      #elm/string "a" #elm/integer "-1" #elm/integer "0" nil
      #elm/string "a" #elm/integer "2" #elm/integer "0" nil
      {:type "Null"} #elm/integer "0" #elm/integer "0" nil
      #elm/string "a" {:type "Null"} #elm/integer "0" nil
      {:type "Null"} {:type "Null"} #elm/integer "0" nil)))


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
  (testing "static"
    (are [s res] (= res (c/compile {} (elm/upper s)))
      #elm/string "" ""
      #elm/string "a" "A"))

  (testing "dynamic"
    (are [s res] (= res (tu/dynamic-compile-eval (elm/upper s)))
      #elm/parameter-ref "empty-string" ""
      #elm/parameter-ref "a" "A"))

  (tu/testing-unary-null elm/upper)

  (tu/testing-unary-form elm/upper))
