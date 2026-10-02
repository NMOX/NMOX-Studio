# A typed queue
class Queue(T)
  def initialize
    @items = [] of T
  end

  def push(item : T) : self
    @items << item
    self
  end

  def pop : T?
    @items.shift?
  end
end

q = Queue(Int32).new.push(1).push(2)
puts "first: #{q.pop}"
