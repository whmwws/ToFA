import { Account } from './Types';

/** 解析 otpauth://totp|hotp/... 与 otpauth-migration 暂不支持（后续可加）。 */
export class OtpauthUri {
  static parse(uri: string): Account | null {
    const text = uri.trim();
    if (!text.startsWith('otpauth://')) {
      return null;
    }
    const withoutScheme = text.substring('otpauth://'.length);
    const slash = withoutScheme.indexOf('/');
    if (slash < 0) {
      return null;
    }
    const type = withoutScheme.substring(0, slash).toUpperCase();
    if (type !== 'TOTP' && type !== 'HOTP') {
      return null;
    }
    const rest = withoutScheme.substring(slash + 1);
    const qIndex = rest.indexOf('?');
    const label = decodeURIComponent(qIndex >= 0 ? rest.substring(0, qIndex) : rest);
    const query = qIndex >= 0 ? rest.substring(qIndex + 1) : '';
    const params: Map<string, string> = new Map();
    for (const pair of query.split('&')) {
      const eq = pair.indexOf('=');
      if (eq > 0) {
        params.set(pair.substring(0, eq).toLowerCase(), decodeURIComponent(pair.substring(eq + 1)));
      }
    }
    const secret = (params.get('secret') ?? '').split(' ').join('').toUpperCase();
    if (secret.length === 0) {
      return null;
    }
    let issuer = params.get('issuer') ?? '';
    let account = label;
    const colon = label.indexOf(':');
    if (colon >= 0) {
      if (issuer.length === 0) {
        issuer = label.substring(0, colon);
      }
      account = label.substring(colon + 1);
    }
    const digits = OtpauthUri.toInt(params.get('digits'), 6) === 8 ? 8 : 6;
    const period = Math.max(1, OtpauthUri.toInt(params.get('period'), 30));
    const counter = Math.max(0, OtpauthUri.toInt(params.get('counter'), 0));
    const algorithm = (params.get('algorithm') ?? 'SHA1').toUpperCase();
    return {
      id: `${issuer}-${account}-${secret.substring(0, 4)}`,
      issuer: issuer,
      account: account,
      secret: secret,
      algorithm: algorithm,
      digits: digits,
      period: period,
      type: type,
      counter: counter
    };
  }

  private static toInt(value: string | undefined, fallback: number): number {
    if (value === undefined || value.length === 0) {
      return fallback;
    }
    const n = Number(value);
    return Number.isNaN(n) ? fallback : Math.floor(n);
  }
}
