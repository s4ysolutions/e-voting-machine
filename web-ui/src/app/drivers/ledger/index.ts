/*
 * E-Voting Machine © 2024 by Sergey Dolin is licensed under CC BY-NC-ND 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by-nc-nd/4.0/
 */

export type LedgerId = string;
export type LedgerField = string | number | boolean | null;
export type LedgerRecord = Readonly<Record<string, LedgerField>>;

export enum LedgerName {

}

interface ILedger {
  add(ledger: LedgerName, record: LedgerRecord): Promise<void>;
  get(ledger: LedgerName, id: LedgerId): Promise<LedgerRecord>;
}

export default ILedger;
