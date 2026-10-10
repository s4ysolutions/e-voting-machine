import {extract, sign, verify} from '../../../../../src/app/drivers/ledger/signing';
import {expect} from 'chai';

const nonceMock = (nonce: string) => () => nonce;

describe('ledger', () => {
  const secret = 'secret';
  const contract = 'contract';
  const nonce = nonceMock('nonce');
  const signed = 'contract|nonce|ggGF8/BUMWjy0ukrYO7HbA==';

  it('should sign a contract', () => {
    const signedActual = sign(secret, contract, nonce);
    expect(signedActual).to.equal(signed);
  });

  it('should fail to sign a contract with wrong secret', () => {
    const signedActual = sign('wrong', contract, nonce);
    expect(signedActual).to.not.equal(signed);
  });

  it('should extract a contract', () => {
    const contractActual = extract(secret, signed);
    expect(contractActual).to.equal(contract);
  });

  it('should fail to extract a contract with wrong secret', () => {
    const contractActual = extract('wrong', signed);
    expect(contractActual).to.be.null;
  });

  it('should verify a contract', () => {
    const verified = verify(secret, signed);
    expect(verified).to.be.true;
  });

  it('should fail to verify a contract with wrong secret', () => {
    const verified = verify('wrong', signed);
    expect(verified).to.be.false;
  });
});
