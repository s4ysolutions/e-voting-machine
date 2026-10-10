/*
 * E-Voting Machine © 2024 by Sergey Dolin is licensed under CC BY-NC-ND 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by-nc-nd/4.0/
 */

import IDb, {DbTableName} from '../../../drivers/db';
import {IVotersRepository, VoterModel} from '../index';
import {IVotingsRepository} from '../../voting';

class DbVoters implements IVotersRepository{
  private readonly db: IDb;

  constructor(db: IDb, votingRepository: IVotingsRepository) {
    this.db = db;
  }

  async createVoter(voterModel: VoterModel): Promise<void> {
    await this.db.create(DbTableName.Voters, {id: voterModel.id, voting: voterModel.voting.id});
  }

}
