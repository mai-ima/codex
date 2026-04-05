import test from 'node:test';
import assert from 'node:assert/strict';

import { Engine } from '../src/core/Engine.js';
import { Entity } from '../src/core/Entity.js';
import { CharacterController } from '../src/core/CharacterController.js';
import { InputState } from '../src/core/InputState.js';
import { ReplayPlayer, ReplayRecorder } from '../src/core/Replay.js';
import { createSMBLikeRuleSet } from '../src/smb/createSMBLikeRuleSet.js';
import { level1 } from '../src/content/level1.js';

function createPlayerAtSpawn() {
  return new Entity({
    id: 'p',
    type: 'player',
    x: level1.playerSpawn.x,
    y: level1.playerSpawn.y,
    width: 14,
    height: 16
  });
}

test('player can move right and accelerate', () => {
  const rules = createSMBLikeRuleSet();
  const engine = new Engine({ world: level1, ruleSet: rules });
  const player = createPlayerAtSpawn();

  engine.addEntity(player, new CharacterController(rules));

  for (let i = 0; i < 15; i++) {
    engine.step(new Map([[player.id, InputState.from({ right: true })]]));
  }

  assert.ok(player.pos.x > level1.playerSpawn.x);
  assert.ok(player.vel.x > 0);
});

test('jump gives negative vertical velocity', () => {
  const rules = createSMBLikeRuleSet();
  const engine = new Engine({ world: level1, ruleSet: rules });
  const player = createPlayerAtSpawn();

  player.pos.y = 7 * level1.tileSize - player.size.height;
  engine.addEntity(player, new CharacterController(rules));

  for (let i = 0; i < 5; i++) {
    engine.step(new Map([[player.id, new InputState()]]));
  }

  engine.step(
    new Map([
      [player.id, InputState.from({ jumpPressed: true, jumpHeld: true })]
    ])
  );

  assert.ok(player.vel.y < 0);
});

test('recording + replay produce same final snapshot for deterministic input', () => {
  const rules = createSMBLikeRuleSet();
  const recordingEngine = new Engine({ world: level1, ruleSet: rules });
  const originalPlayer = createPlayerAtSpawn();
  recordingEngine.addEntity(originalPlayer, new CharacterController(rules));

  const recorder = new ReplayRecorder({ targetEntityId: originalPlayer.id });

  for (let frame = 0; frame < 90; frame++) {
    const input = InputState.from({
      right: true,
      run: frame > 10,
      jumpPressed: frame === 8,
      jumpHeld: frame >= 8 && frame < 14
    });
    recorder.capture(input);
    recordingEngine.step(new Map([[originalPlayer.id, input]]));
  }

  const replayEngine = new Engine({ world: level1, ruleSet: rules });
  const replayPlayer = createPlayerAtSpawn();
  replayEngine.addEntity(replayPlayer, new CharacterController(rules));

  const playback = new ReplayPlayer(recorder.toJSON());
  for (let i = 0; i < 90; i++) {
    replayEngine.step(new Map([[replayPlayer.id, playback.next()]]));
  }

  const a = recordingEngine.snapshot().entities[0];
  const b = replayEngine.snapshot().entities[0];

  assert.equal(a.pos.x, b.pos.x);
  assert.equal(a.pos.y, b.pos.y);
  assert.equal(a.vel.x, b.vel.x);
  assert.equal(a.vel.y, b.vel.y);
});
