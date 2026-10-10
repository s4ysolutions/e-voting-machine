/*
 * E-Voting Machine © 2024 by Sergey Dolin is licensed under CC BY-NC-ND 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by-nc-nd/4.0/
 */

import {createHash} from 'crypto';

const NONCE_LENGTH = 100000;

export const sign = (
  secret: string,
  contract: string,
  nonceGenerator: () => string = () => Math.floor(Math.random() * NONCE_LENGTH).toString(),
): string => {
  const nonce = nonceGenerator();
  const hash = createHash('md5').update(`${contract}|${secret}|${nonce}`).digest('base64');
  return `${contract}|${nonce}|${hash}`;
};

export const extract = (secret: string, signed: string): string | null => {
  const match = signed.match(/^(.*)\|(.*)\|(.*)$/);
  if (!match) {
    return null;
  }

  const [, contract, nonce, digest] = match;
  const hash = createHash('md5').update(`${contract}|${secret}|${nonce}`).digest('base64');

  if (hash === digest) {
    return contract;
  }

  return null;
};

export const verify = (secret: string, signed: string): boolean => {
  return extract(secret, signed) !== null;
};
