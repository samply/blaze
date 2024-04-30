(ns blaze.db.impl.index.type-search-param-reference-url-resource-test-util
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.test-util :refer [search-param-code-of]]
   [blaze.fhir.hash :as hash]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn decode-key-human [kv-store buf]
  {:tb (bb/get-byte! buf)
   :search-param-code (search-param-code-of kv-store buf)
   :url (bs/to-string-utf8 (bs/from-byte-buffer-null-terminated! buf))
   :version (bs/to-string-utf8 (bs/from-byte-buffer-null-terminated! buf))
   :id (codec/id-string (bs/from-byte-buffer-null-terminated! buf))
   :hash-prefix (hash/prefix-from-byte-buffer! buf)})
