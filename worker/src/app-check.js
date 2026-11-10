import { createRemoteJWKSet, jwtVerify } from 'jose';

const firebaseKeys = createRemoteJWKSet(
  new URL('https://firebaseappcheck.googleapis.com/v1/jwks'),
  { cacheMaxAge: 6 * 60 * 60 * 1000 },
);

export async function verifyAppCheck(token, projectNumber, appId, options = {}) {
  try {
    if (
      typeof token !== 'string' || token.trim().length === 0 ||
      typeof projectNumber !== 'string' || !/^[0-9]+$/.test(projectNumber) ||
      typeof appId !== 'string' || appId.trim().length === 0
    ) {
      throw new Error('App Check rejected');
    }

    const currentDate = options.currentDate ?? new Date(Date.now());
    const { payload, protectedHeader } = await jwtVerify(
      token,
      options.keySet ?? firebaseKeys,
      {
        algorithms: ['RS256'],
        issuer: `https://firebaseappcheck.googleapis.com/${projectNumber}`,
        audience: `projects/${projectNumber}`,
        subject: appId,
        requiredClaims: ['exp', 'iat', 'sub'],
        currentDate,
        clockTolerance: 0,
      },
    );

    if (
      protectedHeader.typ !== 'JWT' ||
      payload.iat > Math.floor(currentDate.getTime() / 1000)
    ) {
      throw new Error('App Check rejected');
    }

    return { appId: payload.sub };
  } catch {
    throw new Error('App Check rejected');
  }
}
