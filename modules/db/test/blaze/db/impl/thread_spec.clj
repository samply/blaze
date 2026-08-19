(ns blaze.db.impl.thread-spec
  (:require
   [blaze.db.impl.thread :as thread]
   [clojure.spec.alpha :as s]))

(s/fdef thread/start-thread!
  :args (s/cat :f fn? :name string?))
