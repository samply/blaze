(ns blaze.module
  (:require
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]))

(defmacro reg-collector
  "Registers a metrics collector to the central registry."
  [key collector]
  `(do
     (defmethod ig/init-key ~key ~'[_ _] ~collector)

     (derive ~key :blaze.metrics/collector)))

(defmulti pre-init-spec
  "Return a spec for the supplied key that is used to check the associated
  value before the key is initiated.

  Backport of integrant up to v0.8 available multi-method."
  {:arglists '([key])}
  ig/normalize-key)

(defmethod pre-init-spec :default [_])

(defmethod ig/assert-key :default [k v]
  (when-let [spec (pre-init-spec k)]
    (when-not (s/valid? spec v)
      (throw (ex-info "" (s/explain-data spec v))))))
