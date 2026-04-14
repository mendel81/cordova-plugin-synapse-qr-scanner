'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const ROOT = path.resolve(__dirname, '..');
const FIREBASEX_VERSION = '20.0.1';
const CORDOVA_CLI_PACKAGE = 'cordova@13.0.0';
const CORDOVA_ANDROID_VERSION = '15.0.0';
const CORDOVA_IOS_VERSION = '8.0.1';
const GRADLE8_BIN = '/opt/homebrew/opt/gradle@8/bin';
const OPENJDK21_HOME = '/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home';

function parseArgs(argv) {
  const platforms = [];
  let withFirebasex = false;
  let cordovaCliPackage = null;

  for (let index = 0; index < argv.length; index += 1) {
    const token = argv[index];
    if (token === '--platform' && argv[index + 1]) {
      platforms.push(argv[index + 1]);
      index += 1;
    } else if (token === '--with-firebasex') {
      withFirebasex = true;
    } else if (token === '--cordova-cli-package' && argv[index + 1]) {
      cordovaCliPackage = argv[index + 1];
      index += 1;
    }
  }

  return {
    platforms: platforms.length ? platforms : ['android'],
    withFirebasex: withFirebasex,
    cordovaCliPackage: cordovaCliPackage
  };
}

function run(command, args, cwd) {
  const env = Object.assign({}, process.env);

  if (fs.existsSync(GRADLE8_BIN)) {
    env.PATH = GRADLE8_BIN + path.delimiter + env.PATH;
  }

  if (fs.existsSync(OPENJDK21_HOME)) {
    env.JAVA_HOME = OPENJDK21_HOME;
  }

  const result = spawnSync(command, args, {
    cwd: cwd,
    stdio: 'inherit',
    env: env
  });

  if (result.status !== 0) {
    throw new Error(command + ' ' + args.join(' ') + ' failed with exit code ' + result.status);
  }
}

function runCordova(cordovaCliPackage, args, cwd) {
  if (cordovaCliPackage) {
    run('npx', ['-y', cordovaCliPackage].concat(args), cwd);
    return;
  }

  run('cordova', args, cwd);
}

function writeExampleAssets(appDir) {
  const wwwDir = path.join(appDir, 'www');
  fs.mkdirSync(path.join(wwwDir, 'js'), { recursive: true });
  fs.copyFileSync(path.join(ROOT, 'example-app', 'www', 'index.html'), path.join(wwwDir, 'index.html'));
  fs.copyFileSync(path.join(ROOT, 'example-app', 'www', 'js', 'index.js'), path.join(wwwDir, 'js', 'index.js'));
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'native-code-scanner-smoke-'));
  const appDir = path.join(tempDir, 'app');
  const cordovaCliPackage = args.cordovaCliPackage;

  console.log('Creating temporary Cordova app in', appDir);
  runCordova(cordovaCliPackage, ['create', appDir, 'com.acme.nativecodescanner.smoke', 'NativeScannerSmoke'], ROOT);
  writeExampleAssets(appDir);

  args.platforms.forEach(function eachPlatform(platform) {
    const version = platform === 'ios' ? CORDOVA_IOS_VERSION : CORDOVA_ANDROID_VERSION;
    runCordova(cordovaCliPackage, ['platform', 'add', platform + '@' + version], appDir);
  });

  runCordova(cordovaCliPackage, ['plugin', 'add', ROOT], appDir);

  if (args.withFirebasex) {
    runCordova(cordovaCliPackage, ['plugin', 'add', 'cordova-plugin-firebasex@' + FIREBASEX_VERSION], appDir);
    console.log('Firebasex installed. Full native build still requires valid Firebase config files in the smoke app.');
  }

  args.platforms.forEach(function eachPlatform(platform) {
    runCordova(cordovaCliPackage, ['prepare', platform], appDir);
  });

  args.platforms.forEach(function eachPlatform(platform) {
    if (platform === 'android' && !args.withFirebasex) {
      runCordova(cordovaCliPackage, ['build', 'android'], appDir);
    }

    if (platform === 'ios' && !args.withFirebasex) {
      runCordova(cordovaCliPackage, ['build', 'ios', '--simulator'], appDir);
    }
  });

  console.log('Smoke test completed successfully:', {
    appDir: appDir,
    withFirebasex: args.withFirebasex,
    platforms: args.platforms,
    cordovaCliPackage: cordovaCliPackage || 'global'
  });
}

main();
