(ns gabby.util)

(defn log2
  "Calculate log base 2 of x"
  [x]
  (/ (Math/log x) (Math/log 2)))

; This gets easily tripped up by abbreviations and the like but it will
; work for now!  Actually, it's quite horrible for uncurated data.
(defn sentences
  "Divides strings into sentences using common punctuation conventions."
  [str]
  (re-seq #"[^\.!\?]+[\.\!\?]+" str))

(defn trigrams
  "Turn a sentence into a sequence of trigrams.  This augments the
   sentence with two asterisks and a final tilde to indicate the
   end of a sentence."
  [sentence]
  (let [clean (-> sentence clojure.string/trim clojure.string/lower-case)
        words (re-seq #"[\w\*\~]+" (str "* * " clean " ~"))]
    (partition 3 1 words)))

(defn occurences
  "Lookup the occurrence of the sentence's trigrams in the corpus of documents"
  [sentence frequencies]
  (map #(frequencies % 0) (trigrams sentence)))

(defn probability
  "Product of sentence occurring given the corpus"
  [sentence frequencies]
  ; Is this right?  I'm not sure if it's the number of all trigrams
  ; or unique trigrams in the corpus...
  (let [possible (count frequencies)])
  (reduce * (occurences sentence frequencies)))

(defn perplexity
  [sentences frequencies]
  (let [sum-of-log-of-probability (reduce + (map #(log2 (probability % frequencies)) sentences))
        word-count () ; TBD
        entropy (/ sum-of-log-of-probability word-count)] ; this probably is a miss leading name.
    (Math/pow 2 entropy)))

(defn occuring
  [frequencies x y]
  (filter #(> x (% 1) y) frequencies))

;(def txt (slurp "corpus/fp001.txt"))
;(def tgs (mapcat trigrams (sentences txt)))
;(def fqs (frequencies tgs))