(ns blaze.interaction.transaction.bundle.url
  (:require
   [reitit.core :as reitit]))

(def ^:private router
  (reitit/router
   [["{type}" :type]
    ["{type}/{id}" :resource]]
   {:syntax :bracket}))

(defn match-url [url]
  (when-let [{{:keys [type id]} :path-params} (reitit/match-by-path router url)]
    (if id [type id] [type])))
