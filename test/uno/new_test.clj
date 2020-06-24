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
