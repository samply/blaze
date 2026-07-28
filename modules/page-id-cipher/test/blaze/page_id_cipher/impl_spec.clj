(ns blaze.page-id-cipher.impl-spec
  (:require
   [blaze.page-id-cipher.impl :as impl]
   [blaze.page-id-cipher.impl.spec]
   [blaze.page-id-cipher.spec]
   [clojure.spec.alpha :as s]))

(s/fdef impl/gen-new-key-set-handle
  :args (s/cat)
  :ret ::impl/key-set-handle)

(s/fdef impl/size
  :args (s/cat :handle ::impl/key-set-handle)
  :ret nat-int?)

(s/fdef impl/rotate-keys
  :args (s/cat :handle ::impl/key-set-handle)
  :ret ::impl/key-set-handle)

(s/fdef impl/get-aead
  :args (s/cat :key-set-handle ::impl/key-set-handle)
  :ret :blaze/page-id-cipher)

(s/fdef impl/serialize-key-set
  :args (s/cat :handle ::impl/key-set-handle)
  :ret bytes?)

(s/fdef impl/parse-key-set
  :args (s/cat :bytes bytes?)
  :ret ::impl/key-set-handle)
