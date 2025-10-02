(ns blaze.middleware.link-headers
  "Middleware that will transfer bundle links into link headers according to
  RFC 8288."
  (:refer-clojure :exclude [str])
  (:require
   [blaze.async.comp :refer [do-sync]]
   [blaze.util :refer [str]]
   [clojure.string :as str]))

(defn- link-header-value [{{relation :value} :relation {url :value} :url}]
  (str "<" url ">;rel=\"" relation "\""))

(defn- add-link-header [response links]
  (let [value (str/join "," (map link-header-value links))]
    (cond-> response
      (< (count value) 4096)
      (assoc-in [:headers "Link"] value))))

(defn wrap-link-headers
  [handler]
  (fn [request]
    (do-sync [{{links :link} :body :as response} (handler request)]
      (cond-> response (seq links) (add-link-header links)))))
