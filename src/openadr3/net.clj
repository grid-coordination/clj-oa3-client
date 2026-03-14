(ns openadr3.net
  "Network utilities for OpenADR 3 clients."
  (:import [java.net DatagramSocket InetAddress Inet4Address NetworkInterface]))

(defn detect-lan-ip
  "Detect this machine's LAN IP address.

  Uses a UDP socket connect to 8.8.8.8 to ask the OS which network
  interface it would use for outbound traffic. No packets are actually
  sent (UDP connect is stateless).

  Returns the LAN IP as a string (e.g. \"192.168.1.50\").
  Falls back to \"127.0.0.1\" if detection fails."
  []
  (try
    (with-open [sock (doto (DatagramSocket.)
                       (.connect (InetAddress/getByName "8.8.8.8") 53))]
      (-> sock .getLocalAddress .getHostAddress))
    (catch Exception _
      "127.0.0.1")))

(defn local-ip-addresses
  "Return all non-loopback IPv4 addresses for this host.

  Enumerates network interfaces and filters for those that are up,
  non-loopback, and non-virtual. Returns a vector of IP address strings.

  Useful when the host is multi-homed and you need to pick a specific
  interface for webhook callback registration."
  []
  (->> (NetworkInterface/getNetworkInterfaces)
       enumeration-seq
       (filter #(and (.isUp %)
                     (not (.isLoopback %))
                     (not (.isVirtual %))))
       (mapcat #(enumeration-seq (.getInetAddresses %)))
       (filter #(instance? Inet4Address %))
       (mapv #(.getHostAddress %))))
