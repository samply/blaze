(ns blaze.db.impl.index.resource-as-of-test
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.codec.spec]
    [blaze.db.impl.index.resource-as-of :as rao]
    [blaze.test-util :refer [satisfies-prop]]
    [clojure.spec.alpha :as s]
    [clojure.test :refer [deftest]]
    [clojure.test.check.properties :as prop]))


(defn decode-key [buf]
  {:tid (bb/get-int! buf)
   :did (bb/get-long! buf)
   :t (codec/descending-long (bb/get-5-byte-long! buf))})


(deftest encode-key-test
  (satisfies-prop 10000
    (prop/for-all [tid (s/gen :blaze.db/tid)
                   did (s/gen :blaze.db/did)
                   t (s/gen :blaze.db/t)]
      (= {:tid tid :did did :t t}
         (decode-key (bb/wrap (rao/encode-key tid did t)))))))
