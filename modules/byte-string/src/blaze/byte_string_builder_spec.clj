(ns blaze.byte-string-builder-spec
  (:require
   [blaze.byte-string :refer [byte-string?]]
   [blaze.byte-string-builder :as bsb]
   [clojure.spec.alpha :as s])
  (:import
   [blaze ByteString$Builder]))

(defn- builder? [x] (instance? ByteString$Builder x))

(s/fdef bsb/allocate
  :args (s/cat :capacity nat-int?)
  :ret builder?)

(s/fdef bsb/put-byte!
  :args (s/cat :builder builder? :x int?)
  :ret builder?)

(s/fdef bsb/put-short!
  :args (s/cat :builder builder? :x int?)
  :ret builder?)

(s/fdef bsb/put-int!
  :args (s/cat :builder builder? :x int?)
  :ret builder?)

(s/fdef bsb/put-long!
  :args (s/cat :builder builder? :x int?)
  :ret builder?)

(s/fdef bsb/put-byte-array!
  :args (s/cat :builder builder? :byte-array bytes?)
  :ret builder?)

(s/fdef bsb/put-byte-string!
  :args (s/cat :builder builder? :byte-string byte-string?)
  :ret builder?)

(s/fdef bsb/put-null-terminated-byte-string!
  :args (s/cat :builder builder? :byte-string byte-string?)
  :ret builder?)

(s/fdef bsb/build
  :args (s/cat :builder builder?)
  :ret byte-string?)

(s/fdef bsb/to-bytes
  :args (s/cat :builder builder?)
  :ret bytes?)
