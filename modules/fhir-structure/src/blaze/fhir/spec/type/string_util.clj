(ns blaze.fhir.spec.type.string-util
  (:refer-clojure :exclude [str])
  (:require
   [blaze.util :refer [str]]
   [clojure.string :as str])
  (:import
   [com.google.common.base CaseFormat]))

(set! *warn-on-reflection* true)

(defn capital
  "Converts the first character of `s` into upper case."
  [s]
  (if (or (empty? s) (Character/isUpperCase ^char (.charAt ^String s 0)))
    s
    (str (str/upper-case (subs s 0 1)) (subs s 1))))

(def visible-pattern
  "The regex FHIR uses for uri, url and canonical values.

  Use `visible?` to test strings against it."
  #"(?U)[\p{Print}&&[^\p{Blank}]]*")

(def ^:private ^:const invisible-types
  "Bit set of the Unicode general categories of code points that don't match
  `visible-pattern`."
  (bit-or (bit-shift-left 1 Character/SPACE_SEPARATOR)
          (bit-shift-left 1 Character/LINE_SEPARATOR)
          (bit-shift-left 1 Character/PARAGRAPH_SEPARATOR)
          (bit-shift-left 1 Character/CONTROL)
          (bit-shift-left 1 Character/SURROGATE)
          (bit-shift-left 1 Character/UNASSIGNED)))

(defn visible?
  "Returns true if `s` matches `visible-pattern`.

  Tests each code point only once. Visible ASCII characters, which nearly all
  URIs consist of, are accepted without looking up their general category."
  [^String s]
  (let [n (.length s)]
    (loop [i 0]
      (if (< i n)
        (let [cp (.codePointAt s i)]
          (if (or (and (<= 0x21 cp) (<= cp 0x7E))
                  (zero? (bit-and (bit-shift-right invisible-types (Character/getType cp)) 1)))
            (recur (+ i (Character/charCount cp)))
            false))
        true))))

(defn pascal->kebab [s]
  (.to CaseFormat/UPPER_CAMEL CaseFormat/LOWER_HYPHEN s))
