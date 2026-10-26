/**
 * A JSON tree that behaves like kotlinx.serialization's JsonElement, so data written by the Kotlin app
 * (DataStore lists, backups) decodes with the same results: numbers keep their literal text ("5.0" is
 * not an Int), strings remember they were quoted, and toString() matches JsonElement.toString() byte for
 * byte (BackupCodec derives content IDs from it).
 */
export type KJson =
  | { kind: 'null' }
  | { kind: 'prim'; content: string; isString: boolean }
  | { kind: 'array'; items: KJson[] }
  | { kind: 'object'; entries: Map<string, KJson> };

export const KNull: KJson = { kind: 'null' };
export const kString = (content: string): KJson => ({ kind: 'prim', content, isString: true });
export const kLiteral = (content: string): KJson => ({ kind: 'prim', content, isString: false });
export const kNumber = (n: number): KJson => kLiteral(String(n));
export const kBool = (b: boolean): KJson => kLiteral(b ? 'true' : 'false');
export const kArray = (items: KJson[]): KJson => ({ kind: 'array', items });
export const kObject = (entries: [string, KJson][]): KJson => ({ kind: 'object', entries: new Map(entries) });

export class KJsonSyntaxError extends Error {}

/** Strict JSON (no comments, trailing commas or unquoted keys), like kotlinx's default `Json`. */
export function parseKJson(text: string): KJson {
  let i = 0;
  const fail = (msg: string): never => {
    throw new KJsonSyntaxError(`${msg} at ${i}`);
  };
  const ws = () => {
    while (i < text.length) {
      const c = text.charCodeAt(i);
      if (c === 0x20 || c === 0x0a || c === 0x0d || c === 0x09) i++;
      else break;
    }
  };
  const str = (): string => {
    i++; // opening quote
    let out = '';
    let start = i;
    while (true) {
      if (i >= text.length) fail('Unterminated string');
      const c = text[i];
      if (c === '"') {
        out += text.slice(start, i);
        i++;
        return out;
      }
      if (c === '\\') {
        out += text.slice(start, i);
        const e = text[i + 1];
        i += 2;
        switch (e) {
          case '"': out += '"'; break;
          case '\\': out += '\\'; break;
          case '/': out += '/'; break;
          case 'b': out += '\b'; break;
          case 'f': out += '\f'; break;
          case 'n': out += '\n'; break;
          case 'r': out += '\r'; break;
          case 't': out += '\t'; break;
          case 'u': {
            const hex = text.slice(i, i + 4);
            if (!/^[0-9a-fA-F]{4}$/.test(hex)) fail('Bad unicode escape');
            out += String.fromCharCode(parseInt(hex, 16));
            i += 4;
            break;
          }
          default: fail('Bad escape');
        }
        start = i;
        continue;
      }
      i++;
    }
  };
  const value = (): KJson => {
    ws();
    const c = text[i];
    if (c === '{') {
      i++;
      const entries = new Map<string, KJson>();
      ws();
      if (text[i] === '}') { i++; return { kind: 'object', entries }; }
      while (true) {
        ws();
        if (text[i] !== '"') fail('Expected key');
        const key = str();
        ws();
        if (text[i] !== ':') fail('Expected colon');
        i++;
        entries.set(key, value());
        ws();
        if (text[i] === ',') { i++; continue; }
        if (text[i] === '}') { i++; return { kind: 'object', entries }; }
        fail('Expected , or }');
      }
    }
    if (c === '[') {
      i++;
      const items: KJson[] = [];
      ws();
      if (text[i] === ']') { i++; return { kind: 'array', items }; }
      while (true) {
        items.push(value());
        ws();
        if (text[i] === ',') { i++; continue; }
        if (text[i] === ']') { i++; return { kind: 'array', items }; }
        fail('Expected , or ]');
      }
    }
    if (c === '"') return kString(str());
    // Like kotlinx's tree reader, an unquoted value is any run of non-token characters; it is only
    // checked when decoded (so `01` or `abc` parse, then fail as an Int).
    const start = i;
    while (i < text.length && !/[\s{}[\],:"]/.test(text[i])) i++;
    if (i === start) return fail('Unexpected token');
    const token = text.slice(start, i);
    return token === 'null' ? KNull : kLiteral(token);
  };
  const root = value();
  ws();
  if (i !== text.length) fail('Trailing content');
  return root;
}

function quote(s: string): string {
  let out = '"';
  for (let k = 0; k < s.length; k++) {
    const ch = s[k];
    const c = s.charCodeAt(k);
    if (ch === '"') out += '\\"';
    else if (ch === '\\') out += '\\\\';
    else if (ch === '\n') out += '\\n';
    else if (ch === '\r') out += '\\r';
    else if (ch === '\t') out += '\\t';
    else if (ch === '\b') out += '\\b';
    else if (ch === '\f') out += '\\f';
    else if (c < 0x20) out += '\\u' + c.toString(16).padStart(4, '0');
    else out += ch;
  }
  return out + '"';
}

/** Compact text, identical to kotlinx JsonElement.toString(). */
export function stringifyKJson(e: KJson): string {
  switch (e.kind) {
    case 'null': return 'null';
    case 'prim': return e.isString ? quote(e.content) : e.content;
    case 'array': return '[' + e.items.map(stringifyKJson).join(',') + ']';
    case 'object':
      return '{' + [...e.entries].map(([k, v]) => quote(k) + ':' + stringifyKJson(v)).join(',') + '}';
  }
}

/** Kotlin's String.toLongOrNull: optional sign, decimal digits, in Long range. */
function kotlinToLongOrNull(s: string): bigint | null {
  if (!/^[+-]?\d+$/.test(s)) return null;
  const v = BigInt(s);
  return v < -9223372036854775808n || v > 9223372036854775807n ? null : v;
}

/** Kotlin/JVM String.toDoubleOrNull for the decimal forms JSON can hold (no hex floats). */
function kotlinToDoubleOrNull(s: string): number | null {
  const t = s.trim();
  if (!/^[+-]?(NaN|Infinity|((\d+\.?\d*|\.\d+)([eE][+-]?\d+)?)[fFdD]?)$/.test(t)) return null;
  const core = t.replace(/[fFdD]$/, '');
  return Number(core);
}

/** java.lang.Double.toString: plain decimals in [1e-3, 1e7), otherwise d.dddE±n, always a fractional digit. */
export function javaDoubleToString(x: number): string {
  if (Number.isNaN(x)) return 'NaN';
  if (!Number.isFinite(x)) return x > 0 ? 'Infinity' : '-Infinity';
  if (x === 0) return Object.is(x, -0) ? '-0.0' : '0.0';
  const sign = x < 0 ? '-' : '';
  const a = Math.abs(x);
  // Shortest round-trip digits and exponent, from JS's own shortest representation
  const [mant, expPart] = a.toExponential().split('e');
  const digits = mant.replace('.', '');
  const exp = Number(expPart);
  if (a >= 1e-3 && a < 1e7) {
    if (exp >= 0) {
      const intPart = digits.slice(0, exp + 1).padEnd(exp + 1, '0');
      const frac = digits.slice(exp + 1) || '0';
      return sign + intPart + '.' + frac;
    }
    return sign + '0.' + '0'.repeat(-exp - 1) + digits;
  }
  return sign + digits[0] + '.' + (digits.slice(1) || '0') + 'E' + exp;
}

/**
 * What kotlinx writes for a JsonElement passed through its serializer (encodeToString(JsonArray.serializer(),
 * ...)), which StoredList uses to write unreadable elements back: unquoted literals are re-typed as Long,
 * Double or Boolean when they parse as one, and quoted as strings otherwise.
 */
export function serializeKJson(e: KJson): string {
  switch (e.kind) {
    case 'null': return 'null';
    case 'prim': {
      if (e.isString) return quote(e.content);
      const asLong = kotlinToLongOrNull(e.content);
      if (asLong != null) return asLong.toString();
      if (/^\+?\d+$/.test(e.content) && BigInt(e.content) <= 18446744073709551615n) {
        return BigInt(e.content).toString(); // ULong, printed unsigned
      }
      const asDouble = kotlinToDoubleOrNull(e.content);
      if (asDouble != null) {
        if (!Number.isFinite(asDouble)) throw new Error(`Unexpected special floating-point value ${asDouble}`);
        return javaDoubleToString(asDouble);
      }
      if (e.content === 'true' || e.content === 'false') return e.content;
      return quote(e.content);
    }
    case 'array': return '[' + e.items.map(serializeKJson).join(',') + ']';
    case 'object':
      return '{' + [...e.entries].map(([k, v]) => quote(k) + ':' + serializeKJson(v)).join(',') + '}';
  }
}

/** kotlinx `prettyPrint = true` output (4-space indent, `"key": value`, empty containers as [] and {}). */
export function prettyKJson(e: KJson, indent = ''): string {
  const next = indent + '    ';
  switch (e.kind) {
    case 'array':
      if (e.items.length === 0) return '[]';
      return '[\n' + e.items.map((v) => next + prettyKJson(v, next)).join(',\n') + '\n' + indent + ']';
    case 'object':
      if (e.entries.size === 0) return '{}';
      return '{\n' + [...e.entries].map(([k, v]) => next + quote(k) + ': ' + prettyKJson(v, next)).join(',\n') + '\n' + indent + '}';
    default:
      return stringifyKJson(e);
  }
}

export class DecodeError extends Error {}

export const isObject = (e: KJson | undefined): e is Extract<KJson, { kind: 'object' }> => e?.kind === 'object';
export const isArray = (e: KJson | undefined): e is Extract<KJson, { kind: 'array' }> => e?.kind === 'array';

/** The primitive's content, like kotlinx `jsonPrimitive.contentOrNull` (null for JsonNull and non-primitives). */
export function contentOrNull(e: KJson | undefined): string | null {
  return e?.kind === 'prim' ? e.content : null;
}
