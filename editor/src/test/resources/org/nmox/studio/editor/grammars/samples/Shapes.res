type shape =
  | Circle(float)
  | Rect(float, float)

let area = shape =>
  switch shape {
  | Circle(r) => Js.Math._PI *. r *. r
  | Rect(w, h) => w *. h
  }

let shapes = [Circle(1.0), Rect(2.0, 3.0)]
shapes->Belt.Array.map(area)->Belt.Array.forEach(a => Js.log(a))
