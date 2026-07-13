// cora: YES
fn and_or(x: i32, y: i32) -> i32 {
    if x < 0 || x > 9 {
        return 0;
    }
    if x < 1 && y > 2 {
        return 1;
    }
    2
}
