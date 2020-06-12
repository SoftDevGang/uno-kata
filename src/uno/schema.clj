(ns uno.schema
  (:require [schema.core :as s]))

(s/defschema Card
  {:card/type (s/enum 0 1 2 3 4 5 6 7 8 9 :skip :reverse :draw-two :wild :wild-draw-four)
   :card/color (s/enum :red :yellow :green :blue :wild)})

(def validate-card (s/validator Card))
