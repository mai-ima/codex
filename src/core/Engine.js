import { EventBus } from './EventBus.js';
import { integrate } from './Physics.js';
import { InputState } from './InputState.js';

export class Engine {
  constructor({ world, ruleSet, fixedDt = 1 / 60 }) {
    this.world = world;
    this.ruleSet = ruleSet;
    this.fixedDt = fixedDt;
    this.entities = [];
    this.controllers = new Map();
    this.plugins = [];
    this.events = new EventBus();
    this.tick = 0;
  }

  addEntity(entity, controller = null) {
    this.entities.push(entity);
    if (controller) this.controllers.set(entity.id, controller);
  }

  use(plugin) {
    plugin.install(this);
    this.plugins.push(plugin);
  }

  step(inputByEntity = new Map()) {
    this.events.emit('step:before', {
      tick: this.tick,
      fixedDt: this.fixedDt
    });

    for (const entity of this.entities) {
      if (!entity.flags.alive) continue;
      const controller = this.controllers.get(entity.id);
      if (controller) {
        const input = inputByEntity.get(entity.id) ?? new InputState();
        controller.update(entity, input, this.fixedDt);
      }
      integrate(entity, this.fixedDt, this.world, this.ruleSet);
      this.events.emit('entity:updated', entity);
    }

    this.entities = this.entities.filter((e) => e.flags.alive);
    this.tick += 1;

    this.events.emit('step:after', {
      tick: this.tick,
      fixedDt: this.fixedDt,
      entities: this.entities
    });
  }

  snapshot() {
    return {
      tick: this.tick,
      entities: this.entities.map((entity) => ({
        id: entity.id,
        pos: { ...entity.pos },
        vel: { ...entity.vel },
        flags: { ...entity.flags }
      }))
    };
  }
}
