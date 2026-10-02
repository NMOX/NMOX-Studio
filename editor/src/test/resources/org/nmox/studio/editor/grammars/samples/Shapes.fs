module Shapes

/// A shape and its area.
type Shape =
    | Circle of radius: float
    | Rect of width: float * height: float

let area shape =
    match shape with
    | Circle r -> System.Math.PI * r * r
    | Rect (w, h) -> w * h

[<EntryPoint>]
let main _ =
    [ Circle 1.0; Rect (2.0, 3.0) ]
    |> List.map area
    |> List.iter (printfn "%.2f")
    0
