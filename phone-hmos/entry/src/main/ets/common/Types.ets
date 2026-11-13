/** 手机端（HarmonyOS NEXT）账号模型，与安卓端 TotpAccount.kt 对应。 */
export interface Account {
  id: string;
  issuer: string;
  account: string;
  secret: string;
  algorithm: string;
  digits: number;
  period: number;
  type: string;
  counter: number;
}

/** 与手机端 SyncCodec / 手表端保持一致的精简负载结构 */
export interface WireAccount {
  i: string;
  s: string;
  a: string;
  k: string;
  al: string;
  d: number;
  p: number;
  t: string;
  c: number;
}

export interface WirePayload {
  v: number;
  ty: string;
  ts: number;
  n: number;
  accounts: WireAccount[];
}

export function wireToAccount(w: WireAccount): Account {
  const next: Account = {
    id: w.i.length > 0 ? w.i : `${w.s}-${w.a}`,
    issuer: w.s,
    account: w.a,
    secret: w.k,
    algorithm: w.al.length > 0 ? w.al.toUpperCase() : 'SHA1',
    digits: w.d === 8 ? 8 : 6,
    period: w.p > 0 ? w.p : 30,
    type: w.t === 'HOTP' ? 'HOTP' : 'TOTP',
    counter: w.c > 0 ? w.c : 0
  };
  return next;
}

export function accountToWire(a: Account): WireAccount {
  return {
    i: a.id,
    s: a.issuer,
    a: a.account,
    k: a.secret,
    al: a.algorithm,
    d: a.digits,
    p: a.period,
    t: a.type,
    c: a.counter
  };
}
