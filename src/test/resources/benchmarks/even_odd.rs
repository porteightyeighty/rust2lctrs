// debug: YES
// release: MAYBE
fn is_even(n: i8) -> bool {
    if n == 0 {
        return true;
    }
    return is_odd(n - 1);
}

fn is_odd(n: i8) -> bool {
    if n == 0 {
        return false;
    }
    return is_even(n - 1);
}
