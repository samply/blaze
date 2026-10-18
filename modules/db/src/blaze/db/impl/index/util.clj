(ns blaze.db.impl.index.util
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.db.impl.codec :as codec]))

(set! *warn-on-reflection* true)

(defn read-id! [byte-buffer]
  (bb/get-iso-8859-1-string! byte-buffer (bb/remaining byte-buffer)))

(defmacro read-t!
  "Reads a `t` value (descending-long encoded) from `byte-buffer`, optionally at
  the absolute `index`."
  ([byte-buffer]
   `(codec/descending-long (bb/get-long! ~byte-buffer)))
  ([byte-buffer index]
   `(codec/descending-long (bb/get-long! ~byte-buffer ~index))))
