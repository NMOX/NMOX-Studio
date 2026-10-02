/**
 * A small build script.
 * @author nobody
 */
class Greeter {
    String name
    def greet() { "Hello, ${name}!" }
}

def names = ['Ada', 'Grace']
names.each { n ->
    println new Greeter(name: n).greet()
}
def total = (1..5).inject(0) { acc, x -> acc + x }
assert total == 15
