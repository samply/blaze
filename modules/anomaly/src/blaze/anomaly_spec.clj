(ns blaze.anomaly-spec
  (:require
   [blaze.anomaly :as ba]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef ba/anomaly?
  :args (s/cat :x any?)
  :ret boolean?)

(s/fdef ba/incorrect?
  :args (s/cat :x any?)
  :ret boolean?)

(s/fdef ba/unsupported?
  :args (s/cat :x any?)
  :ret boolean?)

(s/fdef ba/not-found?
  :args (s/cat :x any?)
  :ret boolean?)

(s/fdef ba/conflict?
  :args (s/cat :x any?)
  :ret boolean?)

(s/fdef ba/fault?
  :args (s/cat :x any?)
  :ret boolean?)

(s/fdef ba/busy?
  :args (s/cat :x any?)
  :ret boolean?)

(s/fdef ba/unavailable
  :args (s/cat :msg (s/? (s/nilable string?)) :kvs (s/* (s/cat :k keyword? :v any?)))
  :ret ::anom/anomaly)

(s/fdef ba/interrupted
  :args (s/cat :msg (s/? (s/nilable string?)) :kvs (s/* (s/cat :k keyword? :v any?)))
  :ret ::anom/anomaly)

(s/fdef ba/incorrect
  :args (s/cat :msg (s/? (s/nilable string?)) :kvs (s/* (s/cat :k keyword? :v any?)))
  :ret ::anom/anomaly)

(s/fdef ba/forbidden
  :args (s/cat :msg (s/? (s/nilable string?)) :kvs (s/* (s/cat :k keyword? :v any?)))
  :ret ::anom/anomaly)

(s/fdef ba/unsupported
  :args (s/cat :msg (s/? (s/nilable string?)) :kvs (s/* (s/cat :k keyword? :v any?)))
  :ret ::anom/anomaly)

(s/fdef ba/not-found
  :args (s/cat :msg (s/? (s/nilable string?)) :kvs (s/* (s/cat :k keyword? :v any?)))
  :ret ::anom/anomaly)

(s/fdef ba/conflict
  :args (s/cat :msg (s/? (s/nilable string?)) :kvs (s/* (s/cat :k keyword? :v any?)))
  :ret ::anom/anomaly)

(s/fdef ba/fault
  :args (s/cat :msg (s/? (s/nilable string?)) :kvs (s/* (s/cat :k keyword? :v any?)))
  :ret ::anom/anomaly)

(s/fdef ba/busy
  :args (s/cat :msg (s/? (s/nilable string?)) :kvs (s/* (s/cat :k keyword? :v any?)))
  :ret ::anom/anomaly)

(s/fdef ba/anomaly
  :args (s/cat :x any?)
  :ret (s/nilable ::anom/anomaly))

(s/fdef ba/try-one
  :args (s/cat :type symbol? :category keyword? :body (s/* any?)))

(s/fdef ba/try-all
  :args (s/cat :category keyword? :body (s/* any?)))

(s/fdef ba/try-anomaly
  :args (s/cat :body (s/* any?)))

(s/fdef ba/ex-anom
  :args (s/cat :anomaly ::anom/anomaly))

(s/fdef ba/throw-anom
  :args (s/cat :anomaly ::anom/anomaly))

(s/fdef ba/throw-when
  :args (s/cat :x any?))

(s/fdef ba/when-ok
  :args (s/cat :bindings :clojure/bindings :body (s/* any?)))

(s/fdef ba/if-ok
  :args (s/cat :bindings :clojure/bindings :then any? :else any?))

(s/fdef ba/map
  :args (s/cat :x any? :f ifn?))

(s/fdef ba/exceptionally
  :args (s/cat :x any? :f ifn?))

(s/fdef ba/ignore
  :args (s/cat :x any?))

(s/fdef ba/update
  :args (s/cat :m map? :k some? :f ifn?)
  :ret (s/or :result map? :anomaly ::anom/anomaly))
