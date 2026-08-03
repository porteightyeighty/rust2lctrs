// debug: YES
// release: MAYBE
fn wrap_negate(x: i16) -> i16 {
    let mut i: i16 = x;
    while i < 0 {
        i = -i;
    }
    i
}
