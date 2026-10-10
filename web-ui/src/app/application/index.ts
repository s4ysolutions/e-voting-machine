import di from './di';

class Application {

  constructor() {
  }

  async init() {
    await di.init();
  }
}

export default Application;
