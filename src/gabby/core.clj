(ns gabby.core
  ^{:doc "XMPP for Clojure.
          Standing on the shoulders of Smack, an Open Source XMPP (Jabber) client library."
    :author "Jon Morton"}

  (:import [org.jivesoftware.smack
            ConnectionConfiguration
            Chat ChatManager ChatManagerListener
            MessageListener
            XMPPConnection]
           [org.jivesoftware.smack.packet
            Message Packet Presence Presence$Mode Presence$Type]))

(defn authenticate
  "Returns an active connection to an XMPP server"
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

; Used by listen to build the object expected by Smack.
(defn handler [reply]
  (proxy [MessageListener]
    [] ; MessageListener constructor takes no args in this case
    (processMessage [chat, message]
      ; working with the message like a value, not a java object.
      (reply chat (bean message)))))

; Send a message to a person.  This only works with a chat
; object right now.  It would be nice if it turns a string
; into a chat object.
(defn say [chat s]
  (let [msg (Message.)]
    (.setBody msg (str s))
    (.sendMessage chat msg)))

; This adds a "listening" function to one conversation.
(defn listen
  ([chat f]
    (let [handle (handler f)]
      (.addMessageListener chat handle)
      handle)))

; Tracks a conversation participant and an object
; for interacting with them.
(def chats (atom {}))

; Useful for establishing a conversation proactively.
(defn chat [conn someone]
  "Returns Chat object"
  (let [mngr (.getChatManager conn)
        chat (.createChat mngr someone nil)
        ch   (bean chat)]
    (swap! chats assoc (:participant ch) chat)
    chat))

; Produces a proxy for reacting to chats initiated by others.
; This could be a "private" function.
(defn chat-handler [f]
  (proxy [ChatManagerListener]
      []
    (chatCreated [chat created-locally]
      (let [ch (bean chat)]
        (listen chat f)
        (swap! chats assoc (:participant ch) chat)))))

; Useful for handling conversations initiated by others.
(defn chat-started [conn f]
  (-> (.getChatManager conn) (.addChatListener (chat-handler f))))

; Change presence.
(defn available [conn msg]
  (let [status (Presence. Presence$Type/available msg 0 Presence$Mode/available)]
    (.sendPacket conn status)))

; Different change of presence.  Does not sign out.
(defn away [conn msg]
  (let [status (Presence. Presence$Type/available msg 0 Presence$Mode/away)]
    (.sendPacket conn status)))

; Leave for real.
(defn logout [conn] (.disconnect conn))
