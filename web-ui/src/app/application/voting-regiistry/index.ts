import {IVotingsRepository} from '../../domain/voting';

class VotingRegistry {
  private readonly votingsRepository: IVotingsRepository;

  constructor(votingsRepository: IVotingsRepository) {
    this.votingsRepository = votingsRepository;
  }

}
