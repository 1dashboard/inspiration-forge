#!/usr/bin/env node
'use strict'

// Exports localhost only. It never connects to or modifies the production server.
const fs = require('node:fs')
const path = require('node:path')
const crypto = require('node:crypto')
const net = require('node:net')
const { spawnSync } = require('node:child_process')
const { createRequire } = require('node:module')

const root = path.resolve(__dirname, '../..')
const frontendRequire = createRequire(path.join(root, 'inspiration-forge-frontend/package.json'))
const YAML = frontendRequire('yaml')
// Keep numeric-looking credentials as their exact scalar text (including leading zeroes).
const config = YAML.parse(fs.readFileSync(path.join(root, 'src/main/resources/application.yml'), 'utf8'), { schema: 'failsafe' })
const source = config.spring.datasource
const dbUrl = new URL(source.url.replace(/^jdbc:/, ''))
if (!['localhost', '127.0.0.1'].includes(dbUrl.hostname)) throw new Error('Only localhost can be exported.')
const database = dbUrl.pathname.slice(1)
if (!/^[a-zA-Z0-9_]+$/.test(database)) throw new Error('Invalid local database name.')
if (typeof source.password !== 'string' || source.password.includes('${')) {
  throw new Error('A literal local database password is required; credentials are never printed or packaged.')
}
const env = { ...process.env, MYSQL_PWD: source.password }
const connectionArgs = [
  '--protocol=TCP', '--host=127.0.0.1', `--port=${dbUrl.port || 3306}`,
  `--user=${source.username}`, '--default-character-set=utf8mb4',
]
const tables = [
  'app', 'app_version', 'chat_history', 'flyway_schema_history', 'generation_task',
  'rag_chunk', 'rag_document', 'rag_knowledge_base', 'runtime_app', 'runtime_app_role',
  'runtime_app_session', 'runtime_app_user', 'runtime_app_user_role', 'runtime_model',
  'runtime_record', 'user',
]
const dataTables = tables.filter(table => table !== 'runtime_app_session')
const ignoredDirectories = new Set(['node_modules', '.git', '.vite'])
const folderNames = ['code_output', 'code_deploy', 'code_versions']
const sourceRoot = root.replaceAll('\\', '/')
const assumedBackendRoot = '/opt/1panel/www/sites/inspiration-forge'

function run(command, args, input) {
  const result = spawnSync(command, args, {
    cwd: root, env, windowsHide: true, encoding: 'utf8', input,
    maxBuffer: 128 * 1024 * 1024, timeout: 180_000,
  })
  if (result.error || result.status !== 0) {
    // Do not expose SQL rows, credentials, or generated user content in errors.
    throw new Error(`${command} failed (exit ${result.status ?? 'unavailable'}).`)
  }
  return result.stdout
}

function query(sql, selectedDatabase = database) {
  return run('mysql', [...connectionArgs, `--database=${selectedDatabase}`, '--batch', '--raw',
    '--skip-column-names', '--binary-mode'], sql)
    .trimEnd().split(/\r?\n/).filter(Boolean).map(row => row.split('\t'))
}

function tableChecksums(selectedDatabase = database) {
  return Object.fromEntries(query(`CHECKSUM TABLE ${dataTables.map(t => `\`${t}\``).join(', ')} EXTENDED;`,
    selectedDatabase).map(([name, checksum]) => [name.split('.').pop(), checksum]))
}

function assertEqual(actual, expected, label) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) throw new Error(`${label} did not match.`)
}

function sqlString(value) { return "'" + value.replaceAll("'", "''") + "'" }
function hash(file) { return crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex') }

async function portOpen(port) {
  return new Promise(resolve => {
    const socket = net.createConnection({ host: '127.0.0.1', port })
    const finish = value => { socket.destroy(); resolve(value) }
    socket.once('connect', () => finish(true))
    socket.once('error', () => finish(false))
    socket.setTimeout(1500, () => finish(false))
  })
}

async function main() {
  if (await portOpen(config.server?.port || 8123)) {
    throw new Error('Stop the local test backend before exporting a consistent database/file snapshot.')
  }
  assertEqual(query('SHOW TABLES;').map(row => row[0]).sort(), [...tables].sort(), 'Expected table inventory')
  const checksumBefore = tableChecksums()
  if (Object.values(checksumBefore).some(value => value === 'NULL')) throw new Error('Checksum unavailable.')
  const engines = query(`SELECT TABLE_NAME, ENGINE FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = ${sqlString(database)} AND TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME;`)
  if (engines.some(([, engine]) => engine !== 'InnoDB')) throw new Error('Nontransactional table found.')
  const rowCounts = Object.fromEntries(query(tables.map(t =>
    `SELECT '${t}', COUNT(*) FROM \`${t}\``).join(' UNION ALL ') + ';').map(([name, count]) => [name, Number(count)]))
  const taskStates = Object.fromEntries(query('SELECT status, COUNT(*) FROM generation_task GROUP BY status ORDER BY status;'))
  if (Object.keys(taskStates).some(status => ['RUNNING', 'PENDING', 'GENERATING', 'QUEUED', 'RETRYING'].includes(status))) {
    throw new Error('Active generation tasks must finish or be explicitly cancelled before exporting.')
  }
  // IDs are deliberately kept as strings to avoid JavaScript bigint precision loss.
  const apps = query("SELECT id, codeGenType, IFNULL(deployKey, ''), isDelete, versionNumber FROM app ORDER BY id;")
    .map(([id, type, deployKey, deleted, versionNumber]) => ({ id, type, deployKey, deleted: deleted === '1', versionNumber }))
  const versions = query('SELECT id, appId, versionNumber, sourcePath, status, isDelete FROM app_version ORDER BY id;')
    .map(([id, appId, versionNumber, sourcePath, status, deleted]) => ({ id, appId, versionNumber, sourcePath, status, deleted }))
  const warnings = []
  const activeAppIds = new Set(apps.filter(app => !app.deleted).map(app => app.id))
  for (const app of apps.filter(app => !app.deleted)) {
    const output = path.join(root, 'tmp/code_output', `${app.type}_${app.id}`)
    if (!fs.existsSync(output)) warnings.push(`Active app ${app.id}: local source directory is already missing.`)
    if (app.deployKey && !fs.existsSync(path.join(root, 'tmp/code_deploy', app.deployKey, 'index.html'))) {
      warnings.push(`Active app ${app.id}: deployed index.html is already missing (${app.deployKey}).`)
    }
  }
  for (const version of versions.filter(v => v.status === 'READY' && v.deleted !== '1' && activeAppIds.has(v.appId))) {
    if (!fs.existsSync(version.sourcePath)) warnings.push(`Active app ${version.appId}: READY version ${version.versionNumber} files are already missing.`)
  }
  const liveData = {
    users: Number(query('SELECT COUNT(*) FROM user WHERE isDelete=0;')[0][0]),
    apps: activeAppIds.size,
    deployedApps: apps.filter(app => !app.deleted && app.deployKey).length,
  }
  const stamp = new Date().toISOString().replace(/[-:]/g, '').replace(/T/, '-').slice(0, 15)
  const outputDir = path.join(root, 'target', `data-migration-${stamp}`)
  fs.mkdirSync(outputDir, { recursive: false })
  const exportPath = path.join(outputDir, '01-database.sql')
  const sessionsPath = path.join(outputDir, 'sessions-schema.part.sql')
  const dumpArgs = [...connectionArgs, '--single-transaction', '--quick', '--hex-blob',
    '--no-tablespaces', '--set-gtid-purged=OFF', '--skip-column-statistics', '--skip-add-locks',
    '--skip-comments', '--skip-dump-date', '--skip-triggers', '--complete-insert']
  run('mysqldump', [...dumpArgs, `--result-file=${exportPath}`, database, ...dataTables])
  run('mysqldump', [...dumpArgs, '--no-data', `--result-file=${sessionsPath}`, database, 'runtime_app_session'])
  fs.appendFileSync(exportPath, '\n' + fs.readFileSync(sessionsPath, 'utf8'), 'utf8')
  fs.unlinkSync(sessionsPath) // Only this script's disposable intermediate, not any user data.
  const databaseSql = fs.readFileSync(exportPath, 'utf8')
  if (/^\s*(?:USE\s|CREATE\s+DATABASE|DROP\s+DATABASE)/im.test(databaseSql)) {
    throw new Error('Dump unexpectedly contains a database selection or database-wide DDL.')
  }
  if (/INSERT INTO `runtime_app_session`/.test(databaseSql)) throw new Error('Session data unexpectedly included.')

  const fileManifest = []
  const copiedFiles = []
  const skipped = []
  function copyTree(relative) {
    const absolute = path.join(root, relative)
    const destination = path.join(outputDir, relative)
    fs.mkdirSync(destination, { recursive: true })
    for (const entry of fs.readdirSync(absolute, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name))) {
      const child = path.join(relative, entry.name)
      if (entry.isDirectory() && ignoredDirectories.has(entry.name)) { skipped.push(child.replaceAll('\\', '/')); continue }
      if (entry.isSymbolicLink()) throw new Error(`Symlink requires manual review: ${child}`)
      if (entry.isDirectory()) { copyTree(child); continue }
      if (!entry.isFile()) throw new Error(`Unsupported file type: ${child}`)
      const from = path.join(root, child)
      const to = path.join(outputDir, child)
      fs.copyFileSync(from, to, fs.constants.COPYFILE_EXCL)
      const sha256 = hash(to)
      if (hash(from) !== sha256) throw new Error(`Source changed while copying: ${child}`)
      const item = { path: child.replaceAll('\\', '/'), bytes: fs.statSync(to).size, sha256 }
      copiedFiles.push(item)
      fileManifest.push(item)
    }
  }
  for (const folder of folderNames) copyTree(path.join('tmp', folder))

  const prefix = sourceRoot + '/tmp/'
  const pathSql = `-- Run ONLY after verifying the actual Java process working directory.\n`
    + `-- Edit this one value if /proc/<JAVA_PID>/cwd or -Duser.dir differs.\n`
    + `SET @new_backend_root = ${sqlString(assumedBackendRoot)};\n`
    + `UPDATE app_version\n`
    + `SET sourcePath = CONCAT(@new_backend_root, SUBSTRING(REPLACE(sourcePath, CHAR(92), '/'), ${sourceRoot.length + 1})),\n`
    + `    updateTime = updateTime\n`
    + `WHERE BINARY LEFT(REPLACE(sourcePath, CHAR(92), '/'), ${prefix.length}) = BINARY ${sqlString(prefix)};\n`
    + `SELECT COUNT(*) AS remaining_windows_version_paths FROM app_version WHERE sourcePath REGEXP '^[A-Za-z]:';\n`
  fs.writeFileSync(path.join(outputDir, '02-fix-version-paths.sql'), pathSql, 'utf8')

  // Restore into a new, isolated LOCAL database, then remove only that database.
  const verifyDatabase = `yu_ai_migration_verify_${crypto.randomBytes(6).toString('hex')}`
  let verificationDatabaseCreated = false
  let verification
  try {
    query(`CREATE DATABASE \`${verifyDatabase}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`)
    verificationDatabaseCreated = true
    query(databaseSql, verifyDatabase)
    assertEqual(tableChecksums(verifyDatabase), checksumBefore, 'Restored data checksums')
    if (query('SELECT COUNT(*) FROM runtime_app_session;', verifyDatabase)[0][0] !== '0') throw new Error('Restored sessions not empty.')
    query(pathSql, verifyDatabase)
    const restoredPaths = query('SELECT id, sourcePath FROM app_version ORDER BY id;', verifyDatabase)
    const expectedPaths = versions.map(v => [v.id, v.sourcePath.replaceAll('\\', '/').startsWith(prefix)
      ? assumedBackendRoot + v.sourcePath.replaceAll('\\', '/').slice(sourceRoot.length) : v.sourcePath])
    assertEqual(restoredPaths, expectedPaths, 'Linux version path remapping')
    verification = { isolatedLocalRestorePassed: true, dataChecksumsMatched: true,
      pathRemappingPassed: true, excludedRuntimeSessions: rowCounts.runtime_app_session }
  } finally {
    if (verificationDatabaseCreated && /^yu_ai_migration_verify_[a-f0-9]{12}$/.test(verifyDatabase)) {
      query(`DROP DATABASE \`${verifyDatabase}\`;`)
    }
  }
  assertEqual(tableChecksums(), checksumBefore, 'Unchanged source database')
  for (const item of copiedFiles) {
    if (hash(path.join(root, item.path)) !== item.sha256) throw new Error('Source files changed during export. Export again while idle.')
  }

  const manifest = {
    format: 1, createdAt: new Date().toISOString(), sourceDatabase: database, intendedTargetDatabase: 'pyhpyh',
    sourceRoot, assumedBackendRoot, backendRootMustBeVerified: true, rowCounts,
    liveData, taskStates, warnings, verification, skippedDirectories: skipped,
    includedGeneratedFiles: copiedFiles.length, includedGeneratedBytes: copiedFiles.reduce((n, f) => n + f.bytes, 0),
    sourceTableChecksums: checksumBefore, files: fileManifest,
  }
  fs.writeFileSync(path.join(outputDir, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n')
  fs.copyFileSync(path.join(__dirname, 'MIGRATION-README.md'), path.join(outputDir, 'README.md'))
  const verificationSql = [
    '-- Run in the selected production database pyhpyh after importing and fixing paths.',
    ...tables.map(t => `SELECT '${t}' AS table_name, COUNT(*) AS actual_count, ${t === 'runtime_app_session' ? 0 : rowCounts[t]} AS expected_count FROM \`${t}\`;`),
    "SELECT COUNT(*) AS remaining_windows_version_paths FROM app_version WHERE sourcePath REGEXP '^[A-Za-z]:';",
    "SELECT COUNT(*) AS orphan_apps FROM app a LEFT JOIN user u ON a.userId=u.id WHERE u.id IS NULL;",
    "SELECT COUNT(*) AS orphan_versions FROM app_version v LEFT JOIN app a ON v.appId=a.id WHERE a.id IS NULL;",
  ].join('\n') + '\n'
  fs.writeFileSync(path.join(outputDir, '03-verify.sql'), verificationSql, 'utf8')
  for (const name of ['01-database.sql', '02-fix-version-paths.sql', '03-verify.sql', 'README.md', 'manifest.json']) {
    fileManifest.push({ path: name, sha256: hash(path.join(outputDir, name)) })
  }
  fs.writeFileSync(path.join(outputDir, 'SHA256SUMS'), fileManifest.map(f => `${f.sha256}  ${f.path}`).join('\n') + '\n')
  const zipPath = outputDir + '.zip'
  run('tar', ['-a', '-cf', zipPath, '-C', outputDir, '.'])
  const archiveEntries = run('tar', ['-tf', zipPath]).trim().split(/\r?\n/).map(p => p.replace(/^\.\//, ''))
  for (const item of fileManifest) {
    if (!archiveEntries.includes(item.path)) throw new Error(`Archive entry missing: ${item.path}`)
  }
  const zipSha256 = hash(zipPath)
  fs.writeFileSync(zipPath + '.sha256', `${zipSha256}  ${path.basename(zipPath)}\n`)
  console.log(JSON.stringify({ packageDirectory: outputDir, archive: zipPath, bytes: fs.statSync(zipPath).size,
    sha256: zipSha256, rowCounts, liveData, generatedFiles: copiedFiles.length, warnings, verification }, null, 2))
}

main().catch(error => { console.error(error.message); process.exitCode = 1 })
