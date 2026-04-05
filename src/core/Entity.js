export class Entity {
  constructor({ id, type, x, y, width, height }) {
    this.id = id;
    this.type = type;
    this.pos = { x, y };
    this.vel = { x: 0, y: 0 };
    this.size = { width, height };
    this.flags = {
      onGround: false,
      facing: 1,
      alive: true
    };
    this.meta = {};
  }
}
