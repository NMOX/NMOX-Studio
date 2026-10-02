module Color where

import Prelude
import Effect (Effect)
import Effect.Console (log)

data Color = Red | Green | Blue

derive instance eqColor :: Eq Color

name :: Color -> String
name = case _ of
  Red -> "red"
  Green -> "green"
  Blue -> "blue"

main :: Effect Unit
main = log (name Green)
