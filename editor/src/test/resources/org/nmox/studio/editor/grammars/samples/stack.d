module stack;

import std.stdio : writeln;

/// A generic stack.
struct Stack(T)
{
    private T[] items;

    void push(T item) { items ~= item; }

    T pop()
    in (items.length > 0)
    {
        auto top = items[$ - 1];
        items = items[0 .. $ - 1];
        return top;
    }
}

void main()
{
    Stack!int s;
    s.push(1);
    s.push(2);
    writeln("top: ", s.pop());
}
