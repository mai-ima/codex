export class CharacterController {
  constructor(rules) {
    this.rules = rules;
    this.timeSinceGroundedMs = Infinity;
    this.timeSinceJumpPressedMs = Infinity;
  }

  update(entity, input, dt) {
    const r = this.rules;
    const dtMs = dt * 1000;
    this.timeSinceGroundedMs += dtMs;
    this.timeSinceJumpPressedMs += dtMs;

    if (entity.flags.onGround) this.timeSinceGroundedMs = 0;
    if (input.jumpPressed) this.timeSinceJumpPressedMs = 0;

    const dir = (input.left ? -1 : 0) + (input.right ? 1 : 0);
    if (dir !== 0) entity.flags.facing = dir;

    const accel = entity.flags.onGround
      ? (input.run ? r.runAccel : r.walkAccel)
      : r.airAccel;

    const maxSpeed = input.run ? r.maxRunSpeed : r.maxWalkSpeed;

    if (dir !== 0) {
      entity.vel.x += dir * accel * dt;
    } else if (entity.flags.onGround) {
      const sign = Math.sign(entity.vel.x);
      const decel = r.friction * dt;
      if (Math.abs(entity.vel.x) <= decel) {
        entity.vel.x = 0;
      } else {
        entity.vel.x -= sign * decel;
      }
    }

    entity.vel.x = Math.max(-maxSpeed, Math.min(maxSpeed, entity.vel.x));

    const canCoyote = this.timeSinceGroundedMs <= r.coyoteTimeMs;
    const wantsJump = this.timeSinceJumpPressedMs <= r.jumpBufferMs;

    if (canCoyote && wantsJump) {
      entity.vel.y = r.jumpVelocity;
      entity.flags.onGround = false;
      this.timeSinceGroundedMs = Infinity;
      this.timeSinceJumpPressedMs = Infinity;
    }

    if (entity.vel.y < 0 && input.jumpHeld) {
      entity.vel.y += r.gravity * (1 - r.jumpHoldGravityScale) * dt;
    }

    if (entity.vel.y > r.maxFallSpeed) {
      entity.vel.y = r.maxFallSpeed;
    }
  }
}
