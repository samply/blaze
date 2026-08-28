(ns blaze.server.spec
  (:require
   [clojure.spec.alpha :as s])
  (:import
   [org.eclipse.jetty.util.thread ThreadPool]))

(set! *warn-on-reflection* true)

(s/def :blaze.server/name
  string?)

(s/def :blaze.server/port
  (s/and nat-int? #(<= % 65535)))

(s/def :blaze.server/handler
  fn?)

(s/def :blaze.server/version
  string?)

(s/def :blaze.server/async?
  boolean?)

(s/def :blaze.server/min-threads
  (s/and nat-int? #(<= % 100)))

;; There is no upper bound because what a thread costs to hold is a property of
;; the deployment. The lower bound keeps the pool above the number of threads
;; the connector leases for its acceptor and selectors.
(s/def :blaze.server/max-threads
  (s/and pos-int? #(<= 8 %)))

(s/def :blaze.server/thread-pool
  #(instance? ThreadPool %))
