(ns blaze.terminology-service.local.code-system.sct.type)

(defn parse-sctid
  "Parses `s` as SCTID which is an integer between 6 and 18 digits long."
  [s]
  (parse-long s))
