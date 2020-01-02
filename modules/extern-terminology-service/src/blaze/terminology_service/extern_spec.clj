(ns blaze.terminology-service.extern-spec
  (:require
    [blaze.terminology-service :refer [terminology-service?]]
    [blaze.terminology-service.extern :as extern]
    [clojure.spec.alpha :as s]))


(s/def ::proxy-options
  (s/keys
    :opt-un
    [:proxy-options/host
     :proxy-options/port
     :proxy-options/user
     :proxy-options/password]))


(s/def ::milli-second
  pos-int?)


(s/fdef extern/terminology-service
  :args (s/cat :base string? :proxy-options ::proxy-options
               :connection-timeout (s/nilable ::milli-second)
               :request-timeout (s/nilable ::milli-second))
  :ret terminology-service?)
