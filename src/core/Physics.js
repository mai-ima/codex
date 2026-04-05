import { resolveAxisAlignedMapCollision } from './Collision.js';

export function integrate(entity, dt, world, rules) {
  entity.vel.y += rules.gravity * dt;

  entity.pos.x += entity.vel.x * dt;
  resolveAxisAlignedMapCollision(entity, world.solids, 'x');

  entity.pos.y += entity.vel.y * dt;
  resolveAxisAlignedMapCollision(entity, world.solids, 'y');

  if (entity.pos.y > world.deathY) {
    entity.flags.alive = false;
  }
}
