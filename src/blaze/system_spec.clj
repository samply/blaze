(ns blaze.system-spec
  (:require
    [blaze.db.api-spec]
    [blaze.interaction.create-spec]
    [blaze.interaction.delete-spec]
    [blaze.interaction.history.instance-spec]
    [blaze.interaction.history.system-spec]
    [blaze.interaction.history.type-spec]
    [blaze.interaction.read-spec]
    [blaze.interaction.search-compartment-spec]
    [blaze.interaction.search-system-spec]
    [blaze.interaction.search-type-spec]
    [blaze.interaction.transaction-spec]
    [blaze.interaction.update-spec]
    [blaze.server-spec]
    [blaze.system :as system]
    [clojure.spec.alpha :as s]))


(s/fdef system/init!
  :args (s/cat :env any?))
