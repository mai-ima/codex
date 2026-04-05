import { Engine } from '../core/Engine.js';
import { Entity } from '../core/Entity.js';
import { CharacterController } from '../core/CharacterController.js';
import { InputState } from '../core/InputState.js';
import { ReplayPlayer, ReplayRecorder } from '../core/Replay.js';
import { createSMBLikeRuleSet } from '../smb/createSMBLikeRuleSet.js';
import { level1 } from '../content/level1.js';

const ruleSet = createSMBLikeRuleSet();
const engine = new Engine({ world: level1, ruleSet });

const player = new Entity({
  id: 'player-1',
  type: 'player',
  x: level1.playerSpawn.x,
  y: level1.playerSpawn.y,
  width: 14,
  height: 16
});

engine.addEntity(player, new CharacterController(ruleSet));

const recorder = new ReplayRecorder({ targetEntityId: player.id });

for (let frame = 0; frame < 120; frame++) {
  const input = InputState.from({
    right: true,
    run: frame > 20,
    jumpPressed: frame === 15,
    jumpHeld: frame >= 15 && frame < 24
  });

  recorder.capture(input);
  engine.step(new Map([[player.id, input]]));
}

console.log('recorded final:', engine.snapshot());

const replayEngine = new Engine({ world: level1, ruleSet });
const replayPlayerEntity = new Entity({
  id: 'player-1',
  type: 'player',
  x: level1.playerSpawn.x,
  y: level1.playerSpawn.y,
  width: 14,
  height: 16
});
replayEngine.addEntity(replayPlayerEntity, new CharacterController(ruleSet));

const playerInputs = new ReplayPlayer(recorder.toJSON());
for (let i = 0; i < 120; i++) {
  replayEngine.step(new Map([[replayPlayerEntity.id, playerInputs.next()]]));
}

console.log('replayed final:', replayEngine.snapshot());
