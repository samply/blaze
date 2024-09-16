(ns blaze.handler.util-spec
  (:require
   [blaze.anomaly-spec]
   [blaze.handler.util :as handler-util]
   [blaze.luid.spec]
   [blaze.spec]
   [clojure.spec.alpha :as s]))

(s/fdef handler-util/preference
  :args (s/cat :headers (s/nilable map?) :name string?)
  :ret (s/nilable keyword?))

(s/fdef handler-util/error-response
  :args (s/cat :error some?)
  :ret map?)

(s/fdef handler-util/luid
  :args (s/cat :context (s/keys :req-un [:blaze/clock :blaze/rng-fn]))
  :ret :blaze/luid)
