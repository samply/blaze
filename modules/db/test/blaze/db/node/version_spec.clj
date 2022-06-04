(ns blaze.db.node.version-spec
  (:require
    [blaze.db.kv.spec]
    [blaze.db.node.version :as version]
    [clojure.spec.alpha :as s]))


(s/fdef version/encode-value
  :args (s/cat :version nat-int?)
  :ret bytes?)


(s/fdef version/get
  :args (s/cat :store :blaze.db/kv-store)
  :ret (s/nilable nat-int?))


(s/fdef version/set!
  :args (s/cat :store :blaze.db/kv-store :version nat-int?))
