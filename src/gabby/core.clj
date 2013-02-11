(ns gabby.core
  ^{:doc "XMPP for Clojure.
          Standing on the shoulders of Smack, an Open Source XMPP (Jabber) client library."
    :author "Jon Morton"}
  (:import [org.jivesoftware.smack
            ConnectionConfiguration PacketListener XMPPConnection]
           [org.jivesoftware.smack.packet
            Message Message$Type Packet Presence Presence$Mode Presence$Type]
           [org.jivesoftware.smack.filter
            MessageTypeFilter]))

; This library strives to hide the reality of Java interop from
; people that use it (even though Smack works really well).

(defn auth
  "Return active connection to XMPP server"
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

(defn deauth
  "Close connection"
  [conn]
  (.disconnect conn))

(defn become
  "Create and send a packet to update status"
  [conn message & more]
  (io! (let [defaults {:type "available" :mode "available" :priority 0}
             opts (merge defaults (apply hash-map more))
             priority (:priority opts)
             type (Presence$Type/valueOf (name (:type opts)))
             mode (Presence$Mode/valueOf (name (:mode opts)))
             packet (Presence. type message priority mode)]
         (.sendPacket conn packet))))

(defn say
  "Creates and sends a message"
  [conn to body]
  (io!
   (let [msg (Message. to)]
     (.setType msg Message$Type/chat)
     (.setBody msg body)
     (.sendPacket conn msg))))

(defn- listener
  "Turns a function into a PacketListener.  The function is passed
   a map of the packet's values, not a Packet object."
  [f]
  (proxy [PacketListener]
      []
    (processPacket [packet]
      (f (bean packet)))))

(defn listen
  "Listen for chat messages with f"
  [connection f]
  (let [filter (MessageTypeFilter. Message$Type/chat)]
    (.addPacketListener connection (listener f) filter)))