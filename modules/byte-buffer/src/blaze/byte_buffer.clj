(ns blaze.byte-buffer
  (:refer-clojure :exclude [reset!])
  (:import
   [blaze ByteString]
   [java.nio ByteBuffer]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn byte-buffer?
  "Checks whether `x` is an instance of ByteBuffer."
  [x]
  (instance? ByteBuffer x))

(defn allocate
  "Allocates a new byte buffer.

  The new buffer's position will be zero, its limit will be `capacity`, its
  mark will be undefined, each of its elements will be initialized to zero, and
  its byte order will be BIG_ENDIAN.

  It will have a backing array, and its array offset will be zero."
  {:inline (fn [capacity] `(ByteBuffer/allocate ~capacity))}
  [capacity]
  (ByteBuffer/allocate capacity))

(defn wrap
  "Wraps `byte-array` into a byte buffer."
  {:inline (fn [byte-array] `(ByteBuffer/wrap ~byte-array))}
  [byte-array]
  (ByteBuffer/wrap byte-array))

(defn capacity
  "Returns the capacity of `byte-buffer`."
  {:inline
   (fn [byte-buffer]
     `(.capacity ~(vary-meta byte-buffer assoc :tag `ByteBuffer)))}
  [byte-buffer]
  (.capacity ^ByteBuffer byte-buffer))

(defn put-byte!
  "Puts the byte `x` into `byte-buffer` at the current position."
  {:inline
   (fn [byte-buffer x]
     `(.put ~(vary-meta byte-buffer assoc :tag `ByteBuffer) (byte ~x)))}
  [byte-buffer x]
  (.put ^ByteBuffer byte-buffer (byte x)))

(defn put-short!
  "Puts the short `x` into `byte-buffer` at the current position."
  {:inline
   (fn [byte-buffer x]
     `(.putShort ~(vary-meta byte-buffer assoc :tag `ByteBuffer) ~x))}
  [byte-buffer x]
  (.putShort ^ByteBuffer byte-buffer x))

(defn put-int!
  "Puts the int `x` into `byte-buffer` at the current position."
  {:inline
   (fn [byte-buffer x]
     `(.putInt ~(vary-meta byte-buffer assoc :tag `ByteBuffer) ~x))}
  [byte-buffer x]
  (.putInt ^ByteBuffer byte-buffer x))

(defn put-long!
  "Puts the long `x` into `byte-buffer` at the current position."
  {:inline
   (fn [byte-buffer x]
     `(.putLong ~(vary-meta byte-buffer assoc :tag `ByteBuffer) ~x))}
  [byte-buffer x]
  (.putLong ^ByteBuffer byte-buffer x))

(defn put-byte-array!
  "Puts the `byte-array` into `byte-buffer` at the current position.

  If `offset` and `length` are provided, only the specified portion of the
  `byte-array` is put."
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

(defn put-null-terminated-byte-string!
  "Copies all bytes of `byte-string` into `byte-buffer` and adds a null byte
  after them."
  [byte-buffer byte-string]
  (.copyTo ^ByteString byte-string byte-buffer)
  (put-byte! byte-buffer 0))

(defn limit
  "Returns the limit of `byte-buffer`."
  {:inline
   (fn [byte-buffer]
     `(.limit ~(vary-meta byte-buffer assoc :tag `ByteBuffer)))}
  [byte-buffer]
  (.limit ^ByteBuffer byte-buffer))

(defn set-limit!
  "Sets the limit `byte-buffer` to `limit`."
  {:inline
   (fn [byte-buffer limit]
     `(.limit ~(vary-meta byte-buffer assoc :tag `ByteBuffer) (int ~limit)))}
  [byte-buffer limit]
  (.limit ^ByteBuffer byte-buffer (int limit)))

(defn position
  "Returns the current position of `byte-buffer`."
  {:inline
   (fn [byte-buffer]
     `(.position ~(vary-meta byte-buffer assoc :tag `ByteBuffer)))}
  [byte-buffer]
  (.position ^ByteBuffer byte-buffer))

(defn set-position!
  "Sets the position of `byte-buffer` to `position`."
  {:inline
   (fn [byte-buffer position]
     `(.position ~(vary-meta byte-buffer assoc :tag `ByteBuffer) (int ~position)))}
  [byte-buffer position]
  (.position ^ByteBuffer byte-buffer (int position)))

(defn remaining
  "Returns the number of elements between the current position and the limit of
  `byte-buffer`."
  {:inline
   (fn [byte-buffer]
     `(.remaining ~(vary-meta byte-buffer assoc :tag `ByteBuffer)))}
  [byte-buffer]
  (.remaining ^ByteBuffer byte-buffer))

(defn flip!
  "Flips `byte-buffer`.

  The limit is set to the current position and then the position is set to zero.
  If the mark is defined then it is discarded. After a sequence of channel-read
  or put operations, invoke this method to prepare for a sequence of
  channel-write or relative get operations."
  {:inline
   (fn [byte-buffer]
     `(.flip ~(vary-meta byte-buffer assoc :tag `ByteBuffer)))}
  [byte-buffer]
  (.flip ^ByteBuffer byte-buffer))

(defn rewind!
  "Rewinds `byte-buffer`.

  The position is set to zero and the mark is discarded."
  {:inline
   (fn [byte-buffer]
     `(.rewind ~(vary-meta byte-buffer assoc :tag `ByteBuffer)))}
  [byte-buffer]
  (.rewind ^ByteBuffer byte-buffer))

(defn clear!
  "Clears `byte-buffer`.

  The position is set to zero, the limit is set to the capacity, and the mark is
  discarded."
  {:inline
   (fn [byte-buffer]
     `(.clear ~(vary-meta byte-buffer assoc :tag `ByteBuffer)))}
  [byte-buffer]
  (.clear ^ByteBuffer byte-buffer))

(defn mark!
  "Sets `byte-buffer`'s mark at its position."
  {:inline
   (fn [byte-buffer]
     `(.mark ~(vary-meta byte-buffer assoc :tag `ByteBuffer)))}
  [byte-buffer]
  (.mark ^ByteBuffer byte-buffer))

(defn reset!
  "Resets the position of `byte-buffer` to the previously marked position."
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
  "Reads the next four bytes of `byte-buffer` at its current position, composing
  them into an int value according to the current byte order, and then
  increments the position of `byte-buffer` by four."
  {:inline
   (fn [byte-buffer]
     `(.getInt ~(vary-meta byte-buffer assoc :tag `ByteBuffer)))}
  [byte-buffer]
  (.getInt ^ByteBuffer byte-buffer))

(defn get-long!
  "Reads the next eight bytes of `byte-buffer` at its current position or
  `index`, composing them into a long value according to the current byte order,
  and then increments the position of `byte-buffer` by eight."
  {:inline
   (fn
     ([byte-buffer]
      `(.getLong ~(vary-meta byte-buffer assoc :tag `ByteBuffer)))
     ([byte-buffer index]
      `(.getLong ~(vary-meta byte-buffer assoc :tag `ByteBuffer) (int ~index))))}
  ([byte-buffer]
   (.getLong ^ByteBuffer byte-buffer))
  ([byte-buffer index]
   (.getLong ^ByteBuffer byte-buffer (int index))))

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

(defn size-up-to-null
  "Returns the number of bytes up to the next null byte (0x00) in `byte-buffer`
  or nil if `byte-buffer` doesn't contain a null byte."
  [byte-buffer]
  (when (pos? (remaining byte-buffer))
    (mark! byte-buffer)
    (loop [byte (bit-and (long (get-byte! byte-buffer)) 0xFF)
           size 0]
      (cond
        (zero? byte)
        (do (reset! byte-buffer)
            size)

        (pos? (remaining byte-buffer))
        (recur (bit-and (long (get-byte! byte-buffer)) 0xFF) (inc size))

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
  "Returns the byte array that backs `byte-buffer`."
  {:inline
   (fn [byte-buffer]
     `(.array ~(vary-meta byte-buffer assoc :tag `ByteBuffer)))}
  [byte-buffer]
  (.array ^ByteBuffer byte-buffer))
