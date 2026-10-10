/*
 * E-Voting Machine © 2024 by Sergey Dolin is licensed under CC BY-NC-ND 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by-nc-nd/4.0/
 */

/*
 * E-Voting Machine © 2024 by Sergey Dolin is licensed under CC BY-NC-ND 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by-nc-nd/4.0/
 */

import IDb, {DbField, DbTableName} from '../../../drivers/db';
import {IVotingsRepository, SubjectId, VotingModel} from '../index';
import {VotingStatus} from '../../../protobuffers/voting-registry';


const table = DbTableName.Voting;

export const votingStatusToDb = (status: VotingStatus): DbField => {
  return VotingStatus[status];
};

export const votingStatusFromDb = (status: DbField): VotingStatus => {
  return VotingStatus[status as keyof typeof VotingStatus];
};

export class DbVotingRepository implements IVotingsRepository {
  private readonly db: IDb;

  constructor(db: IDb) {
    this.db = db;
  }

  async createVoting(model: VotingModel): Promise<void> {
    await this.db.transaction(async () => {
      const modelId = await this.db.create(table, {id: model.id, status: votingStatusToDb(model.status)});
      await Promise.all(model.subjects.map(subject => {
        this.db.create(DbTableName.VotingSubjects, {modelId, id: subject.subjectId});
      }));
    });
  }

  async setStatus(model: VotingModel, newStatus: VotingStatus): Promise<VotingModel> {
    await this.db.update(table, model.id, {status: newStatus});
    return {...model, status: newStatus};
  }

  async byId(votingId: string): Promise<VotingModel> {
    const model = await this.db.read(table, votingId);
    const subjects = await this.db.find(DbTableName.VotingSubjects, {modelId: model.id});
    return {
      id: model.id as string,
      status: votingStatusFromDb(model.status),
      subjects: Array.from(subjects.entries()).map(
        ([id, r]) => ({id, subjectId: r.id as SubjectId}))};
  }
}

