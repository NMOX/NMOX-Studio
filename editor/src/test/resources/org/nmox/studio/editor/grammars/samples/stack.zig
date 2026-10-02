const std = @import("std");

pub fn Stack(comptime T: type) type {
    return struct {
        items: std.ArrayList(T),

        const Self = @This();

        pub fn push(self: *Self, item: T) !void {
            try self.items.append(item);
        }

        pub fn pop(self: *Self) ?T {
            return self.items.popOrNull();
        }
    };
}

test "push then pop" {
    var s = Stack(u8){ .items = std.ArrayList(u8).init(std.testing.allocator) };
    defer s.items.deinit();
    try s.push(7);
    try std.testing.expectEqual(@as(?u8, 7), s.pop());
}
