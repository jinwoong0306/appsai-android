import { describe, expect, test } from 'bun:test';
import {
  SignJWT,
  base64url,
  createLocalJWKSet,
  exportJWK,
  generateKeyPair,
} from 'jose';
import { verifyAppCheck } from '../src/app-check.js';

const projectNumber = '123456789';
const appId = '1:123456789:android:test-app';
const currentDate = new Date('2026-11-05T00:00:00.000Z');
const now = Math.floor(currentDate.getTime() / 1000);
const claims = {
  iss: `https://firebaseappcheck.googleapis.com/${projectNumber}`,
  aud: [`projects/${projectNumber}`],
  sub: appId,
  iat: now - 60,
  exp: now + 300,
};
const [signingKeys, otherKeys] = await Promise.all([
  generateKeyPair('RS256', { extractable: true }),
  generateKeyPair('RS256', { extractable: true }),
]);
const publicJwk = {
  ...await exportJWK(signingKeys.publicKey),
  kid: 'firebase-test-key',
  alg: 'RS256',
  use: 'sig',
};
const keySet = createLocalJWKSet({ keys: [publicJwk] });
const options = { keySet, currentDate };

function sign(payload = claims, header = {}, key = signingKeys.privateKey) {
  return new SignJWT(payload)
    .setProtectedHeader({ alg: 'RS256', typ: 'JWT', kid: publicJwk.kid, ...header })
    .sign(key);
}

async function expectRejected(token) {
  await expect(verifyAppCheck(
    token, projectNumber, appId, options,
  )).rejects.toThrow(new Error('App Check rejected'));
}

describe('verifyAppCheck', () => {
  test('returns only the authenticated app ID from an RS256 signature', async () => {
    expect(await verifyAppCheck(
      await sign(), projectNumber, appId, options,
    )).toEqual({ appId });
  });

  test('accepts the project audience as a string', async () => {
    expect(await verifyAppCheck(
      await sign({ ...claims, aud: `projects/${projectNumber}` }),
      projectNumber, appId, options,
    )).toEqual({ appId });
  });

  test('accepts an issued-at timestamp exactly at the fixed clock', async () => {
    expect(await verifyAppCheck(
      await sign({ ...claims, iat: now }), projectNumber, appId, options,
    )).toEqual({ appId });
  });

  test('rejects a modified signed payload even when all required claims remain valid',
    async () => {
      const parts = (await sign()).split('.');
      parts[1] = base64url.encode(JSON.stringify({ ...claims, extra: 'tampered' }));
      await expectRejected(parts.join('.'));
    });

  test('rejects a signature from a different RSA key with the same key ID', async () => {
    await expectRejected(await sign(claims, {}, otherKeys.privateKey));
  });

  test('rejects HS256 instead of RS256', async () => {
    const token = await new SignJWT(claims)
      .setProtectedHeader({ alg: 'HS256', typ: 'JWT' })
      .sign(new Uint8Array(32).fill(1));
    await expectRejected(token);
  });

  for (const [label, typ] of [
    ['access-token type', 'at+jwt'],
    ['lowercase type', 'jwt'],
    ['media type alias', 'application/JWT'],
    ['missing type', undefined],
  ]) {
    test(`rejects ${label}`, async () => {
      await expectRejected(await sign(claims, { typ }));
    });
  }

  for (const [label, changes] of [
    ['wrong issuer', { iss: 'https://firebaseappcheck.googleapis.com/987654321' }],
    ['wrong audience', { aud: ['projects/987654321'] }],
    ['wrong subject', { sub: '1:123456789:android:other-app' }],
    ['expiration exactly now', { exp: now }],
    ['past expiration', { exp: now - 1 }],
    ['future issued-at', { iat: now + 1 }],
    ['fractional future issued-at', { iat: now + 0.5 }],
    ['nonnumeric issued-at', { iat: String(now - 60) }],
    ['nonnumeric expiration', { exp: String(now + 300) }],
  ]) {
    test(`rejects ${label}`, async () => {
      await expectRejected(await sign({ ...claims, ...changes }));
    });
  }

  for (const claim of ['exp', 'iat', 'sub', 'iss', 'aud']) {
    test(`rejects missing ${claim}`, async () => {
      const payload = { ...claims };
      delete payload[claim];
      await expectRejected(await sign(payload));
    });
  }

  for (const [label, token] of [
    ['missing token', undefined],
    ['null token', null],
    ['empty token', ''],
    ['whitespace token', ' '],
    ['object token', {}],
    ['numeric token', 123],
    ['malformed JWT', 'not.a.jwt'],
    ['too few segments', 'a.b'],
    ['too many segments', 'a.b.c.d'],
  ]) {
    test(`rejects ${label}`, async () => {
      await expectRejected(token);
    });
  }

  for (const [label, configuredProject] of [
    ['missing', undefined],
    ['null', null],
    ['empty', ''],
    ['whitespace', ' '],
    ['nonnumeric', 'project-id'],
    ['decimal', '123.5'],
    ['negative', '-123'],
    ['padded', ' 123456789 '],
    ['number instead of configuration string', 123456789],
  ]) {
    test(`rejects ${label} project configuration before key lookup`, async () => {
      let lookedUp = false;
      const configOptions = {
        currentDate,
        keySet: async () => {
          lookedUp = true;
          throw new Error('Key lookup should not run');
        },
      };
      await expect(verifyAppCheck(
        await sign(), configuredProject, appId, configOptions,
      )).rejects.toThrow(new Error('App Check rejected'));
      expect(lookedUp).toBe(false);
    });
  }

  for (const [label, configuredApp] of [
    ['missing', undefined],
    ['null', null],
    ['empty', ''],
    ['whitespace', ' '],
    ['nonstring', 123],
  ]) {
    test(`rejects ${label} app configuration`, async () => {
      await expect(verifyAppCheck(
        await sign(), projectNumber, configuredApp, options,
      )).rejects.toThrow(new Error('App Check rejected'));
    });
  }

  test('rejects an unknown signing key ID', async () => {
    await expectRejected(await sign(claims, { kid: 'unknown-key' }));
  });

  test('hides JWKS failures behind the fixed rejection error', async () => {
    const privateError = new Error('Internal key lookup failure');
    let caught;
    try {
      await verifyAppCheck(await sign(), projectNumber, appId, {
        currentDate,
        keySet: async () => { throw privateError; },
      });
    } catch (error) {
      caught = error;
    }
    expect(caught).toBeInstanceOf(Error);
    expect(caught).not.toBe(privateError);
    expect(caught.message).toBe('App Check rejected');
    expect(caught.cause).toBeUndefined();
  });
});
