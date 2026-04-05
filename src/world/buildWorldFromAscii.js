function solid(x, y, tileSize) {
  return {
    type: 'solid',
    pos: { x: x * tileSize, y: y * tileSize },
    size: { width: tileSize, height: tileSize }
  };
}

export function buildWorldFromAscii({ rows, tileSize = 16, deathYTiles = rows.length + 6 }) {
  const solids = [];
  let playerSpawn = { x: tileSize, y: tileSize };

  for (let y = 0; y < rows.length; y++) {
    for (let x = 0; x < rows[y].length; x++) {
      const ch = rows[y][x];
      if (ch === '#') solids.push(solid(x, y, tileSize));
      if (ch === 'P') {
        playerSpawn = { x: x * tileSize, y: y * tileSize };
      }
    }
  }

  return {
    tileSize,
    deathY: deathYTiles * tileSize,
    playerSpawn,
    solids
  };
}
