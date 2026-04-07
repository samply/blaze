(ns blaze.openid-client.token-provider.protocol)

(defprotocol TokenProvider
  (-current-token [token-provider]))
