(ns blaze.log
  (:require
    [clojure.string :as str]
    [taoensso.timbre :as log]))


(set! *warn-on-reflection* true)


(defn- output-fn
  ([data] (output-fn nil data))
  ([opts data]
   (let [{:keys [no-stacktrace?]} opts
         {:keys [level ?err msg_ ?ns-str ?file hostname_
                 timestamp_ ?line]} data]
     (str
       (force timestamp_) " "
       (force hostname_) " "
       (force (.getName (Thread/currentThread))) " "
       (str/upper-case (name level)) " "
       "[" (or ?ns-str ?file "?") ":" (or ?line "?") "] - "
       (force msg_)
       (when-not no-stacktrace?
         (when-let [err ?err]
           (str "\n" (log/stacktrace err opts))))))))


(log/merge-config!
    {:timestamp-opts
     {:pattern :iso8601
      :locale :jvm-default
      :timezone :utc}
     :output-fn output-fn})
