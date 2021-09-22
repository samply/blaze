(ns blaze.db.impl.search-param.special
  (:require
    [blaze.db.search-param-registry :as sr]
    [taoensso.timbre :as log]))


(defmulti special-search-param
  {:arglists '([context definition])}
  (fn [_ {:keys [name]}] name))


(defmethod sr/search-param "special"
  [context search-param]
  (special-search-param context search-param))


(defmethod special-search-param :default
  [_ {:keys [url code]}]
  (log/debug (format "Skip creating special search parameter `%s` with code `%s` because the rule is missing." url code)))
