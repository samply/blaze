(ns blaze.db.impl.codec-stub
  (:require
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.codec-spec]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]))



;; ---- Key Functions ---------------------------------------------------------

(defn t-key [t t-key]
  (st/instrument
    [`codec/t-key]
    {:spec
     {`codec/t-key
      (s/fspec
        :args (s/cat :type #{t})
        :ret #{t-key})}
     :stub
     #{`codec/t-key}}))



;; ---- Transaction -----------------------------------------------------------

(defn decode-tx [bs t tx]
  (st/instrument
    [`codec/decode-tx]
    {:spec
     {`codec/decode-tx
      (s/fspec
        :args (s/cat :bs #{bs} :t #{t})
        :ret #{tx})}
     :stub
     #{`codec/decode-tx}}))
