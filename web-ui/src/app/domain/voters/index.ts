/*
 * E-Voting Machine © 2024 by Sergey Dolin is licensed under CC BY-NC-ND 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by-nc-nd/4.0/
 */


import {VotingModel} from '../voting';

export type VoterId = string;

export interface VoterModel {
    readonly id: VoterId;
    readonly voting: VotingModel;
}

export interface IVotersRepository {
    createVoter(voterModel: VoterModel): Promise<void>;
}

