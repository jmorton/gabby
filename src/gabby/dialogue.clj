(ns gabby.dialogue)

; Example.

; How do you evaluate this command?
; How do you tie this into the thing that prompts?
; How do you pass the arguments to what executes the command?
; How do you handle exceptions?
; ...states...
; waiting <=> preparing => execute
;   ^------------------------v
; the state determines what happens next
;

(defcommand bind-to-an-exchange prompter
  :start "bind to an exchange"
  :abort "never mind"
  :prompt [{'exchange-name
            :required true
            :prompt  "name of the exchange"
            :matches #"[a-z0-9\-\_\.]+" }
           {'routing-key
            :required no
            :default "#"
            :prompty "routing key"
            :matches #"[a-z0-9\-\_\.]+" }]
  (fn [args]
    (println "Welp, that worked.")
    (println (str args))))

(defcommand trivial prompter
  :start (if-they-say "hello")
  :abort (if-they-say "bye")
  (prompt 'favorite-colour "What's your favorite colour?")
  (fn [favorite-colour]
    prompter/say (str "Oh, your favorite colour is " favorite-colour)))
