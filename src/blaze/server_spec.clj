(ns blaze.server-spec
  (:require
    [blaze.executors :as ex]
    [blaze.server :as server]
    [blaze.server.spec]
    [clojure.spec.alpha :as s])
  (:import
    [java.io Closeable]))


(s/fdef server/init!
  :args (s/cat :port ::server/port :executor ex/executor? :handler fn?
               :version string?))


(s/fdef server/shutdown!
  :args (s/cat :server #(instance? Closeable %)))
