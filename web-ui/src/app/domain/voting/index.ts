/*
 * E-Voting Machine © 2024 by Sergey Dolin is licensed under CC BY-NC-ND 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by-nc-nd/4.0/
 */

import {VotingStatus} from '../../protobuffers/voting-registry';
type VotingId = string;
export type SubjectId = string;

export interface  VotingSubject {
  readonly subjectId: SubjectId;
}

export interface VotingModel {
  readonly id: VotingId;
  readonly status: VotingStatus;
  readonly subjects: Array<VotingSubject>;
}

export interface IVotingsRepository {
  createVoting(votingModel: VotingModel): Promise<void>;
  setStatus(votingModel: VotingModel, newStatus: VotingStatus): Promise<VotingModel>;
  byId(votingId: VotingId): Promise<VotingModel>;
}
