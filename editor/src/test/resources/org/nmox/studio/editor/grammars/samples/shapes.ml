(* A shape and its area. *)
type shape =
  | Circle of float
  | Rect of float * float

let area = function
  | Circle r -> Float.pi *. r *. r
  | Rect (w, h) -> w *. h

let () =
  [ Circle 1.0; Rect (2.0, 3.0) ]
  |> List.map area
  |> List.iter (Printf.printf "%.2f\n")
