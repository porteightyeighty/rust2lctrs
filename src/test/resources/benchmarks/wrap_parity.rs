// debug: YES
// release: MAYBE
fn wrap_parity(x: i16) -> i16 {
    let mut i: i16 = 0;
    while i != x {
        i = i + 2;
    }
    i
}
