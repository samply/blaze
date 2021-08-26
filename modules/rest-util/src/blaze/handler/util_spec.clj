(ns blaze.handler.util-spec
  (:require
    [blaze.handler.util :as handler-util]
    [clojure.spec.alpha :as s]))


(s/fdef handler-util/preference
  :args (s/cat :headers (s/nilable map?) :name string?)
  :ret (s/nilable keyword?))
