(ns uno.game-test
  (:require [clojure.test :refer :all]
            [uno.game :as game]
            [uno.schema :as schema]))

(def red-1 {:card/type 1, :card/color :red})
(def red-2 {:card/type 2, :card/color :red})
(def blue-1 {:card/type 1, :card/color :blue})
(def blue-2 {:card/type 2, :card/color :blue})

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

(deftest all-cards-test
  (is (= 108 (count game/all-cards)))
  (doseq [card game/all-cards]
    (is (schema/validate-card card))))

(deftest start-game-test
  (testing "the game is for 2-10 players"
    (let [players [:player1 :player2 :player3 :player4 :player5
                   :player6 :player7 :player8 :player9 :player10 :player11]]
      (is (thrown-with-msg?
           IllegalArgumentException #"^expected 2-10 players, but was 1$"
           (handle-command {:command/type :game.command/start-game
                            :game/players (take 1 players)}
                           [])))
      (is (not (empty? (handle-command {:command/type :game.command/start-game
                                        :game/players (take 2 players)}
                                       []))))
      (is (not (empty? (handle-command {:command/type :game.command/start-game
                                        :game/players (take 10 players)}
                                       []))))
      (is (thrown-with-msg?
           IllegalArgumentException #"^expected 2-10 players, but was 11$"
           (handle-command {:command/type :game.command/start-game
                            :game/players (take 11 players)}
                           [])))))

  (let [game (apply-events (handle-command {:command/type :game.command/start-game
                                            :game/players [:player1 :player2 :player3]}
                                           []))
        player1-hand (get-in game [:game/players :player1 :player/hand])
        player2-hand (get-in game [:game/players :player2 :player/hand])
        player3-hand (get-in game [:game/players :player3 :player/hand])
        draw-pile (:game/draw-pile game)
        discard-pile (:game/discard-pile game)]

    (testing "every player starts with 7 cards, face down"
      (is (= 7 (count player1-hand)))
      (is (= 7 (count player2-hand)))
      (is (= 7 (count player3-hand))))

    (testing "one card is placed in the discard pile, face up"
      (is (= 1 (count discard-pile))))

    (testing "the rest of the cards are placed in a draw pile, face down"
      (is (= (frequencies game/all-cards)
             (frequencies (concat player1-hand player2-hand player3-hand discard-pile draw-pile)))))

    (testing "first player and gameplay direction"
      (is (= :player1 (:game/current-player game)))
      (is (= [:player2 :player3] (:game/next-players game))))))

(deftest play-card-test
  (let [game-started {:event/type :game.event/game-was-started
                      :game/players {:player1 {:player/hand [blue-1]}
                                     :player2 {:player/hand [blue-2]}}
                      :game/discard-pile [red-2]
                      :game/draw-pile []
                      :game/current-player :player1
                      :game/next-players [:player2]}]

    (testing "players cannot play out of turn"
      (is (thrown-with-msg?
           IllegalArgumentException #"^not current player; expected :player1, but was :player2$"
           (handle-command {:command/type :game.command/play-card
                            :command/player :player2
                            :card/type 2
                            :card/color :blue}
                           [game-started]))))

    (testing "players cannot play cards that are not in their hand"
      (is (thrown-with-msg?
           IllegalArgumentException #"^card not in hand; tried to play .*:card/type 2.*, but hand was .*:card/type 1.*$"
           (handle-command {:command/type :game.command/play-card
                            :command/player :player1
                            :card/type 2
                            :card/color :blue}
                           [game-started]))))

    (testing "players can match the card in discard pile by number"
      (is (= [{:event/type :game.event/card-was-played
               :event/player :player1
               :card/type 1
               :card/color :blue}]
             (handle-command {:command/type :game.command/play-card
                              :command/player :player1
                              :card/type 1
                              :card/color :blue}
                             [(assoc game-started :game/discard-pile [red-1])]))))

    (testing "players can match the card in discard pile by color"
      (is (= [{:event/type :game.event/card-was-played
               :event/player :player1
               :card/type 1
               :card/color :blue}]
             (handle-command {:command/type :game.command/play-card
                              :command/player :player1
                              :card/type 1
                              :card/color :blue}
                             [(assoc game-started :game/discard-pile [blue-2])]))))

    (testing "cards with different number and color will not match"
      (is (thrown-with-msg?
           IllegalArgumentException #"^card \{.*blue.*} does not match the card \{.*red.*} in discard pile$"
           (handle-command {:command/type :game.command/play-card
                            :command/player :player1
                            :card/type 1
                            :card/color :blue}
                           [(assoc game-started :game/discard-pile [red-2])])))))

  ;; TODO
  (testing "if there are no matches or player chooses not to play;"
    (testing "player must draw a card from the discard pile")
    (testing "player may play the card they just drew if it matches")))
