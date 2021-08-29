(ns blaze.page-store.weigh
  (:import
    [clojure.lang Keyword PersistentVector]))


(set! *unchecked-math* :warn-on-boxed)


(defprotocol Weigher
  (-weigh [x]))


(extend-protocol Weigher
  String
  (-weigh [s]
    (let [l (.length s)]
      ;; assumes that the string is encoded in LATIN1
      (+ 40 (* (cond-> (quot l 8) (pos? (rem l 8)) inc) 8))))
  PersistentVector
  (-weigh [xs]
    (let [l (.count xs)
          vector-weight (+ 240 (* (cond-> (quot l 2) (pos? (rem l 2)) inc) 8))]
      (transduce (map -weigh) + vector-weight xs)))
  Keyword
  (-weigh [_]
    ;; a keyword is interned, so it doesn't weight anything
    0))


(defn weigh
  "Returns an estimation of the number of bytes `x` needs in memory.

  The following assumptions are made:
   * keywords don't weigh anything because the are interned
   * strings are encoded in LATIN1 and are not interned
   * the Java object layout works as described in https://github.com/openjdk/jol"
  [x]
  (-weigh x))
