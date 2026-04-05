export function aabbIntersects(a, b) {
  return (
    a.pos.x < b.pos.x + b.size.width &&
    a.pos.x + a.size.width > b.pos.x &&
    a.pos.y < b.pos.y + b.size.height &&
    a.pos.y + a.size.height > b.pos.y
  );
}

export function resolveAxisAlignedMapCollision(entity, solids, axis) {
  entity.flags.onGround = false;

  for (const tile of solids) {
    if (!aabbIntersects(entity, tile)) continue;

    if (axis === 'x') {
      if (entity.vel.x > 0) {
        entity.pos.x = tile.pos.x - entity.size.width;
      } else if (entity.vel.x < 0) {
        entity.pos.x = tile.pos.x + tile.size.width;
      }
      entity.vel.x = 0;
    } else {
      if (entity.vel.y > 0) {
        entity.pos.y = tile.pos.y - entity.size.height;
        entity.flags.onGround = true;
      } else if (entity.vel.y < 0) {
        entity.pos.y = tile.pos.y + tile.size.height;
      }
      entity.vel.y = 0;
    }
  }
}
