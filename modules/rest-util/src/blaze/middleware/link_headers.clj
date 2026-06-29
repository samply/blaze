(ns blaze.middleware.link-headers
  "Middleware that will transfer bundle links into link headers according to
  RFC 8288."
  (:refer-clojure :exclude [str])
  (:require
   [blaze.async.comp :refer [do-sync]]
   [blaze.util :refer [str]]
   [clojure.string :as str]))

(def ^:private ^:const max-link-header-size
  "Upper bound on the size of the `Link` header value in bytes.

  Kept well below a stock reverse proxy buffer (~4k) so that the whole response
  header block (status line, all other headers and the trailing blank line)
  fits alongside the `Link` header."
  2048)

(def ^:private drop-order
  "Order in which links are dropped while the `Link` header value is too large.

  The `next` link is kept until the end so that forward navigation via the
  header survives as long as possible."
  ["previous" "first" "self"])

(defn- link-header-value [{{relation :value} :relation {url :value} :url}]
  (str "<" url ">;rel=\"" relation "\""))

(defn- assemble [links]
  (str/join "," (map link-header-value links)))

(defn- relation [{{relation :value} :relation}]
  relation)

(defn- constrain
  "Returns the `Link` header value assembled from `links`, dropping links in
  `drop-order` until the value fits into `max-link-header-size` bytes.

  Returns nil if the value is still too large after all droppable links have
  been removed."
  [links]
  (loop [links links
         [relation-to-drop & more] drop-order]
    (let [value (assemble links)]
      (if (<= (count value) max-link-header-size)
        value
        (when relation-to-drop
          (recur (remove (comp #{relation-to-drop} relation) links) more))))))

(defn- add-link-header [response links]
  (if-let [value (constrain links)]
    (assoc-in response [:headers "Link"] value)
    response))

(defn wrap-link-headers
  [handler]
  (fn [request]
    (do-sync [{{links :link} :body :as response} (handler request)]
      (cond-> response (seq links) (add-link-header links)))))
