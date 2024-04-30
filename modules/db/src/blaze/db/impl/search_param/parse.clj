(ns blaze.db.impl.search-param.parse
  (:require
   [clojure.string :as str]))

(defn prepare
  "Prepares values by replacing escaped escape chars with the null byte.

  That replacements leads to values that doesn't contain escaped escape chars
  anymore. So only single escape chars that actually escape the separator are
  left.

  The null byte on the other hand is no valid char in the value and therefore a
  perfect candidate to be able to replaced with the escape char later on."
  [s]
  (str/replace s "\\\\" "\0"))

(defn unescape
  "Reverts escaping chars. Works on prepared values."
  [s]
  (-> s (str/replace "\\|" "|") (str/replace "\0" "\\")))
