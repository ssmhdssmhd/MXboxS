#!/usr/bin/env node
/**
 * ============================================================
 * MXbox APK 一键打包工具 (Node.js 版 — 带进度条)
 *
 * 用法:
 *   node build_apk.js [flavor] [abi]
 *
 *   flavor: leanback (电视版) | mobile (手机版) | all (默认)
 *   abi:    arm64_v8a (64位) | armeabi_v7a (32位) | all (默认)
 *
 * 示例:
 *   node build_apk.js                        # 全部 4 个 APK
 *   node build_apk.js leanback arm64_v8a     # 电视版 64 位
 *   node build_apk.js mobile all             # 手机版 32+64
 *   npm run build:tv64                       # 等价上面第一条
 * ============================================================
 */

import { spawn } from 'node:child_process';
import { existsSync, mkdirSync, writeFileSync, readFileSync, copyFileSync, statSync, readdirSync, chmodSync } from 'node:fs';
import { join, dirname, resolve, basename } from 'node:path';
import { fileURLToPath } from 'node:url';

// ─── 路径常量 ───
const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const SCRIPT_DIR = __dirname;
const PROJECT_DIR = resolve(SCRIPT_DIR, '..');

// ─── 颜色输出（不依赖 chalk，纯 ANSI） ───
const c = {
  reset: '\x1b[0m',
  bold: '\x1b[1m',
  dim: '\x1b[2m',
  red: '\x1b[31m',
  green: '\x1b[32m',
  yellow: '\x1b[33m',
  blue: '\x1b[34m',
  magenta: '\x1b[35m',
  cyan: '\x1b[36m',
  white: '\x1b[37m',
  bg_blue: '\x1b[44m',
  bg_green: '\x1b[42m',
  bg_red: '\x1b[41m',
};

function log(icon, msg, color = c.reset) {
  const ts = new Date().toLocaleTimeString('zh-CN', { hour12: false });
  console.log(`${c.dim}[${ts}]${c.reset} ${color}${icon}${c.reset} ${msg}`);
}

// ─── 解析参数 ───
const flavor = process.argv[2] || 'all';
const abi = process.argv[3] || 'all';

// ─── 构建任务列表 ───
function cap(s) { return s.charAt(0).toUpperCase() + s.slice(1); }

function buildTaskList(flavor, abi) {
  const tasks = [];
  const add = (f, a) => tasks.push({
    name: `MXbox-${f}-${a}`,
    flavor: f,
    abi: a,
    // Gradle 任务名: assemble<Flavor><Abi>Release
    // ABI 保留原始下划线: arm64_v8a -> Arm64_v8a
    gradleTask: `:app:assemble${cap(f)}${cap(a)}Release`,
  });

  const flavors = flavor === 'all' ? ['leanback', 'mobile'] : [flavor];
  const abis = abi === 'all' ? ['arm64_v8a', 'armeabi_v7a'] : [abi];

  for (const f of flavors) {
    for (const a of abis) {
      add(f, a);
    }
  }
  return tasks;
}

// ─── 进度条类 ───
class ProgressBar {
  constructor(total, label) {
    this.total = total;
    this.current = 0;
    this.label = label;
    this.width = 40;
    this.startTime = Date.now();
    this.lastRender = 0;
    this.render();
  }

  update(current, status = '') {
    this.current = Math.min(current, this.total);
    const now = Date.now();
    if (now - this.lastRender > 100 || this.current >= this.total) {
      this.render(status);
      this.lastRender = now;
    }
  }

  render(status = '') {
    const pct = Math.floor((this.current / this.total) * 100);
    const filled = Math.floor((this.current / this.total) * this.width);
    const empty = this.width - filled;
    const bar = '█'.repeat(filled) + '░'.repeat(empty);
    const elapsed = ((Date.now() - this.startTime) / 1000).toFixed(1);

    let etaStr = '';
    if (this.current > 0 && this.current < this.total) {
      const rate = this.current / (Date.now() - this.startTime) * 1000;
      const eta = Math.ceil((this.total - this.current) / rate);
      etaStr = ` ETA ${eta}s`;
    }

    const pctColor = pct === 100 ? c.green : pct >= 50 ? c.cyan : c.yellow;
    const line = `\r${c.bold}${this.label}${c.reset} ${pctColor}[${bar}]${c.reset} ${pct}% ${c.dim}(${this.current}/${this.total}) ${elapsed}s${etaStr}${c.reset} ${status ? c.dim + status + c.reset : ''}`;

    // 清除行并重绘
    process.stdout.write('\r\x1b[K');
    process.stdout.write(line);

    if (pct === 100) {
      process.stdout.write('\n');
    }
  }
}

// ─── 检测 Python 3.10（Chaquopy 插件需要） ───
function findPython310() {
  const candidates = [
    '/usr/bin/python3.10',
    '/usr/local/bin/python3.10',
  ];
  // pyenv 路径
  const pyenvRoot = process.env.PYENV_ROOT || join(process.env.HOME || '/root', '.pyenv');
  if (existsSync(join(pyenvRoot, 'versions'))) {
    try {
      for (const ver of readdirSync(join(pyenvRoot, 'versions'))) {
        candidates.push(join(pyenvRoot, 'versions', ver, 'bin', 'python3.10'));
      }
    } catch (_) {}
  }
  for (const p of candidates) {
    if (existsSync(p)) return p;
  }
  return null;
}

// ─── 检查环境 ───
function checkEnvironment() {
  log('🔍', '检查构建环境...', c.cyan);

  const checks = [
    { path: join(SCRIPT_DIR, 'jbr-21', 'bin', 'java'), label: 'JetBrains JBR 21' },
    { path: join(SCRIPT_DIR, 'android-sdk', 'platforms', 'android-37'), label: 'Android SDK Platform 37' },
    { path: join(SCRIPT_DIR, 'android-sdk', 'build-tools', '37.0.0'), label: 'Android Build Tools 37' },
    { path: join(SCRIPT_DIR, 'config', 'release.keystore'), label: '签名密钥' },
    { path: join(PROJECT_DIR, 'gradlew'), label: 'Gradle Wrapper 脚本' },
  ];

  let allOk = true;
  for (const chk of checks) {
    const ok = existsSync(chk.path);
    const icon = ok ? '✅' : '❌';
    const color = ok ? c.green : c.red;
    console.log(`   ${icon} ${color}${chk.label}${c.reset} ${c.dim}${chk.path}${c.reset}`);
    if (!ok) allOk = false;
  }

  if (!allOk) {
    log('❌', '环境检查失败，请确认 BuildTools 已完整解压', c.red);
    process.exit(1);
  }

  // 检测 Python 3.10（Chaquopy 插件依赖，缺失则警告）
  const python310 = findPython310();
  if (python310) {
    console.log(`   ${c.green}✅${c.reset} ${c.green}Python 3.10${c.reset} ${c.dim}${python310}${c.reset}`);
  } else {
    console.log(`   ${c.yellow}⚠️${c.reset} ${c.yellow}Python 3.10 未找到${c.reset} ${c.dim}(Chaquopy 插件需要，请安装 pyenv 或系统 python3.10)${c.reset}`);
  }

  log('✅', '环境检查通过', c.green);

  // 自动接受 Android SDK 许可协议（避免 build-tools 等组件下载失败）
  ensureSdkLicenses();
}

// ─── 确保 SDK 许可协议已接受 ───
function ensureSdkLicenses() {
  const licensesDir = join(SCRIPT_DIR, 'android-sdk', 'licenses');
  mkdirSync(licensesDir, { recursive: true });

  const licenseFiles = {
    'android-sdk-license': [
      '8933bad161af417811c84d56cebcd0da18d802ab',
      'd56f5187479451eabf01fb78af6dfcb131a6481e',
      '24333f8a63b6825ea9c5514f83c2829b004d1fee',
    ],
    'android-sdk-preview-license': [
      '84831b9409646a918e30573bab4c9c91346d8abd',
    ],
  };

  for (const [file, hashes] of Object.entries(licenseFiles)) {
    const filePath = join(licensesDir, file);
    const content = '\n' + hashes.join('\n') + '\n';
    if (!existsSync(filePath)) {
      writeFileSync(filePath, content);
    }
  }
}

// ─── 写配置文件 ───
function writeConfigs() {
  log('📝', '写入 local.properties...', c.cyan);
  const localProps = [
    `sdk.dir=${join(SCRIPT_DIR, 'android-sdk')}`,
    `storeFile=${join(SCRIPT_DIR, 'config', 'release.keystore')}`,
    `keyAlias=release`,
    `storePassword=123456`,
    `keyPassword=123456`,
  ].join('\n') + '\n';
  writeFileSync(join(PROJECT_DIR, 'local.properties'), localProps);

  // 备份并写入 gradle.properties
  const gpPath = join(PROJECT_DIR, 'gradle.properties');
  const gpBakPath = join(PROJECT_DIR, 'gradle.properties.bak_bt');
  let baseContent = '';
  if (existsSync(gpBakPath)) {
    baseContent = readFileSync(gpBakPath, 'utf-8');
  } else if (existsSync(gpPath)) {
    baseContent = readFileSync(gpPath, 'utf-8');
    writeFileSync(gpBakPath, baseContent);
  }
  // 移除旧的注入标记部分
  baseContent = baseContent.split('# ---------- BuildTools')[0].trimEnd();
  const injected = baseContent + '\n\n' + [
    '# ---------- BuildTools 自动注入 (build_apk.js) ----------',
    `org.gradle.java.home=${join(SCRIPT_DIR, 'jbr-21')}`,
    'org.gradle.java.installations.auto-download=false',
    'org.gradle.java.installations.auto-detect=false',
    `org.gradle.java.installations.paths=${join(SCRIPT_DIR, 'jbr-21')}`,
    '# --------------------------------------------------------',
  ].join('\n') + '\n';
  writeFileSync(gpPath, injected);

  // 写入 GRADLE_USER_HOME/gradle.properties（含代理设置，从环境变量自动检测）
  const gradleUserHome = join(SCRIPT_DIR, 'gradle-wrapper');
  const guhProps = [
    '# BuildTools 自动生成 — GRADLE_USER_HOME 配置',
    'org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8',
  ];

  // 检测代理设置（HTTP_PROXY / HTTPS_PROXY / http_proxy / https_proxy）
  const proxyUrl = process.env.HTTPS_PROXY || process.env.https_proxy || process.env.HTTP_PROXY || process.env.http_proxy;
  if (proxyUrl) {
    try {
      const u = new URL(proxyUrl);
      guhProps.push('');
      guhProps.push('# 代理设置（从环境变量自动检测）');
      guhProps.push(`systemProp.http.proxyHost=${u.hostname}`);
      guhProps.push(`systemProp.http.proxyPort=${u.port || 8080}`);
      guhProps.push(`systemProp.https.proxyHost=${u.hostname}`);
      guhProps.push(`systemProp.https.proxyPort=${u.port || 8080}`);
      guhProps.push('systemProp.http.nonProxyHosts=localhost|127.0.0.1|*.svc|*.cluster.local');
      guhProps.push('systemProp.https.nonProxyHosts=localhost|127.0.0.1|*.svc|*.cluster.local');
      log('🔌', `检测到代理: ${u.hostname}:${u.port || 8080}`, c.yellow);
    } catch (_) {
      log('⚠️', `代理 URL 解析失败: ${proxyUrl}`, c.yellow);
    }
  }
  writeFileSync(join(gradleUserHome, 'gradle.properties'), guhProps.join('\n') + '\n');

  // 确保 gradlew 可执行
  try {
    chmodSync(join(PROJECT_DIR, 'gradlew'), 0o755);
  } catch (_) {}

  log('✅', '配置文件已写入', c.green);
}

// ─── 运行单个 Gradle 任务（带进度条） ───
function runGradleTask(task, index, totalTasks) {
  return new Promise((resolvePromise, rejectPromise) => {
    const javaHome = join(SCRIPT_DIR, 'jbr-21');
    const androidHome = join(SCRIPT_DIR, 'android-sdk');
    const gradleUserHome = join(SCRIPT_DIR, 'gradle-wrapper');

    // 构建 PATH：JBR + SDK + Python 3.10（如有）+ 系统 PATH
    const pathParts = [
      `${javaHome}/bin`,
      `${androidHome}/cmdline-tools/latest/bin`,
      `${androidHome}/platform-tools`,
    ];
    const python310 = findPython310();
    if (python310) {
      pathParts.push(dirname(python310));
    }
    pathParts.push(process.env.PATH || '');

    const env = {
      ...process.env,
      JAVA_HOME: javaHome,
      ANDROID_HOME: androidHome,
      ANDROID_SDK_ROOT: androidHome,
      GRADLE_USER_HOME: gradleUserHome,
      PATH: pathParts.join(':'),
    };

    const args = [task.gradleTask, '--no-daemon'];
    // 设置 Chaquopy buildPython（通过 Gradle 属性）
    if (python310) {
      args.push(`-Pchaquopy.buildPython=${python310}`);
    }
    const gradlewPath = join(PROJECT_DIR, 'gradlew');

    const header = `${c.bold}${c.magenta}[${index}/${totalTasks}]${c.reset} ${c.cyan}${task.name}${c.reset}`;
    console.log(`\n${header}`);
    console.log(`   ${c.dim}Gradle Task: ${task.gradleTask}${c.reset}`);

    // 进度条（模拟，因为 Gradle 不提供精确进度）
    const progress = new ProgressBar(100, '   编译进度');
    let progressValue = 0;
    let phase = '初始化';

    const progressTimer = setInterval(() => {
      if (progressValue < 90) {
        // 模拟进度增长，越接近 90 越慢
        const increment = progressValue < 30 ? 2 : progressValue < 60 ? 1.5 : progressValue < 80 ? 0.8 : 0.3;
        progressValue += increment;
        progress.update(Math.floor(progressValue), phase);
      }
    }, 500);

    const child = spawn('bash', [gradlewPath, ...args], {
      cwd: PROJECT_DIR,
      env,
      stdio: ['ignore', 'pipe', 'pipe'],
    });

    let stdoutData = '';
    let stderrData = '';

    // 解析 Gradle 输出更新阶段
    child.stdout.on('data', (data) => {
      const text = data.toString();
      stdoutData += text;

      // 根据 Gradle 输出更新阶段
      if (text.includes('> Task :app:compile')) { phase = '编译源码'; progressValue = Math.max(progressValue, 20); }
      else if (text.includes('> Task :app:process')) { phase = '处理资源'; progressValue = Math.max(progressValue, 35); }
      else if (text.includes('> Task :app:merge')) { phase = '合并资源'; progressValue = Math.max(progressValue, 50); }
      else if (text.includes('> Task :app:package')) { phase = '打包 APK'; progressValue = Math.max(progressValue, 75); }
      else if (text.includes('> Task :app:assemble')) { phase = '组装完成'; progressValue = Math.max(progressValue, 85); }
      else if (text.includes('BUILD SUCCESSFUL')) { phase = '构建成功'; progressValue = 100; }

      // 显示关键输出行
      const lines = text.trim().split('\n').filter(l => l.trim());
      for (const line of lines) {
        if (line.includes('> Task ') || line.includes('BUILD') || line.includes('WARNING')) {
          process.stdout.write(`   ${c.dim}${line.trim()}${c.reset}\n`);
        }
      }
    });

    child.stderr.on('data', (data) => {
      const text = data.toString();
      stderrData += text;
      // 显示错误/警告行
      const lines = text.trim().split('\n').filter(l => l.trim());
      for (const line of lines) {
        if (line.includes('error') || line.includes('ERROR') || line.includes('e: ')) {
          process.stdout.write(`   ${c.red}${line.trim()}${c.reset}\n`);
        }
      }
    });

    child.on('close', (code) => {
      clearInterval(progressTimer);
      progress.update(100, code === 0 ? '完成' : '失败');

      if (code === 0) {
        log('✅', `${task.name} 构建成功`, c.green);
        resolvePromise({ success: true, stdout: stdoutData, stderr: stderrData });
      } else {
        // 输出最后 30 行日志帮助排查
        const allOutput = stdoutData + stderrData;
        const lines = allOutput.trim().split('\n');
        const lastLines = lines.slice(-30);
        console.log(`\n   ${c.red}${c.bold}构建失败，最后 30 行日志：${c.reset}`);
        for (const line of lastLines) {
          console.log(`   ${c.dim}${line}${c.reset}`);
        }
        rejectPromise(new Error(`${task.name} 构建失败 (exit code: ${code})`));
      }
    });

    child.on('error', (err) => {
      clearInterval(progressTimer);
      progress.update(100, '错误');
      rejectPromise(new Error(`无法启动 gradlew: ${err.message}`));
    });
  });
}

// ─── 收集 APK 产物 ───
function collectApks() {
  const outDir = join(SCRIPT_DIR, 'apk-out');
  mkdirSync(outDir, { recursive: true });

  const found = [];
  const searchDirs = [
    join(PROJECT_DIR, 'Release', 'apk'),
    join(PROJECT_DIR, 'app', 'build', 'outputs', 'apk'),
  ];

  for (const dir of searchDirs) {
    if (!existsSync(dir)) continue;
    walkDir(dir, (filePath) => {
      if (filePath.endsWith('.apk') && filePath.includes('release')) {
        const fileName = basename(filePath);
        const destPath = join(outDir, fileName);
        try {
          copyFileSync(filePath, destPath);
          const size = statSync(destPath).size;
          found.push({ name: fileName, path: destPath, size });
        } catch (_) {}
      }
    });
  }

  return found;
}

function walkDir(dir, callback) {
  if (!existsSync(dir)) return;
  const entries = readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = join(dir, entry.name);
    if (entry.isDirectory()) {
      walkDir(fullPath, callback);
    } else {
      callback(fullPath);
    }
  }
}

function formatSize(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

// ─── 主函数 ───
async function main() {
  console.log();
  console.log(`${c.bold}${c.bg_blue}  MXbox APK Build Tool (Node.js)  ${c.reset}`);
  console.log(`${c.dim}  Flavor: ${flavor}  |  ABI: ${abi}${c.reset}`);
  console.log();

  // 1. 环境检查
  checkEnvironment();

  // 2. 写配置
  await writeConfigs();

  // 3. 构建任务列表
  const tasks = buildTaskList(flavor, abi);
  log('📦', `共 ${tasks.length} 个构建任务: ${tasks.map(t => t.name).join(', ')}`, c.cyan);
  console.log();

  // 4. 总进度条
  const totalProgress = new ProgressBar(tasks.length, '总体进度');

  const results = { success: [], failed: [] };

  for (let i = 0; i < tasks.length; i++) {
    try {
      await runGradleTask(tasks[i], i + 1, tasks.length);
      results.success.push(tasks[i].name);
    } catch (err) {
      results.failed.push({ name: tasks[i].name, error: err.message });
      log('❌', err.message, c.red);
      // 继续构建下一个
    }
    totalProgress.update(i + 1, `${i + 1}/${tasks.length} 完成`);
  }

  // 5. 收集 APK
  console.log();
  log('📂', '收集 APK 产物...', c.cyan);
  const apks = collectApks();

  // 6. 汇总
  console.log();
  console.log(`${c.bold}${'═'.repeat(60)}${c.reset}`);
  console.log(`${c.bold}  打包结果汇总${c.reset}`);
  console.log(`${c.bold}${'═'.repeat(60)}${c.reset}`);

  if (results.success.length > 0) {
    console.log(`\n  ${c.green}${c.bold}✅ 成功 (${results.success.length}):${c.reset}`);
    for (const name of results.success) {
      console.log(`     ${c.green}• ${name}${c.reset}`);
    }
  }

  if (results.failed.length > 0) {
    console.log(`\n  ${c.red}${c.bold}❌ 失败 (${results.failed.length}):${c.reset}`);
    for (const f of results.failed) {
      console.log(`     ${c.red}• ${f.name}${c.reset} ${c.dim}- ${f.error}${c.reset}`);
    }
  }

  if (apks.length > 0) {
    console.log(`\n  ${c.cyan}${c.bold}📦 APK 产物 (BuildTools/apk-out/):${c.reset}`);
    for (const apk of apks) {
      console.log(`     ${c.cyan}• ${apk.name}${c.reset} ${c.dim}(${formatSize(apk.size)})${c.reset}`);
    }
  }

  console.log(`\n${c.bold}${'═'.repeat(60)}${c.reset}`);

  if (results.failed.length === 0 && apks.length > 0) {
    log('🎉', `全部构建成功！共产出 ${apks.length} 个 APK`, c.green);
    process.exit(0);
  } else if (results.success.length > 0) {
    log('⚠️', `部分成功: ${results.success.length} 成功, ${results.failed.length} 失败`, c.yellow);
    process.exit(0);
  } else {
    log('❌', '构建失败，请查看上方日志', c.red);
    process.exit(1);
  }
}

// 入口
main().catch((err) => {
  log('💥', `未捕获错误: ${err.message}`, c.red);
  console.error(err.stack);
  process.exit(1);
});
