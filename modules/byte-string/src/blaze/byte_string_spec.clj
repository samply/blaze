(ns blaze.byte-string-spec
  (:require
    [blaze.byte-string :as bs]
    [clojure.spec.alpha :as s])
  (:import
    [java.nio.charset Charset]))


(s/fdef bs/byte-string?
  :args (s/cat :x any?)
  :ret boolean?)


(s/fdef bs/from-byte-array
  :args (s/cat :bs bytes?)
  :ret bs/byte-string?)


(s/fdef bs/from-string
  :args (s/cat :s string? :charset #(instance? Charset %))
  :ret bs/byte-string?)


(s/fdef bs/<=
  :args (s/cat :a bs/byte-string? :b bs/byte-string? :c (s/? bs/byte-string?))
  :ret boolean?)
