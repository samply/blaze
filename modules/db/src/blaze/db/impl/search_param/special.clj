(ns blaze.db.impl.search-param.special
  (:require
    [blaze.db.search-param-registry :as sr]
    [taoensso.timbre :as log]))


(defmulti special-search-param (fn [{:keys [code]}] code))


(defmethod sr/search-param "special" [search-param]
  (special-search-param search-param))


(defmethod special-search-param :default
  [{:keys [url code]}]
  (log/debug (format "Skip creating special search parameter `%s` with code `%s` because the rule is missing." url code)))
