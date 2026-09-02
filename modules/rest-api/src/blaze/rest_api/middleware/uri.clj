(ns blaze.rest-api.middleware.uri
  (:require
   [clojure.string :as str]))

(defn- decode-dollar
  "Replaces the percent-encoded operation marker `/%24` in `uri` with `/$`."
  [uri]
  (str/replace uri "/%24" "/$"))

(defn wrap-decode-dollar
  "Middleware that rewrites a percent-encoded dollar sign at the start of a path
  segment into a literal dollar sign, so that operations can also be invoked
  with URLs like `[base]/Patient/ABC/%24everything`.

  RFC 3986 doesn't require encoding the dollar sign in a path, because it's a
  sub-delim and so allowed in a path segment. But some HTTP clients encode it
  anyway. Because the dollar sign has no delimiting role in the FHIR URL
  grammar, decoding it can't introduce any ambiguity. Especially it can't
  collide with a resource id, which is restricted to `[A-Za-z0-9\\-\\.]{1,64}`.

  Deliberately only the operation marker is decoded. Decoding the whole path
  would turn `%2F` into `/` and so change the segment structure."
  [handler]
  (fn [request respond raise]
    (handler (update request :uri decode-dollar) respond raise)))
