(ns blaze.auth
  (:require
   [blaze.auth.protocols :as p]))

(defn authenticate
  "Returns the identity of `request` or a failure anomaly carrying
  `:blaze.auth/realm`."
  [backend request]
  (p/-authenticate backend request))

(defn authenticated?
  "Returns true if `request` has an identity."
  [request]
  (boolean (:identity request)))

(defn www-authenticate
  "Returns the value of the `WWW-Authenticate` header of a 401 response caused
  by `failure`.

  The realm is always included. The error code is included only if `failure`
  carries one, which is the case if credentials were rejected (RFC 6750 §3.1)."
  {:arglists '([failure])}
  [{:blaze.auth/keys [realm error]}]
  (cond-> (format "Bearer realm=\"%s\"" realm)
    error (str (format ", error=\"%s\"" error))))
