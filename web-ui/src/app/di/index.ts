import {IVotingsRepository} from '../domain/voting';

interface Di {
  readonly votingRepository: IVotingsRepository;
}

export default Di;
