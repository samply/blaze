(ns blaze.byte-buffer-spec
  (:require
   [blaze.byte-buffer :as bb :refer [byte-buffer?]]
   [clojure.spec.alpha :as s]))

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

(s/fdef bb/limit
  :args (s/cat :byte-buffer byte-buffer?)
  :ret nat-int?)

(s/fdef bb/size-up-to-null
  :args (s/cat :byte-buffer byte-buffer?)
  :ret (s/nilable nat-int?))
