(ns blaze.terminology-service.local.search-index
  "Full-text search index for code system concepts using Apache Lucene.

  Builds an in-memory index from concept display names and designations,
  enabling word-prefix and fuzzy matching for the ValueSet/$expand filter
  parameter."
  (:import
   [java.util Collection]
   [org.apache.lucene.analysis.standard StandardAnalyzer]
   [org.apache.lucene.document Document Field$Store StringField TextField]
   [org.apache.lucene.index DirectoryReader IndexWriter IndexWriterConfig
    StoredFields Term]
   [org.apache.lucene.search BooleanClause$Occur BooleanQuery$Builder
    FuzzyQuery IndexSearcher PrefixQuery Query ScoreDoc TermInSetQuery
    TopDocs]
   [org.apache.lucene.store ByteBuffersDirectory Directory]
   [org.apache.lucene.util BytesRef]))

(set! *warn-on-reflection* true)

(defn- add-doc
  ([^IndexWriter writer code text]
   (let [doc (Document.)]
     (.add doc (StringField. "code" ^String code Field$Store/YES))
     (.add doc (TextField. "text" ^String text Field$Store/NO))
     (.addDocument writer doc)))
  ([^IndexWriter writer code text module-id]
   (let [doc (Document.)]
     (.add doc (StringField. "code" ^String code Field$Store/YES))
     (.add doc (TextField. "text" ^String text Field$Store/NO))
     (.add doc (StringField. "module-id" ^String module-id Field$Store/NO))
     (.addDocument writer doc))))

(defn- append-designation! [^StringBuilder sb {:keys [value]}]
  (when-let [v (:value value)]
    (when (pos? (.length sb))
      (.append sb " "))
    (.append sb ^String v)))

(defn- concept-texts
  "Extracts searchable text from a concept. Returns a single concatenated string
  of the display and all designation values."
  [{:keys [display designation]}]
  (let [sb (StringBuilder.)]
    (when-let [d (:value display)]
      (.append sb ^String d))
    (run! #(append-designation! sb %) designation)
    (.toString sb)))

(defn build
  "Builds an in-memory Lucene search index from a map of code to concept.

  Each concept should have at least a :display field with a :value. Designations
  are also indexed if present."
  [concepts]
  (let [dir (ByteBuffersDirectory.)
        config (IndexWriterConfig. (StandardAnalyzer.))]
    (with-open [writer (IndexWriter. dir config)]
      (run!
       (fn [[code concept]]
         (let [text (concept-texts concept)]
           (when-not (empty? text)
             (add-doc writer code text))))
       concepts))
    dir))

(defn build-with-modules
  "Builds an in-memory Lucene search index from a sequence of
  `[code text module-id]` tuples. Each document stores the module-id for
  query-time filtering."
  [entries]
  (let [dir (ByteBuffersDirectory.)
        config (IndexWriterConfig. (StandardAnalyzer.))]
    (with-open [writer (IndexWriter. dir config)]
      (run!
       (fn [[code text module-id]]
         (when-not (empty? text)
           (add-doc writer code text module-id)))
       entries))
    dir))

(defn- analyze-terms
  "Splits and lowercases the filter text into terms."
  [^String text]
  (when-not (empty? text)
    (.split (.toLowerCase text) "\\s+")))

(defn- text-query
  "Builds a query that matches `term` via prefix or fuzzy (edit distance up to 2).
  Combines both so that incomplete words (prefix) and typos (fuzzy) are handled."
  [term]
  (let [t (Term. "text" ^String term)
        builder (BooleanQuery$Builder.)]
    (.add builder (PrefixQuery. t) BooleanClause$Occur/SHOULD)
    (.add builder (FuzzyQuery. t) BooleanClause$Occur/SHOULD)
    (.build builder)))

(defn- set-query
  "Builds a query that restricts results to the given set of values in `field`."
  [field values]
  (TermInSetQuery. ^String field ^Collection (mapv #(BytesRef. ^String %) values)))

(defn- build-query
  "Builds a Lucene query from filter text, optionally restricted to a set of
  codes and/or module-ids."
  [^String filter-text codes module-ids]
  (let [terms (analyze-terms filter-text)]
    (when (seq terms)
      (let [builder (BooleanQuery$Builder.)]
        (run!
         #(.add builder (text-query %) BooleanClause$Occur/MUST)
         terms)
        (when (seq codes)
          (.add builder (set-query "code" codes) BooleanClause$Occur/FILTER))
        (when (seq module-ids)
          (.add builder (set-query "module-id" module-ids) BooleanClause$Occur/FILTER))
        (.build builder)))))

(defn search
  "Searches the index with the given filter text. Returns an ordered vector of
  matching codes, ranked by relevance. Returns at most `max-results` matches.

  When `codes` is provided, only concepts with those codes are considered.
  When `module-ids` is provided, only documents with those module-ids are
  considered."
  ([^Directory dir ^String filter-text max-results]
   (search dir filter-text max-results nil nil))
  ([^Directory dir ^String filter-text max-results codes]
   (search dir filter-text max-results codes nil))
  ([^Directory dir ^String filter-text max-results codes module-ids]
   (when-let [^Query query (build-query filter-text codes module-ids)]
     (with-open [reader (DirectoryReader/open dir)]
       (let [searcher (IndexSearcher. reader)
             ^TopDocs top-docs (.search searcher query (int max-results))
             stored-fields (.storedFields searcher)]
         (mapv
          (fn [^ScoreDoc sd]
            (.get (.document ^StoredFields stored-fields (.-doc sd)) "code"))
          (.-scoreDocs top-docs)))))))
