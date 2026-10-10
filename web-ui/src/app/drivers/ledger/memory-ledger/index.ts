/*
 * E-Voting Machine © 2024 by Sergey Dolin is licensed under CC BY-NC-ND 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by-nc-nd/4.0/
 */


import ILedger, {LedgerId, LedgerName, LedgerRecord} from '../index';

const LATENCY = 100;
const id = 1;

const delay = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

class MemoryLedger implements ILedger {
  private readonly ledgers = new Map<LedgerName, Map<LedgerId, LedgerRecord>>();

  async add(ledger: LedgerName, record: LedgerRecord): Promise<void> {
    await delay(LATENCY);
    const l = this.getLedger(ledger);
    l.set(id.toString(), record);
  }

  async get(ledger: LedgerName, id: LedgerId): Promise<LedgerRecord | null> {
    await delay(LATENCY);
    const l = this.getLedger(ledger);
    return l.get(id) || null;
  }

  private getLedger(ledger: LedgerName) : Map<LedgerId, LedgerRecord> {
    const l = this.ledgers.get(ledger);
    if (l == null) {
      throw new Error(`Ledger ${ledger} not found`);
    }
    return l;
  }
}
