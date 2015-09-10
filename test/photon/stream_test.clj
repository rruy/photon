(ns photon.stream-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [go-loop go <! >! chan buffer <!! close!]]
            [ring.mock.request :as mock]
            [clojure.tools.logging :as log]
            [muon-clojure.client :as cl]
            [photon.filedb :as filedb]
            [photon.streams :as streams]
            [photon.muon :as m])
  (:use midje.sweet))

(def amazon-url "amqp://localhost")
(def db (filedb/->DBFile "/tmp/temporalphoton.log"))

(defn prepare! []
  (let [ms (m/start-server! "photon-test" db)]
    (streams/clean! (:stm ms))
    ms))

(defn test-cold []
  (let [b (cl/muon-client amazon-url "monitor-client" "monitor" "client")
        c (cl/with-muon b (cl/stream-subscription "muon://photon-test/stream"
                                                  :stream-name "dummy"
                                                  :stream-type :cold
                                                  :from 0))]
    (loop [ev (<!! c) n 0]
      (if (nil? ev)
        (do
          (log/info "Total:" n "events.")
          n)
        (do
          (log/debug "Event received")
          (recur (<!! c) (inc n)))))))

(defn test-hot-cold []
  (let [b (cl/muon-client amazon-url "1monitor-client" "2monitor" "3lient")
        c (cl/with-muon b (cl/stream-subscription "muon://photon-test/stream"
                                                  :stream-name "dummy"
                                                  :stream-type :hot-cold
                                                  :from 0))]
    (go
      (loop [ev (<! c) n 0]
        (if (nil? ev)
          (do
            (log/info "Total:" n "events.")
            n)
          (do
            (log/info "Event received")
            (recur (<! c) (inc n))))))
    (Thread/sleep 10000)
    (close! c)))

(let [ms (prepare!)
      n1 (test-cold)
      n2 (test-cold)]
  (fact "Consistent behaviour in cold streaming" n1 => n2))

(let [ms (prepare!)
      a (cl/muon-client amazon-url "asap-client" "asap" "client")]
  (cl/with-muon a (cl/post-event "muon://photon-test/events"
                                 {:test :ok
                                  :stream-name "dummy"}))
  (cl/with-muon a (cl/post-event "muon://photon-test/events"
                                 {:test :ok
                                  :stream-name "dummy"}))
  (cl/with-muon a (cl/post-event "muon://photon-test/events"
                                 {:test :ok
                                  :stream-name "dummy"}))
  (cl/with-muon a (cl/post-event "muon://photon-test/events"
                                 {:test :ok
                                  :stream-name "dummy"}))
  (fact "Correct count" (test-cold) => 4))

(let [n1 (test-cold)
      n2 (test-cold)]
  (fact "Consistent behaviour in cold streaming after posts" n1 => n2))

