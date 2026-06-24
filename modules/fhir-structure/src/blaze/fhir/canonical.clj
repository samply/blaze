(ns blaze.fhir.canonical
  "Central registry of Blaze-minted FHIR canonical URLs.

  Blaze migrated its canonical base from the legacy `samply.github.io` host to
  the owned `blaze-server.org` domain. To stay backward compatible with
  already-stored data and existing clients, the legacy canonicals remain
  recognized on read, and both forms are emitted wherever the FHIR element
  repeats, through at least v2."
  (:require
   [blaze.fhir.spec.type :as type]
   [clojure.string :as str]))

(def base
  "Canonical base URL for all Blaze-minted FHIR canonicals."
  "https://blaze-server.org/fhir")

(def old-base
  "Legacy canonical base URL, kept for backward compatibility through v2."
  "https://samply.github.io/blaze/fhir")

(defn url
  "Builds the current canonical URL for `path` (e.g. \"CodeSystem/JobType\")."
  [path]
  (str base "/" path))

(defn old-url
  "Builds the legacy canonical URL for `path`."
  [path]
  (str old-base "/" path))

(defn urls
  "Returns both the current and legacy canonical URLs for `path`, current first."
  [path]
  [(url path) (old-url path)])

(defn legacy-url
  "Returns the legacy-base form of `url` when it is on the current `base`, else
  nil."
  [url]
  (when (str/starts-with? url base)
    (str/replace-first url base old-base)))

(defn both-urls
  "Returns both the current and legacy form of the Blaze canonical `url`, the
  current first, regardless of which form `url` is given in. A non-Blaze `url`
  is returned unchanged in a single-element vector.

  Use to normalize a canonical (e.g. a submitted `meta.profile`) to both forms."
  [url]
  (cond
    (str/starts-with? url base) [url (str/replace-first url base old-base)]
    (str/starts-with? url old-base) [(str/replace-first url old-base base) url]
    :else [url]))

(defn matches?
  "True if `s` equals the current canonical `url` or its legacy equivalent.

  `url` must be a canonical on the current `base`. Use on read paths to accept
  both the new and the legacy form of a Blaze-minted canonical (e.g. when
  dispatching on a CodeSystem system or reading an `identifier.system`)."
  [url s]
  (or (= url s)
      (and (some? s) (= s (legacy-url url)))))

(defn extensions
  "Returns two extensions for `path` sharing `value`, the current URL first.

  Use for repeating `extension` elements that should carry both the new and the
  legacy canonical for backward compatibility."
  [path value]
  [(type/extension {:url (url path) :value value})
   (type/extension {:url (old-url path) :value value})])

(defn system-codings
  "Returns codings for the current `system` URL sharing `code`, the current
  system first.

  When `system` is on the current `base`, both the current and the legacy
  system are emitted (current first) for backward compatibility; otherwise only
  `system` is emitted. Use for repeating coding arrays whose system is only
  known as a full URL (e.g. job outputs)."
  [system code]
  (let [coding #(type/coding {:system (type/uri-interned %) :code (type/code code)})]
    (if-let [old (legacy-url system)]
      [(coding system) (coding old)]
      [(coding system)])))

(defn both-codings
  "Returns `coding` in both the current and legacy system form (current first)
  when its system is a Blaze canonical, otherwise `coding` unchanged in a
  single-element vector. All other coding fields (code, display) are preserved.

  Use to expand an existing coding (e.g. one submitted by a client) to both
  forms so it satisfies both the current and the legacy profile."
  [coding]
  (let [urls (some-> (-> coding :system :value) both-urls)]
    (if (= 2 (count urls))
      (mapv #(assoc coding :system (type/uri-interned %)) urls)
      [coding])))

(defn codings
  "Returns two codings for `path` sharing `code`, the current system first.

  Use for repeating coding arrays (e.g. `code.coding`, `meta.tag`) that should
  carry both the new and the legacy CodeSystem for backward compatibility."
  [path code]
  (system-codings (url path) code))
