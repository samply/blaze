(ns blaze.byte-buffer
  (:refer-clojure :exclude [reset!])
  (:import
    [com.google.protobuf ByteString]
    [java.nio ByteBuffer]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn byte-buffer? [x]
  #(instance? ByteBuffer x))


(defn direct?
  {:inline
   (fn [byte-buffer]
     `(.isDirect ~(vary-meta byte-buffer assoc :tag `ByteBuffer)))}
  [byte-buffer]
  (.isDirect ^ByteBuffer byte-buffer))


(defn allocate
  "Allocates a new byte buffer.

  The new buffer's position will be zero, its limit will be `capacity`, its
  mark will be undefined, each of its elements will be initialized to zero, and
  its byte order will be BIG_ENDIAN.

  It will have a backing array, and its array offset will be zero."
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


(defn put-5-byte-long!
  [byte-buffer ^long x]
  (put-byte! byte-buffer (bit-shift-right (unchecked-long x) 32))
  (put-int! byte-buffer x))


(defn put-long!
  {:inline
   (fn [byte-buffer x]
     `(.putLong ~(vary-meta byte-buffer assoc :tag `ByteBuffer) ~x))}
  [byte-buffer x]
  (.putLong ^ByteBuffer byte-buffer x))


(defn put-byte-array!
  {:inline
   (fn
     ([byte-buffer byte-array]
      `(.put ~(vary-meta byte-buffer assoc :tag `ByteBuffer) ~byte-array))
     ([byte-buffer byte-array offset length]
      `(.put ~(vary-meta byte-buffer assoc :tag `ByteBuffer) ~byte-array
             (int ~offset) (int ~length))))}
  ([byte-buffer byte-array]
   (.put ^ByteBuffer byte-buffer ^bytes byte-array))
  ([byte-buffer byte-array offset length]
   (.put ^ByteBuffer byte-buffer ^bytes byte-array offset length)))


(defn put-byte-buffer!
  "Copies all bytes of `src` byte buffer into `dst` byte buffer."
  {:inline
   (fn [dst src]
     `(.put ~(vary-meta dst assoc :tag `ByteBuffer)
            ~(vary-meta src assoc :tag `ByteBuffer)))}
  [dst src]
  (.put ^ByteBuffer dst ^ByteBuffer src))


(defn put-byte-string!
  "Copies all bytes of `byte-string` into `byte-buffer`."
  [byte-buffer byte-string]
  (.copyTo ^ByteString byte-string byte-buffer)
  byte-buffer)


(defn limit
  "Returns the limit of `byte-buffer`."
  {:inline
   (fn [byte-buffer]
     `(.limit ~(vary-meta byte-buffer assoc :tag `ByteBuffer)))}
  [byte-buffer]
  (.limit ^ByteBuffer byte-buffer))


(defn set-limit!
  {:inline
   (fn [byte-buffer limit]
     `(.limit ~(vary-meta byte-buffer assoc :tag `ByteBuffer) (int ~limit)))}
  [byte-buffer limit]
  (.limit ^ByteBuffer byte-buffer (int limit)))


(defn position
  {:inline
   (fn [byte-buffer]
     `(.position ~(vary-meta byte-buffer assoc :tag `ByteBuffer)))}
  [byte-buffer]
  (.position ^ByteBuffer byte-buffer))


(defn set-position!
  {:inline
   (fn [byte-buffer position]
     `(.position ~(vary-meta byte-buffer assoc :tag `ByteBuffer) (int ~position)))}
  [byte-buffer position]
  (.position ^ByteBuffer byte-buffer (int position)))


(defn remaining
  "Returns the number of elements between the current position and the limit."
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
  "The 1-arity variant reads the byte at the current position of `byte-buffer`
  and increments the position afterwards. The 2-arity variant uses absolute
  `index` access."
  {:inline
   (fn
     ([byte-buffer]
      `(.get ~(vary-meta byte-buffer assoc :tag `ByteBuffer)))
     ([byte-buffer index]
      `(.get ~(vary-meta byte-buffer assoc :tag `ByteBuffer) (int ~index))))}
  ([byte-buffer]
   (.get ^ByteBuffer byte-buffer))
  ([byte-buffer index]
   (.get ^ByteBuffer byte-buffer (int index))))


(defn get-int!
  {:inline
   (fn [byte-buffer]
     `(.getInt ~(vary-meta byte-buffer assoc :tag `ByteBuffer)))}
  [byte-buffer]
  (.getInt ^ByteBuffer byte-buffer))


(defn get-5-byte-long! [byte-buffer]
  (+ (bit-shift-left (bit-and (get-byte! byte-buffer) 0xFF) 32)
     (bit-and (get-int! byte-buffer) 0xFFFFFFFF)))


(defn get-long!
  {:inline
   (fn [byte-buffer]
     `(.getLong ~(vary-meta byte-buffer assoc :tag `ByteBuffer)))}
  [byte-buffer]
  (.getLong ^ByteBuffer byte-buffer))


(defn copy-into-byte-array!
  "Copies all bytes of `byte-buffer` into `byte-array`."
  {:inline
   (fn
     ([byte-buffer byte-array]
      `(.get ~(vary-meta byte-buffer assoc :tag `ByteBuffer) ~byte-array))
     ([byte-buffer byte-array offset length]
      `(.get ~(vary-meta byte-buffer assoc :tag `ByteBuffer) ~byte-array
             (int ~offset) (int ~length))))}
  ([byte-buffer byte-array]
   (.get ^ByteBuffer byte-buffer ^bytes byte-array))
  ([byte-buffer byte-array offset length]
   (.get ^ByteBuffer byte-buffer ^bytes byte-array offset length)))


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
