(ns blaze.page-store.hash
  "Hashes are calculated over an unambiguous encoding of their input, so that
  different clauses never produce the same hash input bytes in `hash-clause` and
  different disjunctions of clause hashes never do so in `hash-hashes`.

  That guarantee is about the inputs of those two functions. `hash-clauses`
  hands over a disjunction of one clause and that clause itself as the same
  one-element list of hashes, so both share one hash. They are equivalent.

  All tokens have to be built by `hash-clauses`, so that every page store
  calculates the same token for the same clauses."
  (:import
   [com.google.common.hash HashCode Hasher Hashing]
   [java.util HexFormat]))

(set! *warn-on-reflection* true)

(def ^:private ^HexFormat hex-format
  (.withUpperCase (HexFormat/of)))

(def ^:private ^:const string-type (byte 0))
(def ^:private ^:const keyword-type (byte 1))

(defn- put-string! [^Hasher hasher ^String s]
  (.putInt hasher (.length s))
  (.putUnencodedChars hasher s))

(defn- put-clause-element! [^Hasher hasher x]
  (.putByte hasher (if (keyword? x) keyword-type string-type))
  (put-string! hasher (str x)))

(defn hash-clause
  "Calculates a SHA256 hash of `clause`."
  [clause]
  (let [hasher (.newHasher (Hashing/sha256))]
    (.putInt ^Hasher hasher (count clause))
    (run! (partial put-clause-element! hasher) clause)
    (.hash ^Hasher hasher)))

(defn- put-disjunction! [^Hasher hasher hashes]
  (.putInt hasher (count hashes))
  (run! #(.putBytes hasher (.asBytes ^HashCode %)) hashes))

(defn hash-hashes
  "Calculates a SHA256 hash of `hashes`."
  [hashes]
  (let [hasher (.newHasher (Hashing/sha256))]
    (.putInt ^Hasher hasher (count hashes))
    (run! (partial put-disjunction! hasher) hashes)
    (.hash ^Hasher hasher)))

(defn- multiple-clauses? [disjunction]
  (sequential? (first disjunction)))

(defn- hash-disjunction [disjunction]
  (mapv (fn [clause] [(hash-clause clause) clause])
        (if (multiple-clauses? disjunction) disjunction [disjunction])))

(defn hash-clauses
  "Calculates all hashes of `clauses`.

  Returns a map of:
   * :hash    - the SHA256 hash of `clauses`, the token before encoding
   * :hashes  - a vector of disjunctions, each a vector of clause hashes
   * :clauses - a map of clause hash to clause

  A disjunction of one clause results in the same hashes as that clause itself,
  because both are equivalent."
  [clauses]
  (let [disjunctions (mapv hash-disjunction clauses)
        hashes (mapv (partial mapv first) disjunctions)]
    {:hash (hash-hashes hashes)
     :hashes hashes
     :clauses (into {} cat disjunctions)}))

(defn encode
  "Encodes `hash` via Base16, returning a token, a string of length 64."
  [hash]
  (.formatHex hex-format (.asBytes ^HashCode hash)))

(defn decode
  "Decodes `token` into the hash it was encoded from."
  [token]
  (HashCode/fromBytes (.parseHex hex-format ^String token)))
