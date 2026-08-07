// debug: YES
// release: YES
fn widths(a: u16, b: u32, c: u64, d: u128, e: i64, f: i128, mut n: u8) -> u8 {
    let p: u16 = a + 1;
    let q: u32 = b + 1;
    let r: u64 = c + 1;
    let s: u128 = d + 1;
    let t: i64 = e + 1;
    let u: i128 = f + 1;
    while n > 0 {
        n = n - 1;
    }
    n
}
