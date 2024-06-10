(ns blaze.fhir.spec.references)

(set! *warn-on-reflection* true)

(defn split-literal-ref
  "Tries to split `s` into a type and id.

  Returns nil if `s` doesn't contain a valid type and id separated by `/`."
  {:arglists '([s])}
  [^String s]
  (if (and (pos? (.length s)) (= \/ (.charAt s 0)))
    (recur (.substring s 1))
    (let [idx (.indexOf s 47)]
      (when (pos? idx)
        (let [type (.substring s 0 idx)]
          (when (.matches (re-matcher #"[A-Z]([A-Za-z0-9_]){0,254}" type))
            (let [id (.substring s (unchecked-inc-int idx))]
              (when (.matches (re-matcher #"[A-Za-z0-9\-\.]{1,64}" id))
                [type id]))))))))
