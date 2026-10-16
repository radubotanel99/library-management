# CheckoutBar
A bar fixed to the bottom of every screen for the action the desk repeats all day: lend or return a copy.

- Left: the last transaction (cover, title, who/when, badge). Centre: Book no. and Member fields around a round 52px `brand` Lend button. Right: switch to Return mode (the centre button becomes a return arrow and the Member field hides).
- Background `surface`, top hairline `line`, `shadow-bar`.

**Consumer provides:** the last transaction, the mode, and the submit handler. The Book no. field takes a barcode scanner's input and submits on Enter.

**Do** keep focus in the Book no. field after each transaction. **Don't** add more fields; anything more complex opens the full loan form.
