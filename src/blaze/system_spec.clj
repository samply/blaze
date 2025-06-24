(ns blaze.system-spec
  (:require
   [blaze.db.api-spec]
   [blaze.system :as system]
   [blaze.util-spec]
   [clojure.spec.alpha :as s]))

(s/fdef system/init!
  :args (s/cat :env any?))
