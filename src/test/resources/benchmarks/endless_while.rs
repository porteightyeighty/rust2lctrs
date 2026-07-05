// cora: MAYBE
// The `while true` twin of endless_loop: encodes identically to `loop {}` (divergence, no
// fall-through). A unit return, because `while` has type () in rustc, so it cannot be the tail of
// an i16 function the way `loop {}`'s never-type can.
fn endless_while() {
    while true {}
}
