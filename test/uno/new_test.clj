(ns uno.new-test
  (:require [clojure.test :refer :all]
            [uno.game :as game]
            [uno.schema :as schema])
  (:import (uno GameRulesViolated)))

(def red-1 {:card/type 1, :card/color :red})
(def red-2 {:card/type 2, :card/color :red})
(def red-3 {:card/type 3, :card/color :red})
(def green-1 {:card/type 1, :card/color :green})
(def green-2 {:card/type 2, :card/color :green})
(def green-3 {:card/type 3, :card/color :green})
(def yellow-1 {:card/type 1, :card/color :yellow})
(def yellow-2 {:card/type 2, :card/color :yellow})
(def yellow-3 {:card/type 3, :card/color :yellow})
(def blue-1 {:card/type 1, :card/color :blue})
(def blue-2 {:card/type 2, :card/color :blue})
(def blue-3 {:card/type 3, :card/color :blue})
(def wild {:card/type :wild})

(defn- apply-events [events]
  (->> events
       (map schema/validate-event)
       (reduce game/projection nil)))

(defn- handle-command [command events]
  (->> (game/handle-command (schema/validate-command command)
                            (apply-events events))
       (map schema/validate-event)))

;; Rules for the Uno game
;; https://www.unorules.com/

(defn start-game [players]
  (apply-events (handle-command {:command/type :game.command/start-game
                                 :game/players players}
                                nil)))

(def minimum-players 2)
(def maximum-players 10)

(defn all-numbers-of-players []
  (for [number-of-players (range minimum-players (inc maximum-players))]
    (take number-of-players [:player1 :player2 :player3 :player4 :player5 :player6 :player7 :player8 :player9 :player10])))

(defn number-of-cards-in-hand [game player]
  (count (get-in game [:game/players player :player/hand])))

(deftest foo-test
  (testing "when the game starts, all players have 7 cards"
    (let [game (start-game [:player1 :player2 :player3])]
      (is (= 7
             (count (get-in game [:game/players :player1 :player/hand]))
             (count (get-in game [:game/players :player2 :player/hand]))
             (count (get-in game [:game/players :player3 :player/hand]))))))

  (testing "when a game of 2-10 players starts, all players have 7 cards"
    (doseq [players (all-numbers-of-players)]
      (testing (format "- for %s players" (count players))
        (let [game (start-game players)]
          (doseq [player players]
            (is (= 7 (number-of-cards-in-hand game player))
                player))))))

  (testing "the rest of the cards are placed in draw pile face down"
    (is (= 108 (count game/all-cards)))
    (let [game (start-game [:player1 :player2 :player3])]
      (is (= (- 108
                ;; player hands
                7 7 7
                ;; discard pile
                1)
             (count (:game/draw-pile game)))))))
