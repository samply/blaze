(ns blaze.cql
  (:require
    [blaze.elm.compiler.external-data :as ed]
    [blaze.module :refer [reg-collector]]))


(reg-collector ::retrieve-total
  ed/retrieve-total)
