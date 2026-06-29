// cora: MAYBE
fn sum_skip_stuck(n: i16) -> i16 {
    let mut i: i16 = 0;
    let mut acc: i16 = 0;
    while i < n {
        if i == 5 {
            continue;
        }
        i = i + 1;
        acc = acc + i;
    }
    acc
}
