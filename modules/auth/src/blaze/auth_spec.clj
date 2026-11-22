(ns blaze.auth-spec
  (:require
   [blaze.auth :as auth]
   [blaze.auth.spec]
   [clojure.spec.alpha :as s]))

(s/fdef auth/authenticate
  :args (s/cat :backend :blaze.auth/backend :request map?)
  :ret (s/or :failure :blaze.auth/failure :identity :blaze.auth/identity))

(s/fdef auth/authenticated?
  :args (s/cat :request map?)
  :ret boolean?)

(s/fdef auth/www-authenticate
  :args (s/cat :failure :blaze.auth/failure)
  :ret string?)
