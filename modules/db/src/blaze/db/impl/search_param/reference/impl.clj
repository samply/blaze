(ns blaze.db.impl.search-param.reference.impl
  (:require
   [blaze.anomaly :as ba]
   [blaze.byte-string :as bs]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.search-param.parse :as p]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]))

(defn compile-value-new [type-byte-index value]
  (let [value (p/prepare value)
        [p1 p2] (str/split value #"(?<!\\)\|" 2)]
    (if p2
      {:url (bs/from-utf8-string (p/unescape p1))
       :version (bs/from-utf8-string (p/unescape p2))}
      (let [[p1 p2] (str/split (p/unescape value) #"/" 2)]
        (if p2
          (if-let [tb (type-byte-index p1)]
            (if (s/valid? :blaze.resource/id p2)
              {:ref-id (codec/id-byte-string p2) :ref-tb tb}
              (ba/incorrect (format "Invalid resource id `%s`." p2)))
            {:url (bs/from-utf8-string (p/unescape value))})
          (if (s/valid? :blaze.resource/id p1)
            {:ref-id (codec/id-byte-string p1)}
            (ba/incorrect (format "Invalid resource id `%s`." p1))))))))
