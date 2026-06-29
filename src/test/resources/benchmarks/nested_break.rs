// cora: YES
fn nested_break(mut x: i16) -> i16 {
    let mut total: i16 = 0;
    while x > 0 {
        if total > 50 {
            break;
        }
        let mut y: i16 = x;
        while y > 0 {
            if y == 3 {
                break;
            }
            total = total + 1;
            y = y - 1;
        }
        x = x - 1;
    }
    total
}
