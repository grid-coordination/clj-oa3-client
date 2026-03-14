(ns openadr3.net
  "Network utilities for OpenADR 3 clients."
  (:import [java.net DatagramSocket InetAddress]))

(defn detect-lan-ip
  "Detect this machine's LAN IP address.

  Uses a UDP socket connect to a non-routable address to ask the OS
  which network interface it would use for outbound traffic. No packets
  are actually sent.

  Returns the LAN IP as a string (e.g. \"192.168.1.50\").
  Falls back to \"127.0.0.1\" if detection fails."
  []
  (try
    (let [sock (DatagramSocket.)]
      (.connect sock (InetAddress/getByName "10.255.255.255") 1)
      (let [ip (.getHostAddress (.getLocalAddress sock))]
        (.close sock)
        ip))
    (catch Exception _
      "127.0.0.1")))
