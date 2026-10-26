(ns blaze.system-spec
  (:require
   [blaze.db.api-spec]
   [blaze.system :as system]
   [blaze.util-spec]
   [clojure.spec.alpha :as s]))

(s/fdef system/init!
  :args (s/cat :env any?))

(s/fdef system/merge-features
  :args (s/cat :blaze-edn map? :env (s/map-of string? string?)
               :bind-map (s/map-of symbol? any?))
  :ret map?)
