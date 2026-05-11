(ns blaze.fhir.hash-spec
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.hash.spec]
   [blaze.fhir.spec]
   [blaze.fhir.spec.type-spec]
   [clojure.spec.alpha :as s])
  (:import
   [blaze ByteString$Builder]))

(s/fdef hash/hash?
  :args (s/cat :x any?)
  :ret boolean?)

(s/fdef hash/from-byte-buffer!
  :args (s/cat :byte-buffer bb/byte-buffer?)
  :ret :blaze.resource/hash)

(s/fdef hash/from-hex
  :args (s/cat :s string?)
  :ret :blaze.resource/hash)

(s/fdef hash/into-byte-string-builder!
  :args (s/cat :builder #(instance? ByteString$Builder %)
               :hash :blaze.resource/hash)
  :ret #(instance? ByteString$Builder %))

(s/fdef hash/prefix-into-byte-buffer!
  :args (s/cat :byte-buffer bb/byte-buffer? :hash :blaze.resource/hash)
  :ret bb/byte-buffer?)

(s/fdef hash/prefix-into-byte-string-builder!
  :args (s/cat :builder #(instance? ByteString$Builder %)
               :hash :blaze.resource/hash)
  :ret #(instance? ByteString$Builder %))

(s/fdef hash/to-byte-array
  :args (s/cat :hash :blaze.resource/hash)
  :ret bytes?)

(s/fdef hash/prefix
  :args (s/cat :hash :blaze.resource/hash)
  :ret int?)

(s/fdef hash/prefix-from-byte-buffer!
  :args (s/cat :byte-buffer bb/byte-buffer?)
  :ret int?)

(s/fdef hash/generate
  :args (s/cat :resource :fhir/Resource)
  :ret :blaze.resource/hash)
