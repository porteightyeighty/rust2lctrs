// debug: YES
// release: YES
fn loop_break(mut x: i32) -> i32 {
    loop {
        if x >= 10 {
            break;
        }
        x = x + 1;
    }
    x
}
