(defproject gabby "0.1.0-SNAPSHOT"
  :description "Gabby (loves to chat) is an XMPP library for Clojure that stands on the shoulders of Smack."
  :url "http://github.com/jmorton/gabby"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [jivesoftware/smack "3.1.0"]
                 [com.novemberain/langohr "2.0.1"]
                 [org.clojure/tools.logging "0.2.6"]]
  :plugins [[lein-marginalia "0.7.1"]])
