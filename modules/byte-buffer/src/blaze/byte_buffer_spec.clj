(ns blaze.byte-buffer-spec
  (:require
   [blaze.byte-buffer :as bb :refer [byte-buffer?]]
   [clojure.spec.alpha :as s])
  (:import
   [com.google.protobuf ByteString]))

(s/fdef bb/allocate
  :args (s/cat :capacity nat-int?)
  :ret byte-buffer?)

(s/fdef bb/wrap
  :args (s/cat :byte-array bytes?)
  :ret byte-buffer?)

(s/fdef bb/capacity
  :args (s/cat :byte-buffer byte-buffer?)
  :ret nat-int?)

(s/fdef bb/put-byte!
  :args (s/cat :byte-buffer byte-buffer? :x int?)
  :ret byte-buffer?)

(s/fdef bb/put-short!
  :args (s/cat :byte-buffer byte-buffer? :x int?)
  :ret byte-buffer?)

(s/fdef bb/put-int!
  :args (s/cat :byte-buffer byte-buffer? :x int?)
  :ret byte-buffer?)

(s/fdef bb/put-long!
  :args (s/cat :byte-buffer byte-buffer? :x int?)
  :ret byte-buffer?)

(s/fdef bb/put-byte-array!
  :args (s/cat :byte-buffer byte-buffer? :byte-array bytes?
               :offset (s/? nat-int?) :length (s/? nat-int?))
  :ret byte-buffer?)

(s/fdef bb/put-byte-buffer!
  :args (s/cat :dst byte-buffer? :src byte-buffer?)
  :ret byte-buffer?)

(s/fdef bb/put-byte-string!
  :args (s/cat :byte-buffer byte-buffer? :byte-string #(instance? ByteString %))
  :ret byte-buffer?)

(s/fdef bb/put-null-terminated-byte-string!
  :args (s/cat :byte-buffer byte-buffer? :byte-string #(instance? ByteString %))
  :ret byte-buffer?)

(s/fdef bb/limit
  :args (s/cat :byte-buffer byte-buffer?)
  :ret nat-int?)

(s/fdef bb/position
  :args (s/cat :byte-buffer byte-buffer?)
  :ret nat-int?)

(s/fdef bb/set-position!
  :args (s/cat :byte-buffer byte-buffer? :position nat-int?)
  :ret byte-buffer?)

(s/fdef bb/size-up-to-null
  :args (s/cat :byte-buffer byte-buffer?)
  :ret (s/nilable nat-int?))
