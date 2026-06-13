fn two_breaks(mut x: i32) -> i32 {
    while x < 100 {
        if x == 42 {
            break;
        }
        x = x + 7;
        if x > 80 {
            break;
        }
    }
    x
}
