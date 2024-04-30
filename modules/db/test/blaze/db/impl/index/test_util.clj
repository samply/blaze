(ns blaze.db.impl.index.test-util
  (:require
   [blaze.byte-string :as bs]
   [blaze.coll.core :as coll]
   [blaze.db.impl.iterators :as i]
   [blaze.db.impl.search-param.search-param-code-registry :as search-param-code-registry]
   [blaze.db.impl.search-param.system-registry :as system-registry]
   [blaze.db.kv :as kv]))

(defn- of-id [kv-store column-family id]
  (with-open [snapshot (kv/new-snapshot kv-store)]
    (coll/first
     (i/entries snapshot column-family
                (keep
                 (fn [[kb vb]]
                   (when (= id (bs/from-byte-buffer! vb))
                     (bs/to-string-utf8 (bs/from-byte-buffer! kb)))))))))

(defn search-param-code-of [kv-store buf]
  (let [id (bs/from-byte-buffer! buf 3)]
    (when-not (= search-param-code-registry/null-id id)
      (of-id kv-store :search-param-code id))))

(defn system-of [kv-store buf]
  (let [id (bs/from-byte-buffer! buf 3)]
    (when-not (= system-registry/null-id id)
      (of-id kv-store :system id))))
