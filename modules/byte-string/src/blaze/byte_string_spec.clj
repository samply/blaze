(ns blaze.byte-string-spec
  (:require
   [blaze.byte-buffer :refer [byte-buffer?]]
   [blaze.byte-buffer-spec]
   [blaze.byte-string :as bs]
   [clojure.spec.alpha :as s]))

(s/fdef bs/byte-string?
  :args (s/cat :x any?)
  :ret boolean?)

(s/fdef bs/from-byte-array
  :args (s/cat :bs bytes?)
  :ret bs/byte-string?)

(s/fdef bs/from-utf8-string
  :args (s/cat :s string?)
  :ret bs/byte-string?)

(s/fdef bs/from-iso-8859-1-string
  :args (s/cat :s string?)
  :ret bs/byte-string?)

(s/fdef bs/from-byte-buffer!
  :args (s/cat :byte-buffer byte-buffer? :size (s/? nat-int?))
  :ret bs/byte-string?)

(s/fdef bs/from-byte-buffer-null-terminated!
  :args (s/cat :byte-buffer byte-buffer?)
  :ret (s/nilable bs/byte-string?))

(s/fdef bs/from-hex
  :args (s/cat :s string?)
  :ret bs/byte-string?)

(s/fdef bs/nth
  :args (s/cat :bs bs/byte-string? :index nat-int?)
  :ret int?)

(s/fdef bs/size
  :args (s/cat :bs bs/byte-string?)
  :ret nat-int?)

(s/fdef bs/subs
  :args (s/cat :bs bs/byte-string? :start nat-int? :end (s/? nat-int?))
  :ret bs/byte-string?)

(s/fdef bs/concat
  :args (s/cat :a bs/byte-string? :b bs/byte-string?)
  :ret bs/byte-string?)

(s/fdef bs/<
  :args (s/cat :a bs/byte-string? :b bs/byte-string?)
  :ret boolean?)

(s/fdef bs/<=
  :args (s/cat :a bs/byte-string? :b bs/byte-string? :c (s/? bs/byte-string?))
  :ret boolean?)

(s/fdef bs/>
  :args (s/cat :a bs/byte-string? :b bs/byte-string?)
  :ret boolean?)

(s/fdef bs/hex
  :args (s/cat :bs bs/byte-string?)
  :ret string?)

(s/fdef bs/to-byte-array
  :args (s/cat :bs bs/byte-string?)
  :ret bytes?)

(s/fdef bs/to-string-utf8
  :args (s/cat :bs bs/byte-string?)
  :ret string?)

(s/fdef bs/to-string-iso-8859-1
  :args (s/cat :bs bs/byte-string?)
  :ret string?)

(s/fdef bs/as-read-only-byte-buffer
  :args (s/cat :bs bs/byte-string?)
  :ret byte-buffer?)
