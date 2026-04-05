export function createSMBLikeRuleSet(overrides = {}) {
  return {
    gravity: 1600,
    walkAccel: 2200,
    runAccel: 3000,
    airAccel: 1200,
    maxWalkSpeed: 180,
    maxRunSpeed: 290,
    friction: 2400,
    jumpVelocity: -520,
    jumpHoldGravityScale: 0.58,
    maxFallSpeed: 900,
    coyoteTimeMs: 90,
    jumpBufferMs: 110,
    ...overrides
  };
}
