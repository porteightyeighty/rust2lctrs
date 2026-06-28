// cora: YES
fn shadow_scoped(n: i8) -> i8 {
    let x: i8 = n;
    if n > 0 {
        let x: i8 = x + 1;
        return x;
    }
    x
}
