// debug: YES
// release: YES
fn call_plain(x: i8) -> i8 {
    let y: i8 = double(x);
    y + 1
}

fn double(x: i8) -> i8 {
    x + x
}
