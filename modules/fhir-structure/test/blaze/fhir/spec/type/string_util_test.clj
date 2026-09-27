(ns blaze.fhir.spec.type.string-util-test
  (:require
   [blaze.fhir.spec.type.string-util :as su]
   [blaze.fhir.spec.type.string-util-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest capital-test
  (are [s c] (= c (su/capital s))
    "" ""
    "ab" "Ab"
    "aB" "AB"
    "Ab" "Ab"
    "AB" "AB"))

(deftest visible?-test
  (testing "visible characters"
    (are [s] (su/visible? s)
      ""
      "!"
      "~"
      "http://example.com/fhir/CodeSystem/foo|1.0.0"
      (apply str (map char (range 0x21 0x7F)))
      "ü"
      "http://example.com/ü"
      "ühttp://example.com/"
      "😀"
      "http://example.com/😀"))

  (testing "invisible characters"
    (are [s] (not (su/visible? s))
      " "
      "\t"
      "\n"
      "a b"
      (str (char 0x7F))
      (str (char 0x80))
      (str (char 0xA0))
      " "
      " "
      "͸"
      "\uD800"
      "\uDE00"
      "\uDE00\uD83D"
      "fooü "
      "ü\n"))

  (testing "agrees with the visible pattern on every code point"
    (st/unstrument)
    (is (empty? (remove
                 (fn [cp]
                   (let [s (Character/toString (int cp))]
                     (= (su/visible? s) (.matches (re-matcher su/visible-pattern s)))))
                 (range (inc Character/MAX_CODE_POINT)))))))

(deftest pascal->kebab-test
  (is (= "ab-cd" (su/pascal->kebab "AbCd"))))
