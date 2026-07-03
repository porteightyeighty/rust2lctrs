// cora: YES
fn sum_skip(n: i16) -> i16 {
    let mut i: i16 = 0;
    let mut acc: i16 = 0;
    while i < n {
        i = i + 1;
        if i == 5 {
            continue;
        }
        acc = acc + i;
    }
    acc
}
