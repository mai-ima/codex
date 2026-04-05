import { InputState } from './InputState.js';

export class ReplayRecorder {
  constructor({ targetEntityId }) {
    this.targetEntityId = targetEntityId;
    this.frames = [];
  }

  capture(input) {
    this.frames.push(input.clone());
  }

  toJSON() {
    return {
      targetEntityId: this.targetEntityId,
      frames: this.frames
    };
  }
}

export class ReplayPlayer {
  constructor(recording) {
    this.recording = recording;
    this.index = 0;
  }

  next() {
    if (this.index >= this.recording.frames.length) {
      return InputState.from();
    }

    const frame = InputState.from(this.recording.frames[this.index]);
    this.index += 1;
    return frame;
  }
}
