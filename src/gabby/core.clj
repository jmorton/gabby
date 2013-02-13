;; ## Gabby (loves to chat)
;;
;; She is an XMPP library for Clojure who stands on the shoulders of Smack,
;; an Open Source XMPP (Jabber) client library.

(ns gabby.core
    { :author "Jon Morton" }
  (:import [org.jivesoftware.smack
            ConnectionConfiguration PacketListener XMPPConnection]
           [org.jivesoftware.smack.packet
            Message Message$Type Packet Presence Presence$Mode Presence$Type]
           [org.jivesoftware.smack.filter
            MessageTypeFilter PacketFilter]))

;; Although Gabby is addicted to Smack, she hides it pretty well.

;; ## Connecting to a server

(defn auth
  "Pass a map of credentials to auth to obtain a connection.  I like to use
  (read-string (slurp \"config.clj\")) to keep my configuration away from my
  committed code."
  [credentials]
  (let [user (:user credentials)
        pass (:pass credentials)
        host (:host credentials)
        port (:port credentials)
        domain (:domain credentials)
        conf (ConnectionConfiguration. host port domain)
        conn (XMPPConnection. conf)]
    (.connect conn)
    (.login conn user pass)
    conn))

;; ## ...and now... the good stuff.

;; From here on out, most functions take the connection as the first parameter
;; and return a map of whatever packet was sent.

;; ## Change Gabby's status

(defn become
  "Change your connection's status to 'available' or 'away'.  More options
  are possible, but I don't use them."
  [conn message & more]
  (io! (let [defaults {:type "available" :mode "available" :priority 0}
             opts (merge defaults (apply hash-map more))
             priority (:priority opts)
             type (Presence$Type/valueOf (name (:type opts)))
             mode (Presence$Mode/valueOf (name (:mode opts)))
             packet (Presence. type message priority mode)]
         (.sendPacket conn packet)
         (bean packet))))

;; ## Say something...

(defn say
  "Creates and sends a message."
  [conn to body]
  ;; This is not safe to use inside of transactions because it is IO.
  (io!
   (let [msg (Message. to)]
     (.setType msg Message$Type/chat)
     (.setBody msg body)
     (.sendPacket conn msg)
     ;; Return a map instead of some interop object.
     (bean msg))))

;; ## Hear something...

(defn- listener
  "Wraps `f` in a PacketListener."
  [f]
  (reify PacketListener
    (processPacket [this packet]
      (f (bean packet)))))

(defn listen
  "Call `f` whenever a chat message is received."
  [conn f]
  (let [filter (MessageTypeFilter. Message$Type/chat)]
    (.addPacketListener conn filter (listener f))))

;; ## Cherishing things once said...


(defn- starts-with? [prefix str]
  "A utility function to keep subsequent code more readable."
  (= 0 (.indexOf str prefix)))

(defn- to-or-from-filter
  "Create a filter detecting messages sent either to or from
   a specific party."
  [str]
  (reify PacketFilter
    (accept [this packet]
      (some #(starts-with? str %)
            #{(.getFrom packet) (.getTo packet)}))))

(defn record
  "Keep track of chats sent to or from a specific participant.  Here
   is how you can configure recording and obtain subsequent messages.

      (def journal (atom []))
      (record bot \"jon.morton@gmail.com\" journal)
      ;; some message is sent or received...
      (->> @journal (filter :body) (map :body))

  "
  [conn recipient journal]
  (.addPacketListener conn
    (listener
      (fn [msg]
        (swap! journal conj msg)))
    (to-or-from-filter recipient)))

;; # Farewell!

(defn deauth
  "Close the connection."
  [conn]
  (.disconnect conn))
