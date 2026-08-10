// debug: YES
// release: MAYBE
fn while_div(n: i16) -> i16 {
    let half: i16 = n / 2;
    let mut i: i16 = 0;
    while i < half {
        i = i + 1;
    }
    i
}
