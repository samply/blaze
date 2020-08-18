(ns blaze.elm.compiler.query-spec
  (:require
    [blaze.elm.compiler.query :as query]
    [blaze.elm.expression :refer [expr?]]
    [clojure.spec.alpha :as s]))


(s/def ::create-with-clause
  (s/fspec
    :args (s/cat :context :blaze.elm.expression/context
                 :resource :blaze/resource
                 :scope :blaze.elm.expression/scope)))


(s/fdef query/with-xform-factory
  :args (s/cat :create-with-clause ::create-with-clause))


(s/fdef query/where-xform-factory
  :args (s/cat :expr expr?))


(s/fdef query/return-xform-factory
  :args (s/cat :expr (s/nilable expr?) :distinct boolean?))


(defn xform-factory? [x]
  (satisfies? query/XformFactory x))


(s/fdef query/xform-factory
  :args (s/cat :with-xform-factories (s/coll-of xform-factory?)
               :where-xform-factory (s/nilable xform-factory?)
               :return-xform-factory (s/nilable xform-factory?)))


(s/fdef query/eduction-expr
  :args (s/cat :xform-factory xform-factory? :source expr?))


(s/fdef query/into-vector-expr
  :args (s/cat :xform-factory xform-factory? :source expr?))


(s/fdef query/sort-expr
  :args (s/cat :source expr? :sort-by-item some?))


(s/fdef query/xform-sort-expr
  :args (s/cat :xform-factory xform-factory? :source expr? :sort-by-item some?))
