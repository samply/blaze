(ns blaze.db.impl.arrays-support
  "Reimplementation of some of `jdk.internal.util/ArraysSupport` functionality")


(set! *unchecked-math* :warn-on-boxed)


(def ^:const ^long soft-max-array-length (- Integer/MAX_VALUE 8))


(defn- huge-length ^long [^long old-length ^long min-growth]
  (let [min-length (+ old-length min-growth)]
    (when (< Integer/MAX_VALUE min-length)
      (throw (OutOfMemoryError. (format "Required array length %d + %d is too large" old-length (max min-growth min-growth)))))
    min-length))


(defn new-length
  "Computes a new array length given an array's current length, a minimum growth
  amount, and a preferred growth amount.

  This method is used by objects that contain an array that might need to be
  grown in order to fulfill some immediate need (the minimum growth amount) but
  would also like to request more space (the preferred growth amount) in order
  to accommodate potential future needs. The returned length is usually clamped
  at the soft maximum length in order to avoid hitting the JVM implementation
  limit. However, the soft maximum will be exceeded if the minimum growth amount
  requires it.

  If the preferred growth amount is less than the minimum growth amount, the
  minimum growth amount is used as the preferred growth amount.

  The preferred length is determined by adding the preferred growth amount to
  the current length. If the preferred length does not exceed the soft maximum
  length (`soft-max-array-length`) then the preferred length is returned.

  If the preferred length exceeds the soft maximum, we use the minimum growth
  amount. The minimum required length is determined by adding the minimum growth
  amount to the current length. If the minimum required length exceeds
  `Integer/MAX_VALUE`, then this method throws an `OutOfMemoryError`. Otherwise,
  this method returns the greater of the soft maximum or the minimum required
  length."
  ^long [^long old-length ^long min-growth ^long pref-growth]
  (let [pref-length (+ old-length (max min-growth pref-growth))]
    (if (<= pref-length soft-max-array-length)
      pref-length
      (huge-length old-length min-growth))))
