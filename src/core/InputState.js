export class InputState {
  constructor() {
    this.left = false;
    this.right = false;
    this.run = false;
    this.jumpPressed = false;
    this.jumpHeld = false;
  }

  static from(partial = {}) {
    const input = new InputState();
    Object.assign(input, partial);
    return input;
  }

  clone() {
    return InputState.from(this);
  }
}
