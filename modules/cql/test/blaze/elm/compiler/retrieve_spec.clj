(ns blaze.elm.compiler.retrieve-spec
  (:require
    [blaze.elm.code-spec :refer [code?]]
    [blaze.elm.compiler.retrieve :as retrieve]
    [blaze.elm.expression :refer [expr?]]
    [blaze.elm.util-spec]
    [clojure.spec.alpha :as s]))


(s/fdef retrieve/with-related-context-expr
  :args (s/cat :node :blaze.db/node
               :related-context-expr expr?
               :data-type string?
               :code-property string?
               :codes (s/nilable (s/coll-of code?)))
  :ret expr?)


(s/fdef retrieve/expr
  :args (s/cat :node :blaze.db/node
               :eval-context string?
               :data-type string?
               :code-property string?
               :codes (s/nilable (s/coll-of code?)))
  :ret expr?)
