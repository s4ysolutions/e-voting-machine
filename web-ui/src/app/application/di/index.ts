import {DbVotingRepository} from 'app/domain/voting/db';
import Di from '../../di';
import IDb from 'app/drivers/db';
import {IVotingsRepository} from 'app/domain/voting';
import { MemoryDB } from 'app/drivers/db/memory-db';

const LATENCY = 50;
const delay = () =>
  new Promise(resolve => setTimeout(resolve, LATENCY));

class AppDi implements Di {
  private readonly db: IDb;
  readonly votingRepository: IVotingsRepository;

  constructor() {
    this.db = new MemoryDB();
    this.votingRepository = new DbVotingRepository(this.db);
  }

  async init() {
    await delay();
  }
}

const di = new AppDi();

export default di;
