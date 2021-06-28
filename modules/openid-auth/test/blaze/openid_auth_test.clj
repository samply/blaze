(ns blaze.openid-auth-test
  (:require
    [blaze.openid-auth :as openid-auth]
    [buddy.auth.protocols :as p]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is]]
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:import
    [com.pgssoft.httpclient HttpClientMock]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn backend [http-client url]
  (-> {::openid-auth/backend
       {:http-client http-client
        :scheduler (ig/ref :blaze/scheduler)
        :provider-url url}
       :blaze/scheduler {}}
      ig/init))


(deftest init-test
  (let [http-client (HttpClientMock.)]
    (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
        (.doReturnStatus 404))

    (let [{::openid-auth/keys [backend] :as system}
          (backend http-client "http://localhost:8080")]
      (is (satisfies? p/IAuthentication backend))
      (Thread/sleep 2000)
      (ig/halt! system))))
