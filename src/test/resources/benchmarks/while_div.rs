// cora: YES
fn while_div(n: i16) -> i16 {
    let mut i: i16 = 0;
    while i / 2 < n {
        i = i + 1;
    }
    i
}
