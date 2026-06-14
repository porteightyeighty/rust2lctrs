// cora: YES
fn nested_break(mut x: i32) -> i32 {
    let mut total: i32 = 0;
    while x > 0 {
        if total > 50 {
            break;
        }
        let mut y: i32 = x;
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
