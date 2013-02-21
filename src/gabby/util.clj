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

(defn count-words
  [sentence]
  (count (re-seq #"[\w\*\~]+" sentence)))

(defn perplexity
  [sentences frequencies]
  (let [log-probability (reduce + (map #(log2 (probability % frequencies)) sentences))
        ; (count sentences) virtually adds the extra "stop" word to each sentence
        ; in order to follow a common peplexity model.
        vocabulary-size (+ (reduce + (map count-words sentences)) (count sentences))
        average (/ log-probability vocabulary-size)]
    (println [log-probability vocabulary-size])
    (Math/pow 2 (- average))))

(defn occuring
  [frequencies x y]
  (filter #(> x (% 1) y) frequencies))

;(def txt (slurp "corpus/fp001.txt"))
;(def tgs (mapcat trigrams (sentences txt)))
;(def fqs (frequencies tgs))