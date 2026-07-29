(ns blaze.page-id-cipher-spec
  (:require
   [blaze.db.spec]
   [blaze.page-id-cipher :as page-id-cipher]
   [blaze.scheduler.spec]
   [clojure.spec.alpha :as s])
  (:import
   [clojure.lang IAtom]))

(s/fdef page-id-cipher/->Cipher
  :args (s/cat :state #(instance? IAtom %)
               :future :blaze.scheduler/future))

(s/fdef page-id-cipher/->DocumentReferenceSubscriber
  :args (s/cat :node :blaze.db/node :state #(instance? IAtom %)
               :subscription nil?))
