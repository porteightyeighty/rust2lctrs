// cora: NO
fn sum_skip_stuck(n: i32) -> i32 {
    let mut i: i32 = 0;
    let mut acc: i32 = 0;
    while i < n {
        if i == 5 {
            continue;
        }
        i = i + 1;
        acc = acc + i;
    }
    acc
}
