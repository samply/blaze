(ns blaze.db.impl.byte-buffer
  (:import
    [com.google.protobuf ByteString]
    [java.nio ByteBuffer])
  (:refer-clojure :exclude [reset!]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn byte-buffer? [x]
  #(instance? ByteBuffer x))


(defn allocate
  {:inline (fn [capacity] `(ByteBuffer/allocate ~capacity))}
  [capacity]
  (ByteBuffer/allocate capacity))


(defn allocate-direct
  {:inline (fn [capacity] `(ByteBuffer/allocateDirect ~capacity))}
  [capacity]
  (ByteBuffer/allocateDirect capacity))


(defn wrap
  {:inline (fn [byte-array] `(ByteBuffer/wrap ~byte-array))}
  [byte-array]
  (ByteBuffer/wrap byte-array))


(defn capacity
  {:inline
   (fn [byte-buffer]
     `(.capacity ~(vary-meta byte-buffer assoc :tag `ByteBuffer)))}
  [byte-buffer]
  (.capacity ^ByteBuffer byte-buffer))


(defn put-byte!
  {:inline
   (fn [byte-buffer x]
     `(.put ~(vary-meta byte-buffer assoc :tag `ByteBuffer) (byte ~x)))}
  [byte-buffer x]
  (.put ^ByteBuffer byte-buffer (byte x)))


(defn put-short!
  {:inline
   (fn [byte-buffer x]
     `(.putShort ~(vary-meta byte-buffer assoc :tag `ByteBuffer) ~x))}
  [byte-buffer x]
  (.putShort ^ByteBuffer byte-buffer x))


(defn put-int!
  {:inline
   (fn [byte-buffer x]
     `(.putInt ~(vary-meta byte-buffer assoc :tag `ByteBuffer) ~x))}
  [byte-buffer x]
  (.putInt ^ByteBuffer byte-buffer x))


(defn put-long!
  {:inline
   (fn [byte-buffer x]
     `(.putLong ~(vary-meta byte-buffer assoc :tag `ByteBuffer) ~x))}
  [byte-buffer x]
  (.putLong ^ByteBuffer byte-buffer x))


(defn put-byte-string!
  "Copies all bytes of `byte-string` into `byte-buffer`."
  [byte-buffer byte-string]
  (.copyTo ^ByteString byte-string byte-buffer)
  byte-buffer)


(defn put-byte-buffer!
  "Copies all bytes of `src` byte buffer into `dst` byte buffer."
  [dst src]
  (.put ^ByteBuffer dst ^ByteBuffer src))


(defn limit
  {:inline
   (fn [byte-buffer]
     `(.limit ~(vary-meta byte-buffer assoc :tag `ByteBuffer)))}
  [byte-buffer]
  (.limit ^ByteBuffer byte-buffer))


(defn set-limit!
  {:inline
   (fn [byte-buffer limit]
     `(.limit ~(vary-meta byte-buffer assoc :tag `ByteBuffer) ~limit))}
  [byte-buffer limit]
  (.limit ^ByteBuffer byte-buffer ^long limit))


(defn position
  {:inline
   (fn [byte-buffer]
     `(.position ~(vary-meta byte-buffer assoc :tag `ByteBuffer)))}
  [byte-buffer]
  (.position ^ByteBuffer byte-buffer))


(defn set-position!
  {:inline
   (fn [byte-buffer position]
     `(.position ~(vary-meta byte-buffer assoc :tag `ByteBuffer) ~position))}
  [byte-buffer position]
  (.position ^ByteBuffer byte-buffer ^long position))


(defn remaining
  {:inline
   (fn [byte-buffer]
     `(.remaining ~(vary-meta byte-buffer assoc :tag `ByteBuffer)))}
  [byte-buffer]
  (.remaining ^ByteBuffer byte-buffer))


(defn flip!
  {:inline
   (fn [byte-buffer]
     `(.flip ~(vary-meta byte-buffer assoc :tag `ByteBuffer)))}
  [byte-buffer]
  (.flip ^ByteBuffer byte-buffer))


(defn rewind!
  {:inline
   (fn [byte-buffer]
     `(.rewind ~(vary-meta byte-buffer assoc :tag `ByteBuffer)))}
  [byte-buffer]
  (.rewind ^ByteBuffer byte-buffer))


(defn clear!
  {:inline
   (fn [byte-buffer]
     `(.clear ~(vary-meta byte-buffer assoc :tag `ByteBuffer)))}
  [byte-buffer]
  (.clear ^ByteBuffer byte-buffer))


(defn mark!
  {:inline
   (fn [byte-buffer]
     `(.mark ~(vary-meta byte-buffer assoc :tag `ByteBuffer)))}
  [byte-buffer]
  (.mark ^ByteBuffer byte-buffer))


(defn reset!
  {:inline
   (fn [byte-buffer]
     `(.reset ~(vary-meta byte-buffer assoc :tag `ByteBuffer)))}
  [byte-buffer]
  (.reset ^ByteBuffer byte-buffer))


(defn get-byte!
  {:inline
   (fn
     ([byte-buffer]
      `(.get ~(vary-meta byte-buffer assoc :tag `ByteBuffer)))
     ([byte-buffer index]
      `(.get ~(vary-meta byte-buffer assoc :tag `ByteBuffer) ~index)))}
  ([byte-buffer]
   (.get ^ByteBuffer byte-buffer))
  ([byte-buffer index]
   (.get ^ByteBuffer byte-buffer ^long index)))


(defn get-int!
  {:inline
   (fn [byte-buffer]
     `(.getInt ~(vary-meta byte-buffer assoc :tag `ByteBuffer)))}
  [byte-buffer]
  (.getInt ^ByteBuffer byte-buffer))


(defn get-long!
  {:inline
   (fn [byte-buffer]
     `(.getLong ~(vary-meta byte-buffer assoc :tag `ByteBuffer)))}
  [byte-buffer]
  (.getLong ^ByteBuffer byte-buffer))


(defn copy-into-byte-array!
  "Copies all bytes of `byte-buffer` into `byte-array`."
  {:inline
   (fn [byte-buffer byte-array offset length]
     `(.get ~(vary-meta byte-buffer assoc :tag `ByteBuffer) ~byte-array ~offset ~length))}
  [byte-buffer byte-array offset length]
  (.get ^ByteBuffer byte-buffer byte-array offset length))


(defn size-up-to-null [byte-buffer]
  (when (pos? (remaining byte-buffer))
    (mark! byte-buffer)
    (loop [byte (bit-and (get-byte! byte-buffer) 0xFF)
           size 0]
      (cond
        (zero? byte)
        (do (reset! byte-buffer)
            size)
        (pos? (remaining byte-buffer))
        (recur (bit-and (get-byte! byte-buffer) 0xFF) (inc size))

        :else
        (do (reset! byte-buffer)
            nil)))))


(defn mismatch
  "Finds and returns the relative index of the first mismatch between `a` and
  `b`.

  The index is relative to the position of each buffer and will be in the range
  of 0 (inclusive) up to the smaller of the remaining elements in each buffer
  (exclusive).

  If the two buffers share a common prefix then the returned index is the length
  of the common prefix and it follows that there is a mismatch between the two
  buffers at that index within the respective buffers. If one buffer is a proper
  prefix of the other then the returned index is the smaller of the remaining
  elements in each buffer, and it follows that the index is only valid for the
  buffer with the larger number of remaining elements.

  Otherwise, returns -1 if there is no mismatch."
  {:inline
   (fn [a b]
     `(.mismatch ~(vary-meta a assoc :tag `ByteBuffer) ~b))}
  [a b]
  (.mismatch ^ByteBuffer a b))


(defn array
  {:inline
   (fn [byte-buffer]
     `(.array ~(vary-meta byte-buffer assoc :tag `ByteBuffer)))}
  [byte-buffer]
  (.array ^ByteBuffer byte-buffer))
