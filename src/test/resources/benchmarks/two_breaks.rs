// cora: YES
fn two_breaks(mut x: i16) -> i16 {
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
