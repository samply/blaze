(ns blaze.elm.expression.cache.bloom-filter-spec
  (:require
    [blaze.db.spec]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.external-data :as ed]
    [blaze.elm.expression.cache :as ec]
    [blaze.elm.expression.cache.bloom-filter :as bloom-filter]
    [blaze.elm.expression.cache.bloom-filter.spec]
    [clojure.spec.alpha :as s]))


(s/fdef bloom-filter/might-contain?
        :args (s/cat :bloom-filter ::ec/bloom-filter :resource ed/resource?)
        :ret boolean?)


(s/fdef bloom-filter/create
        :args (s/cat :node :blaze.db/node :expression core/expr?)
        :ret ::ec/bloom-filter)


(s/fdef bloom-filter/recreate
        :args (s/cat :node :blaze.db/node :old-bloom-filter ::ec/bloom-filter
                     :expression core/expr?)
        :ret ::ec/bloom-filter)
