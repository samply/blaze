(ns blaze.db.impl.search-param.token.impl
  (:require
   [blaze.anomaly :refer [when-ok]]
   [blaze.byte-string :as bs]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.search-param.parse :as p]
   [blaze.db.impl.search-param.system-registry :as system-registry]
   [clojure.string :as str]))

(defn compile-value-new
  "Compiles `value` into a map of :value and optional :system-id.

  Splits the value on the first unescaped pipe char (`|`) into the system-id and
  the value part. Besides the case were system and value are present, there are
  three other cases were either the system or the value isn't present. In case
  no pipe char is found, the map will contain only the :value. In case the value
  starts with a pipe char, the :system-id will be the special id `000000`.In
  case the value ends with a pipe char, the map will contain only the
  :system-id.

  The pipe char can be escaped using a backslash (`\\`). In case the backslash
  itself is meant, it has to be escaped by using another backslash.

  See also: https://hl7.org/fhir/r4/search.html#escaping"
  [kv-store value]
  (let [[p1 p2] (str/split (p/prepare value) #"(?<!\\)\|" 2)]
    (cond
      (nil? p2)
      {:value (bs/from-utf8-string (p/unescape p1))}
      (= "" p2)
      (when-ok [system-id (system-registry/id-of kv-store (p/unescape p1))]
        {:system-id system-id})
      (= "" p1)
      {:system-id codec/null-system-id
       :value (bs/from-utf8-string (p/unescape p2))}
      :else
      (when-ok [system-id (system-registry/id-of kv-store (p/unescape p1))]
        {:system-id system-id
         :value (bs/from-utf8-string (p/unescape p2))}))))
