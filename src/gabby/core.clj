;; ## Gabby (loves to chat)
;;
;; ...is an XMPP library for Clojure that stands on the shoulders of
;; [Smack](http://www.igniterealtime.org/projects/smack/).

(ns gabby.core
    { :author "Jon Morton" }
  (:require [gabby.jabber :as jabber]
        [gabby.rabbit :as rabbit])
  (:import [org.jivesoftware.smack
            ConnectionConfiguration PacketListener XMPPConnection]
           [org.jivesoftware.smack.packet
            Message Message$Type Packet Presence Presence$Mode Presence$Type]
           [org.jivesoftware.smack.filter
            MessageTypeFilter PacketFilter]))

(defn start [amqp-conf xmpp-conf]
  "Connect to the XMPP and AMQP servers, and create a session map
   to keep track of people that have talked to the system."
  (let [amqp (rabbit/connect amqp-conf)
        xmpp (jabber/auth xmpp-conf)
        sessions (atom {})]
    (jabber/listen xmpp (fn [packet]
                     (rabbit/route sessions packet :amqp amqp :xmpp xmpp)))
    [amqp xmpp sessions]))

(defn stop [amqp-conn xmpp-conn & more]
  "Close the XMPP and AMQP connections.  Channels will disconnect
   automatically."
  ;(rabbit/close amqp-conn)
  (jabber/close xmpp-conn))

(defn -main [& args]
  (println "Starting Gabby+Rabbit")
  (let [amqp-config (merge rabbit/*default-config*
                           (read-string (slurp "config/amqp.clj")))
        xmpp-config (merge jabber/*default-config*
                           (read-string (slurp "config/xmpp.clj")))]
    (start amqp-config xmpp-config))
  (loop [] (Thread/sleep 10000) (recur)))