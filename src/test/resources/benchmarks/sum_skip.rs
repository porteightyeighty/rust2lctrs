// cora: MAYBE
fn sum_skip(n: i32) -> i32 {
    let mut i: i32 = 0;
    let mut acc: i32 = 0;
    while i < n {
        i = i + 1;
        if i == 5 {
            continue;
        }
        acc = acc + i;
    }
    acc
}
