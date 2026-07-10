(ns blaze.validator.extern.semaphore-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.validator.extern.semaphore :as sem]
   [clojure.spec.alpha :as s])
  (:import
   [clojure.lang IAtom]))

(s/fdef sem/semaphore
  :args (s/cat :permits pos-int?)
  :ret #(instance? IAtom %))

(s/fdef sem/acquire!
  :args (s/cat :semaphore #(instance? IAtom %))
  :ret ac/completable-future?)

(s/fdef sem/release!
  :args (s/cat :semaphore #(instance? IAtom %))
  :ret any?)
