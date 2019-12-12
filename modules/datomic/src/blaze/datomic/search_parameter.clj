(ns blaze.datomic.search-parameter
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [datomic-tools.schema :refer [defattr defenum]]))


(defenum :search-parameter/type
  :search-parameter.type/string)


(defattr :search-parameter/code
  :db/valueType :db.type/string
  :db/cardinality :db.cardinality/one)


(defattr :search-parameter/json-key
  :db/valueType :db.type/string
  :db/cardinality :db.cardinality/one)


(s/def :blaze/search-parameter
  (s/keys
    :req
    [:search-parameter/type
     :search-parameter/code
     :search-parameter/json-key]))


(defmulti normalize :search-parameter/type)


(defmethod normalize :search-parameter.type/string
  [_ value]
  (-> value
      (str/replace #"[\p{Punct}]" " ")
      (str/replace #"\s+" " ")
      (str/lower-case)))
