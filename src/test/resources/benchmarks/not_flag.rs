// debug: YES
// release: YES
fn not_flag(p: bool, q: bool) -> bool {
    if !p {
        return q;
    }
    !(p && q)
}
