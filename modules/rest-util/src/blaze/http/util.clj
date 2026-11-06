(ns blaze.http.util
  (:require
   [blaze.anomaly :as ba :refer [if-ok]]
   [clojure.core.protocols :refer [Datafiable]]
   [clojure.datafy :refer [datafy]]
   [clojure.string :as str])
  (:import
   [java.net URLDecoder]
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

(defn- invalid-percent-encoding?
  "Returns true if `param` contains a percent sign that isn't followed by two
  hex digits."
  [param]
  (some? (re-find #"%(?![0-9A-Fa-f]{2})" param)))

(defn- invalid-percent-encoding-anom [param]
  (ba/incorrect
   (format "Invalid percent-encoding in parameter `%s`." param)
   :fhir/issue "invalid"))

(defn- decode-component
  "Decodes the properly percent-encoded `s` using `encoding`."
  [^String s ^String encoding]
  (if (or (str/includes? s "%") (str/includes? s "+"))
    (URLDecoder/decode s encoding)
    s))

(defn- decode-param
  "Decodes the name-value pair `param` into a tuple of name and value. Returns
  an anomaly if `param` isn't properly percent-encoded."
  [encoding param]
  (if (invalid-percent-encoding? param)
    (invalid-percent-encoding-anom param)
    (if-let [idx (str/index-of param \=)]
      [(decode-component (subs param 0 idx) encoding)
       (decode-component (subs param (inc idx)) encoding)]
      [(decode-component param encoding) ""])))

(defn- assoc-param
  "Associates `value` under `name` in `params`, collecting the values of a
  parameter occurring more than once into a vector."
  [params name value]
  (if-some [existing-value (params name)]
    (assoc params name (if (vector? existing-value)
                         (conj existing-value value)
                         [existing-value value]))
    (assoc params name value)))

(defn- assoc-decoded-param [encoding params param]
  (if-ok [[name value] (decode-param encoding param)]
    (assoc-param params name value)
    reduced))

(defn form-decode
  "Decodes the `application/x-www-form-urlencoded` string `s`, like a query
  string, into a map of parameter names to values using `encoding` (default
  UTF-8). The value of a parameter occurring more than once is a vector of all
  its values in order.

  Returns an anomaly if a parameter isn't properly percent-encoded, where
  `ring.util.codec/form-decode-map` would silently drop it."
  ([s]
   (form-decode s "UTF-8"))
  ([s encoding]
   (transduce
    (remove empty?)
    (completing (partial assoc-decoded-param encoding))
    {}
    (str/split s #"&"))))
