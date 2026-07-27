// debug: MAYBE
// release: MAYBE
// The McCarthy 91 function: nested recursion whose termination argument needs a non-obvious
// ranking. Cora does not get it under either profile, so both markers pin MAYBE.
fn mccarthy(n: i32) -> i32 {
    if n <= 100 {
        return mccarthy(mccarthy(n + 11));
    }
    n - 10
}
