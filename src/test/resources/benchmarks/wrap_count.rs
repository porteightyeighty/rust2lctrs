// profile: release
// cora: MAYBE
fn wrap_count(x: i16) -> i16 {
    let mut y: i16 = x;
    while y > 0 {
        y = y + 1;
    }
    y
}
