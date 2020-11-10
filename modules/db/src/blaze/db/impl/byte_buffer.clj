(ns blaze.db.impl.byte-buffer
  (:import
    [com.google.protobuf ByteString]
    [java.nio ByteBuffer]))


(set! *warn-on-reflection* true)


(defn into!
  "Copies all bytes of `byte-string` into `byte-buffer`."
  [byte-buffer byte-string]
  (.copyTo ^ByteString byte-string byte-buffer))


(defn put-int! [byte-buffer x]
  (.putInt ^ByteBuffer byte-buffer ^long x))


(defn put-long! [byte-buffer x]
  (.putLong ^ByteBuffer byte-buffer ^long x))


(defn get-int! [byte-buffer]
  (.getInt ^ByteBuffer byte-buffer))


(defn array [byte-buffer]
  (.array ^ByteBuffer byte-buffer))
