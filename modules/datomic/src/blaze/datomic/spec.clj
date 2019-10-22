(ns blaze.datomic.spec
  (:require
    [clojure.spec.alpha :as s]))


(s/def :schema/element
  some?)


(s/def :element/primitive?
  boolean?)


(s/def :element/choice-type?
  boolean?)


(s/def :element/part-of-choice-type?
  boolean?)


(s/def :element/type-attr-ident
  keyword?)


(s/def :element/type-choices
  (s/coll-of :schema/element))


(s/def :element/type
  :schema/element)


(s/def :element/type-code
  string?)


(s/def :element/json-key
  string?)


(s/def :element/value-set-binding
  string?)
