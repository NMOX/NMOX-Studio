# A small class and a comprehension
class Kettle
  constructor: (@litres = 1.5) ->
  boil: (minutes) ->
    "#{@litres} l after #{minutes} min"

square = (x) -> x * x
squares = (square n for n in [1..5] when n % 2 is 1)
legacy = `var y = 1`
console.log new Kettle().boil(3), squares
