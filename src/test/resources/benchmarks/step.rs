// cora: YES
fn step(x: i8) -> i8 {
    let mut y: i8 = 0;
    if x > 0 {
        y = 1;
    } else {
        y = x - 1;
    }
    y
}
