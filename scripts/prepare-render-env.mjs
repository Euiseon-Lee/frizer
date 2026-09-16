import {readFileSync, writeFileSync, existsSync} from 'node:fs';
import {parseEnv} from 'node:util';
import {randomBytes} from 'node:crypto';

// Secret output stays in a gitignored file; never print values or include them in shell arguments.
const source = parseEnv(readFileSync('.env', 'utf8'));
if (!source.DATABASE_URL_UNPOOLED) throw new Error('Run neon env pull first (direct Postgres URL required).');
const db = new URL(source.DATABASE_URL_UNPOOLED);
if (!['postgres:', 'postgresql:'].includes(db.protocol) || db.hostname.includes('-pooler')) {
    throw new Error('Use the direct Neon Postgres URL for Flyway and session timezone settings.');
}
const destination = '.env.render';
if (existsSync(destination)) throw new Error('.env.render already exists. Review it rather than overwriting credentials.');
const values = {
    SPRING_PROFILES_ACTIVE: 'prod,render',
    JAVA_TOOL_OPTIONS: '-Xms64m -Xmx256m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -Xss512k',
    FRIZER_DB_URL: `jdbc:postgresql://${db.host}${db.pathname}?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory`,
    FRIZER_DB_USERNAME: decodeURIComponent(db.username),
    FRIZER_DB_PASSWORD: decodeURIComponent(db.password),
    FRIZER_LOGIN_USERNAME: 'frizer',
    FRIZER_LOGIN_PASSWORD: randomBytes(24).toString('base64url'),
};
writeFileSync(destination, Object.entries(values).map(([key,value]) => `${key}=${JSON.stringify(value)}`).join('\n')+'\n', {flag:'wx',mode:0o600});
console.log('Created .env.render for Render environment import. Contains secrets: do not commit or paste into chat.');
