(ns blaze.http.util
  (:require
   [clojure.core.protocols :refer [Datafiable]]
   [clojure.datafy :refer [datafy]]
   [clojure.string :as str])
  (:import
   [org.apache.hc.core5.http HeaderElement NameValuePair]
   [org.apache.hc.core5.http.message BasicHeaderValueParser ParserCursor]))

(set! *warn-on-reflection* true)

(extend-protocol Datafiable
  HeaderElement
  (datafy [element]
    (let [params (mapv datafy (.getParameters element))]
      (cond->
       {:name (str/lower-case (.getName element))
        :value (.getValue element)}
        (seq params)
        (assoc :params params))))
  NameValuePair
  (datafy [pair]
    {:name (str/lower-case (.getName pair))
     :value (.getValue pair)}))

(defn parse-header-value
  "Parses the header value string `s` into elements which have a :name, a :value
  and possible :params that itself have :name and :value.

  The element name is converted to lower-case."
  [s]
  (when s
    (let [cursor (ParserCursor. 0 (count s))]
      (->> (.parseElements BasicHeaderValueParser/INSTANCE s cursor)
           (mapv datafy)))))

(def ^:private media-range-pattern
  "Pattern of a media range consisting of a type and a subtype token according
  to RFC 9110. Wildcards are covered because `*` is a token character."
  #"[!#$%&'*+.^_`|~0-9a-z-]+/[!#$%&'*+.^_`|~0-9a-z-]+")

(defn- media-range? [{:keys [name value]}]
  (and (nil? value) (re-matches media-range-pattern name)))

(def ^:private qvalue-pattern
  "Pattern of a q-value according to RFC 9110, which only allows values from 0
  to 1 with up to three decimal digits."
  #"0(?:\.[0-9]{0,3})?|1(?:\.0{0,3})?")

(defn- parse-q
  "Parses the q-value `s`. Returns nil if `s` is nil or doesn't follow the
  RFC 9110 grammar."
  [s]
  (when (and s (re-matches qvalue-pattern s))
    (Double/parseDouble s)))

(defn- q-value
  "Returns the first valid q-value of `params` or 1.0 if there is none."
  [params]
  (or (some (fn [{:keys [name value]}] (when (= "q" name) (parse-q value))) params)
      1.0))

(defn- weighted-name
  "Returns the name of the header `element` together with its q-value or nil if
  the q-value is 0, which means not acceptable."
  [{:keys [name params]}]
  (let [q (q-value params)]
    (when (pos? q)
      [name q])))

(defn- acceptable-names
  "Returns the names of the acceptable header `elements` sorted by descending
  q-value. Elements with the same q-value keep their order."
  [elements]
  (->> (keep weighted-name elements)
       (sort-by second >)
       (map first)))

(defn- full-wildcard? [{:keys [name]}]
  (= "*/*" name))

(defn parse-accept
  "Parses the Accept header value `s` into a seq of lower-case media types
  sorted by descending q-value.

  Media types with the same q-value keep their order, except `*/*` which comes
  after all others. Invalid media types and media types with a q-value of 0,
  which means not acceptable, are removed."
  [s]
  (when s
    (->> (parse-header-value s)
         (filter media-range?)
         (sort-by full-wildcard?)
         (acceptable-names))))

(def ^:private language-range-pattern
  "Pattern of a language range according to RFC 4647."
  #"\*|[a-z]{1,8}(?:-[a-z0-9]{1,8})*")

(defn- language-range? [{:keys [name value]}]
  (and (nil? value) (re-matches language-range-pattern name)))

(defn parse-accept-language
  "Parses the Accept-Language header value `s` into a seq of lower-case
  language ranges sorted by descending q-value.

  Language ranges with the same q-value keep their order. Invalid language
  ranges and language ranges with a q-value of 0, which means not acceptable,
  are removed."
  [s]
  (when s
    (->> (parse-header-value s)
         (filter language-range?)
         (acceptable-names))))
