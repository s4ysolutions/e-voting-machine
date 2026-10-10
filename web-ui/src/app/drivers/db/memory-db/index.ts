/*
 * E-Voting Machine © 2024 by Sergey Dolin is licensed under CC BY-NC-ND 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by-nc-nd/4.0/
 */

/*
 * E-Voting Machine © 2024 by Sergey Dolin is licensed under CC BY-NC-ND 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by-nc-nd/4.0/
 */

import IDb, {DbField, DbId, DbRecord, DbRow, DbTable, DbTableName} from '../index';

const LATENCY = 100;
let id = 1;

const delay = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

export class MemoryDB implements IDb {
  private db: Map<DbTableName, Map<DbId, DbRecord>> = new Map();

  async create(table: DbTableName, record: DbRecord): Promise<DbId> {
    const sid = (id++).toString();
    const t = this.getTable(table);
    t.set(sid, record);
    await delay(LATENCY);
    return sid;
  }

  async read(table: DbTableName, id: DbId): Promise<DbRecord | null> {
    await delay(LATENCY);
    const t = this.getTable(table);
    return t.get(id) || null;
  }

  async update(table: DbTableName, id: DbId, record: DbRecord): Promise<void> {
    await delay(LATENCY);
    const old = await this.read(table, id) || {};
    this.db.get(table).set(id, {...old, ...record});
  }

  async delete(table: DbTableName, id: DbId): Promise<void> {
    await delay(LATENCY);
    const t = this.getTable(table);
    t.delete(id);
  }

  async list(table: DbTableName): Promise<DbTable> {
    await delay(LATENCY);
    return this.getTable(table);
  }

  async clear(table: DbTableName): Promise<void> {
    await delay(LATENCY);
    this.db.set(table, new Map());
  }

  async find(table: DbTableName, query: DbRecord): Promise<DbTable> {
    const records = await this.list(table);
    const result = new Map<DbId, DbRecord>();

    for (const [id, record] of records.entries()) {
      let match = true;
      for (const key in query) {
        if (record[key] !== query[key]) {
          match = false;
          break;
        }
      }
      if (match) {
        result.set(id, record);
      }
    }

    return result;
  }

  transaction<T>(action: () => Promise<T>): Promise<T> {
    return action();
  }

  private getTable(table: DbTableName): Map<DbId, DbRecord> {
    const t = this.db.get(table);
    if (t == null) {
      throw new Error(`Table ${table} not found`);
    }
    return t;
  }
}
