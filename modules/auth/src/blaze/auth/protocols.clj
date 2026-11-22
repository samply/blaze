(ns blaze.auth.protocols)

(defprotocol AuthenticationBackend
  (-authenticate [backend request]
    "Returns the identity of `request` or an anomaly carrying
    `:blaze.auth/realm`, if `request` has no credentials or they are rejected.

    The anomaly has the category `incorrect` without credentials and the
    category `forbidden` together with `:blaze.auth/error` if the credentials
    are rejected."))
