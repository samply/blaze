(ns blaze.db.impl.search-param.special
  (:require
   [blaze.db.impl.search-param.core :as sc]
   [taoensso.timbre :as log]))

(defmulti special-search-param
  {:arglists '([index definition])}
  (fn [_ {:keys [name]}] name))

(defmethod sc/search-param "special"
  [{:keys [index]} search-param]
  (special-search-param index search-param))

(defmethod special-search-param :default
  [_ {:keys [url code]}]
  (log/debug (format "Skip creating special search parameter `%s` with code `%s` because the rule is missing." url code)))
