/*
 * E-Voting Machine © 2024 by Sergey Dolin is licensed under CC BY-NC-ND 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by-nc-nd/4.0/
 */

import { VotingStatus } from 'app/protobuffers/voting-registry';

export type DbId = string;
export type DbField = string | number | boolean | null;
export type DbRecord = Readonly<Record<string, DbField>>;
export type DbRow = [DbId, DbRecord];
export type DbTable = ReadonlyMap<DbId, DbRecord>;

export enum DbTableName {
  Voting = 'voting',
  VotingSubjects  = 'voting-subjects',
  Voters = 'voters',
}

interface IDb {
  create(table: DbTableName, record: DbRecord): Promise<DbId>;
  read(table: DbTableName, id: DbId): Promise<DbRecord | null>;
  update(table: DbTableName, id: DbId, record: DbRecord): Promise<void>;
  delete(table: DbTableName, id: DbId): Promise<void>;
  list(table: DbTableName): Promise<DbTable>;
  clear(table: DbTableName): Promise<void>;
  find(table: DbTableName, query: Record<string, DbField>): Promise<DbTable>;
  transaction<T>(action: () => Promise<T>): Promise<T>;
}

export default IDb;

